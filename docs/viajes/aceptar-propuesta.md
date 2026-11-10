# Aceptar una propuesta (FLY-26)

El dador elige una de las propuestas de su carga y nace el viaje: es el flujo C del diseño.

| Endpoint | Rol | Respuestas |
|---|---|---|
| `POST /api/cargas/propuestas/{id}/aceptacion` | DADOR | 201 con el viaje y `Location: /api/viajes/{id}` |

| Código | Cuándo |
|---|---|
| 403 | La carga no es del dador que acepta, o quien acepta no es DADOR |
| 404 | La propuesta no existe |
| 409 | La propuesta ya no está PENDIENTE (aceptada, rechazada o invalidada), la carga ya no está abierta, la propuesta venció, la unidad ya tiene un viaje que se pisa con esta carga (RN-15), u otra aceptación de la misma carga o de la misma unidad ganó de mano |
| 422 | Falta aceptar el riesgo (RN-06), o la unidad ya no está habilitada (la propuesta queda INVALIDADA) |
| 503 | transporte-service no responde: no se confirma nada |

El cuerpo es opcional. Solo hace falta cuando la propuesta dice `requiereAceptacionRiesgo`:

```json
{ "aceptoRiesgo": true }
```

## Qué pasa al aceptar

1. **Validación sin cambios.** Se controla el dueño de la carga, que la propuesta siga PENDIENTE y sin vencer, que la carga siga abierta y el riesgo (RN-06). Si algo falla, se responde sin llamar a transporte.
2. **Habilitación.** Se consulta `GET /internal/unidades/{id}/habilitacion?hasta=<fin de la descarga>` en transporte-service (RN-04). Esta llamada se hace **fuera de toda transacción**, para no tener filas bloqueadas mientras se espera la respuesta.
3. **Si la unidad ya no está habilitada:** la propuesta pasa a INVALIDADA en su propia transacción y se responde 422 con los motivos. La carga y las demás propuestas no cambian, y el dador puede elegir otra.
4. **Si está habilitada**, todo en **una sola transacción**:
   - se crea el viaje CONFIRMADO, con su primer evento y una copia de la razón social, el teléfono y la patente que informó transporte;
   - la propuesta queda ACEPTADA y las demás pendientes de la carga quedan RECHAZADA;
   - la carga queda ASIGNADA.

El contacto del dador se copia de su organización. Una carga publicada antes de que se exigiera (RN-07) queda sin ese contacto, y eso no frena la aceptación.

## Dos aceptaciones a la vez

Hay cuatro capas, y cada una cubre un caso distinto:

| Capa | Qué frena | Dónde |
|---|---|---|
| Bloqueo pesimista de la carga (`SELECT ... FOR UPDATE`) | Dos aceptaciones de la **misma carga**: la segunda espera y, cuando entra, ve la carga ASIGNADA (409) | `CargasDeViajes.bloquear` |
| Bloqueo pesimista de las propuestas de la carga y de la unidad | Dos cargas **distintas** con la **misma unidad** en ventanas que se pisan: la segunda espera y, cuando entra, ve el viaje nuevo (409, RN-15) | `PropuestasDeViajes.bloquearDeLaCargaYLaUnidad` |
| Bloqueo optimista (`@Version` de `Carga`) | Cualquier otro cambio de la carga que no pase por estos bloqueos (por ejemplo, que el dador la cancele) | `Carga.version` |
| Índice único parcial en Postgres | La última red: un solo viaje no cancelado por carga | `uk_viaje_carga_no_cancelado` en `schema-postgresql.sql` |

Por qué se bloquean las **propuestas** de la unidad y no sus viajes: si la unidad todavía no tiene viajes, no hay ninguna fila que bloquear y las dos aceptaciones pasan (el problema de las filas "fantasma"). Las propuestas de las dos aceptaciones sí existen.

Los bloqueos se toman **todos al principio y siempre en el mismo orden**: primero la carga y después las propuestas ordenadas por id. La primera versión bloqueaba la unidad y después iba rechazando las demás propuestas de la carga, y `AceptacionesSimultaneasTest` encontró un deadlock: dos aceptaciones de la misma carga con unidades distintas se esperaban una a la otra. Si igual la base no consigue un bloqueo en 5 segundos, o elige una transacción como víctima de un deadlock, se responde 409 para que el dador vuelva a intentar, en vez de un 500.

## Ejemplo

```bash
curl -s -X POST http://localhost:8085/api/cargas/propuestas/12/aceptacion -H "Authorization: Bearer $TOKEN"
```

Responde 201 con el mismo cuerpo que `GET /api/viajes/{id}` (ver [consulta-de-viajes.md](consulta-de-viajes.md)), con `estado: CONFIRMADO` y un único evento en la línea de tiempo.

Con la unidad ya no habilitada:

```json
{
  "title": "Regla de negocio incumplida",
  "status": 422,
  "detail": "La unidad de esta propuesta ya no está habilitada: Revisión técnica vence el 2026-11-01, antes del 2026-11-05. La propuesta quedó INVALIDADA; podés elegir otra."
}
```
