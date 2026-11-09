# Ellie 1.21.1 full-source migration: Phase 3 evidence (2026-11-09)

## What this branch adds

Two isolated CI workflows only:
1. `.github/workflows/ellie-source-snapshot.yml` exports all 53 original `ellie/src` Java files from pinned base commit `3176e5635d924850014c86b97b68ed359e172b1b`, with SHA256 manifest. GitHub Actions [run 42678886756](https://github.com/Joelandonly/PlayerEngine/actions/runs/42678886756) passed.
2. `.github/workflows/ellie-mapping-inputs.yml` fetched actual Minecraft 1.21.1 Mojang client/server mappings plus Fabric Tiny v2 intermediary mappings and checked publisher hashes. [Run 42679717197](https://github.com/Joelandonly/PlayerEngine/actions/runs/42679717197) passed.

No production `ellie/src`, `ellie/tools`, setup, excavation, or schematic class was changed.

## Isolated local migration output (delivered as a ZIP separately, not committed)

- Exact PR #29 reference JAR SHA256: `3caebfa15570b65b32fe3f4b87109601b3bd1bdd2d08404720031ebb0037ddc0`.
- 53/53 canonical originals integrated and verified against Git blob SHA1, with decompiled originals preserved.
- Engine outer class-name coverage 1,275/1,275; no source-name gaps. This is *not* method or binary parity.
- Derived Mojang-named **preview** for 1,275 Java files; 689 changed; all 394 referenced intermediary class IDs, all 851 methods, 1,440/1,446 unique fields mapped, with six remaining fields confirmed by `Blocks` context.
- Java 21 parses 1,275 original and 1,275 preview files; preview has zero intermediary identifiers in code and zero colliding simple-name imports.
- JDK-only compilation: 252 source files; five suites pass. Two restored resource-task classes pass 15 isolated tests.
- **Not yet run**: full Minecraft/Fabric/Architectury Gradle compile, ABI and Mixin parity, fresh-world tests. Network dependency access blocked the local analysis container. The separate portable ZIP includes Windows script `Run-Phase3-Full-Compile.cmd` which downloads pinned Gradle and generates actual compiler error reports.

## Merge/release gate

Treat this as non-production recovery infrastructure. Do **not** merge/ship as complete source parity or replace the owner-confirmed working Ellie JAR. Full Gradle + diff/API/Mixin + gameplay validation are still required. Earlier user-confirmed live `excavate` continues to refer to the existing working compiled implementation.
