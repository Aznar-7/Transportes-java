-- Tipos de documento (FLY-9). Corre en cada arranque: ON CONFLICT no repite los que ya están por el nombre único.
INSERT INTO tipo_documento (nombre, aplica_a, obligatorio, vence, requiere_suma_asegurada) VALUES
    ('Póliza de responsabilidad civil', 'UNIDAD', TRUE, TRUE, FALSE),
    ('Póliza de carga', 'UNIDAD', TRUE, TRUE, TRUE),
    ('Revisión técnica', 'UNIDAD', TRUE, TRUE, FALSE),
    ('Licencia profesional', 'TRANSPORTISTA', TRUE, TRUE, FALSE)
ON CONFLICT DO NOTHING;
