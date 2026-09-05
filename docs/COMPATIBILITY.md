# Compatibility policy

Hangul keeps one client JAR for the current Minecraft 26.x line, requiring Fabric Loader but no separate Fabric API or native library. The release build targets stable 26.2. The compatibility gate checks that exact compiled JAR against every declared game version, including actual Fabric Loader resolution, Mixin transformation and headless widget tests.

## Current matrix

The 1.3.0-beta.5 candidate targets the following matrix. Development uses Java 25, Fabric Loader 0.19.5 and Fabric Loom 1.17.20; the minimum Loader requirement remains 0.19.3.

Compilation uses the 0.19.3 Loader API so the emitted Mixin annotation format remains compatible with that minimum. Development and runtime tests resolve the selected Loader's own Mixin/MixinExtras libraries from its installer metadata. This separates the compiler API from the runtime without bundling either in Hangul. Minimum-Loader checks also passed on 26.1, 26.2 and 26.3-pre-2.

On 2026-09-05 the same stable-built candidate passed all 16 targets on Windows: 251 metadata/bytecode checks per target, 13 transformed game classes with 42 installed hooks, and headless search/widget regression tests. The Linux CI matrix is configured but has not yet run for this unpublished candidate.

| Minecraft | Loader | Candidate scope | Input backend |
| --- | --- | --- | --- |
| 26.1, 26.1.1, 26.1.2 | 0.19.3+ | Stable game versions | GLFW |
| 26.2 | 0.19.3+ | Default build target | GLFW |
| 26.3-snapshot-1 through snapshot-3 | 0.19.3+ | Experimental | GLFW |
| 26.3-snapshot-4 through snapshot-10 | 0.19.3+ | Experimental | SDL3 |
| 26.3-pre-1, 26.3-pre-2 | 0.19.3+ | Experimental | SDL3 |

Snapshots and pre-releases remain experimental. Headless tests load all 13 targeted game classes through Fabric, verify the installed hooks, exercise a real search tree and run transformed single-line and multiline widgets with deterministic font metrics. They test temporary-value restoration, selection replacement, unchanged-frame layout reuse and font-cache invalidation. They do not open a native window or exercise Windows IME, Hanja candidates, graphics drivers, GUI scaling or fullscreen. Those interactive checks remain a release requirement; no new native IME pass is claimed for this candidate.

## Launcher IDs and Fabric predicates

Fabric normalizes Minecraft `26.3-snapshot-N` to `26.3-alpha.N`, and `26.3-pre-N` to `26.3-pre.N`. Raw launcher IDs in `depends.minecraft` therefore do not match these game versions. `custom.hangul:tested_game_versions` records the exact Mojang IDs used by the verification script and CI; `depends.minecraft` contains their exact normalized predicates. The binary check requires both lists to agree with Fabric Loader's own normalizer and with the tested game JAR. No open-ended snapshot range or dependency override is used.

## Why one JAR can cross the SDL3 boundary

Snapshot 4 replaced GLFW with SDL3 and changed physical-key and modifier values. Hangul never links against either native library. `InputKeyCompat` resolves Minecraft's own `InputConstants` fields once at class initialization, then translates physical A-Z keys to the stable ASCII input expected by the Dubeolsik composer. Native typing and search do not perform reflective screen lookup; the emergency F6 path uses cached reflection to validate its focused owner.

Minecraft 26.2 also moved screen, overlay, and HUD ownership from `Minecraft` to `Gui`. `MinecraftUiCompat` isolates that small API difference. Its cached reflective members are used only for focus lookup and the F6 status message.

The two screen-lifecycle Mixins are intentionally optional alternatives. Static verification requires exactly one `setScreen` hook on each game version, and the runtime check verifies that hook was applied. All input/render hooks remain required. Loom 1.17.20 also supplies the development JVM stack setting needed after Snapshot 10; this is not an extra mod dependency.

## Release verification

Run the complete declared matrix from PowerShell:

```powershell
.\verify-compatibility.ps1
```

The script builds stable 26.2 once, stores the candidate in `build/compatibility/hangul.jar`, and checks its SHA-256 after every target. Variant compilation cannot replace that candidate. `build/libs` retains the stable release artifact.

For a smaller declared subset or the minimum Loader version:

```powershell
.\verify-compatibility.ps1 -MinecraftVersions 26.2,26.3-pre-2 -LoaderVersion 0.19.3
```

CI builds one stable candidate and passes it to the complete Linux version matrix, representative Windows targets, and minimum-Loader checks. Verification code and its fixture mod are kept outside the release JAR and source archive. Before publishing a newly added version, pass these checks and the [Windows IME checklist](RELEASE_CHECKLIST.md).

## Official references

- [Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [Minecraft Java Edition 26.3 Snapshot 4](https://feedback.minecraft.net/hc/en-us/articles/47424728811149-Minecraft-Java-Edition-26-3-Snapshot-4)
- [Fabric game-version metadata](https://meta.fabricmc.net/v2/versions/game)
- [Fabric Loader metadata for 26.3-pre-2](https://meta.fabricmc.net/v2/versions/loader/26.3-pre-2)
- [Minecraft 26.3 Pre-release 2](https://www.minecraft.net/en-us/article/minecraft-26-3-pre-release-2)
- [Fabric Loom documentation](https://docs.fabricmc.net/develop/loom/)
- [Fabric mod dependency version syntax](https://docs.fabricmc.net/develop/loader/fabric-mod-json)

Minecraft 26.1 and newer are distributed without code obfuscation. Fabric Loom therefore uses `net.fabricmc.fabric-loom`, and Fabric metadata reports intermediary `0.0.0`; there is no Yarn artifact for these targets.
