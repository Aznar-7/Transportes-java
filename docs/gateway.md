# API Gateway

`gateway-service` es la única entrada a la API, en el puerto 8085. No tiene reglas de negocio: valida el token, controla el rol que pide cada ruta y reenvía el pedido al servicio que corresponde. Es Spring Cloud Gateway 2025.1.4, la línea que va con Spring Boot 4.1.

## Rutas

| Path | Va a |
|---|---|
| `/api/transporte/**` | `transporte-service` (`TRANSPORTE_URL`) |
| `/api/dadores/**`, `/api/cargas/**`, `/api/viajes/**` | `cargas-service` (`CARGAS_URL`) |
| `/internal/**` | Ninguno: no tiene ruta y responde 404 |

Se reenvía sin reescribir el path: los servicios exponen los mismos paths. El rol que pide cada ruta está en [rutas.md](gateway/rutas.md).

## El recorrido del token

1. **El usuario le pide un token a Keycloak** (`localhost:8081`, realm `flety`; ver [infra/keycloak/README.md](../infra/keycloak/README.md)). El token es un JWT firmado que trae el `sub` (quién es) y `realm_access.roles` (qué rol tiene).
2. **Lo manda al Gateway** como `Authorization: Bearer <token>`.
3. **El Gateway valida la firma y la vigencia** con las claves públicas del realm (`JWK_SET_URI`). Si falta o no es válido: **401**.
4. **Convierte los roles** de `realm_access.roles` a `ROLE_*` (`RolesKeycloak`) y **controla el rol de la ruta**. Si no corresponde: **403**.
5. **Reenvía el pedido** al servicio con el mismo header.
6. **El servicio vuelve a validar el token** (flety-commons) y además controla al dueño del recurso, que el Gateway no puede saber (RN-T1).
7. **Si el servicio necesita al otro** (`cargas-service` a `transporte-service`), le reenvía el mismo token por la red de Docker, a un `/internal`.

```
cliente ──token──> Gateway (firma + rol) ──mismo token──> servicio (firma + dueño) ──mismo token──> /internal del otro
```

Validar dos veces es a propósito (defensa en profundidad): si alguien llegara a un servicio salteando el Gateway, igual necesitaría un token válido.

## Por qué así

- **El orden de las reglas importa.** Spring Security aplica la primera que coincide, así que las específicas van antes que las generales: `GET /api/cargas/de-paso` (TRANSPORTISTA) antes que `/api/cargas/**` (DADOR). Al revés, un transportista buscando cargas de paso recibiría 403.
- **`/internal/**` da 404 y no 403.** Se deja pasar sin token, pero como no tiene ruta responde 404: desde afuera no se ve que exista.
- **No usa flety-commons.** El Gateway es reactivo (WebFlux) y la seguridad de commons está armada para servlets. El conversor de roles es la misma idea en una clase chica.
- **Es el único que publica puerto** junto con Keycloak. `transporte-service`, `cargas-service` y Postgres solo se alcanzan por la red `flety-net` (ver [infra/README.md](../infra/README.md)).

## Cómo se prueba

`GatewaySeguridadTest` levanta el Gateway con su seguridad real y reemplaza los dos servicios por dos `HttpServer` del JDK que responden con el `Authorization` que les llegó. Así se ve, además de los 401 y 403, que el pedido llega al servicio correcto con el mismo token. Solo el `JwtDecoder` es falso: reconoce tokens fijos en vez de bajar las claves de Keycloak.

Falta: las rutas `/docs/transporte` y `/docs/cargas` para abrir el Swagger de cada servicio a través del Gateway en desarrollo.
