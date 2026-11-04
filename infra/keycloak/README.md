# Realm flety

`flety-realm.json` es el export del realm que Keycloak importa al arrancar con `--import-realm` (se monta en `/opt/keycloak/data/import`).

## Qué trae

- Realm `flety`.
- Roles de realm: `TRANSPORTISTA`, `DADOR` y `OPERADOR`.
- Cliente `flety-test`: público (sin secret) y con *Direct Access Grants* habilitado, para pedir tokens con usuario y contraseña desde curl, Postman o Swagger.
- Usuarios de prueba (la contraseña es igual al usuario; son solo para desarrollo local):

| Usuario | Contraseña | Rol |
|---|---|---|
| `transportista1` | `transportista1` | `TRANSPORTISTA` |
| `transportista2` | `transportista2` | `TRANSPORTISTA` |
| `dador1` | `dador1` | `DADOR` |
| `operador1` | `operador1` | `OPERADOR` |

`transportista2` existe para probar el control de dueño (RN-T1): tiene el mismo rol que `transportista1`, así que si pide un recurso de `transportista1` el Gateway lo deja pasar y el 403 lo tiene que dar el servicio.

Si el contenedor de Keycloak ya existía, el realm no se vuelve a importar y los cambios de este archivo no se aplican: Keycloak saltea los realms que ya tiene. Para tomarlos, recreá el contenedor:

```bash
docker compose up -d --force-recreate keycloak
```

## Pedir un token

Con Keycloak levantado en `localhost:8081`:

```bash
curl -s -X POST http://localhost:8081/realms/flety/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=flety-test \
  -d username=transportista1 \
  -d password=transportista1
```

La respuesta trae `access_token`. Para guardarlo en una variable (requiere `jq`):

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/flety/protocol/openid-connect/token \
  -d grant_type=password -d client_id=flety-test \
  -d username=transportista1 -d password=transportista1 | jq -r .access_token)
```

En PowerShell:

```powershell
$TOKEN = (Invoke-RestMethod -Method Post -Uri http://localhost:8081/realms/flety/protocol/openid-connect/token `
  -Body @{ grant_type='password'; client_id='flety-test'; username='transportista1'; password='transportista1' }).access_token
```

Y se usa como `Authorization: Bearer $TOKEN` contra el Gateway (`localhost:8085`).

## Leer el token

Pegá el `access_token` en https://jwt.io. Los campos que usan los servicios:

- `sub`: id del usuario en Keycloak; es el que se compara con el dueño del recurso.
- `realm_access.roles`: tiene que incluir el rol del usuario (por ejemplo `TRANSPORTISTA`).
- `iss`: `http://localhost:8081/realms/flety`.
- `exp`: vence a los 30 minutos.

## Claves públicas

Los servicios validan la firma con el JWKS del realm. Desde la red de Docker es `http://keycloak:8080/realms/flety/protocol/openid-connect/certs`, y desde la máquina es `http://localhost:8081/realms/flety/protocol/openid-connect/certs`.
