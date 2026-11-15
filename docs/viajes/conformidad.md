# Conformidad de la entrega (FLY-30)

Cuando el viaje queda ENTREGADO empieza la ventana de conformidad de 48 h (FLY-27). En ese plazo el dador da conformidad, o abre una disputa (FLY-31). Si no hace ninguna de las dos, conforma el sistema.

| Endpoint | Rol | Respuestas |
|---|---|---|
| `POST /api/viajes/{id}/conformidad` | DADOR | 200 con el viaje CONFORMADO (el mismo cuerpo que `GET /api/viajes/{id}`) |

| Código | Cuándo |
|---|---|
| 403 | Quien conforma no es el dador de este viaje, o no tiene el rol DADOR |
| 404 | El viaje no existe |
| 409 | El viaje no está ENTREGADO (todavía no se entregó, o ya está conformado o cancelado), o la ventana de 48 h ya venció: la conformidad la da el sistema |

No lleva cuerpo.

## Qué pasa al conformar

- El viaje pasa a CONFORMADO, con `conformadoPor: DADOR`, y la línea de tiempo suma el evento `Conformó el dador`.
- La carga queda CERRADA: el viaje terminó. Las dos cosas cambian en la misma transacción, como en la cancelación (FLY-28).
- CONFORMADO es un estado final (`Conformado` no permite ninguna acción): conformar de nuevo, disputar o cancelar dan 409.

## Una sola transición, la dispare quien la dispare

`Entregado.conformar(viaje, por, actor)` es el único camino a CONFORMADO, para el dador y para el sistema. Lo único que cambia es de qué lado de la ventana tiene que estar cada uno:

| Quién conforma | `ConformadoPor` | Actor del evento | Cuándo puede |
|---|---|---|---|
| El dador, por la API | `DADOR` | su `sub` | mientras la ventana está abierta (hasta `ventanaConformidadHasta`, inclusive) |
| El Reloj | `SISTEMA` | `SISTEMA` | recién cuando la ventana venció |

Fuera de su lado, cada uno recibe 409 (`ConflictoException`): el pedido está bien, lo que choca es el momento. Como no hay dos caminos, no puede pasar que un viaje quede distinto según quién lo conformó: el estado, la carga CERRADA y el evento salen siempre del mismo `Viaje.marcarConformado`.
