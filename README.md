# TreasureQuest

Plugin de **búsqueda del tesoro** para Paper 1.21.x (Java 21).

## Subir a GitHub y obtener el .jar
1. Crea un repo en GitHub y sube **todo el contenido** de esta carpeta (incluida `.github/`).
2. Cada push a `main` compila y publica un *Release* con `TreasureQuest.jar`.
3. Para una versión con nombre: `git tag v1.0.0 && git push --tags`.

## Comandos
| Comando | Descripción |
|---|---|
| `/tq` | Menú principal |
| `/tq shop` · `/tq events` · `/tq top` | Tienda, eventos activos, ranking |
| `/tq track [n\|off]` | Rastrear un tesoro (flecha + distancia en pantalla) |
| `/tq start <tier> [mundo]` | Iniciar evento (admin) |
| `/tq stop <all\|n>` | Detener eventos (admin) |
| `/tq map give <jugador> <tier\|custom:id>` | Dar mapa (admin) |
| `/tq custom create <id> <tier>` | Crear cofre personalizado y editar su loot |
| `/tq custom edit/delete/spawn/map/price/tier/display/list` | Gestión de personalizados |
| `/tq reload` | Recargar config |

Permisos: `treasurequest.use` (todos), `treasurequest.admin` (op).

## Notas
- Tiers: `tier1`–`tier3` (eventos globales), `magic` y `legendary` (mapas/tienda/regalos). Todo editable en `config.yml`.
- Vault es opcional: sin él la tienda cobra niveles de XP y no hay recompensa en dinero.
- Pensado para Paper (no Folia).
