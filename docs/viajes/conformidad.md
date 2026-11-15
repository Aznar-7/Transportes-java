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

## La conformidad automática

`RelojesCargas` (paquete `reloj`) es el Reloj de cargas, igual que `RelojesTransporte`. Solo dispara: cada 15 minutos llama a `ConformidadAutomaticaService.conformarVencidas(ahora)`, que busca los viajes ENTREGADOS con la ventana vencida y los conforma como `SISTEMA`.

| Propiedad | Por defecto | Qué hace |
|---|---|---|
| `flety.relojes.habilitados` | `true` | Prende o apaga los relojes. En los tests está en `false` |
| `flety.relojes.conformidad` | `PT15M` | Cada cuánto corre: un viaje queda a lo sumo 15 minutos de más en ENTREGADO |
| `flety.relojes.conformidad-inicio` | `PT1M` | Cuánto espera la primera vuelta después del arranque |

- **Un viaje por transacción.** Si uno falla, los demás se conforman igual, y nunca se bloquean todos juntos.
- **Si el dador actúa a la vez,** el `@Version` del viaje lo detecta (o el viaje ya no está ENTREGADO): el Reloj lo saltea, porque ya no le toca, y lo vuelve a mirar en la vuelta siguiente.
- **Un viaje EN_DISPUTA no entra:** el dador ya actuó dentro de la ventana (FLY-31).
- **La regla recibe la hora,** así los tests la prueban con una ventana vencida sin esperar a que el Reloj dispare.

Fuera de su lado, cada uno recibe 409 (`ConflictoException`): el pedido está bien, lo que choca es el momento. Como no hay dos caminos, no puede pasar que un viaje quede distinto según quién lo conformó: el estado, la carga CERRADA y el evento salen siempre del mismo `Viaje.marcarConformado`.
