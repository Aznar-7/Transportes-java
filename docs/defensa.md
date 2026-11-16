# Guía Total de Decisiones de Arquitectura y Diseño para la Defensa

Esta guía reúne las decisiones de diseño, justificaciones técnicas, patrones implementados y alternativas descartadas de **toda la plataforma Flety** (`gateway-service`, `transporte-service`, `cargas-service`, `flety-commons` e infraestructura).
---

## 1. Visión del Negocio y Bounded Contexts

### ¿Qué problema de negocio resuelve Flety?
En el transporte de carga por carretera, los camiones que regresan a su base suelen viajar vacíos (*retornos vacíos*). Ese viaje de vuelta genera costos hundidos de combustible, peajes y horas de chofer. Hoy se consiguen cargas de retorno mediante grupos informales de WhatsApp o intermediarios, sin auditoría de las partes, sin verificación de habilitación vehicular y sin trazabilidad jurídica o administrativa del viaje. Flety digitaliza y asegura ese proceso conectando la oferta de retornos con la demanda de cargas de paso en un entorno B2B auditado.

### ¿Por qué dos microservicios de negocio y no uno o tres?
Se aplicó el criterio de diseño guiado por el dominio (*Domain-Driven Design - DDD*) cortando por **quién es el dueño del dato y qué razones de cambio comparten**:

- **`transporte-service`**: Dueño de transportistas, camiones, pólizas/documentación obligatoria, habilitación y retornos. Cambia por razones operativas del transportista y auditoría documental.
- **`cargas-service`**: Dueño de dadores, cargas, propuestas comerciales, matching geográfico, contratos de viaje, hitos y conformidad. Cambia por la dinámica de contratación y logística comercial.

#### Alternativas descartadas:
1. **Monolito**: Descartado porque la verificación documental y auditoría manual de camiones tiene requerimientos de seguridad, disponibilidad y ciclo de vida muy distintos a la alta concurrencia y consultas en tiempo real del mercado de cargas y ruteo.
2. **Tres microservicios (separando `viajes-service` de `cargas-service`)**: Descartado deliberadamente tras análisis de diseño. Separar viajes de cargas habría introducido una llamada distribuida adicional en el camino crítico de la adjudicación, requiriendo coordinación transaccional en dos fases (*2PC* o *Saga*) para un dominio donde la carga transiciona directamente a asignada en el mismo instante en que se confirma el viaje. `cargas-service` concentra el contrato comercial desde la publicación hasta la conformidad, manteniendo alta cohesión y evitando latencia de red innecesaria.

---

## 2. API Gateway: Único Punto de Entrada

### ¿Por qué Spring Cloud Gateway con WebFlux reactivo?
El Gateway es la única puerta de enlace pública (puerto 8085). Nadie fuera de la red Docker conoce las IPs o puertos de los microservicios ni de la base de datos. Se eligió **Spring Cloud Gateway (reactivo con Netty y WebFlux)** porque atiende miles de conexiones concurrentes con un pool de hilos mínimo (*Event Loop non-blocking*), optimizando el consumo de CPU y memoria en el punto de entrada más exigido.

### ¿Por qué el Gateway NO contiene reglas de negocio?
Por el principio de **Responsabilidad Única (SRP)**. El Gateway solo:
1. Valida la firma criptográfica y vigencia del JWT con las claves públicas de Keycloak.
2. Aplica control grueso de autorización según la ruta y el rol (`RolesKeycloak`).
3. Enruta la petición al microservicio correspondiente sin reescribir el path.
No accede a bases de datos ni conoce el estado interno de las entidades de negocio.

### ¿Por qué el orden de las reglas de seguridad importa en el Gateway?
Spring Security evalúa las reglas en orden de arriba hacia abajo y aplica la **primera que coincide**. Por eso, las reglas más específicas deben declararse antes que las generales:
- `GET /api/cargas/de-paso` (exclusivo de `TRANSPORTISTA`) debe declararse **antes** que `/api/cargas/**` (asociado a `DADOR`). Si se invirtiera el orden, un transportista buscando cargas recibiría erróneamente `403 Forbidden`.

### ¿Por qué los endpoints `/internal/**` responden 404 desde afuera?
En el Gateway no existe ninguna ruta mapeada para `/internal/**`. Por lo tanto, cualquier intento de un cliente externo de consumir un endpoint interno responde `404 Not Found` en lugar de `403 Forbidden`. Desde el exterior, la API interna es invisible.

---

## 3. Microservicio Transporte: Habilitación y Retornos

### ¿Cómo se evalúa la habilitación de una unidad (RN-04)?
Una unidad **no puede operar** por el simple hecho de existir. Su método de habilitación evalúa dinámicamente tres condiciones obligatorias:
1. Que el transportista propietario esté en estado `VERIFICADO` (auditado por un operador de Flety).
2. Que la unidad tenga cargados todos los tipos de documento marcados como obligatorios (`aplica_a = UNIDAD` y `obligatorio = true`, tales como póliza de responsabilidad civil, póliza de carga y revisión técnica).
3. Que dichos documentos estén en estado `APROBADO` por el operador y su vigencia (`vigencia_hasta`) cubra el momento de la operación (hasta el fin de la ventana de descarga en el caso de un viaje).
Si alguna condición falla, la unidad responde `habilitada: false` con la lista de causales específicas.

### Tareas Programadas en Transporte
`transporte-service` ejecuta dos tareas en lote mediante `@Scheduled`:
- **Vencimiento de documentos (RN-05)**: Diariamente transiciona a `VENCIDO` los documentos cuya fecha de vigencia expiró, inhabilitando automáticamente la unidad para nuevas propuestas sin alterar viajes que ya se encuentren en curso.
- **Vencimiento de retornos (RN-17)**: Marca en `VENCIDO` los retornos cuya ventana de salida ya expiró.

---

## 4. Microservicio Cargas: Mercado y Matching de Paso

### Flujo A — Algoritmo de Búsqueda de Cargas de Paso (RN-01, RN-02)
Calcular el desvío geográfico real por carretera es una operación computacionalmente costosa y sujeta a cuotas de APIs externas. Para que la búsqueda sea ultraeficiente, Flety implementa un **algoritmo en embudo (de lo barato a lo costoso)**:

1. **Consulta interna**: Pide el retorno a `transporte-service` (`GET /internal/retornos/{id}`) para obtener ruta, ventana de salida y desvío máximo tolerado.
2. **Filtro relacional barato en Postgres**: Filtra cargas abiertas (`PUBLICADA` o `CON_PROPUESTAS`), coincidencia temporal entre ventanas de carga y salida, compatibilidad de tipo de carrocería, y capacidad física de peso y volumen.
3. **Prefiltro espacial en SQL (Bounding Box)**: En la misma consulta de base de datos se comprueba que el origen y destino de la carga caigan dentro de una caja envolvente (*Bounding Box*) trazada alrededor de la ruta del retorno con el margen del desvío máximo.
4. **Corte de mejores N**: Sobre las candidatas que superan el Bounding Box, calcula en memoria el desvío en línea recta (Haversine) y preselecciona las **mejores N** (10 por defecto).
5. **Cálculo de desvío real (RN-01)**: Solo para esas N candidatas se calcula el desvío por carretera mediante `CalculadorDesvio` (OpenRouteService). Se descartan las que superen el desvío máximo.
6. **Ordenamiento final (RN-02)**: Se ordenan en primer lugar por menor desvío en kilómetros ascendente; ante empates, por mayor precio ofrecido por kilómetro descendente.

### Flujo B — Postulación y Contraoferta (RN-03, RN-04)
- El transportista puede aceptar el precio inicial del dador o realizar una **única contraoferta** económica. Flety no implementa subastas abiertas ni regateos infinitos para agilizar el cierre logístico.
- Antes de registrar la propuesta en `PENDIENTE`, `cargas-service` consulta sincrónicamente a `transporte-service` (`GET /internal/unidades/{id}/habilitacion?hasta=`) para garantizar que el camión esté habilitado hasta el fin de la ventana de descarga.

---

## 5. Adjudicación Transaccional y Ciclo de Vida del Viaje

### Flujo C — Aceptación de la Propuesta (RN-06, RN-14, RN-15)
Cuando el dador elige una propuesta (`POST /api/cargas/propuestas/{id}/aceptacion`), se ejecuta una operación crítica de alta consistencia:

1. **Llamada a transporte fuera de la transacción**: Vuelve a consultar la habilitación de la unidad con `transporte-service` fuera de `@Transactional`. Esto evita mantener abiertas conexiones JDBC o filas bloqueadas en Postgres mientras se espera una llamada de red HTTP.
2. **Falla cerrada ante caída (503)**: Si `transporte-service` no responde o da timeout, la adjudicación se aborta inmediatamente con `503 Service Unavailable`. Nunca se adjudica a ciegas.
3. **Invalidación por caducidad (422)**: Si un documento venció entre la propuesta y la aceptación, la propuesta se marca como `INVALIDADA` y responde 422.
4. **Transacción atómica de confirmación (`ConfirmacionDeViaje`)**:
   - **Garantía "Una carga, un viaje" (RN-14)**: Bloqueo optimista mediante `@Version` en la entidad `Carga` + un **índice único parcial en PostgreSQL** (`WHERE estado <> 'CANCELADO'`). Si dos dadores o procesos intentan aceptar concurrentemente, uno confirma y el otro recibe `409 Conflict`.
   - **Garantía "Sin superposición de camión" (RN-15)**: Bloqueo pesimista `PESSIMISTIC_WRITE` sobre los viajes activos de la unidad, serializando adjudicaciones simultáneas sobre el mismo camión para rechazar colisiones temporales con 409.
   - **Adjudicación**: La propuesta elegida pasa a `ACEPTADA`, las demás propuestas pendientes de esa carga pasan a `RECHAZADA`, y la carga pasa a `ASIGNADA`.
   - **Copia estática de datos**: El nuevo viaje en estado `CONFIRMADO` almacena una copia inmutable de la razón social, teléfono y dominio del camión. A partir de este momento, **la ejecución y cierre del viaje no dependen de `transporte-service`**. Si transporte cae, los viajes en curso operan sin problemas.
   - **Liberación de datos de contacto (RN-13)**: Solo con el viaje confirmado, ambas partes tienen acceso mutuo a números de teléfono y direcciones exactas de carga y descarga.

### Flujo D — Ciclo de Vida del Viaje (Patrón State, RN-T2, RN-18, RN-19)

```
                                    ┌──> CONFORMADO            (Dador o Reloj a las 48 h)
CONFIRMADO ──> CARGADO ──> EN_TRANSITO ──> ENTREGADO ─┤
    │                                      (48 h)     └──> EN_DISPUTA ──┬──> CONFORMADO        (Operador a favor)
    └──> CANCELADO                                                      └──> CERRADO_CON_DISPUTA (Operador en contra)
```

- **Patrón State**: Cada uno de los 8 estados es una clase concreta que implementa la interfaz `EstadoViaje`. Todos los métodos arrojan excepción por defecto (`TransicionNoPermitidaException` -> 409) y cada estado solo sobrescribe las transiciones válidas. La clase `Viaje` no contiene condicionales `if` ni `switch` sobre su estado.
- **Secuencia lineal obligatoria (RN-T2)**: El transportista asignado es el único habilitado para reportar la carga (`CARGADO`), inicio de ruta (`EN_TRANSITO`) y arribo en destino (`ENTREGADO`). Saltear un hito arroja 409.
- **Comprobante de entrega y remito (RN-18)**: Al marcar `ENTREGADO`, es obligatorio registrar número de remito, nombre y DNI del receptor.
- **Ventana de salvaguarda de 48 horas**: El arribo fija una ventana temporal de 48 horas:
  - El dador puede conformar manualmente (`POST /conformidad`, `conformadoPor = DADOR`).
  - Si el dador no actúa en 48 h, el Reloj programado de `cargas-service` conforma automáticamente el viaje (`conformadoPor = SISTEMA`).
  - Dentro de las 48 h, el dador puede objetar la entrega abriendo una disputa (`POST /disputas`), pasando a `EN_DISPUTA` (RN-19). El operador de Flety audita y dicta la resolución final (`CONFORMADO` o `CERRADO_CON_DISPUTA`).
- **Cancelación anticipada (RN-10)**: Permitida solo en estado `CONFIRMADO` (antes de cargar). Si la cancelación ocurre a menos de 24 horas del inicio de la ventana de carga, el viaje queda marcado con penalidad (`conPenalidad = true`).

---

## 6. Seguridad Integral y Gestión de Identidad

### Keycloak y Modelo de Tokens JWT
Flety utiliza **Keycloak 26** como servidor de identidad (OpenID Connect / OAuth 2.0) con el realm preconfigurado `flety`:
- Roles de realm: `TRANSPORTISTA`, `DADOR`, `OPERADOR`.
- Usuarios semilla de prueba: `transportista1`, `transportista2`, `dador1`, `operador1`.

### ¿Por qué NO hay tabla de usuarios en las bases de datos?
Flety delega íntegramente la gestión de usuarios, contraseñas, hashing y sesiones en Keycloak. Cada microservicio almacena únicamente el `sub` (UUID) del token como `ownerSub` en sus tablas de negocio.
- **Ventaja**: Desacopla la identidad del dominio. Si el usuario actualiza credenciales o políticas de autenticación, la lógica de negocio de los microservicios no se altera.

### Validación en Dos Capas (Defensa en Profundidad)
```
Cliente ──Bearer JWT──> Gateway (firma + rol de ruta) ──mismo JWT──> Microservicio (firma + rol + dueño RN-T1)
```
1. **Capa 1 — Gateway (Control grueso)**: Valida la firma del token con las claves públicas de Keycloak (`JWKS`) y controla el rol exigido por la ruta. Bloquea peticiones anónimas (401) o de rol equivocado (403).
2. **Capa 2 — Microservicio (Control fino)**: Vuelve a validar la firma del token (por si se vulnera la red interna) y aplica el **control de pertenencia / dueño (RN-T1)** comparando el `sub` del token con el `ownerSub` del registro. Si un transportista intenta modificar el retorno o camión de otro, el servicio responde `403 Forbidden`.

### Propagación del Token entre Microservicios
Cuando `cargas-service` consulta a `transporte-service` (`GET /internal/**`), el componente `ReenvioTokenInterceptor` de `flety-commons` extrae automáticamente el token Bearer del `SecurityContext` actual y lo inyecta en la cabecera `Authorization` de la llamada saliente de `RestClient`. El microservicio llamado valida el token con la misma seguridad.

---

## 7. Librería Común (`flety-commons`)

### Qué se comparte y qué NO se comparte *(Pregunta fija de la defensa)*
| Componente | ¿Se comparte? | Justificación técnica |
|---|---|---|
| **Excepciones base y ProblemDetail** | **SÍ** | Estandariza la semántica de errores RFC 7807 en ambas APIs (400, 401, 403, 404, 409, 422, 503). |
| **Validación de CUIT (`@CuitValido`)** | **SÍ** | Ambos servicios validan organizaciones con CUIT y algoritmo módulo 11. |
| **Seguridad base y JWT** | **SÍ** | Ambos servicios son Resource Servers que validan el mismo realm de Keycloak. |
| **Fábrica de RestClient** | **SÍ** | Unifica timeouts (2 s conexión, 5 s lectura), interceptores y reintentos. |
| **Patrón Strategy de desvío** | **SÍ** | Permite alternar entre OpenRouteService y Haversine con fallback ante caídas. |
| **Contrato interno (`/internal/**`)** | **SÍ** | DTOs `HabilitacionUnidadDTO`, `RetornoInternoDTO` y enum `TipoCarroceria`. |
| **OpenAPI / Swagger común** | **SÍ** | Esquema Bearer y botón Authorize inyectados por autoconfiguración. |
| **Entidades JPA y repositorios** | **NO** | Violaría *Database per Service*. Ataría los esquemas de bases de datos de ambos servicios. |
| **Máquinas de estado (`EstadoViaje`)** | **NO** | Cada ciclo de vida pertenece a un único dueño. `cargas-service` es dueño del viaje. |
| **Reglas de negocio del dominio** | **NO** | Cada servicio encapsula su propio *Bounded Context*. |
| **Tareas programadas (`@Scheduled`)** | **NO** | Cada servicio gestiona sus propios procesos en lote. |

### Criterio de Corte General
> *"Se comparte infraestructura técnica repetida y el contrato de comunicación interna; queda adentro de cada microservicio todo lo que representa negocio, estado, persistencia o datos propios."*

### Empaquetado y Versionado Semántico (SemVer)
- Empaquetado como biblioteca JAR estándar (sin goal `repackage` para evitar fat-JARs ejecutables).
- Registrada con autoconfiguración de Spring Boot (`AutoConfiguration.imports`).
- Versión congelada en **`1.0.0`**. El costo de compartir DTOs se mitiga mediante cambios puramente aditivos compatibles (agregar campos opcionales sin romper contratos existentes).

---

## 8. Patrones de Diseño Implementados

1. **Patrón Strategy (Cálculo de desvío - FLY-43)**: Interfaz `CalculadorDesvio` con `EstrategiaRutaOpenRouteService` (ruta vial real), `EstrategiaRutaLineaRecta` (distancia Haversine) y `CalculadorDesvioConFallback`. Desacopla el algoritmo del consumidor y garantiza degradación elegante.
2. **Patrón State (Ciclo de vida del viaje)**: Interfaz `EstadoViaje` con clases concretas para cada uno de los 8 estados del viaje. Encapsula las transiciones válidas y responde 409 ante acciones inválidas.
3. **Factory Method (`FabricaRestClient`)**: Centraliza la parametrización de clientes HTTP con timeouts y reenvío de tokens.
4. **Template / Handler Advice**: `GlobalExceptionHandler` estandariza el procesamiento de excepciones en toda la plataforma.

---

## 9. Integraciones Externas y Resiliencia ante Fallos

- **OpenRouteService (Rutas y distancias)**:
  - Al publicar retorno: Si la API de rutas cae, el retorno se guarda con `rutaAproximada = true`.
  - Al buscar cargas: Si la API cae, `CalculadorDesvio` conmuta a línea recta y marca los resultados como aproximados.
  - **La plataforma nunca se cae por indisponibilidad de la API externa de rutas**.
- **Georef Argentina (Coordenadas de localidades)**:
  - Si el cliente envía coordenadas explícitas, se priorizan las del cliente y no se consume cuota externa.
  - Si no se mandaron coordenadas y Georef no responde: Responde `503 Service Unavailable` con mensaje descriptivo pidiendo ingresar coordenadas manuales. **Nunca se inventan coordenadas falsas**.
- **Falla Cerrada en Transporte Interno**:
  - Si `transporte-service` cae al proponer o aceptar un viaje, `cargas-service` responde `503` y no confirma nada. Se prioriza la seguridad jurídica antes que registrar viajes con camiones no habilitados.

---

## 10. Persistencia, Auditoría y Concurrencia

- **Aislamiento de bases**: `transporte_db` y `cargas_db` corren sobre PostgreSQL 16 con esquemas y usuarios separados. Ninguna consulta SQL cruza bases de datos.
- **Auditoría inmutable (`EVENTO_VIAJE`)**: La tabla de eventos de viaje es estrictamente de solo inserción (*Append-Only*). Cada cambio de estado registra fecha, actor (`sub` o `SISTEMA`) y motivo, sin admitir modificaciones ni borrados.
- **Fechas en UTC**: Todas las ventanas horarias y fechas de vigencia se persisten en formato `Instant` (UTC). La conversión a hora local es responsabilidad del cliente que presenta los datos.
- **Tests con H2 en memoria**: Las suites automatizadas corren sobre H2 en memoria y tokens JWT simulados (`@WithMockUser` o `jwt()`), logrando que cientos de tests se ejecuten en segundos sin requerir Docker ni Keycloak levantados.

---

## 11. Matriz de Preguntas Frecuentes para la Defensa

| Pregunta de la mesa | Respuesta clave del equipo |
|---|---|
| **¿Por qué dividieron en dos servicios y no en tres (separando viajes)?** | Porque `cargas-service` concentra el ciclo comercial desde que la carga nace hasta que se conforma. Separar viajes habría introducido transacciones distribuidas en dos fases (*2PC* o *Sagas*) y llamadas de red en el momento crítico de la adjudicación, sin aportar valor al negocio. |
| **¿Qué pasa si `transporte-service` se cae mientras un dador acepta una propuesta?** | El sistema aplica **falla cerrada**: responde `503 Service Unavailable` y aborta la transacción sin confirmar el viaje. Es preferible rechazar temporalmente la operación antes que confirmar un viaje con un camión no habilitado o con pólizas vencidas (RN-04). |
| **¿Cómo aseguran que dos dadores no acepten la misma carga al mismo tiempo?** | Mediante dos capas: 1) Bloqueo optimista con `@Version` en la entidad `Carga` (la segunda transacción falla con `OptimisticLockingFailureException` y devuelve 409). 2) Un índice único parcial en PostgreSQL sobre `viaje.carga_id` para viajes no cancelados. |
| **¿Cómo evitan que un camión tenga dos viajes a la misma hora?** | Mediante bloqueo pesimista `PESSIMISTIC_WRITE` sobre los viajes activos de la unidad al momento de confirmar, procesando las adjudicaciones de forma secuencial y rechazando superposiciones horarias con 409 (RN-15). |
| **¿Por qué el Gateway valida el JWT y el microservicio lo vuelve a validar?** | Por el principio de **Defensa en Profundidad**. El Gateway hace control grueso (firma, vigencia y rol por ruta). El microservicio vuelve a validar la firma (por si alguien accede directo en la red interna) y aplica el control fino de dueño (RN-T1) comparando el `sub` con el `ownerSub` en la base de datos. |
| **¿Por qué no hay tabla de usuarios en las bases de datos?** | Flety delega completamente la identidad y credenciales en Keycloak (OIDC). Las entidades de dominio solo guardan el `sub` del token, desacoplando la gestión de identidades del negocio. |
| **¿Qué comparten en la librería común y qué dejaron afuera?** | Se comparte solo infraestructura técnica repetida (excepciones, `ProblemDetail`, JWT, clientes HTTP, utilidades) y el contrato interno (DTOs `/internal/**`). Se dejaron afuera entidades JPA, repositorios, máquinas de estado y reglas de negocio para respetar el principio *Database per Service* y permitir despliegues independientes. |
| **¿Por qué el Gateway no usa `flety-commons`?** | Porque el Gateway fue desarrollado con Spring Cloud Gateway reactivo (Spring WebFlux), mientras que la seguridad y controladores de `flety-commons` están construidos para servlets tradicionales (Spring WebMVC blocking). |
| **¿Cómo resuelven la búsqueda de paso sin que OpenRouteService sea un cuello de botella?** | Con un algoritmo en embudo: 1) filtro relacional de ventanas y carrocerías, 2) prefiltro espacial por Bounding Box en la misma consulta SQL, 3) preselección de las mejores N por Haversine en línea recta, y 4) desvío vial real con OpenRouteService solo para esas N candidatas. |
| **¿Qué pasa si cae OpenRouteService en plena operación?** | El patrón Strategy implementado conmuta automáticamente a la estrategia en línea recta (`CalculadorDesvioConFallback`) y marca los resultados como aproximados (`aproximado = true`). El servicio nunca deja de funcionar. |
