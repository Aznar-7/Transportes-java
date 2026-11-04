# Hitos del viaje (FLY-27)

El transportista registra cómo avanza el viaje: cargó, salió, entregó. El ciclo de vida se modela con el patrón State.

| Endpoint | Rol | Respuestas |
|---|---|---|
| `POST /api/viajes/{id}/hitos` | TRANSPORTISTA | 200 con el viaje actualizado (el mismo cuerpo que `GET /api/viajes/{id}`) |

| Código | Cuándo |
|---|---|
| 400 | El pedido está mal armado: falta el hito o tiene un valor que no existe, ENTREGADO sin comprobante (o el comprobante incompleto), o un comprobante con otro hito |
| 403 | Quien registra no es el transportista de este viaje, o no tiene el rol TRANSPORTISTA |
| 404 | El viaje no existe |
| 409 | El estado actual no permite ese hito: saltearlo, repetirlo o volver atrás. También si otro pedido cambió el viaje al mismo tiempo |

```json
{ "hito": "CARGADO" }
```

Los hitos van en orden y de a uno:

```
CONFIRMADO --cargar--> CARGADO --iniciarTransito--> EN_TRANSITO --registrarEntrega--> ENTREGADO
```

| Hito del pedido | Acción del viaje | Solo desde |
|---|---|---|
| `CARGADO` | `cargar` | CONFIRMADO |
| `EN_TRANSITO` | `iniciarTransito` | CARGADO |
| `ENTREGADO` | `registrarEntrega` | EN_TRANSITO |

Cada hito deja un renglón en la línea de tiempo (`EventoViaje`), con el estado anterior, el nuevo y el `sub` del transportista.

## La entrega y su comprobante

Para pasar a ENTREGADO el comprobante es obligatorio: el remito y quién recibió la carga. Es la prueba de la entrega si después el dador abre una disputa.

```json
{
  "hito": "ENTREGADO",
  "comprobante": { "numeroRemito": "R-0001-00012345", "recibioNombre": "Ana Gómez", "recibioDni": "30111222" }
}
```

- **Sin comprobante es 400, no 409.** Es un pedido incompleto, no un problema de estado, así que se responde antes de mirar el viaje. Mandar un comprobante con otro hito también es 400: si se ignorara en silencio, el transportista creería que la entrega quedó registrada.
- **Se guarda en `ComprobanteEntrega`,** que no se edita ni se borra: su repositorio solo ofrece guardar y leer. Hay a lo sumo uno por viaje (`viaje_id` es único).
- **Corre la ventana de conformidad.** Al entregar se guarda `entregadoEn` y `ventanaConformidadHasta = entregadoEn + 48 h` (`Viaje.VENTANA_CONFORMIDAD`). En ese plazo el dador da conformidad o abre una disputa; si no hace nada, la conformidad la da el sistema (FLY-30). Las dos fechas salen en `GET /api/viajes/{id}`.
- **El remito queda en la línea de tiempo:** el evento de la entrega dice `Remito R-0001-00012345, recibió Ana Gómez`.

Un 400 por falta de comprobante:

```json
{
  "title": "Datos inválidos",
  "status": 400,
  "detail": "Hay campos con errores",
  "errores": { "comprobanteSegunElHito": "el comprobante es obligatorio con ENTREGADO y no va con los demás hitos" }
}
```

## Patrón State

`EstadoViaje` es la interfaz de los estados. Declara las seis acciones del viaje: `cargar`, `iniciarTransito`, `registrarEntrega`, `conformar`, `disputar` y `cancelar`. Cada estado tiene su clase (`Confirmado`, `Cargado`, `EnTransito`, `Entregado`), que sabe qué acciones permite y a qué estado pasa con cada una.

- **El Viaje no decide nada.** `viaje.cargar(sub)` llama a `cargar` del objeto de su estado actual. Así no hay un switch por estado en cada acción, y una transición nueva se agrega en la clase del estado, sin tocar `Viaje`.
- **Todo está prohibido por defecto.** Los métodos de la interfaz son `default` y lanzan `TransicionNoPermitidaException`; cada estado sobrescribe solo lo que permite. Un estado nuevo arranca sin permitir nada, y no se puede habilitar una transición por olvido.
- **La base guarda el enum.** `TipoEstadoViaje` es lo que se guarda y se consulta; `EstadosViaje` lo traduce al objeto de estado. Los estados no tienen datos propios (los datos son del viaje), así que hay una sola instancia de cada uno para todos los viajes.
- **Los estados que faltan.** CANCELADO (FLY-28), CONFORMADO (FLY-30), EN_DISPUTA y CERRADO_CON_DISPUTA (FLY-31) tienen por ahora un `EstadoSinAcciones`, que no permite nada. Cada historia reemplaza ese lugar por su clase.

### Por qué 409 y no 422

`TransicionNoPermitidaException` hereda de `ReglaNegocioException`, porque el ciclo de vida del viaje es una regla de negocio. El manejador común de flety-commons respondería 422, pero acá corresponde 409: el pedido está bien, lo que choca es el estado actual del viaje, y el mismo pedido en otro momento se acepta. `ViajesExceptionHandler` atiende solo esa excepción, antes que el manejador común (`@Order(HIGHEST_PRECEDENCE)`), y responde 409 con el mismo formato:

```json
{
  "title": "Conflicto con el estado actual",
  "status": 409,
  "detail": "El viaje está CONFIRMADO: no se puede iniciar el tránsito",
  "estadoActual": "CONFIRMADO"
}
```

## Dos hitos a la vez

`Viaje` tiene `@Version`. Si llegan dos pedidos juntos para el mismo viaje, los dos leen la misma versión y el segundo en guardar recibe 409 (bloqueo optimista). No hace falta un bloqueo pesimista: solo el transportista del viaje registra hitos, así que los choques son raros y alcanza con avisarle que reintente.
