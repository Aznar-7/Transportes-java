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

## Por qué el Reloj no conforma un viaje en disputa

El Reloj de conformidad (`RelojesCargas`, FLY-30) no sabe nada de disputas, y no hace falta. Lo frenan dos reglas que viven en el viaje, no en la tarea programada:

1. **El estado.** `EnDisputa` no permite `conformar`: cualquier intento, del dador o del sistema, da 409.
2. **La consulta.** El Reloj solo busca viajes ENTREGADOS con la ventana vencida, y un viaje EN_DISPUTA ya no lo es.

Así la tarea programada y las disputas no se conocen: el Reloj no tiene un `if` sobre disputas, y si mañana aparece otro motivo para frenar un viaje, alcanza con otro estado que no permita conformar.
