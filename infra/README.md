# Infraestructura local

Postgres y Keycloak se levantan con un comando, usando el [`docker-compose.yml`](../docker-compose.yml) de la raíz del repo.

## Requisitos

- Docker Desktop (o Docker Engine con el plugin `docker compose`).
- Libres los puertos `5432` (Postgres) y `8081` (Keycloak).

## Levantar

Desde la raíz del repo:

```bash
cp .env.example .env      # solo la primera vez; en PowerShell: Copy-Item .env.example .env
docker compose up -d
```

`.env` tiene las contraseñas y no se sube al repo. Si falta alguna variable, el compose lo avisa y no arranca.

Keycloak tarda unos 20 segundos en quedar listo. Para seguir el arranque:

```bash
docker compose logs -f keycloak    # listo cuando aparece "Listening on: http://0.0.0.0:8080"
```

## Qué queda andando

| Servicio | Desde la máquina | Desde la red `flety-net` | Para qué |
|---|---|---|---|
| Postgres 16 | `localhost:5432` | `postgres:5432` | Una base por servicio |
| Keycloak 26 | `http://localhost:8081` | `http://keycloak:8080` | Realm `flety`, tokens |

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

- **`port is already allocated`**: ya hay algo en el 5432 o el 8081 (por ejemplo, un Postgres instalado en la máquina). Frenalo o cambiá el puerto de la izquierda en el compose.
- **`password authentication failed`** después de cambiar el `.env`: el usuario se creó con la contraseña vieja. Corré `docker compose down -v` y volvé a levantar.
- **`init-db.sql` falla con errores raros de sintaxis**: puede que el archivo tenga saltos de línea de Windows (CRLF). El `.gitattributes` lo evita, pero si lo editaste a mano, guardalo con LF.
