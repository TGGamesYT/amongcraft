# AmongCraft — Project Notes for Claude

## Git workflow (IMPORTANT)
- **Always commit directly to the `main` branch.**
- **Never create or push to any other branch.** Do not open feature branches.
- Commit work with clear, descriptive messages and push to `origin main`.

## Project overview
- Minecraft **Fabric** mod for **Minecraft 1.20.1** (Yarn mappings).
- Goal: recreate **Among Us** inside Minecraft (roles, tasks, meetings, sabotage, ejections).
- Maven group `me.tg`, mod id `amongcraft`.
- Source split into `src/main` (common/server) and `src/client` (client-only).

## Build
- `./gradlew build` — builds the mod jar.
- Java 17, Fabric Loom.
