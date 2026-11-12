# Transporte

`transporte-service` sabe quién puede transportar, con qué unidad y por dónde vuelve: transportistas, unidades, documentos, la habilitación de cada unidad y los retornos. Es dueño de esos datos (base `transporte_db`) y aplica RN-04, RN-05 y RN-17. `cargas-service` le consulta lo que necesita por dos endpoints internos ([contrato](contrato-interno/transporte-interno.yml)) y nunca al revés.

## Endpoints

Todos los errores salen como `ProblemDetail`. El detalle de cada código está en Swagger (`/swagger-ui/index.html` del servicio).

| Endpoint | Rol | Qué hace | Éxito |
|---|---|---|---|
| `POST /api/transporte/transportistas` | TRANSPORTISTA | Da de alta la organización | 201 |
| `GET /api/transporte/transportistas/me` | TRANSPORTISTA | Consulta la organización propia | 200 |
| `POST /api/transporte/unidades` | TRANSPORTISTA | Registra una unidad | 201 |
| `GET /api/transporte/unidades` | TRANSPORTISTA | Lista las unidades propias | 200 |
| `POST /api/transporte/unidades/{id}/documentos` | TRANSPORTISTA | Carga un documento: queda `EN_REVISION` | 201 |
| `GET /api/transporte/unidades/{id}/habilitacion` | TRANSPORTISTA | Dice si la unidad puede proponer y qué le falta | 200 |
| `POST /api/transporte/retornos` | TRANSPORTISTA | Publica un retorno con la ruta calculada | 201 |
| `GET /api/transporte/retornos` | TRANSPORTISTA | Lista los propios, con filtro `estado` | 200 |
| `GET /api/transporte/retornos/{id}` | TRANSPORTISTA | Muestra un retorno propio | 200 |
| `DELETE /api/transporte/retornos/{id}` | TRANSPORTISTA | Lo cancela: baja lógica, queda `CANCELADO` | 204 |
| `GET /api/transporte/operador/transportistas` | OPERADOR | Lista transportistas, con filtro `estado` | 200 |
| `PATCH /api/transporte/operador/transportistas/{id}/verificacion` | OPERADOR | Verifica a un transportista | 200 |
| `GET /api/transporte/operador/documentos` | OPERADOR | Lista documentos, con filtro `estado` | 200 |
| `PATCH /api/transporte/operador/documentos/{id}/revision` | OPERADOR | Aprueba o rechaza (con motivo) un documento | 200 |
| `GET /internal/unidades/{id}/habilitacion?hasta=` | cualquier token | Para `cargas-service`, flujos B y C | 200 |
| `GET /internal/retornos/{id}` | cualquier token | Para `cargas-service`, flujo A | 200 |

## Quién es el dueño (RN-T1)

Ninguna tabla guarda usuarios. Cada transportista tiene el `sub` del JWT como `ownerSub`, y el service lo compara con `UsuarioActual.id()` de flety-commons. Una unidad, un documento o un retorno son de quien es dueño de su transportista. Si alguien con el rol correcto pide el recurso de otro, responde **403**; si el recurso no existe, **404**.

Los listados filtran por dueño en la misma consulta (por ejemplo `findByUnidadTransportistaOwnerSub…`), así un transportista nunca ve lo de otro aunque se olvide un `if`.

Los dos `/internal` no verifican dueño: devuelven `transportistaSub` y el control lo hace `cargas-service`, que es quien conoce al usuario del flujo. Los protege que el Gateway no los publica.

## Alta y verificación

El transportista se registra y nace `PENDIENTE_VERIFICACION`. Un usuario tiene una sola organización, y el CUIT no se repite: los dos casos dan 409.

```http
POST /api/transporte/transportistas
Authorization: Bearer <token de transportista1>

{"cuit": "30546671166", "razonSocial": "Transportes Pérez SRL", "telefono": "+5493514445566"}
```

```json
201 Created
{"id": 1, "cuit": "30546671166", "razonSocial": "Transportes Pérez SRL", "telefono": "+5493514445566",
 "estado": "PENDIENTE_VERIFICACION"}
```

La respuesta no tiene `ownerSub` ni el pedido puede traer `estado`: nadie se verifica a sí mismo. Lo verifica un operador con `PATCH .../verificacion`, y verificar dos veces da 409.

Las unidades cuelgan del transportista: dominio único (409 si se repite), carrocería y capacidad en toneladas y m³.

## Documentos

Los tipos de documento son una tabla, no código. Los iniciales están en [`data.sql`](../transporte-service/src/main/resources/data.sql), que corre en cada arranque sin duplicarlos:

| Tipo | Aplica a | Obligatorio | Vence | Pide suma asegurada |
|---|---|---|---|---|
| Póliza de responsabilidad civil | Unidad | Sí | Sí | No |
| Póliza de carga | Unidad | Sí | Sí | Sí (RN-06) |
| Revisión técnica | Unidad | Sí | Sí | No |
| Licencia profesional | Transportista | Sí | Sí | No |

Un documento nace `EN_REVISION` y el operador lo pasa a `APROBADO` o `RECHAZADO` (el rechazo pide motivo). Revisarlo dos veces da 409. Al cargarlo:

- Vencido de entrada: **400**, porque se ve en el pedido mismo (`@Future`).
- Un tipo que no aplica a la unidad, un tipo que vence sin `vigencia` o la póliza de carga sin `sumaAsegurada`: **422**, porque depende de la tabla de tipos.

La licencia profesional queda en la tabla, pero el diseño solo tiene endpoint de documentos por unidad, así que hoy no se puede cargar y RN-04 mira solo los documentos de la unidad.

## Habilitación (RN-04)

Una unidad está habilitada **hasta una fecha** si:

1. su transportista está `VERIFICADO`, y
2. cada tipo obligatorio de unidad tiene al menos un documento `APROBADO` vigente hasta esa fecha.

El cálculo vive en `HabilitacionService` y lo usan los dos endpoints, así nunca pueden dar resultados distintos. No corta en el primer problema: junta todos los motivos.

| Situación | Motivo |
|---|---|
| El transportista no está verificado | `El transportista no está VERIFICADO` |
| Ningún documento aprobado de un tipo | `Falta el documento obligatorio Póliza de carga` |
| Hay uno aprobado pero vence antes | `Revisión técnica vence el 2026-11-01, antes del 2026-11-05` |

`habilitada` es `true` solo si no hay motivos. `sumaAseguradaCarga` es la de la póliza de carga aprobada y vigente (la mayor si hay varias), o `null` si no tiene: con eso `cargas-service` aplica RN-06.

`cargas-service` pregunta con `hasta` = fin de la ventana de descarga de la carga, al proponer y otra vez al aceptar, porque un documento puede vencer en el medio. El transportista puede preguntar sin `hasta` y se calcula para este momento. Ejemplos de respuesta: [habilitada](contrato-interno/ejemplos/habilitacion-habilitada.json) y [no habilitada](contrato-interno/ejemplos/habilitacion-no-habilitada.json).

## Retornos

```http
POST /api/transporte/retornos
Authorization: Bearer <token de transportista1>

{"unidadId": 1,
 "origen":  {"provincia": "Santa Fe", "localidad": "Rosario", "lat": -32.9468, "lng": -60.6393},
 "destino": {"provincia": "Córdoba", "localidad": "Córdoba"},
 "salidaDesde": "2026-11-20T09:00:00Z", "salidaHasta": "2026-11-20T21:00:00Z",
 "desvioMaximoKm": 30}
```

1. **La unidad** tiene que ser propia (403 si es de otro, 404 si no existe).
2. **Las coordenadas:** si el lugar trae `lat` y `lng` se usan tal cual. Si no, se le piden a Georef; si no la ubica, responde **503** con "No pudimos ubicar la localidad …: enviá las coordenadas". Nunca se inventan.
3. **La ruta** se le pide a la API de rutas. Si falla, o si el servicio no tiene cliente de rutas, se guarda la línea recta origen–destino con `rutaAproximada: true` y duración 0, y queda en el log. `cargas-service` usa esa marca para ensanchar el prefiltro.
4. **`desvioMaximoKm`** vale 30 si no viene.

Nace `PUBLICADO`. El transportista puede cancelarlo (`DELETE`): es una baja lógica, queda `CANCELADO` y su historia no se pierde. Cancelar uno que ya no está publicado da 409.

Los clientes de Georef y de rutas se piden con `ObjectProvider`: si el servicio todavía no tiene el bean, se comporta como si la API estuviera caída, sin fakes en producción.

## Relojes (RN-05 y RN-17)

`RelojesTransporte` solo dispara; la regla está en cada service, que recibe la hora. Por eso los tests usan una fecha fija.

| Reloj | Cuándo | Qué hace |
|---|---|---|
| Documentos | Todos los días a las 3 (hora de Córdoba) | `EN_REVISION` o `APROBADO` con la vigencia cumplida → `VENCIDO`. Un rechazado queda igual |
| Retornos | Cada 15 minutos | `PUBLICADO` con la ventana de salida terminada → `VENCIDO` |

Los horarios se cambian con `flety.relojes.documentos` (cron) y `flety.relojes.retornos` (duración). En los tests el reloj está apagado (`flety.relojes.habilitados: false`) para que no toque los datos de otro test. Un documento vencido deja de contar para la habilitación; los viajes ya confirmados siguen, porque `cargas-service` guardó su copia de los datos.

## Cómo se prueba

`mvn test` en `transporte-service` corre todo contra H2, sin Keycloak: `TokensDePrueba` reemplaza el `JwtDecoder` por uno que reconoce tokens fijos, y el resto de la seguridad de flety-commons es la real. Además de los tests de cada módulo:

- `HabilitacionTest` y `RetornoConsultaTest` comparan los campos de los `/internal` con los ejemplos del contrato.
- `PermisosTest` prueba que cada endpoint dé 403 con el rol equivocado.
- `SwaggerTest` prueba que el documento OpenAPI tenga los códigos reales.
