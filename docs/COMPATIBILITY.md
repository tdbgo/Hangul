# Compatibility policy

Hangul keeps one dependency-free client JAR for the current Minecraft 26.x line. The default release build targets the latest stable version, while the release gate compiles the same sources against every version declared in `fabric.mod.json`.

## Current matrix

Checked on 2026-08-16 with Java 25, Fabric Loader 0.19.3, and Fabric Loom 1.17.19.

| Minecraft | Loader | Build status | Input backend |
| --- | --- | --- | --- |
| 26.1, 26.1.1, 26.1.2 | 0.19.3+ | Supported | GLFW |
| 26.2 | 0.19.3+ | Supported; default build target | GLFW |
| 26.3-snapshot-1 through snapshot-3 | 0.19.3+ | Supported snapshot | GLFW |
| 26.3-snapshot-4 through snapshot-8 | 0.19.3+ | Supported snapshot | SDL3 |

Snapshots are testing builds. A successful compile verifies named classes, method descriptors, fields, Mixin annotation processing and remapping, resources, and the dependency-free composer checks. It does not prove that every injection still matches at runtime or replace an interactive Windows IME smoke test.

## Why one JAR can cross the SDL3 boundary

Snapshot 4 replaced GLFW with SDL3 and changed physical-key and modifier values. Hangul never links against either native library. `InputKeyCompat` resolves Minecraft's own `InputConstants` fields once at class initialization, then translates physical A-Z keys to the stable ASCII input expected by the Dubeolsik composer. The hot composition and search paths remain free of reflection.

Minecraft 26.2 also moved screen, overlay, and HUD ownership from `Minecraft` to `Gui`. `MinecraftUiCompat` isolates that small API difference. Its cached reflective members are used only for focus lookup and the F6 status message.

## Release verification

Run the complete declared matrix from PowerShell:

```powershell
.\verify-compatibility.ps1
```

The stable 26.2 build is last so the resulting release JAR uses the stable compile target.

For a newly published snapshot, first build it explicitly without widening `fabric.mod.json`:

```powershell
.\verify-compatibility.ps1 -MinecraftVersions 26.3-snapshot-9
```

Only add the version after the clean build and an interactive IME smoke test pass. Do not use an open-ended Minecraft dependency range for snapshots.

## Official references

- [Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [Minecraft Java Edition 26.3 Snapshot 4](https://feedback.minecraft.net/hc/en-us/articles/47424728811149-Minecraft-Java-Edition-26-3-Snapshot-4)
- [Fabric game-version metadata](https://meta.fabricmc.net/v2/versions/game)
- [Fabric Loader metadata for 26.3-snapshot-8](https://meta.fabricmc.net/v2/versions/loader/26.3-snapshot-8)
- [Fabric Loom documentation](https://docs.fabricmc.net/develop/loom/)
- [Fabric mod dependency version syntax](https://docs.fabricmc.net/develop/loader/fabric-mod-json)

Minecraft 26.1 and newer are distributed without code obfuscation. Fabric Loom therefore uses `net.fabricmc.fabric-loom`, and Fabric metadata reports intermediary `0.0.0`; there is no Yarn artifact for these targets.
