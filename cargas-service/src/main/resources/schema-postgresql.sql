-- Lo corre Spring al arrancar (spring.sql.init), después de que Hibernate crea o actualiza las tablas
-- (spring.jpa.defer-datasource-initialization). Es idempotente: se puede correr en cada arranque.
-- Las sentencias se separan con @@ y no con punto y coma, porque el cuerpo de la función lleva punto y coma adentro.

-- evento_viaje es la auditoría append-only del viaje (FLY-25). La aplicación ya no la modifica (columnas
-- updatable = false y @PreRemove), pero con esto tampoco se puede desde SQL directo: un UPDATE, DELETE o
-- TRUNCATE a mano, un script de mantenimiento con un WHERE equivocado o un bug que salte a JPA.
-- No frena al dueño de la tabla si desactiva el trigger a propósito: es una protección contra errores, no contra él.
CREATE OR REPLACE FUNCTION evento_viaje_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'evento_viaje es append-only: no se permite %', TG_OP
        USING HINT = 'Los eventos del viaje solo se agregan. Para borrar datos de prueba: docker compose down -v';
END;
$$
@@

-- Fila por fila, para UPDATE y DELETE.
CREATE OR REPLACE TRIGGER evento_viaje_sin_cambios
    BEFORE UPDATE OR DELETE ON evento_viaje
    FOR EACH ROW EXECUTE FUNCTION evento_viaje_append_only()
@@

-- TRUNCATE no dispara los triggers de fila: necesita uno propio, por sentencia.
CREATE OR REPLACE TRIGGER evento_viaje_sin_truncate
    BEFORE TRUNCATE ON evento_viaje
    FOR EACH STATEMENT EXECUTE FUNCTION evento_viaje_append_only()
@@
