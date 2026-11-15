# Flety

Backend de microservicios para la optimización logística B2B que conecta camiones que retornan vacíos con cargas que les quedan de paso.

Permite a los transportistas rentabilizar sus viajes de retorno, a las empresas dadoras de carga reducir costos de flete, y a ambas partes operar con seguridad mediante validación documental, control estricto de concurrencia y registro secuencial de hitos con comprobante de entrega.

---

## Arquitectura del Sistema

El sistema está diseñado bajo una arquitectura de microservicios con separación estricta de responsabilidades y bases de datos aisladas (*Database per Service*):

```
                   :8085                               Red interna flety-net
Cliente ──────> gateway-service ──┬──> transporte-service :8091 ──┐
   │                              └──> cargas-service :8092 ──────┼──> postgres :5432
   │                                        │  (GET /internal)    │     transporte_db, cargas_db
   │                                        └──> transporte-service┘
   └── Token ──> keycloak :8081 (realm flety)
```

- **API Gateway (puerto 8085)**: Único punto de entrada a la plataforma. Valida la firma del token JWT y controla permisos por ruta y rol.
- **transporte-service (puerto 8091)**: Gestión de transportistas, camiones, documentación obligatoria, habilitación y retornos.
- **cargas-service (puerto 8092)**: Gestión de dadores, cargas, búsqueda de paso con desvío geográfico, propuestas, viajes, hitos, conformidad y disputas.
- **flety-commons**: Biblioteca compartida con excepciones, manejo estándar de errores (`ProblemDetail`), seguridad, clientes de APIs externas y contrato interno.
- **Keycloak (puerto 8081)**: Servidor de identidad y autenticación OAuth2 / OpenID Connect (realm `flety`).
- **PostgreSQL (puerto 5432)**: Instancia de base de datos con esquemas y usuarios separados (`transporte_db` y `cargas_db`).

---

## Documentación por Módulo

Cada componente y aspecto de arquitectura cuenta con su propia documentación detallada:

| Módulo / Tema | Documento | Descripción |
|---|---|---|
| **API Gateway** | [docs/gateway.md](docs/gateway.md) | Enrutamiento reactivo (Spring Cloud Gateway), seguridad por ruta, protección de `/internal/**` y recorrido del token. |
| **Transporte** | [docs/transporte.md](docs/transporte.md) | Altas, documentos con vigencia, verificación de unidades (RN-04) y publicación de retornos con ruta calculada. |
| **Mercado de Cargas** | [docs/mercado.md](docs/mercado.md) | Altas de dadores, publicación de cargas y algoritmo de búsqueda de cargas de paso con desvío geográfico (RN-01, RN-02). |
| **Viajes y Conformidad** | [docs/viajes.md](docs/viajes.md) | Postulación de propuestas, adjudicación transaccional, ciclo de vida del viaje (patrón State), hitos, remito y disputas. |
| **Librería Común** | [docs/commons.md](docs/commons.md) | Arquitectura de `flety-commons`: qué se comparte y qué no, justificación de diseño, patrón Strategy y empaquetado. |
| **Seguridad** | [docs/seguridad.md](docs/seguridad.md) | Configuración del realm de Keycloak, roles, estructura de tokens JWT y validación en dos capas (Gateway + microservicio). |
| **Despliegue** | [docs/despliegue.md](docs/despliegue.md) | Guía paso a paso para levantar la solución con Docker Compose, variables de entorno (`.env`), perfiles y resolución de problemas. |
| **Contrato Interno** | [docs/contrato-interno/transporte-interno.yml](docs/contrato-interno/transporte-interno.yml) | Especificación OpenAPI de los endpoints internos `/internal/**` para comunicación entre servicios. |

---

## Usuarios y Credenciales de Prueba

Keycloak se inicializa automáticamente con el realm `flety` (importado desde `infra/keycloak/flety-realm.json`) con los siguientes usuarios semilla para desarrollo y pruebas:

| Usuario | Contraseña | Rol de Realm | Propósito en la Demo |
|---|---|---|---|
| `transportista1` | `transportista1` | `TRANSPORTISTA` | Registra organización, unidad, sube documentos, publica retornos, postula propuestas y reporta los hitos del viaje. |
| `transportista2` | `transportista2` | `TRANSPORTISTA` | Segundo transportista para pruebas de control de acceso por dueño (RN-T1) y competencia de propuestas sobre una carga. |
| `dador1` | `dador1` | `DADOR` | Registra organización dadora, publica cargas, evalúa y acepta propuestas, y confirma la conformidad de la entrega. |
| `operador1` | `operador1` | `OPERADOR` | Personal de Flety: audita organizaciones, aprueba/rechaza documentación de unidades y resuelve disputas comerciales. |

- **Cliente Keycloak**: `flety-test` (acceso directo habilitado con grant type `password`).
- **Endpoint de tokens**: `http://localhost:8081/realms/flety/protocol/openid-connect/token`

---

## Criterios de Verificación Actualizados (C1 y C2)

A continuación se detallan los escenarios de verificación del MVP acordados con la cátedra para comprobar el funcionamiento de extremo a extremo:

### C1 — Habilitación de la Unidad

Verifica que un camión solo quede habilitado para operar una vez que el transportista fue verificado y todos sus documentos obligatorios fueron aprobados por un operador (RN-04).

1. **Condición inicial**:
   - `transportista1` da de alta su organización (`POST /api/transporte/transportistas`) y queda en estado `PENDIENTE_VERIFICACION`.
   - Da de alta una unidad (`POST /api/transporte/unidades`) y carga sus documentos obligatorios (`POST /api/transporte/unidades/{id}/documentos`), los cuales quedan en estado `EN_REVISION`.
2. **Paso 1 — Consulta previa a la verificación**:
   - `transportista1` consulta el estado de habilitación:
     ```http
     GET /api/transporte/unidades/{id}/habilitacion
     Authorization: Bearer <token transportista1>
     ```
   - **Resultado esperado**: `200 OK` con `habilitada: false` y la lista detallada de motivos (`"Transportista pendiente de verificación"`, `"Póliza RC en revisión"`, etc.).
3. **Paso 2 — Auditoría del operador**:
   - `operador1` audita y verifica la organización:
     ```http
     PATCH /api/transporte/operador/transportistas/{id}/verificacion
     Authorization: Bearer <token operador1>
     ```
   - `operador1` aprueba los documentos obligatorios de la unidad:
     ```http
     PATCH /api/transporte/operador/documentos/{id}/revision
     Authorization: Bearer <token operador1>
     Body: {"estado": "APROBADO"}
     ```
4. **Paso 3 — Consulta posterior a la verificación**:
   - `transportista1` vuelve a consultar la habilitación:
     ```http
     GET /api/transporte/unidades/{id}/habilitacion
     ```
   - **Resultado esperado**: `200 OK` con `habilitada: true` y lista de motivos vacía. La unidad queda formalmente apta para postularse a cargas.

---

### C2 — Publicación de un Retorno con Ruta Calculada

Verifica la integración con Georef Argentina (resolución de coordenadas geográficas) y OpenRouteService (cálculo de distancias y duración real de la ruta).

1. **Condición inicial**:
   - `transportista1` está verificado y su unidad se encuentra habilitada (`habilitada: true`).
   - Clave de API de OpenRouteService configurada en `.env`.
2. **Paso 1 — Publicación indicando solo localidades**:
   - El transportista publica el retorno enviando solo los nombres de provincia y localidad de origen y destino, sin coordenadas manuales:
     ```http
     POST /api/transporte/retornos
     Authorization: Bearer <token transportista1>
     Content-Type: application/json

     {
       "unidadId": 1,
       "origen": {"provincia": "Córdoba", "localidad": "Córdoba"},
       "destino": {"provincia": "Santa Fe", "localidad": "Rosario"},
       "salidaDesde": "2026-11-20T08:00:00Z",
       "salidaHasta": "2026-11-20T18:00:00Z",
       "desvioMaximoKm": 30
     }
     ```
3. **Paso 2 — Resolución y cálculo**:
   - `transporte-service` invoca a Georef Argentina para obtener latitud y longitud oficiales de ambas localidades.
   - Con las coordenadas, invoca a OpenRouteService para calcular la ruta vial, distancia en kilómetros y duración estimada.
   - **Resultado esperado**: `201 Created` con el retorno en estado `PUBLICADO`, coordenadas geográficas completas, `rutaDistanciaKm` real, `rutaDuracionS` y `rutaAproximada: false`. *(En caso de indisponibilidad de la API externa de rutas, el sistema almacena el retorno con degradación elegante marcando `rutaAproximada: true`)*.

---

### Resumen de Otros Criterios de la Plataforma

- **C3 — Búsqueda de cargas de paso**: Filtrado geométrico espacial por Bounding Box, compatibilidad física/temporal y ordenamiento por desvío vial real (RN-01, RN-02).
- **C4 — Propuesta y confirmación del viaje**: Contraoferta económica, validación documental sincrónica (`GET /internal/**`) y cerrojo transaccional concurrente con `@Version` (RN-14, RN-15).
- **C5 — Ejecución del viaje y conformidad**: Secuencia estricta de hitos (`CARGADO` → `EN_TRANSITO` → `ENTREGADO`), remito digital de entrega y ventana de conformidad de 48 horas (RN-18).
- **C6 — Control de acceso**: Validación en dos capas: 401 sin token, 403 por rol en Gateway, 403 por dueño en el microservicio (RN-T1) y 404 para endpoints internos.
- **C7 — Falla cerrada ante caídas**: Respuesta inmediata `503 Service Unavailable` sin inconsistencias parciales si `transporte-service` no está disponible.

---

## Ejecución Rápida con Docker

### Requisitos previos
- Docker Desktop o Docker Engine con plugin `docker compose` v2.
- Puertos `8081` (Keycloak) y `8085` (API Gateway) libres.

### Paso a paso

1. **Configurar variables de entorno**:
   ```bash
   cp .env.example .env
   ```
   *(Opcional: completar la clave de OpenRouteService en `OPENROUTESERVICE_API_KEY` para ruteo en vivo).*

2. **Levantar toda la plataforma**:
   ```bash
   docker compose up -d
   ```
   *El orden de arranque está coordinado por healthchecks: PostgreSQL → Keycloak (importa el realm) → microservicios → API Gateway.*

3. **Verificar el estado de los contenedores**:
   ```bash
   docker compose ps
   ```

4. **Acceder a la documentación Swagger UI**:
   - Transporte: `http://localhost:8091/swagger-ui/index.html`
   - Cargas: `http://localhost:8092/swagger-ui/index.html`
   *(Ambos Swagger cuentan con el botón **Authorize** para ingresar el Bearer JWT provisto por `flety-commons`).*

5. **Detener la plataforma**:
   ```bash
   docker compose down
   # Para reiniciar desde cero eliminando volúmenes: docker compose down -v
   ```

---

## Ejecución de Pruebas Automatizadas

Los tests de cada componente utilizan base de datos H2 en memoria y tokens simulados, por lo que no requieren Docker ni Keycloak levantados:

```bash
# Compilar e instalar la librería común en el repositorio local
mvn -f flety-commons/pom.xml install

# Ejecutar tests de transporte-service (87 tests)
mvn -f transporte-service/pom.xml test

# Ejecutar tests de cargas-service (273 tests)
mvn -f cargas-service/pom.xml test

# Ejecutar tests de gateway-service
mvn -f gateway-service/pom.xml test
```

