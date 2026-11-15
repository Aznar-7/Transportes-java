# Despliegue

Cómo se arma y se levanta todo Flety en una máquina: los tres servicios, el gateway, Postgres y Keycloak, con un solo `docker compose`. La referencia de cada pieza está en [infra/README.md](../infra/README.md); esta es la guía paso a paso.

## Qué se levanta

```
                 :8085                             red interna flety-net
cliente ──────> gateway-service ──┬──> transporte-service :8091 ──┐
   │                              └──> cargas-service :8092 ──────┼──> postgres :5432
   │                                        │  (contrato /internal)│     transporte_db, cargas_db
   │                                        └──> transporte-service┘
   └── token ──> keycloak :8081 (realm flety)
```

| Contenedor | Imagen | Puerto en la máquina | Para qué |
|---|---|---|---|
| `flety-gateway` | `flety/gateway-service` (se construye) | 8085 | La única entrada a la API |
| `flety-keycloak` | `quay.io/keycloak/keycloak:26.0` | 8081 | Tokens (realm `flety`) |
| `flety-transporte` | `flety/transporte-service` (se construye) | ninguno | Transportistas, unidades, documentos, retornos |
| `flety-cargas` | `flety/cargas-service` (se construye) | ninguno | Dadores, cargas, propuestas, viajes |
| `flety-postgres` | `postgres:16` | ninguno | Una base y un usuario por servicio |

Solo el gateway y Keycloak publican puertos: a los servicios y a la base no se llega desde afuera, solo por la red interna.

## Requisitos

- Docker Desktop, o Docker Engine con el plugin `docker compose` y BuildKit (viene activado).
- Libres los puertos 8081 y 8085.
- Para correr un servicio fuera de Docker: Java 21 y Maven 3.9.

No hace falta instalar Java ni Maven para levantar todo con Docker: las imágenes compilan adentro.

## Levantar todo

Desde la raíz del repo:

**1. Crear el `.env`** (solo la primera vez):

```bash
cp .env.example .env          # en PowerShell: Copy-Item .env.example .env
```

El `.env` tiene las contraseñas y está en `.gitignore`: nunca se sube. Si falta una variable, el compose avisa cuál y no arranca.

| Variable | Para qué |
|---|---|
| `POSTGRES_PASSWORD` | Superusuario de Postgres, solo para administrar |
| `TRANSPORTE_DB_PASSWORD`, `CARGAS_DB_PASSWORD` | Usuario de cada servicio: `init-db.sql` los crea con estas contraseñas y el compose se las pasa a cada servicio |
| `KEYCLOAK_ADMIN_PASSWORD` | Consola de Keycloak (`http://localhost:8081/admin`, usuario `admin`) |

**2. Construir y levantar:**

```bash
docker compose up -d --build
```

Qué pasa, en orden:

1. **Se construyen las tres imágenes** con el mismo [`Dockerfile`](../Dockerfile) de plantilla (ver abajo). La primera vez tarda unos minutos porque baja Maven y las dependencias; después, segundos.
2. **Arranca Postgres.** La primera vez, con el volumen vacío, `infra/postgres/init-db.sql` crea `transporte_db` y `cargas_db`, cada una con su usuario.
3. **Arranca Keycloak** e importa el realm de `infra/keycloak/flety-realm.json`: roles, el cliente `flety-test` y los usuarios de prueba.
4. **Arrancan transporte y cargas** cuando Postgres está sano. Hibernate crea las tablas; transporte carga los tipos de documento (`data.sql`) y cargas el trigger append-only de `evento_viaje` y el índice parcial de viajes (`schema-postgresql.sql`). Los dos scripts se pueden correr en cada arranque.
5. **Arranca el gateway.**

**3. Ver que esté todo andando:**

```bash
docker compose ps
docker compose logs -f cargas-service     # listo cuando aparece "Started CargasServiceApplication"
```

Keycloak tarda unos 20 segundos, y cada servicio entre 20 y 60 la primera vez.

**4. Probarlo** con un token real, entrando por el gateway:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/flety/protocol/openid-connect/token \
  -d grant_type=password -d client_id=flety-test \
  -d username=dador1 -d password=dador1 | jq -r .access_token)

curl -i http://localhost:8085/api/viajes                                   # 401: sin token
curl -i http://localhost:8085/api/viajes -H "Authorization: Bearer $TOKEN"  # 200: []
curl -i http://localhost:8085/api/transporte/unidades -H "Authorization: Bearer $TOKEN"  # 403: un dador no entra a transporte
```

El primer comando usa `jq` para sacar el `access_token` de la respuesta; sin `jq`, copialo a mano de la respuesta de Keycloak. En PowerShell, el token se pide como en [infra/keycloak/README.md](../infra/keycloak/README.md). Los usuarios de prueba (la contraseña es igual al usuario) son `transportista1`, `transportista2`, `dador1`, `dador2` y `operador1`.

## Bajar y empezar de cero

```bash
docker compose down        # frena y borra los contenedores; los datos de Postgres quedan en el volumen
docker compose down -v     # además borra el volumen: la base y los usuarios se vuelven a crear
```

Usá `down -v` después de cambiar contraseñas en el `.env` o `init-db.sql` (los usuarios se crean solo con el volumen vacío), o para borrar los datos de prueba: `evento_viaje` es append-only y no se puede vaciar con un DELETE.

## Cómo se arma una imagen

Hay un solo `Dockerfile`, en la raíz, que sirve para los tres servicios. Cambia solo el argumento `SERVICIO`, que es la carpeta del módulo:

```bash
docker build --build-arg SERVICIO=cargas-service -t flety/cargas-service .
```

- **Multi-stage.** La etapa `build` tiene Maven y el JDK: instala `flety-commons` con `mvn install` (no está publicada en ningún repositorio Maven) y después compila el servicio. La etapa final copia solo el JAR sobre una imagen con el JRE: Maven, el código y las dependencias de compilación no llegan a la imagen que corre.
- **El contexto es la raíz del repo**, porque hacen falta `flety-commons` y la carpeta del servicio. El [`.dockerignore`](../.dockerignore) es una lista de lo que sí entra: el `.env`, el `.git` y los `target/` nunca llegan al build.
- **Cache de Maven.** Las dependencias se guardan en un cache mount de BuildKit y no se vuelven a bajar.
- **Corre como el usuario `flety`**, no como root, y con `java` como proceso 1 para apagarse ordenado con `docker stop`.
- **Los tests no corren en el build:** se corren antes, con Maven (ver abajo).

## Configuración

Cada servicio recibe su configuración por variables de entorno del compose; las contraseñas salen del `.env`.

| Servicio | Variables |
|---|---|
| transporte-service | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `JWK_SET_URI` |
| cargas-service | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `TRANSPORTE_URL`, `JWK_SET_URI` |
| gateway-service | `TRANSPORTE_URL`, `CARGAS_URL`, `JWK_SET_URI` |

`JWK_SET_URI` es la URL interna de Keycloak (`http://keycloak:8080/realms/flety/protocol/openid-connect/certs`): con esas claves públicas cada uno valida la firma del token. El emisor del token sigue siendo `localhost:8081`, así que un token pedido desde la máquina vale adentro de la red.

Los relojes de cargas (conformidad a las 48 h, vencimiento de propuestas y cargas) se ajustan con `flety.relojes.*`; ver [viajes/relojes.md](viajes/relojes.md). Para cambiarlos en Docker, por ejemplo para una demo:

```yaml
# en un docker-compose.override.yml
services:
  cargas-service:
    environment:
      SPRING_APPLICATION_JSON: '{"flety":{"relojes":{"conformidad":"PT30S"}}}'
```

Georef y la API de rutas todavía usan sus versiones de prueba (fakes o línea recta), así que hoy no hace falta ninguna clave externa. Cuando se conecte OpenRouteService, su clave va en el `.env` como `OPENROUTESERVICE_API_KEY`, nunca en el repo.

## Correr un servicio fuera de Docker (desarrollo)

Para depurar un servicio desde el IDE contra la base y el Keycloak del compose:

```bash
# 1. Solo la infraestructura, con el puerto de Postgres publicado
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres keycloak

# 2. La librería común, una vez y cada vez que cambie
mvn -f flety-commons/pom.xml install

# 3. El servicio, con la contraseña de su base
DB_PASSWORD=cargas mvn -f cargas-service/pom.xml spring-boot:run
```

`docker-compose.dev.yml` publica el 5432, que el compose principal cierra a propósito. `transporte-service` se corre con el perfil `local` (`application-local.yml`). Para cargas, `TRANSPORTE_URL` apunta por defecto a `http://localhost:8091`.

## Tests

```bash
mvn -f flety-commons/pom.xml install
mvn -f transporte-service/pom.xml test
mvn -f cargas-service/pom.xml test
mvn -f gateway-service/pom.xml test
```

No necesitan Docker: usan H2 en memoria y tokens de prueba. Lo que es propio de Postgres (el trigger de `evento_viaje` y el índice parcial de viajes) se prueba contra el compose.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `port is already allocated` | Algo usa el 8081 o el 8085 (o el 5432, con el archivo dev). Frenalo o cambiá el puerto de la izquierda en el compose |
| `password authentication failed` después de cambiar el `.env` | Los usuarios de la base se crearon con la contraseña vieja: `docker compose down -v` |
| Un usuario ve "Todavía no registraste tu organización" aunque se había registrado | Keycloak se recreó sin los `id` fijos del realm y cambió su `sub`. El realm actual los tiene: `docker compose up -d --force-recreate keycloak` y, si quedaron datos viejos, `docker compose down -v` |
| Los cambios del realm no aparecen | Keycloak no reimporta un realm que ya tiene: `docker compose up -d --force-recreate keycloak` |
| Un servicio no compila fuera de Docker con errores raros de `flety-commons` | Hay una versión vieja instalada en `~/.m2`: `mvn -f flety-commons/pom.xml install` |
| `"/FALTA-build-arg-SERVICIO/...": not found` | Se corrió `docker build` sin `--build-arg SERVICIO=...` |
