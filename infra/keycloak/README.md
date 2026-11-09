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
| `dador2` | `dador2` | `DADOR` |
| `operador1` | `operador1` | `OPERADOR` |

**Cada usuario tiene un `id` fijo en el JSON**, y ese `id` es el `sub` de sus tokens. Keycloak guarda los usuarios en su base embebida, dentro del contenedor: cuando el contenedor se recrea (`docker compose down`, `up --force-recreate` o un cambio en el compose), vuelve a importar el realm. Sin un `id` fijo, cada import inventa uno nuevo, y los datos de Postgres (que sí persisten en su volumen) quedan a nombre de un `sub` que ya no existe: el transportista ve "Todavía no registraste tu organización" y tampoco puede registrarse de nuevo, porque su CUIT ya está. Para agregar un usuario, generá su `id` igual que los demás (UUIDv5 del nombre de usuario):

```bash
python -c "import uuid; print(uuid.uuid5(uuid.NAMESPACE_URL, 'https://flety/usuarios/NOMBRE'))"
```

`transportista2` y `dador2` existen para probar el control de dueño (RN-T1): tienen el mismo rol que `transportista1` y `dador1`, así que si piden un recurso del otro el Gateway los deja pasar y el 403 lo tiene que dar el servicio. Con `dador2` también se prueba RN-07: hasta que registra su organización, no puede publicar cargas (422).

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
