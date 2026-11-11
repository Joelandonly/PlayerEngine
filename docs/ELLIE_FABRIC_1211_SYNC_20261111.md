# Ellie 1.21.1 Fabric source synchronization

**Status: staging branch initialized. Source upload is pending and is NOT yet in GitHub. Do not merge.**

Source of truth: the user-supplied `EllieSrc.zip` at the November 11, 2026 checkpoint, selecting `recovery/phase4-safe-zones-preview` as the authoritative staged Java tree rather than the older top-level `common/src`. This is Minecraft **1.21.1 Fabric**, distinct from historical root **1.20.1 Quilt** project.

Source is to be committed under `ellie/fabric-1.21.1-recovered/` on branch `sync/ellie-fabric-1.21.1-safezones-20261111`, preserving the existing root project and other branches.

Local audit before upload:
- 1,325 source/build-input files selected; compiled JARs, Gradle caches, old recovery snapshots, logs, local credentials excluded
- 22/22 Safe Zones source manifest entries match; 11 require CRLF-to-LF normalization to compare the pre-Windows SHA-256 values
- Local Gradle-built JAR and Safe Zones release JAR have identical SHA-256: `173b889f3c9564b1a21fdbf14b63749d410d1086ab7c4fb61fae4486a167bb0c`
- High-confidence key/token/private-key pattern scan found no literal secrets in selected files
- Original owner-reported Safe Zones in-game functionality; an independent clean-checkout build is **not** established

Expected features: native owner commands, Hero, top-down excavation, block knowledge, shared intelligent tools, shared acquisition and material bill, safe zones, schematic integration.

**Known unresolved regression:** imported Litematica schematics demand exact dark-oak materials; generic wood substitution has not been applied project-wide to schematic planning and placement.

Acceptance before merging to main: actual source upload; verify file hashes; run clean Java 21/Gradle Fabric build; compare packaged class/feature markers with the known-good JAR; perform safety and schematic in-game checks; review upstream LGPL/provenance and dependency license obligations.
