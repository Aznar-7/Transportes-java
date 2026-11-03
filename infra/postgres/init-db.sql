-- Lo ejecuta la imagen de Postgres una sola vez, cuando el volumen está vacío
-- (todo lo que esté en /docker-entrypoint-initdb.d corre en el primer arranque).
--
-- Una base y un usuario por servicio: cada microservicio es dueño de sus datos
-- y no puede leer ni escribir la base del otro.
--
-- Las contraseñas no están en el repo: \getenv las lee de las variables de
-- entorno del contenedor, que el compose toma del archivo .env.

\set ON_ERROR_STOP on

\getenv transporte_password TRANSPORTE_DB_PASSWORD
\getenv cargas_password CARGAS_DB_PASSWORD

CREATE USER transporte WITH PASSWORD :'transporte_password';
CREATE DATABASE transporte_db OWNER transporte;
REVOKE ALL ON DATABASE transporte_db FROM PUBLIC;

CREATE USER cargas WITH PASSWORD :'cargas_password';
CREATE DATABASE cargas_db OWNER cargas;
REVOKE ALL ON DATABASE cargas_db FROM PUBLIC;
