# Viajes

El módulo `com.flety.cargas.viajes` de `cargas-service` se ocupa de lo que pasa desde que el dador elige una propuesta hasta que el viaje termina: crea el viaje (flujo C), lo lleva por sus estados con el patrón State, y lo cierra con la conformidad, una disputa o una cancelación (flujo D). Vive en la base `cargas_db`, junto con mercado ([mercado.md](mercado.md)), y le pregunta a `transporte-service` si la unidad sigue habilitada ([transporte.md](transporte.md)).

Cada tema tiene su detalle en [`docs/viajes/`](viajes/); este documento es el mapa.

## Endpoints

Todos los errores salen como `ProblemDetail`. El Gateway controla el rol de cada ruta ([gateway/rutas.md](gateway/rutas.md)); el servicio vuelve a controlarlo y además controla que quien pide sea parte del viaje (RN-T1).

| Endpoint | Rol | Qué hace | Éxito | Detalle |
|---|---|---|---|---|
| `POST /api/cargas/propuestas/{id}/aceptacion` | DADOR | Acepta una propuesta y crea el viaje CONFIRMADO | 201 | [aceptar-propuesta.md](viajes/aceptar-propuesta.md) |
| `GET /api/viajes` | DADOR o TRANSPORTISTA | Mis viajes | 200 | [consulta-de-viajes.md](viajes/consulta-de-viajes.md) |
| `GET /api/viajes/{id}` | DADOR o TRANSPORTISTA | Un viaje con contactos y línea de tiempo | 200 | [consulta-de-viajes.md](viajes/consulta-de-viajes.md) |
| `POST /api/viajes/{id}/hitos` | TRANSPORTISTA | CARGADO, EN_TRANSITO o ENTREGADO (con comprobante) | 200 | [hitos-del-viaje.md](viajes/hitos-del-viaje.md) |
| `POST /api/viajes/{id}/cancelacion` | DADOR o TRANSPORTISTA | Cancela antes de cargar | 200 | [cancelacion-del-viaje.md](viajes/cancelacion-del-viaje.md) |
| `POST /api/viajes/{id}/conformidad` | DADOR | Conforma la entrega dentro de las 48 h | 200 | [conformidad.md](viajes/conformidad.md) |
| `POST /api/viajes/{id}/disputas` | DADOR | Reclama sobre la entrega dentro de las 48 h | 201 | [disputas.md](viajes/disputas.md) |
| `GET /api/viajes/disputas?estado=` | OPERADOR | Bandeja de disputas | 200 | [disputas.md](viajes/disputas.md) |
| `GET /api/viajes/disputas/{id}` | OPERADOR | Una disputa | 200 | [disputas.md](viajes/disputas.md) |
| `POST /api/viajes/disputas/{id}/resolucion` | OPERADOR | Resuelve a favor o en contra del transportista | 200 | [disputas.md](viajes/disputas.md) |

## Flujo C: de la propuesta al viaje

El dador acepta una propuesta (`AceptarPropuestaService`):

1. **Valida sin cambiar nada.** Que la carga sea suya (403), que la propuesta siga PENDIENTE y sin vencer y la carga abierta (409), y el riesgo (RN-06, 422). Si algo falla, responde sin llamar a nadie.
2. **Pregunta a transporte si la unidad sigue habilitada** hasta el fin de la descarga (RN-04), **fuera de toda transacción**: así no hay filas bloqueadas mientras se espera la respuesta. Si transporte no responde, 503 y no se confirma nada.
3. **Si la unidad ya no está habilitada**, la propuesta queda INVALIDADA (en su propia transacción) y responde 422 con los motivos.
4. **Si está habilitada, todo en una sola transacción** (`ConfirmacionDeViaje`): bloquea, vuelve a validar (pudo cambiar durante la llamada), controla RN-15, y crea el viaje CONFIRMADO con su primer evento y la copia de los contactos y la patente. La propuesta queda ACEPTADA, las demás pendientes RECHAZADA y la carga ASIGNADA.

El orquestador no es `@Transactional` a propósito: cada paso con base es un método `@Transactional` de otro bean (`ConfirmacionDeViaje`), porque una llamada entre métodos del mismo bean no pasa por el proxy y no abriría la transacción.

## Los estados del viaje

```
                                   ┌──> CONFORMADO            (dador en la ventana, Reloj al vencer, u operador a favor)
CONFIRMADO ──> CARGADO ──> EN_TRANSITO ──> ENTREGADO ─┤
    │                                      (48 h)     └──> EN_DISPUTA ──┬──> CONFORMADO        (operador a favor)
    └──> CANCELADO                                                      └──> CERRADO_CON_DISPUTA (operador en contra)
```

| Acción | Desde | A | Quién |
|---|---|---|---|
| `cargar` | CONFIRMADO | CARGADO | transportista |
| `iniciarTransito` | CARGADO | EN_TRANSITO | transportista |
| `registrarEntrega` (con comprobante) | EN_TRANSITO | ENTREGADO, abre la ventana de 48 h | transportista |
| `cancelar` | CONFIRMADO | CANCELADO (la carga queda CANCELADA, PUBLICADA o VENCIDA según quién y cuándo) | dador o transportista |
| `conformar` | ENTREGADO | CONFORMADO (la carga queda CERRADA) | dador con la ventana abierta, o el Reloj (SISTEMA) con la ventana vencida |
| `disputar` | ENTREGADO | EN_DISPUTA | dador con la ventana abierta |
| `resolverDisputa` | EN_DISPUTA | CONFORMADO (por OPERADOR) o CERRADO_CON_DISPUTA (la carga queda CERRADA) | operador |

CONFORMADO, CERRADO_CON_DISPUTA y CANCELADO son finales: no admiten ninguna acción.

### Patrón State

`EstadoViaje` es la interfaz con las siete acciones. Cada estado tiene su clase (`Confirmado`, `Cargado`, `EnTransito`, `Entregado`, `EnDisputa`, `Conformado`, `CerradoConDisputa`, `Cancelado`), y `Viaje` le pasa cada acción al objeto de su estado actual: no tiene ni un `if` sobre su estado.

- **Todo prohibido por defecto.** Los métodos de la interfaz son `default` y lanzan `TransicionNoPermitidaException`; cada estado sobrescribe solo lo que permite. La API responde **409**: el pedido está bien, lo que choca es el estado actual.
- **Una sola transición, la dispare quien la dispare.** El dador, el Reloj y el operador llegan a CONFORMADO por el mismo `marcarConformado`; lo único que cambia es quién queda registrado.
- **La base guarda el enum** (`TipoEstadoViaje`) y `EstadosViaje` lo traduce a su objeto. Si un valor del enum se queda sin clase, el servicio no arranca.
- **Cada cambio deja un `EventoViaje`**, la línea de tiempo del viaje. Es append-only en tres niveles: sin setters, columnas `updatable = false` y un trigger en Postgres que rechaza UPDATE, DELETE y TRUNCATE.

## Concurrencia: qué pasa con dos pedidos a la vez

Lo más fácil de explicar es con dos pedidos que llegan al mismo tiempo.

### Dos dadores no pueden aceptar dos propuestas de la misma carga

Una carga tiene dos propuestas, P1 y P2. El dador aprieta "aceptar" en P1 y, por un doble clic o desde otra pestaña, también en P2. Los dos pedidos pasan la validación y la llamada a transporte casi a la vez, y entran a confirmar:

| | Pedido A (acepta P1) | Pedido B (acepta P2) |
|---|---|---|
| 1 | `SELECT carga ... FOR UPDATE`: consigue el bloqueo | `SELECT carga ... FOR UPDATE`: **espera**, la fila es de A |
| 2 | Crea el viaje, P1 ACEPTADA, P2 RECHAZADA, carga ASIGNADA | sigue esperando |
| 3 | Commit: suelta el bloqueo | consigue el bloqueo y **vuelve a leer**: la carga está ASIGNADA y P2 RECHAZADA |
| 4 | 201 con el viaje | **409** "La propuesta ya no está pendiente (está RECHAZADA)" |

La clave es el paso 3: B no confía en lo que validó antes de esperar. Valida de nuevo **después** de conseguir el bloqueo, con los datos que dejó A. Por eso los bloqueos se toman antes de cualquier lectura: una entidad que Hibernate ya cargó no se refresca con un `FOR UPDATE` posterior.

### Una unidad no puede quedar en dos viajes que se pisan (RN-15)

Ahora son dos cargas **distintas** de dos dadores, y el mismo transportista propuso la misma unidad en las dos, con ventanas que se pisan. Los dos dadores aceptan a la vez. El bloqueo de la carga no sirve, porque son cargas distintas, y bloquear "los viajes de la unidad" tampoco: si la unidad todavía no tiene viajes, no hay ninguna fila que bloquear (el problema de las filas fantasma) y los dos pasarían el control de RN-15.

Lo que sí existe para los dos pedidos son **las propuestas de la unidad**. `ConfirmacionDeViaje` las bloquea (`PropuestasDeViajes.bloquearDeLaCargaYLaUnidad`): el segundo espera al primero y, cuando entra, el control de superposición ya ve el viaje nuevo y responde **409** (RN-15).

### El deadlock que encontró el test

La primera versión bloqueaba las propuestas de la unidad y después iba rechazando las otras de la carga. `AceptacionesSimultaneasTest` encontró que dos aceptaciones de la misma carga con **unidades distintas** se trababan: A tenía su unidad y esperaba una propuesta de B; B tenía la suya y esperaba una de A. La solución es tomar **todos los bloqueos al principio y siempre en el mismo orden**: primero la carga y después todas las propuestas que se van a tocar, ordenadas por id. Si igual la base no consigue un bloqueo en 5 s, o elige un pedido como víctima de un deadlock, se responde 409 en vez de 500.

### Las demás capas

| Qué frena | Cómo | Dónde |
|---|---|---|
| Dos cambios del mismo viaje a la vez (dos hitos, cancelar y cargar, el dador conformando mientras el Reloj también) | `@Version` del viaje: el segundo en guardar recibe 409. El Reloj, si choca, saltea el viaje y lo vuelve a mirar en la vuelta siguiente | `Viaje.version` |
| Un cambio de la carga fuera de estos bloqueos (por ejemplo, el dador cancelándola) | `@Version` de la carga | `Carga.version` |
| El Reloj venciendo una propuesta o una carga mientras alguien la acepta | El UPDATE masivo espera el bloqueo de la aceptación y reevalúa su WHERE: la fila ya no está PENDIENTE ni abierta, y no la toca | [relojes.md](viajes/relojes.md) |
| Un segundo viaje no cancelado para la misma carga, si un bug salteara todo lo anterior | Índice único parcial `uk_viaje_carga_no_cancelado` en Postgres | `schema-postgresql.sql` |

## Relojes

`RelojesCargas` conforma los viajes a las 48 h y vence propuestas (RN-16) y cargas (RN-17). Solo dispara: cada regla vive en un service que recibe la hora. Un viaje en disputa no se conforma solo porque su estado no lo permite y la consulta del Reloj solo busca ENTREGADOS: el Reloj no sabe nada de disputas. Detalle en [relojes.md](viajes/relojes.md).

## Decisiones

| Decisión | Por qué |
|---|---|
| Copiar contactos y patente en el viaje en vez de referenciarlos | El transportista vive en otra base, y el viaje tiene que mostrar con quién se acordó aunque después cambie su teléfono |
| Viaje y carga cambian en la misma transacción (cancelar, conformar, resolver) | O cambian los dos o ninguno: nunca queda un viaje terminado con la carga ASIGNADA |
| El operador no tiene control de dueño | No es parte del viaje: el rol OPERADOR alcanza para ver y resolver cualquier disputa |
| Repositorios propios sobre `Carga` y `Propuesta` (`CargasDeViajes`, `PropuestasDeViajes`, los del Reloj) | Viajes y el Reloj declaran solo lo que usan, sin tocar los repositorios de mercado |

## Cómo se prueba

```bash
mvn -f flety-commons/pom.xml install
mvn -f cargas-service/pom.xml test
```

| Test | Qué cubre |
|---|---|
| `AceptarPropuestaTest`, `AceptacionesSimultaneasTest` | Flujo C con transporte simulado, y dos aceptaciones a la vez (misma carga y misma unidad) |
| `EstadoViajeTest`, `EstadosDelViajeTest`, `ViajeDelegaEnSuEstadoTest` | El patrón State en memoria |
| `RegistrarHitoTest`, `TransicionesInvalidasTest` | Hitos por la API: orden, comprobante, 400 y 409 |
| `CancelarViajeTest`, `CancelacionDelViajeTest`, `EstadoDeLaCargaAlCancelarTest`, `CancelarDespuesDeCargadoTest` | Cancelación, penalidad y estado de la carga |
| `ConformidadDelViajeTest`, `ConformidadDelDadorTest`, `ConformidadFueraDeVentanaTest`, `ConformidadAutomaticaTest` | Conformidad del dador y del Reloj |
| `DisputaDelViajeTest`, `AbrirDisputaTest`, `ResolverDisputaTest`, `RelojIgnoraDisputasTest` | Disputas, y que el Reloj no conforme un viaje en disputa |
| `VencerPropuestasTest`, `VencerCargasTest`, `LoQuePasePrimeroTest` | Los vencimientos del Reloj |
| `ViajeTest`, `ViajeControllerTest`, `ViajeDeTercerosTest` | Entidades, línea de tiempo append-only y consulta con control de dueño |

El trigger de `evento_viaje` y el índice parcial son de Postgres y no corren en los tests (H2): se prueban contra el compose ([despliegue.md](despliegue.md)).
