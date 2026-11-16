# Librería Común: flety-commons

`flety-commons` es la biblioteca Maven compartida (`com.flety:flety-commons:1.0.0`) que contiene la infraestructura reutilizable y el contrato interno entre microservicios de Flety.

Su objetivo es evitar la duplicación de código técnico entre `transporte-service` y `cargas-service`, manteniendo la lógica de negocio y los datos estrictamente encapsulados en cada servicio.

---

## Servicios que la usan y quién no

| Servicio | ¿Usa flety-commons? | Motivo |
|---|---|---|
| **transporte-service** | **Sí** | Reutiliza excepciones, manejo de errores `ProblemDetail`, validación de CUIT, seguridad JWT, clientes HTTP y DTOs de contrato interno. |
| **cargas-service** | **Sí** | Reutiliza la misma infraestructura de errores, validación, seguridad, clientes externos (ruteo y georreferenciación), cálculo de desvío (Strategy) y el contrato interno. |
| **gateway-service** | **No** | El API Gateway está desarrollado con **Spring Cloud Gateway (reactivo con Spring WebFlux)**, mientras que la configuración de seguridad y controladores de `flety-commons` está diseñada para el modelo de servlets (**Spring MVC blocking**). El Gateway utiliza una clase liviana equivalente (`RolesKeycloak`) para mapear roles sin acoplarse a la librería. |

---

## Qué se comparte

Se comparte exclusivamente **código técnico repetido e infraestructura común**, además del **contrato de comunicación interna**:

| Componente | Qué contiene | Por qué se comparte |
|---|---|---|
| **Excepciones base** | `ReglaNegocioException`, `ConflictoException`, `AccesoDenegadoException`, `RecursoNoEncontradoException`, `ServicioNoDisponibleException`, `ServicioExternoException`. | Estandariza la semántica de errores en toda la plataforma. Cada excepción representa un tipo de fallo bien definido sin repetir definiciones. |
| **Manejo global de errores** | `GlobalExceptionHandler` (`@RestControllerAdvice`). | Captura las excepciones de negocio y de validación (`MethodArgumentNotValidException`, etc.) y las transforma en respuestas estándar **RFC 7807 (`ProblemDetail`)** con códigos HTTP uniformes (400, 401, 403, 404, 409, 422, 503). |
| **Validación de CUIT** | Anotación `@CuitValido` y validador `CuitValidador`. | Ambos microservicios dan de alta organizaciones (transportistas y dadores) que requieren CUIT argentino válido (11 dígitos y dígito verificador módulo 11). |
| **Seguridad base y JWT** | `SeguridadAutoConfiguration`, `RolesKeycloakConverter`, `UsuarioActual` y `ReenvioTokenInterceptor`. | Ambos servicios validan tokens JWT emitidos por el realm `flety` de Keycloak. El conversor pasa `realm_access.roles` a `ROLE_*`, `UsuarioActual` da acceso estático al `sub` del usuario autenticado, y el interceptor propaga el token en llamadas salientes. |
| **OpenAPI / Swagger común** | `OpenApiAutoConfiguration` y `SwaggerSecurityCustomizer`. | Registra automáticamente el esquema de seguridad HTTP Bearer JWT en Swagger UI (`components.securitySchemes.bearer`) y añade el botón verde **Authorize** en ambos microservicios sin repetir configuración (FLY-38). |
| **Fábrica de RestClient** | `FabricaRestClient`. | Configura instancias de `RestClient.Builder` con timeouts estrictos (2 s de conexión, 5 s de lectura), conversión automática de errores 4xx/5xx en excepciones de commons, e interceptor de reenvío de token Bearer. |
| **Clientes de APIs externas** | `RoutingClient` (`OpenRouteServiceClientHttp`) y `GeorefClient` (`GeorefClientHttp`). | Ambos servicios consumen las mismas APIs externas: `transporte-service` para ruteo de retornos y coordenadas; `cargas-service` para ubicación de cargas y cálculo de desvíos. |
| **Patrón Strategy de desvío** | `CalculadorDesvio`, `EstrategiaRutaOpenRouteService` y `EstrategiaRutaLineaRecta`. | Permite a `cargas-service` calcular desvíos reales contra OpenRouteService o alternar de forma transparente a línea recta con fallback ante caídas o contingencias (FLY-43). |
| **Utilidades geográficas** | `GeoUtils`. | Cálculo de distancias en línea recta mediante fórmula de Haversine y cálculo de caja envolvente (*Bounding Box*) alrededor de una ruta para prefiltro espacial. |
| **Contrato interno** | DTOs `HabilitacionUnidadDTO`, `RetornoInternoDTO` y enum `TipoCarroceria`. | Define el lenguaje común que comparten `cargas-service` (cliente) y `transporte-service` (servidor) en los endpoints protegidos `/internal/**`. |

---

## Qué NO se comparte y por qué (Justificación de arquitectura)

Para evitar el acoplamiento y preservar la independencia de los microservicios, se definió un límite estricto sobre qué elementos **nunca** deben formar parte de la librería:

| Elemento | Dónde vive | Por qué NO se comparte |
|---|---|---|
| **Entidades JPA y repositorios** | En cada microservicio (`transporte_db` y `cargas_db`). | Compartir entidades JPA ataría los esquemas de bases de datos de ambos servicios, violando el principio de **Database per Service**. Si una entidad cambiara, ambos servicios tendrían que migrar y redeployarse juntos, eliminando el despliegue independiente. |
| **Máquinas de estado y transiciones** | En cada microservicio (`EstadoViaje` en cargas; `EstadoDocumento` en transporte). | El ciclo de vida de un recurso pertenece a un único dueño. `cargas-service` administra la vida del viaje y sus 8 estados (patrón State); `transporte-service` administra los estados de transportistas y documentos. |
| **Reglas de negocio del dominio** | En cada microservicio. | Las reglas de negocio (matching geográfico RN-01/02, validación de habilitación RN-04, control de ventanas RN-15, transacciones de adjudicación RN-14) corresponden al *Bounded Context* específico de cada servicio. |
| **Tareas programadas (`@Scheduled`)** | En cada microservicio. | Cada servicio gestiona sus propios procesos en lote: `transporte-service` vence documentos y retornos; `cargas-service` vence propuestas, cargas y ejecuta la conformidad automática a las 48 h. |
| **Configuración de base de datos** | En cada microservicio (`application.yml`). | Cada servicio tiene sus propias credenciales, pool de conexiones Hikari y base de datos física aislada. |
| **Controllers y DTOs públicos** | En cada microservicio. | Cada microservicio expone su API pública propia con su contrato REST hacia el API Gateway. |

---

## Criterio de corte general

> **Se comparte infraestructura técnica repetida y el contrato de comunicación interna; queda adentro de cada microservicio todo lo que representa negocio, estado, persistencia o datos propios.**

---

## Distribución, empaquetado y versión

1. **Empaquetado sin `repackage`**:
   `flety-commons` utiliza el `maven-compiler-plugin` y `maven-jar-plugin` estándar. **No** utiliza el goal `repackage` de `spring-boot-maven-plugin`, ya que este generaría un fat-JAR ejecutable inutilizable como dependencia de otros proyectos.
2. **Versionado Semántico (SemVer)**:
   Se publica bajo la versión congelada **`1.0.0`**, garantizando estabilidad para el consumo de `transporte-service` y `cargas-service`. Cualquier cambio futuro que rompa compatibilidad exigirá una versión mayor (`2.0.0`).
3. **Autoconfiguración declarativa**:
   Se registra mediante el estándar de Spring Boot en `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, activándose automáticamente en los servicios consumidores mediante anotaciones condicionales (`@ConditionalOnClass`, `@ConditionalOnMissingBean`).
4. **Build en Docker multi-stage**:
   Dado que `flety-commons` es una librería interna no publicada en Maven Central, el `Dockerfile` raíz del proyecto utiliza compilación multi-stage: primero compila e instala `flety-commons` en el repositorio local `.m2` de la etapa de build, y luego compila el microservicio correspondiente.

---

## Costo asumido y mitigación

Compartir los DTOs del contrato interno en la librería implica un costo: **un cambio en un DTO obliga a recompilar y desplegar ambos microservicios**.

Este costo se mitiga deliberadamente mediante dos decisiones:
1. **Versionado semántico estricto (`1.0.0`)**: Evita roturas accidentales en tiempo de compilación o ejecución.
2. **Cambios aditivos compatibles**: Cualquier evolución futura del contrato se diseña agregando campos nuevos opcionales, sin renombrar ni eliminar campos existentes.

Se descartó la alternativa de duplicar los DTOs en cada servicio, ya que el riesgo de desincronización silenciosa del contrato interno entre equipos era significativamente mayor.

