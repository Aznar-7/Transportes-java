# Disputas (FLY-31)

Dentro de la ventana de conformidad de 48 h, el dador puede, en lugar de conformar, reclamar sobre la entrega. El viaje queda frenado en EN_DISPUTA hasta que un operador lo resuelva.

| Endpoint | Rol | Respuestas |
|---|---|---|
| `POST /api/viajes/{id}/disputas` | DADOR | 201 con el viaje EN_DISPUTA (el mismo cuerpo que `GET /api/viajes/{id}`) y `Location: /api/viajes/{id}` |

```json
{ "motivo": "Faltante", "descripcion": "Llegaron 18 pallets de 20" }
```

| Código | Cuándo |
|---|---|
| 400 | Falta el motivo o la descripción, o son demasiado largos (100 y 1000 caracteres) |
| 403 | Quien reclama no es el dador de este viaje, o no tiene el rol DADOR |
| 404 | El viaje no existe |
| 409 | El viaje no está ENTREGADO (todavía no se entregó, ya está en disputa, o ya terminó), o la ventana de 48 h venció: la conformidad la da el sistema |

## Qué pasa al abrir una disputa

- El viaje pasa a EN_DISPUTA, y la línea de tiempo suma el evento `Disputa abierta: <motivo>`.
- Se guarda la `Disputa` ABIERTA, con el motivo, la descripción, quién la abrió y cuándo.
- La carga sigue ASIGNADA: el viaje todavía no terminó.
- El detalle del viaje (`GET /api/viajes/{id}`) trae la sección `disputa`, que ven las dos partes. Es null si el viaje no tuvo disputa.

La regla de tiempo es la misma que la de la conformidad del dador (FLY-30): con la ventana abierta, el dador elige conformar o disputar; cuando vence, ya no puede ninguna de las dos cosas.

## La resolución del operador

| Endpoint | Rol | Respuestas |
|---|---|---|
| `GET /api/viajes/disputas?estado=ABIERTA` | OPERADOR | 200 con las disputas de ese estado (por defecto ABIERTA), de la más vieja a la más nueva |
| `GET /api/viajes/disputas/{id}` | OPERADOR | 200 con la disputa y el estado actual del viaje; 404 si no existe |
| `POST /api/viajes/disputas/{id}/resolucion` | OPERADOR | 200 con la disputa RESUELTA y el estado final del viaje |

```json
{ "aFavorDelTransportista": false, "resolucion": "Faltaban 2 pallets" }
```

| Decisión | El viaje queda | `conformadoPor` | La carga queda |
|---|---|---|---|
| A favor del transportista | CONFORMADO | `OPERADOR` | CERRADA |
| En contra | CERRADO_CON_DISPUTA | null | CERRADA |

- **La decisión la toma el viaje.** `EnDisputa.resolverDisputa` es la única forma de salir de EN_DISPUTA. A favor del transportista, reutiliza la misma `marcarConformado` que el dador y el Reloj (FLY-30): no hay un tercer camino a CONFORMADO.
- **Viaje, carga y disputa cambian en la misma transacción.** La `Disputa` queda RESUELTA, con la decisión, el fundamento, el operador y la fecha.
- **Se resuelve una sola vez.** Resolver de nuevo da 409: el viaje ya no está EN_DISPUTA. Si dos operadores resuelven a la vez, el `@Version` del viaje frena al segundo.
- **CERRADO_CON_DISPUTA es final,** igual que CONFORMADO: no admite ninguna acción.
- **Sin control de dueño.** El operador no es parte del viaje: el gateway y el controller le exigen el rol OPERADOR, y con eso puede ver y resolver cualquier disputa.

| Código | Cuándo |
|---|---|
| 400 | Falta `aFavorDelTransportista` o la resolución |
| 403 | No tiene el rol OPERADOR |
| 404 | La disputa no existe |
| 409 | La disputa ya estaba resuelta |

## Por qué el Reloj no conforma un viaje en disputa

El Reloj de conformidad (`RelojesCargas`, FLY-30) no sabe nada de disputas, y no hace falta. Lo frenan dos reglas que viven en el viaje, no en la tarea programada:

1. **El estado.** `EnDisputa` no permite `conformar`: cualquier intento, del dador o del sistema, da 409.
2. **La consulta.** El Reloj solo busca viajes ENTREGADOS con la ventana vencida, y un viaje EN_DISPUTA ya no lo es.

Así la tarea programada y las disputas no se conocen: el Reloj no tiene un `if` sobre disputas, y si mañana aparece otro motivo para frenar un viaje, alcanza con otro estado que no permita conformar.
