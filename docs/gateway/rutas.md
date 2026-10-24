# Rutas del Gateway

El Gateway (puerto 8085) valida el JWT y el rol, y reenvía sin reescribir el path.
Lo que no puede saber (de quién es el recurso, en qué estado está) lo resuelve cada servicio.
Fuente: secciones 9 y 12 del diseño.

| Path | Verbo | Rol | Servicio |
|---|---|---|---|
| `/api/transporte/transportistas/**` | todos | TRANSPORTISTA | transporte |
| `/api/transporte/unidades/**` | todos | TRANSPORTISTA | transporte |
| `/api/transporte/retornos/**` | todos | TRANSPORTISTA | transporte |
| `/api/transporte/operador/**` | todos | OPERADOR | transporte |
| `/api/dadores/**` | todos | DADOR | cargas |
| `/api/cargas/de-paso` | GET | TRANSPORTISTA | cargas |
| `/api/cargas/{id}/propuestas` | POST | TRANSPORTISTA | cargas |
| `/api/cargas/{id}/propuestas` | GET | DADOR | cargas |
| `/api/cargas/propuestas/{id}` | DELETE | TRANSPORTISTA | cargas |
| `/api/cargas/propuestas/{id}/aceptacion` | POST | DADOR | cargas |
| `/api/cargas/propuestas/{id}/rechazo` | POST | DADOR | cargas |
| `/api/cargas/{id}` | GET | DADOR o TRANSPORTISTA | cargas |
| `/api/cargas/**` (publicar, listar, cancelar) | POST, GET, DELETE | DADOR | cargas |
| `/api/viajes/disputas/**` | todos | OPERADOR | cargas |
| `/api/viajes/{id}/hitos` | POST | TRANSPORTISTA | cargas |
| `/api/viajes/{id}/conformidad` | POST | DADOR | cargas |
| `/api/viajes/{id}/disputas` | POST | DADOR | cargas |
| `/api/viajes`, `/api/viajes/{id}`, `/api/viajes/{id}/cancelacion` | GET, POST | DADOR o TRANSPORTISTA | cargas |
| `/internal/**` | — | no se publica: desde afuera da 404 | — |
| `/docs/transporte`, `/docs/cargas` | GET | sin rol, solo perfil dev | Swagger de cada servicio |

## Orden de las reglas

Spring Security aplica la **primera** regla que coincide, así que las específicas van antes que las generales:
`/api/cargas/de-paso` y `/api/cargas/{id}/propuestas` antes que `/api/cargas/**`,
y `/api/viajes/disputas/**` antes que `/api/viajes/**`.
Si se invierte el orden, un TRANSPORTISTA buscando cargas de paso recibe 403.
