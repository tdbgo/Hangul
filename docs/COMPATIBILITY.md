# Compatibility policy

Hangul shares its input and search sources across loaders. The Fabric JAR also runs on Quilt; NeoForge and Forge have separate JARs. No separate API mod, compatibility bridge or native library is required. Stable 26.2 remains the compilation target. The compatibility gate checks each exact platform JAR on the declared loader/version combinations.

## Minecraft 26.4 Snapshot 1 candidate: 1.3.0-beta.8

The 26.4 line has only reached Snapshot 1 as of 2026-09-23. This candidate adds **that exact snapshot** to the Fabric/Quilt JAR. It does not declare later snapshots or the eventual stable 26.4 release. Fabric Loader 0.19.5 and Quilt Loader 0.31.0-beta.4 both applied the packaged JAR's 13 target classes and 42 hooks against Snapshot 1, and the input, search, and sign widget checks passed. Fabric Loader 0.19.3 also passed its minimum-version check.

The unchanged production code still compiles against Minecraft 26.2. The Windows full-matrix run passed 45 loader/game combinations: 18 each for Fabric and Quilt, five for NeoForge, and four for Forge. Five more minimum-Fabric-Loader checks passed, including 26.4 Snapshot 1. Each platform JAR passed packaging checks; the same shared classes are present in all three files.

A 26.4 development client started with the default Vulkan backend, entered an isolated world, displayed and sent committed Korean text, saved the world and exited normally. The automation inserted complete Unicode text, so this observation alone does **not** prove live Windows IME composition or Hanja candidate behavior. The tester subsequently confirmed Korean composition and Hanja input in the 26.4 test window. The evidence and its limits are recorded in [release readiness](RELEASE_READINESS.md).

| Platform | 26.4 Snapshot 1 | Previous targets |
| --- | --- | --- |
| Fabric | Candidate JAR passed Loader 0.19.5 and minimum 0.19.3 checks | 26.1–26.3 as declared in the JAR |
| Quilt | Same candidate JAR passed Loader 0.31.0-beta.4 checks | Same declared versions as Fabric |
| NeoForge | No 26.4 loader artifact available when checked | 26.1–26.3 |
| Forge | No 26.4 loader artifact available when checked | 26.1–26.2 |

The input path uses Minecraft's SDL3 abstraction and does not call Vulkan. Still, the new default renderer makes a real 26.4 startup and screen check useful in addition to Mixin tests. The release gate checks the exact snapshot ID and Fabric's normalized predicate `26.4-alpha.1`; it does not bypass dependency metadata.

## Minecraft 26.3 support candidate: 1.3.0-beta.7

This is the published Hangul release for **stable Minecraft 26.3**. It retains the beta.6 game targets and adds 26.3 for Fabric, Quilt and NeoForge. Older beta.6 files are unchanged and do not permit stable 26.3.

| Platform | Added game target | Loader selected for verification |
| --- | --- | --- |
| Fabric | 26.3 | 0.19.5; minimum 0.19.3 checked separately |
| Quilt | 26.3 | 0.31.0-beta.4, using the Fabric JAR |
| NeoForge | 26.3 | 26.3.0.3-beta, using the NeoForge JAR |
| Forge | None | No official 26.3 artifact was available on 2026-09-17; retain the beta.6 pairs below |

The selected NeoForge loader is itself a beta. An untested newer loader is not implicitly supported. The candidate keeps exact loader predicates in `platform-matrix.json` and its packaged metadata.

The same 26.2-built JAR is tested on each supported game version. The shared SDL/GLFW key adapter does not need a second native library or a separate 26.3 build. New backend checks resolve each runtime's actual key constants, exercise A–Z and Shift/shortcut classification, and assert that ordinary movement and language-switch keys pass through the controller. They do not simulate Windows IME key delivery. Every loader must now produce a fresh success receipt matching the exact candidate SHA-256; an early process exit or stale result cannot pass.

On 2026-09-17, the complete Windows run passed 43 loader/game pairs: 17 each on Fabric and Quilt, five on NeoForge, and four on Forge. Four additional minimum-Fabric-Loader checks passed on 26.1, 26.2, 26.3-pre-2 and 26.3. Each run verified 13 transformed classes, 42 hooks, backend key handling and widget/search behavior. All 11,172 modern Hangul syllable cases and final packaging checks passed. Production class bytes match beta.6 for each platform. These results do not establish native Windows IME behavior on 26.3.

Native 26.3 checks were then completed using separate Fabric, Quilt and NeoForge clients, direct observations and explicit tester confirmation. The [release readiness record](RELEASE_READINESS.md) separates these evidence types and their limits; it does not claim every detailed screen check was repeated on every loader. Beta.7 was published on 2026-09-17.

## Multiloader beta: 1.3.0-beta.6

Version beta.6 provides Fabric/Quilt, NeoForge and Forge files. The older beta.5 file remains Fabric-only; support is not applied retroactively.

| Platform | Candidate game versions | Loader versions selected for verification |
| --- | --- | --- |
| Fabric | 26.1, 26.1.1, 26.1.2, 26.2; 26.3 Snapshot 1–10 and Pre-release 1–2 | 0.19.5; minimum metadata remains 0.19.3 |
| Quilt | Same 16 game versions as Fabric | 0.31.0-beta.4 |
| NeoForge | 26.1, 26.1.1, 26.1.2, 26.2 | 26.1.0.19-beta, 26.1.1.15-beta, 26.1.2.104, 26.2.0.77 |
| Forge | 26.1, 26.1.1, 26.1.2, 26.2 | 62.0.9, 63.0.2, 64.1.3, 65.1.3 respectively |

NeoForge and Forge use exact loader predicates until additional versions are verified. Neither declares 26.3 support. The version pairs are recorded in `platform-matrix.json`; do not bypass metadata with dependency overrides.

On 2026-09-06, the candidate passed all 40 loader/game combinations in this table on Windows, plus three Fabric minimum-Loader checks (26.1, 26.2 and 26.3-pre-2 with Loader 0.19.3). Every run verified 13 transformed game classes and 42 hooks, then passed the shared widget/search checks. The Fabric candidate also passed all 11,172 modern Hangul syllable regression cases. These are local results; remote CI runs are reported separately.

All four platforms run the same test-only Mixin and widget checks. Forge starts the checks through a separate fixture Mixin before the game window opens. NeoForge uses a separate fixture entrypoint with its early loading window disabled. Both read packaged candidate JARs, not production classes from development output directories, and require a success receipt matching the candidate SHA-256. A successful build is not accepted if the fixture failed to run.

Forge's bundled upstream Mixin 0.8.7 does not recognize `JAVA_25`. Only the Forge artifact targets Java 21 bytecode and `JAVA_21` Mixin compatibility; the Minecraft dependencies and game runtime still require Java 25. No replacement Mixin library is bundled. Fabric and NeoForge retain Java 25 bytecode.

The initial Forge candidate's missing `pack.mcmeta` triggered a loading warning and subsequent integrated-server loot failures. The corrected artifact includes a format range covering both client resources (84–88) and server data (101.1–107.1); verification parses the range with each target game's codecs. The original artifact is rejected by the packaging gate.

On 2026-09-06, the corrected Forge/26.2 artifact was also tested interactively on Windows: warning-free startup, the previously failing block-drop command, native chat composition and one Enter submission, two-line book input with save/reopen, sign composition and save, fullscreen/windowed transition, and composition/cancel after F3+T resource reload. The isolated world saved and the client exited normally. No F6 fallback or pasted Korean was used for those composition checks.

NeoForge/26.2 was also tested interactively on Windows on 2026-09-06: immediate native composition and Backspace, one chat submission, sign input/save, book selection replacement and two-page save/reopen, fullscreen transitions during composition, block drops, resource reload, and normal world save/exit. Hanja conversion was confirmed by the tester. Sign arrow delivery was inconclusive in that native session; Enter line navigation worked. A later headless regression checks the transformed sign's Up/Down/Enter handlers directly, including line wrap and stale-composition cleanup; this does not establish OS key delivery while the IME is active.

That native NeoForge run showed a deprecated `logoFile` metadata warning. The candidate now uses `iconFile`, following the [NeoForge mod-list migration](https://github.com/neoforged/Documentation/issues/372), with a packaging gate rejecting `logoFile`. This is a metadata-only change; the icon may not be shown by older 26.1 mod-list screens that predate `iconFile`. Input classes are unchanged. The rebuilt candidate passed all four declared NeoForge loader checks; the earlier native result must not be described as a native run of the new hash.

Quilt/26.2 was tested in its isolated local world with the packaged candidate: immediate native composition, final-consonant deletion, one chat submission, sign selection replacement and Enter navigation with save, and fullscreen/windowed transitions. Additional book saving and native Down-key delivery were not established by the recorded observations.

On 2026-09-06, the tester reported that all remaining checks were complete, closing the manual test request. No additional per-version, per-loader or artifact-hash execution records accompanied that confirmation. It is recorded as tester-reported completion, not as an independently observed run of every combination or of the updated NeoForge hash. Automated preedit/Hanja character events remain distinct from OS candidate-window verification, and 26.3 remains experimental. See the [release readiness record](RELEASE_READINESS.md).

The [beta.6 CI run](https://github.com/tdbgo/Hangul/actions/runs/34021385715) passed all 26 jobs on commit `1822826`, including both platform matrices and the final packaging gate. Release documentation changes do not alter those sources or build settings. CI artifacts have identical class bytes to the locally tested files; archive hashes differ only because of text line endings and the order of Fabric's client-only manifest list. The local, native-tested files remain the release candidates.

Build all artifacts and check the full matrix with Java 25 and Python 3.11+:

```powershell
.\verify-multiloader.ps1 -FullMatrix
```

For one platform:

```powershell
.\gradlew.bat verifyQuiltApplication --no-daemon
.\gradlew.bat -Pplatform=neoforge :neoforge:build --no-daemon
.\gradlew.bat -Pplatform=forge :forge:build --no-daemon
```

Platform JARs are produced in the root `build/libs` (Fabric/Quilt) and `platforms/<loader>/build/libs` directories. Verification fixtures stay outside those release directories. Source archives also exclude verification code. ForgeGradle may provision a Java 8 toolchain for its launcher helper; it is a development-tool requirement, not an additional player runtime requirement.

## Previous release: Fabric 1.3.0-beta.5

The 1.3.0-beta.5 candidate targets the following matrix. Development uses Java 25, Fabric Loader 0.19.5 and Fabric Loom 1.17.20; the minimum Loader requirement remains 0.19.3.

Compilation uses the 0.19.3 Loader API so the emitted Mixin annotation format remains compatible with that minimum. Development and runtime tests resolve the selected Loader's own Mixin/MixinExtras libraries from its installer metadata. This separates the compiler API from the runtime without bundling either in Hangul. Minimum-Loader checks also passed on 26.1, 26.2 and 26.3-pre-2.

On 2026-09-05 the same stable-built candidate passed all 16 targets on Windows: 251 metadata/bytecode checks per target, 13 transformed game classes with 42 installed hooks, and headless search/widget regression tests. The [build workflow](https://github.com/tdbgo/Hangul/actions/workflows/build.yml) records remote CI results separately.

| Minecraft | Loader | Candidate scope | Input backend |
| --- | --- | --- | --- |
| 26.1, 26.1.1, 26.1.2 | 0.19.3+ | Stable game versions | GLFW |
| 26.2 | 0.19.3+ | Default build target | GLFW |
| 26.3-snapshot-1 through snapshot-3 | 0.19.3+ | Experimental | GLFW |
| 26.3-snapshot-4 through snapshot-10 | 0.19.3+ | Experimental | SDL3 |
| 26.3-pre-1, 26.3-pre-2 | 0.19.3+ | Experimental | SDL3 |

Snapshots and pre-releases remain experimental. Headless tests load all 13 targeted game classes through Fabric, verify the installed hooks, exercise a real search tree and run transformed single-line and multiline widgets with deterministic font metrics. They test temporary-value restoration, selection replacement, unchanged-frame layout reuse and font-cache invalidation. They do not open a native window or exercise Windows IME, Hanja candidates, graphics drivers, GUI scaling or fullscreen.

Windows Minecraft 26.2 was also tested in-game with the beta.5 JAR: native composition appeared before confirmation without F6, Backspace removed the final consonant, Enter sent one completed chat message, and book composition appeared inline. Follow-up manual checks on the installed beta.5 client were reported successful for books, signs, Hanja conversion and window switching. This interactive result applies to 26.2, not to the 26.3 experimental targets.

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
- [Fabric Loader metadata for 26.3](https://meta.fabricmc.net/v2/versions/loader/26.3)
- [Minecraft Java Edition 26.3](https://feedback.minecraft.net/hc/en-us/articles/48913133328013-Minecraft-Java-Edition-26-3)
- [Minecraft 26.3 Pre-release 2](https://www.minecraft.net/en-us/article/minecraft-26-3-pre-release-2)
- [Fabric Loom documentation](https://docs.fabricmc.net/develop/loom/)
- [Fabric mod dependency version syntax](https://docs.fabricmc.net/develop/loader/fabric-mod-json)

Minecraft 26.1 and newer are distributed without code obfuscation. Fabric Loom therefore uses `net.fabricmc.fabric-loom`, and Fabric metadata reports intermediary `0.0.0`; there is no Yarn artifact for these targets.
