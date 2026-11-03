# Consulta de viajes (FLY-25)

Un viaje nace cuando el dador acepta una propuesta (FLY-26). Las dos partes, el dador y el transportista, lo pueden consultar con su estado, los contactos de la otra parte y la línea de tiempo.

| Endpoint | Rol | Respuestas |
|---|---|---|
| `GET /api/viajes` | DADOR o TRANSPORTISTA | 200 con mis viajes (lista vacía si no tengo) |
| `GET /api/viajes/{id}` | DADOR o TRANSPORTISTA | 200 si soy parte, 403 si no, 404 si no existe |

Sin token, o con un token inválido, cualquiera de los dos responde 401.

## Reglas

- **RN-T1, control por dueño.** El rol lo controla el Gateway, pero no sabe de quién es cada viaje: eso lo resuelve cargas-service. Comparo el `sub` del token con el del dador y el del transportista del viaje. Un TRANSPORTISTA que no participa del viaje recibe 403, aunque su rol sea válido.
- **RN-13, contactos y dirección exacta.** Se ven desde que el viaje está CONFIRMADO. Si el viaje se cancela, `contactos` y `direccion` vuelven en `null`.
- **Línea de tiempo append-only.** Cada cambio de estado deja un `EventoViaje` que no se modifica ni se borra (ver más abajo).

## Ejemplo

```bash
curl -s http://localhost:8085/api/viajes/1 -H "Authorization: Bearer $TOKEN"
```

```json
{
  "id": 1,
  "cargaId": 7,
  "propuestaId": 12,
  "estado": "CARGADO",
  "miRol": "DADOR",
  "origen": { "provincia": "Córdoba", "localidad": "Córdoba", "direccion": "Av. Colón 1000" },
  "destino": { "provincia": "Santa Fe", "localidad": "Rosario", "direccion": "Bv. Oroño 500" },
  "cargaDesde": "2026-11-05T11:00:00Z",
  "cargaHasta": "2026-11-05T19:00:00Z",
  "descargaDesde": "2026-11-06T11:00:00Z",
  "descargaHasta": "2026-11-07T11:00:00Z",
  "precioAcordado": 800000.00,
  "contactos": {
    "dador": { "nombre": "Molinos del Centro SA", "telefono": "+54 351 555-9876" },
    "transportista": { "nombre": "Transportes Pérez SRL", "telefono": "+54 351 555-1234" },
    "unidadDominio": "AB123CD"
  },
  "entregadoEn": null,
  "ventanaConformidadHasta": null,
  "conformadoPor": null,
  "lineaDeTiempo": [
    { "estadoAnterior": null, "estadoNuevo": "CONFIRMADO", "actorSub": "9a2e-dador1", "ocurridoEn": "2026-11-03T22:10:00Z", "detalle": null },
    { "estadoAnterior": "CONFIRMADO", "estadoNuevo": "CARGADO", "actorSub": "3f1c-transportista1", "ocurridoEn": "2026-11-05T12:30:00Z", "detalle": null }
  ]
}
```

Un tercero recibe un ProblemDetail:

```json
{ "type": "about:blank", "title": "Sin permiso", "status": 403, "detail": "Tu rol no te permite hacer esto", "instance": "/api/viajes/1" }
```

El `detail` es el texto genérico del manejador común de flety-commons, igual para cualquier 403.

## Modelo

- **Viaje**: carga, propuesta, ids del transportista y la unidad, precio acordado y estado. Guarda una **copia** de los contactos y de la patente al confirmarse, porque el transportista vive en otra base y el viaje tiene que mostrar con quién se acordó, aunque después cambien sus datos.
- **EventoViaje**: una tabla de auditoría *append-only*, con el estado anterior y el nuevo, el `sub` de quien actuó, la fecha y hora, y un detalle opcional. El estado del viaje no tiene setter: solo cambia con `Viaje.cambiarEstado`, que siempre agrega un evento. Las columnas son `updatable = false` y `@PreRemove` corta cualquier borrado.
- **ComprobanteEntrega** y **Disputa**: quedan modeladas acá. Las usan FLY-27 (entrega) y FLY-30/31 (disputas).
- **TipoEstadoViaje**: CONFIRMADO, CARGADO, EN_TRANSITO, ENTREGADO, CONFORMADO, EN_DISPUTA, CERRADO_CON_DISPUTA y CANCELADO. El nombre `EstadoViaje` queda reservado para la interfaz del patrón State (FLY-27).

## Viajes activos por unidad (RN-15)

`ViajesActivosPorUnidad` es la interfaz con la que el mercado (FLY-23) controla que una unidad no tenga dos viajes superpuestos. La implementación lee la tabla de viajes. Un viaje cuenta como activo mientras no esté CONFORMADO, CERRADO_CON_DISPUTA ni CANCELADO, y su ventana va desde el inicio de la ventana de carga hasta el fin de la de descarga.
