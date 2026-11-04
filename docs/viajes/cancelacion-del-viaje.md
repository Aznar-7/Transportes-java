# Cancelación del viaje (FLY-28)

Cualquiera de las dos partes puede cancelar el viaje mientras esté CONFIRMADO, es decir, antes de que se cargue la mercadería.

| Endpoint | Rol | Respuestas |
|---|---|---|
| `POST /api/viajes/{id}/cancelacion` | DADOR o TRANSPORTISTA | 200 con el viaje cancelado (el mismo cuerpo que `GET /api/viajes/{id}`) |

| Código | Cuándo |
|---|---|
| 400 | Falta el motivo o tiene más de 500 caracteres |
| 403 | Quien cancela no es parte de este viaje, o tiene otro rol (por ejemplo, OPERADOR) |
| 404 | El viaje no existe |
| 409 | El viaje ya se cargó o está en un estado posterior (CARGADO, EN_TRANSITO, ENTREGADO...), ya estaba cancelado, u otro pedido cambió el viaje al mismo tiempo |

```json
{ "motivo": "Se rompió el camión" }
```

## Patrón State

Cancelar es una acción más del viaje (`EstadoViaje.cancelar`). Solo `Confirmado` la sobrescribe: en todos los demás estados queda la versión por defecto, que responde 409. No hizo falta tocar ningún otro estado para prohibirla.

`Cancelado` es un estado final: no sobrescribe ninguna acción, así que tampoco se puede cancelar dos veces ni retomar el viaje.

## Penalidad

Si faltan **menos de 24 h** para el inicio de la ventana de carga (`cargaDesde`), la cancelación queda con penalidad: a esa altura la otra parte ya se organizó. Exactamente 24 h todavía es sin penalidad, y si la ventana ya empezó también es con penalidad. No depende de quién cancela.

La respuesta trae la cancelación:

```json
"cancelacion": {
  "canceladoPor": "TRANSPORTISTA",
  "canceladoEn": "2026-11-04T15:00:00Z",
  "motivo": "Se rompió el camión",
  "conPenalidad": true
}
```

`cancelacion` es null mientras el viaje siga en pie. El evento de la línea de tiempo también lo dice: `Canceló el TRANSPORTISTA con penalidad: Se rompió el camión`.

Con el viaje cancelado ya no hay acuerdo, así que no se muestran los contactos ni las direcciones exactas (RN-13).
