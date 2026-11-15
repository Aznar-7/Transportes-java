# Relojes de cargas-service (FLY-30 y FLY-32)

`RelojesCargas` (paquete `reloj`) es el Reloj del diseño para cargas, igual que `RelojesTransporte`. Solo dispara: cada tarea llama a un service que recibe la hora, y la regla vive en ese service. Por eso los tests prueban "dentro de 25 h" o "con la ventana vencida" sin esperar.

| Tarea | Cada | Qué hace | Regla |
|---|---|---|---|
| `conformarVencidas` | 15 min | Conforma como SISTEMA los viajes ENTREGADOS con la ventana de 48 h vencida | FLY-30, [conformidad.md](conformidad.md) |
| `vencerPropuestas` | 5 min | Vence las propuestas PENDIENTES | RN-16 |
| `vencerCargas` | 15 min | Vence las cargas sin viaje con la ventana de carga cerrada (antes vence las propuestas con la misma hora) | RN-17 |

| Propiedad | Por defecto |
|---|---|
| `flety.relojes.habilitados` | `true` (en los tests, `false`) |
| `flety.relojes.conformidad`, `flety.relojes.conformidad-inicio` | `PT15M`, `PT1M` |
| `flety.relojes.propuestas`, `flety.relojes.propuestas-inicio` | `PT5M`, `PT1M` |
| `flety.relojes.cargas`, `flety.relojes.cargas-inicio` | `PT15M`, `PT1M` |

La primera vuelta de cada tarea espera un minuto, para no competir con el arranque.

## Vencer propuestas (RN-16)

Una propuesta PENDIENTE vence **a las 24 h de creada o cuando cierra la ventana de carga, lo que pase primero**. El Reloj la pasa a VENCIDA si se cumple cualquiera de estas condiciones:

- `creadaEn` es de hace 24 h o más;
- la ventana de su carga ya cerró (`cargaHasta`);
- su campo `vencimiento` ya pasó. Mercado lo calcula al crearla como el mínimo de las dos anteriores; el Reloj no depende de que esté bien calculado, pero lo respeta si algún día se acorta.

Solo vencen las PENDIENTES: una ACEPTADA, RECHAZADA, RETIRADA o INVALIDADA ya tiene su final.

## Vencer cargas (RN-17)

Una carga **sin viaje** (PUBLICADA o CON_PROPUESTAS) cuya ventana de carga ya cerró (`cargaHasta` pasó) queda VENCIDA: nadie puede ir a buscarla a tiempo. Una ASIGNADA tiene viaje y no vence, aunque su ventana haya pasado; lo que pase con ese viaje lo deciden sus propios estados (cancelación, conformidad).

La tarea de cargas vence primero las propuestas con la misma hora: así una carga vencida no deja propuestas PENDIENTES colgadas, porque todas vencen como mucho al cerrar la ventana de su carga.

## Cómo está hecho

- **Repositorios propios del Reloj** (`PropuestasVencibles` y `CargasVencibles`) sobre las entidades del contrato de modelo (C3). No toca el `PropuestaRepository` ni el `CargaRepository` de mercado.
- **Consultas por fecha con Spring Data.** Es un `@Modifying @Query` que compara instantes (`creadaEn <= :haceUnDia`, `cargaHasta <= :ahora`): una sola sentencia vence todas, sin cargar las entidades.
- **`@Version` a mano.** Un UPDATE de JPQL no pasa por `@Version`, así que el de cargas suma 1 a la versión. Si un pedido había leído la carga antes (por ejemplo, el dador cancelándola), su UPDATE choca con la versión nueva y recibe 409 en vez de pisar el VENCIDA.
- **No pisa una aceptación en curso.** Aceptar bloquea la carga y la propuesta (`SELECT ... FOR UPDATE`, FLY-26). El UPDATE del Reloj espera, y cuando entra vuelve a evaluar el WHERE con las filas ya ASIGNADA y ACEPTADA, que dejan de cumplirlo y no se tocan. Al revés, si vence primero, la aceptación las ve vencidas y responde 409.
