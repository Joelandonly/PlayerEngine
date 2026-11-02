# Minecraft agent development toolkit

Vendored from [Jahrome907/minecraft-agent-skills](https://github.com/Jahrome907/minecraft-agent-skills) at commit [`22d61ce6b7a0c5f215008824aa6b94ecc73f2a43`](https://github.com/Jahrome907/minecraft-agent-skills/commit/22d61ce6b7a0c5f215008824aa6b94ecc73f2a43). The bundled skill and script files are copied without changing their contents. Original license: MIT, preserved in `docs/third_party/LICENSE-minecraft-agent-skills.txt`.

| Selected skill | Project use |
| --- | --- |
| `minecraft-modding` | Fabric/Java mod changes and build guidance |
| `minecraft-testing` | Unit, Minecraft GameTest and integration test design |
| `minecraft-ci-release` | CI, candidate JAR checks and release discipline |
| `minecraft-multiloader` | Architectury common and Fabric/NeoForge module boundaries |

The skill folders live in `.agents/skills/` so compatible coding agents such as Codex can discover them. This toolkit is **not** a Minecraft mod and does **not** directly add commands or abilities to Ellie.

## Important: source branch vs playable build

The current checked-in source and existing `verify-construction.yml` build target Minecraft **1.20.1**, Java **17**, PlayerEngine **1.0.0**. The actual player-tested binary is Minecraft **1.21.1**, Java **21**, Fabric API **0.116.17+1.21.1**, PlayerEngine/Player2NPC **1.4.0**. We still need matching 1.4.0 source integrated before treating this branch as a source rebuild of that binary. The skills include examples for Minecraft 1.21.11 and 26.x; do not copy those APIs into Minecraft 1.21.1 blindly.

## Validating binary patches

```bash
bash tools/check-playerengine-abi.sh /path/to/playerengine-fabric-1.21.1-1.4.0.jar
```

Requires Python 3 and Java 21 with `javap`. The helper checks archive CRCs, expected Fabric ID and construction class presence, Java 21 class versions, and original binary-compatible `BuildFromCode(BuildStructureTask, String)` constructor. It targets the exact prior `NoSuchMethodError` failure. It is a **static** gate, not proof all classes will link at runtime, and not proof that the NPC places blocks correctly.

## In-game acceptance check

Use a disposable Minecraft **1.21.1 Fabric** world with matching Player2NPC and API versions. Give Ellie a 5x5 wooden shelter task with limited materials. Inspect the Task Chain, gather/craft events, movement and real block placement; independently verify the planned structure against the world. Repeat after an interruption, with missing materials, and after a world restart. No false completion messages or exceptions are acceptable.

Workflows: `verify-construction.yml` remains the legacy 1.20.1 build; `inspect-1211-release.yml` inspects the official 1.21.1 binary; `validate-minecraft-skills.yml` validates the vendored toolkit. None alone proves 1.4.0 in-game behavior.

Do not copy the upstream repository's `AGENTS.md` into this project: our root `AGENTS.md` records our specific two-version constraint.
