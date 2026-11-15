# Relojes de cargas-service (FLY-30 y FLY-32)

`RelojesCargas` (paquete `reloj`) es el Reloj del diseño para cargas, igual que `RelojesTransporte`. Solo dispara: cada tarea llama a un service que recibe la hora, y la regla vive en ese service. Por eso los tests prueban "dentro de 25 h" o "con la ventana vencida" sin esperar.

| Tarea | Cada | Qué hace | Regla |
|---|---|---|---|
| `conformarVencidas` | 15 min | Conforma como SISTEMA los viajes ENTREGADOS con la ventana de 48 h vencida | FLY-30, [conformidad.md](conformidad.md) |
| `vencerPropuestas` | 5 min | Vence las propuestas PENDIENTES | RN-16 |

| Propiedad | Por defecto |
|---|---|
| `flety.relojes.habilitados` | `true` (en los tests, `false`) |
| `flety.relojes.conformidad`, `flety.relojes.conformidad-inicio` | `PT15M`, `PT1M` |
| `flety.relojes.propuestas`, `flety.relojes.propuestas-inicio` | `PT5M`, `PT1M` |

La primera vuelta de cada tarea espera un minuto, para no competir con el arranque.

## Vencer propuestas (RN-16)

Una propuesta PENDIENTE vence **a las 24 h de creada o cuando cierra la ventana de carga, lo que pase primero**. El Reloj la pasa a VENCIDA si se cumple cualquiera de estas condiciones:

- `creadaEn` es de hace 24 h o más;
- la ventana de su carga ya cerró (`cargaHasta`);
- su campo `vencimiento` ya pasó. Mercado lo calcula al crearla como el mínimo de las dos anteriores; el Reloj no depende de que esté bien calculado, pero lo respeta si algún día se acorta.

Solo vencen las PENDIENTES: una ACEPTADA, RECHAZADA, RETIRADA o INVALIDADA ya tiene su final.

## Cómo está hecho

- **Repositorios propios del Reloj** (`PropuestasVencibles`) sobre las entidades del contrato de modelo (C3). No toca el `PropuestaRepository` ni el `CargaRepository` de mercado.
- **Consultas por fecha con Spring Data.** Es un `@Modifying @Query` que compara instantes (`creadaEn <= :haceUnDia`, `cargaHasta <= :ahora`): una sola sentencia vence todas, sin cargar las entidades.
- **No pisa una aceptación en curso.** Aceptar bloquea la propuesta (`SELECT ... FOR UPDATE`, FLY-26). El UPDATE del Reloj espera, y cuando entra vuelve a evaluar el WHERE con la fila ya ACEPTADA, que deja de ser PENDIENTE y no se toca. Al revés, si vence primero, la aceptación la ve VENCIDA y responde 409.
