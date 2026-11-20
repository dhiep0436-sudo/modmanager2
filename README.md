# Mod Manager (Fabric, Minecraft 1.21.11)

Client-side mod. Click **Mod Manager** (top-left of the title screen and pause menu).

- **Mods / Resource Packs / Shaders tabs**: search Modrinth (filtered to 1.21.11), click Install.
  - Resource packs are downloaded, enabled and reloaded instantly.
  - Shaders are saved to `shaderpacks` (select them in Video Settings; needs Iris).
  - Mods are saved to `mods` (required dependencies included). Restart Minecraft to load them.
- **Installed Mods tab**: Disable/Enable renames the jar to `.jar.disabled`. Restart Minecraft to apply.

Minecraft cannot load or unload mod code while running, so mods need a restart. For live mod toggling,
see the Hotwire mod.

## Build
1. Install JDK 21 and Gradle 9.2+ (or generate a project at https://fabricmc.net/develop/template/ and copy `src/` plus
   the Gradle files into it to get the wrapper).
2. Check versions in `gradle.properties` against https://fabricmc.net/develop/template/ (1.21.11 section).
3. `gradle build` -> jar in `build/libs/`. Put it in `mods/` with Fabric API.
