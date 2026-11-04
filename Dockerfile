# syntax=docker/dockerfile:1.7
#
# Plantilla única para las imágenes de Flety (FLY-29): el mismo Dockerfile arma transporte-service,
# cargas-service y el gateway. Cambia solo el argumento SERVICIO, que es la carpeta del módulo:
#
#   docker build --build-arg SERVICIO=cargas-service -t flety/cargas-service .
#
# El contexto es la raíz del repo porque la imagen necesita dos carpetas: flety-commons y la del servicio.
# El .dockerignore deja entrar solo esas (y nunca el .env).
#
# Multi-stage: la etapa "build" tiene Maven y el JDK completo (cerca de 500 MB) y compila; la etapa final
# copia únicamente el JAR sobre una imagen con solo el JRE. Maven, el código fuente y el repositorio local
# no llegan a la imagen que corre.

ARG JAVA_VERSION=21

# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build

WORKDIR /src

# flety-commons va primero: no está publicada en ningún repositorio Maven, así que hay que instalarla en el
# repositorio local (~/.m2) antes de compilar el servicio, que la declara como dependencia.
# El cache mount guarda ~/.m2 entre builds: las dependencias se bajan una sola vez y no quedan en la imagen.
COPY flety-commons/pom.xml flety-commons/pom.xml
COPY flety-commons/src flety-commons/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -f flety-commons/pom.xml install -DskipTests

# El ARG se declara recién acá: si se declarara antes, cambiar de servicio invalidaría también la capa de
# flety-commons, que es la misma para todos.
# El valor por defecto es el mensaje de error: sin --build-arg, el COPY de abajo falla diciendo
# '"/FALTA-build-arg-SERVICIO/pom.xml": not found'. Un RUN test -n no sirve, porque BuildKit resuelve los COPY
# antes de llegar a ese RUN y el error que se ve es otro, mucho menos claro.
ARG SERVICIO=FALTA-build-arg-SERVICIO

# Primero el pom y después el código: si solo cambia el código, Docker reutiliza la capa del pom.
COPY ${SERVICIO}/pom.xml ${SERVICIO}/pom.xml
COPY ${SERVICIO}/src ${SERVICIO}/src

# Los tests no corren acá: se corren con mvn test antes de construir la imagen. Correrlos en cada build
# haría la imagen mucho más lenta de armar sin dar nada nuevo.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -f ${SERVICIO}/pom.xml package -DskipTests \
    && cp ${SERVICIO}/target/*.jar /app.jar

# ---------- Etapa 2: correr ----------
FROM eclipse-temurin:${JAVA_VERSION}-jre AS runtime

# Un usuario sin privilegios: si alguien explotara una falla del servicio, no sería root dentro del contenedor.
RUN groupadd --system flety && useradd --system --gid flety --no-create-home flety

WORKDIR /app
COPY --from=build /app.jar app.jar
USER flety

# JAVA_OPTS permite ajustar la JVM desde el compose (memoria, perfiles) sin reconstruir la imagen.
# exec reemplaza al shell por java: así java es el proceso 1 y recibe el SIGTERM de docker stop para
# apagarse ordenado (cerrar conexiones, terminar los pedidos en curso).
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
