# Infraestructura local

Todo Flety (Postgres, Keycloak, transporte-service, cargas-service y el gateway) se levanta con un comando, usando el [`docker-compose.yml`](../docker-compose.yml) de la raíz del repo.

## Requisitos

- Docker Desktop (o Docker Engine con el plugin `docker compose` y BuildKit, que viene activado por defecto).
- Libres los puertos `8081` (Keycloak) y `8085` (gateway).

## Levantar

Desde la raíz del repo:

```bash
cp .env.example .env      # solo la primera vez; en PowerShell: Copy-Item .env.example .env
docker compose up -d --build
```

`--build` arma las imágenes de los servicios con el código actual. La primera vez tarda un par de minutos (baja Maven y las dependencias); después, segundos.

El gateway (FLY-14) se levanta con el resto. Las rutas y el rol que exige cada una están en [`docs/gateway/rutas.md`](../docs/gateway/rutas.md).

### Solo la infraestructura, para correr un servicio desde el IDE

El compose principal no publica el puerto de Postgres. [`docker-compose.dev.yml`](../docker-compose.dev.yml) lo agrega:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres keycloak
```

Así el servicio que corrés en la máquina se conecta a `localhost:5432`, como antes.

`.env` tiene las contraseñas y no se sube al repo. Si falta alguna variable, el compose lo avisa y no arranca.

Keycloak tarda unos 20 segundos en quedar listo. Para seguir el arranque:

```bash
docker compose logs -f keycloak    # listo cuando aparece "Listening on: http://0.0.0.0:8080"
```

## Qué queda andando

| Servicio | Desde la máquina | Desde la red `flety-net` | Para qué |
|---|---|---|---|
| Gateway | `http://localhost:8085` | `http://gateway-service:8085` | La única entrada a la API |
| Keycloak 26 | `http://localhost:8081` | `http://keycloak:8080` | Realm `flety`, tokens |
| transporte-service | no se publica | `http://transporte-service:8091` | Transportistas, unidades, retornos |
| cargas-service | no se publica | `http://cargas-service:8092` | Cargas, propuestas y viajes |
| Postgres 16 | no se publica (`localhost:5432` con `docker-compose.dev.yml`) | `postgres:5432` | Una base por servicio |

**Solo el gateway y Keycloak publican puertos.** Los servicios se hablan por la red interna con el nombre del servicio, y desde afuera no hay forma de llegarles salteando el gateway y su control de rol. Keycloak se publica porque el cliente le pide el token directamente. La base tampoco se publica: nadie se conecta a ella sin pasar por un servicio.

Cada servicio recibe su configuración por variables de entorno del compose. Las contraseñas salen del `.env`, las mismas que `init-db.sql` usa para crear los usuarios:

| Servicio | Variables |
|---|---|
| transporte-service | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (de `TRANSPORTE_DB_PASSWORD`), `SPRING_JPA_HIBERNATE_DDL_AUTO`, `JWK_SET_URI` |
| cargas-service | `DB_URL`, `DB_USER`, `DB_PASSWORD` (de `CARGAS_DB_PASSWORD`), `TRANSPORTE_URL`, `JWK_SET_URI` |
| gateway-service | `TRANSPORTE_URL`, `CARGAS_URL`, `JWK_SET_URI` |

`JWK_SET_URI` es la URL interna de Keycloak (`keycloak:8080`). Los servicios no validan el emisor, así que el token que se pide desde la máquina (emisor `localhost:8081`) vale igual adentro de la red.

### Postgres

[`postgres/init-db.sql`](postgres/init-db.sql) crea una base y un usuario por servicio. Cada usuario es dueño de su base y no puede conectarse a la del otro:

| Base | Usuario | Contraseña (variable de `.env`) |
|---|---|---|
| `transporte_db` | `transporte` | `TRANSPORTE_DB_PASSWORD` |
| `cargas_db` | `cargas` | `CARGAS_DB_PASSWORD` |

Para entrar a mirar:

```bash
docker exec -it flety-postgres psql -U cargas -d cargas_db
```

El script corre **solo la primera vez**, cuando el volumen `postgres-data` está vacío. Si cambiás `init-db.sql` o las contraseñas del `.env`, hay que borrar el volumen (ver abajo).

### Keycloak

Al arrancar importa el realm de [`keycloak/flety-realm.json`](keycloak/flety-realm.json) (el del FLY-2: roles, cliente `flety-test` y usuarios de prueba). Cómo pedir un token está en [`keycloak/README.md`](keycloak/README.md).

- Consola de administración: http://localhost:8081/admin (usuario `admin`, contraseña `KEYCLOAK_ADMIN_PASSWORD` del `.env`).
- El realm se vuelve a importar en cada arranque: lo que se cambie a mano en la consola se pierde. Los cambios permanentes van al JSON.
- El emisor (`iss`) de los tokens es siempre `http://localhost:8081/realms/flety`, aunque el token se pida desde adentro de la red de Docker.

## Imágenes de los servicios

Hay un solo [`Dockerfile`](../Dockerfile), en la raíz, que sirve de plantilla para todos los servicios: cambia solo el argumento `SERVICIO`, que es la carpeta del módulo.

```bash
docker build --build-arg SERVICIO=cargas-service -t flety/cargas-service .
docker build --build-arg SERVICIO=transporte-service -t flety/transporte-service .
```

- **Multi-stage.** La etapa `build` (Maven + JDK) compila y la etapa final copia solo el JAR sobre una imagen con el JRE. Con cargas-service, la etapa de compilación pesa 1,03 GB y la imagen final 568 MB: Maven, el código y las dependencias de compilación no viajan.
- **flety-commons primero.** La librería común no está publicada en ningún repositorio Maven: la imagen la instala (`mvn install`) antes de compilar el servicio que la usa.
- **El contexto es la raíz del repo**, porque hacen falta dos carpetas: `flety-commons` y la del servicio. El [`.dockerignore`](../.dockerignore) es una lista de lo que sí entra; el `.env`, el `.git` y los `target/` nunca llegan al build.
- **Caché de Maven.** Las dependencias se guardan en un cache mount de BuildKit: se bajan una vez y los builds siguientes tardan segundos.
- **Usuario sin privilegios.** El servicio corre como `flety`, no como root.
- **Los tests no corren en el build:** se corren antes, con `mvn test`.

## Bajar y empezar de cero

```bash
docker compose down        # frena y borra los contenedores; los datos de Postgres quedan en el volumen
docker compose down -v     # además borra el volumen: la próxima vez init-db.sql vuelve a correr
```

## Problemas comunes

- **`port is already allocated`**: ya hay algo en el 8081 o el 8085 (o en el 5432, si usás `docker-compose.dev.yml`; por ejemplo, un Postgres instalado en la máquina). Frenalo o cambiá el puerto de la izquierda en el compose.
- **`"/FALTA-build-arg-SERVICIO/...": not found`**: se corrió `docker build` sin `--build-arg SERVICIO=...`.
- **`password authentication failed`** después de cambiar el `.env`: el usuario se creó con la contraseña vieja. Corré `docker compose down -v` y volvé a levantar.
- **`init-db.sql` falla con errores raros de sintaxis**: puede que el archivo tenga saltos de línea de Windows (CRLF). El `.gitattributes` lo evita, pero si lo editaste a mano, guardalo con LF.
