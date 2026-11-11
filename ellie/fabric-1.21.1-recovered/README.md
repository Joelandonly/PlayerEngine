# Ellie 1.21.1 Fabric — source snapshot (November 11, 2026)

**Status: recovered source snapshot; compilation documented on the owner's PC, no independent clean-checkout build or gameplay parity established.**

This standalone Gradle/Architectury project is intentionally under `ellie/fabric-1.21.1-recovered/` so the repository's historical Minecraft **1.20.1 Quilt** project at its root remains untouched. The snapshot came from `C:\EllieSrc\recovery\phase4-safe-zones-preview`, **not** the older `C:\EllieSrc\common\src` source tree.

## Included features

- Native owner commands and Hero, top-down excavation and lifecycle recovery
- 1,060-block native knowledge database and locator
- Full-inventory tool selection
- Shared project material acquisition and reservations
- Owner-managed persistent Safe Zones for block breaking and placement
- Existing schematic preparation, crafting, navigation and observatory code

**Known remaining work:** imported `.litematic` wood palette substitution is not integrated with project-wide acquisition; the user observed this in the November 11 logs. Do not represent the latest source as fixing this.

## Build (Java 21 and Gradle 8.14)

Run from this directory:

```powershell
gradle --no-daemon --console=plain :fabric:build
python tools/verify_snapshot.py
```

`gradle` must be installed; the 507 MB working directory's downloaded Gradle distribution/caches were intentionally excluded. The old workspace's `C:\EllieSrc\.phase3-tools\gradle-8.14\bin\gradle.bat` can also be used. Network access is normally required for first-time Gradle dependency resolution. The original successful build was recorded in `recovery/gradle-phase4-safe-zones.log` (not committed because it may contain personal environment data).

Original installed-release SHA-256: `173b889f3c9564b1a21fdbf14b63749d410d1086ab7c4fb61fae4486a167bb0c`; the JAR is **not committed**. Hash parity of source files is documented in `SOURCE_SHA256.json`, with 11 source files matching the original Safe Zones staging receipt after CRLF-to-LF line-ending normalization. The original working release was reported successful in Minecraft by the owner; a clean-checkout rebuild is still an acceptance gate before merging into `main`.

## Source provenance and scope

See `NOTICE.md` for upstream provenance. Upstream project (Goodbird-git/PlayerEngine, `1.21.1-arch`) is LGPL-licensed. This source recovery includes existing authored, upstream and decompiled files, not a blanket originality claim. Review source-license compatibility and artifact provenance before a public release.

`common/build.gradle` and `fabric/build.gradle` are intentionally simplified only in source-path configuration: the old local staging-variant switches were removed, so normal `common/src` / `fabric/src` directories are compiled. No Java gameplay files were modified in creating this snapshot. The original files remain in the user's working ZIP and in the manifest.
