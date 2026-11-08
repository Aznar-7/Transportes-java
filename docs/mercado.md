# Mercado de cargas

El módulo `com.flety.cargas.mercado` de `cargas-service` conecta a quien tiene una carga con quien vuelve vacío: dadores, cargas, búsqueda de cargas de paso y propuestas. Cubre los flujos A y B del diseño. Cuando el dador acepta una propuesta empieza el flujo C, que es de viajes ([aceptar-propuesta.md](viajes/aceptar-propuesta.md)).

## Endpoints

Todos los errores salen como `ProblemDetail`. El detalle de cada código está en Swagger.

| Endpoint | Rol | Qué hace | Éxito |
|---|---|---|---|
| `POST /api/dadores` | DADOR | Da de alta la organización | 201 |
| `GET /api/dadores/me` | DADOR | Consulta la organización propia | 200 |
| `POST /api/cargas` | DADOR | Publica una carga | 201 |
| `GET /api/cargas` | DADOR | Lista las propias, con filtros `estado` y `rubro` | 200 |
| `GET /api/cargas/{id}` | DADOR o TRANSPORTISTA | Muestra la carga según quién mira | 200 |
| `DELETE /api/cargas/{id}` | DADOR | Da de baja: queda `CANCELADA` | 204 |
| `GET /api/cargas/de-paso?retornoId=` | TRANSPORTISTA | Flujo A | 200 |
| `POST /api/cargas/{id}/propuestas` | TRANSPORTISTA | Flujo B | 201 |
| `GET /api/cargas/{id}/propuestas` | DADOR | Lista las propuestas de una carga | 200 |
| `DELETE /api/cargas/propuestas/{id}` | TRANSPORTISTA | Retira una propuesta | 204 |
| `POST /api/cargas/propuestas/{id}/rechazo` | DADOR | Rechaza una propuesta | 200 |

## Publicar una carga

Para publicar, el dador tiene que estar registrado y haberse declarado empresa (RN-07), y la carga no puede ser peligrosa ni sobredimensionada (RN-08). Si no cumple, responde 422. Un pedido mal armado, como un CUIT inválido o unas ventanas incoherentes, responde 400.

Si el pedido trae `lat` y `lng`, se usan tal cual. Si no, se le piden a Georef; si Georef no puede ubicar la localidad, responde 503 y pide que se manden las coordenadas. Nunca se inventan.

## Flujo A: buscar cargas de paso

Lo inicia el transportista con su retorno. Va de lo barato a lo caro, para gastar lo menos posible de la API de rutas.

1. **Pide el retorno** a `transporte-service` (`GET /internal/retornos/{id}`). Si es de otro responde 403, si no existe 404 y si ya no está publicado 409.
2. **Filtra en la base** por estado (`PUBLICADA` o `CON_PROPUESTAS`), ventana de carga contra ventana de salida, carrocería, peso y volumen.
3. **Aplica el prefiltro geográfico**, en la misma consulta: el retiro y la entrega tienen que caer en una caja alrededor de la ruta, con el desvío máximo como margen. Si la ruta es aproximada, el margen es el doble.
4. **Elige las mejores N** según el desvío en línea recta, que se calcula sin llamar a nadie. N es `flety.busqueda.max-desvios-reales`, 10 por defecto.
5. **Calcula el desvío de esas N** con `CalculadorDesvio` y saca las que pasan el máximo del retorno (RN-01).
6. **Ordena según RN-02:** menor desvío primero y, si empatan, mayor precio por km.

Cada resultado trae la vista reservada de la carga, `desvioKm`, `precioPorKm` y `aproximado`. Nunca trae la dirección exacta, las coordenadas ni los datos del dador (RN-13).

## Flujo B: proponer

Lo inicia el transportista con una unidad y un retorno propios, aceptando el precio del dador o con una única contraoferta (RN-03). Los controles van en este orden:

| Paso | Control | Si falla |
|---|---|---|
| 1 | La carga existe | 404 |
| 2 | Está abierta, su ventana no cerró y este transportista no propuso antes | 409 |
| 3 | `transporte-service` responde con el retorno y con la habilitación hasta el fin de la descarga | 404 o 503 |
| 4 | La unidad y el retorno son del usuario | 403 |
| 5 | El retorno sigue publicado | 409 |
| 6 | El retorno es con esa unidad | 422 |
| 7 | La unidad está habilitada (RN-04) | 422 con los motivos |
| 8 | Carrocería, peso, volumen y ventana son compatibles (RN-01) | 422 |
| 9 | La unidad no tiene un viaje que se superponga (RN-15) | 409 |
| 10 | Una contraoferta no es igual al precio del dador | 422 |

Si pasa todo, en una sola transacción:

1. Se bloquea la carga con `PESSIMISTIC_WRITE` y se repite el paso 2, porque mientras tanto pudo llegar otro pedido.
2. Se crea la propuesta `PENDIENTE`, y la carga queda `CON_PROPUESTAS`.

Si el valor declarado supera la suma asegurada, la propuesta igual se crea, con `requiereAceptacionRiesgo = true` (RN-06): decide el dador al aceptar. Vence a las 24 h o al cerrar la ventana de carga, lo que pase primero (RN-16).

Una propuesta retirada también cuenta: el mismo transportista no puede volver a proponer para esa carga.

## Qué pasa si algo se cae

| Qué se cae | Flujo A | Flujo B |
|---|---|---|
| `transporte-service` | 503, sin resultados | 503, y no se crea nada |
| API de rutas | 200: sigue en línea recta y los resultados salen con `aproximado = true` | No la usa |

**Por qué se trata distinto.** Sin el retorno no se sabe qué buscar, y sin la habilitación no se sabe si la unidad cumple RN-04: el sistema falla cerrado en vez de suponer. Sin la API de rutas el desvío se puede estimar, y el resultado avisa que es aproximado.

Las llamadas HTTP se hacen fuera de toda transacción, para no tener una conexión a la base ni filas bloqueadas mientras se espera otro servicio.

## Decisiones

| Decisión | Por qué |
|---|---|
| Dos DTO para una carga (`CargaResponse` y `CargaReservadaResponse`) | Un dato que no está en el tipo no se puede filtrar por olvidarse de borrarlo |
| El transportista no ve `lat` ni `lng` | El dador puede haber mandado las coordenadas exactas |
| `DELETE` es una baja lógica | Las propuestas de la carga quedan como historia; las pendientes pasan a `RECHAZADA` |
| El dueño se controla en cargas | Los endpoints internos no lo controlan: devuelven `transportistaSub` y cargas lo compara con el token |
| Retirar y rechazar bloquean la propuesta | `Propuesta` no tiene `@Version`: sin el bloqueo, un retiro podría pisar una aceptación |

## Cómo se prueba

```bash
mvn -f flety-commons/pom.xml install
mvn -f cargas-service/pom.xml test
```

| Test | Qué cubre |
|---|---|
| `FlujosDeMercadoTest` | Los flujos A y B de punta a punta, con `transporte-service` simulado por un servidor HTTP |
| `PropuestaControllerTest`, `CargasDePasoTest`, `CargaControllerTest`, `DadorControllerTest` | Cada regla y cada código de respuesta |
| `PublicarCargaReglasTest` | RN-07 y RN-08 |
| `PropuestasSimultaneasTest` | Dos propuestas iguales a la vez: una entra y la otra recibe 409 |
| `SwaggerDeMercadoTest` | Que Swagger documente los códigos de cada endpoint |

## Qué depende de otras tarjetas

| Tarjeta | Qué falta |
|---|---|
| FLY-36 | `GeorefConfig` usa el fake de `flety-commons`, que conoce pocas localidades |
| FLY-43 | `BusquedaConfig` usa `CalculadorDesvioFake`, que da siempre el mismo desvío: la caída a línea recta se prueba recién con la estrategia real |
| FLY-35 | `cargas-service` todavía no reenvía el token a `transporte-service` |
| Endpoints `/internal` de transporte | Todavía no existen, y tienen que completar `transportistaSub` |
| FLY-32 | Una propuesta guarda su `vencimiento`, pero todavía no hay reloj que pase a `VENCIDA` las propuestas y las cargas |
