# Ellie documentation authority audit, 2026-11-09

**Reviewed source:** draft PR #29 at `4626bf2e724f0b4f05ae5234968680132dc293a8`. This review is docs/CI-only. It does not claim that all NPC commands work in live survival Minecraft.

## Current references

| Document | Source-of-truth use |
| --- | --- |
| [ELLIE_COMMANDS.md](ELLIE_COMMANDS.md) / [ELLIE_COMMANDS.tsv](ELLIE_COMMANDS.tsv) | Current PR #29 **49 registered / 8 unregistered** NPC IDs, including the three additions since the original 46-ID audit |
| [ELLIE_BACKLOG_CURRENT_20261109.md](ELLIE_BACKLOG_CURRENT_20261109.md) | Canonical up-to-date **ELLIE-001–037** triage |
| [ELLIE_BACKLOG.md](ELLIE_BACKLOG.md) | **Historical detailed evidence**, now restored to ELLIE-001–029 from both branches |
| [ELLIE_VALIDATION.md](ELLIE_VALIDATION.md) | Versioned acceptance tests T-01–T-44; CI is distinct from gameplay |
| [ELLIE_RELEASE_HANDOFF.md](ELLIE_RELEASE_HANDOFF.md) | Exact GitHub source, JAR and test-build handoff |
| [ELLIE_INVENTORY_OVERFLOW_BACKLOG_20261109.md](ELLIE_INVENTORY_OVERFLOW_BACKLOG_20261109.md) | ELLIE-032 **proposal only**; no auto-disposal implementation |
| [verify_ellie_consistency.py](verify_ellie_consistency.py) | Read-only CI checks for manifest/source, setup bills and issue numbering |

## Dated technical notes: valid within their stated revisions only

- [ELLIE_COMBAT_ACQUIRE_RESUME_20261109.md](ELLIE_COMBAT_ACQUIRE_RESUME_20261109.md), [ELLIE_EXCAVATE_20261109.md](ELLIE_EXCAVATE_20261109.md): PR #28/#29 fix and CI tests. **Live resumption still unverified**.
- [ELLIE_P0_CRAFTING_DEPOT_20261108.md](ELLIE_P0_CRAFTING_DEPOT_20261108.md), [ELLIE_V18_3_LIFECYCLE_STORAGE_SYNC.md](ELLIE_V18_3_LIFECYCLE_STORAGE_SYNC.md): scoped plank/depot/stop-loop fixes, not blanket crafting reliability.
- [ELLIE_SCHEMATIC_PROJECTS_V18.md](ELLIE_SCHEMATIC_PROJECTS_V18.md), [ELLIE_SCHEMATIC_PREPARE_V18_1.md](ELLIE_SCHEMATIC_PREPARE_V18_1.md), [ELLIE_SCHEMATIC_NATIVE_BINDINGS_V18_2.md](ELLIE_SCHEMATIC_NATIVE_BINDINGS_V18_2.md), [ELLIE_SCHEMATIC_ACQUISITION_V18_3.md](ELLIE_SCHEMATIC_ACQUISITION_V18_3.md): revision-specific source/ABI/partial-live project evidence. No complete live schematic build confirmed.
- [ELLIE_PARTIAL_BLOCK_NAVIGATION_V17.md](ELLIE_PARTIAL_BLOCK_NAVIGATION_V17.md), [ELLIE_FOOD_RELIABILITY_V16.md](ELLIE_FOOD_RELIABILITY_V16.md), [ELLIE_WATER_ESCAPE_STALL_V15.md](ELLIE_WATER_ESCAPE_STALL_V15.md), [ELLIE_DESCENT_BLOCK_VIEW_V14.md](ELLIE_DESCENT_BLOCK_VIEW_V14.md), [ELLIE_SAFE_DESCENTS_V13.md](ELLIE_SAFE_DESCENTS_V13.md), [ELLIE_REMOTE_EXECUTION_V12.md](ELLIE_REMOTE_EXECUTION_V12.md): dated test and hotfix boundaries; no universal safety/navigation claim.

## Older revisions and historical evidence, not active instructions

- [ELLIE_SETUP.md](ELLIE_SETUP.md), [ELLIE_SURVIVAL_SETUP.md](ELLIE_SURVIVAL_SETUP.md), [ELLIE_SETUP11_RELIABILITY.md](ELLIE_SETUP11_RELIABILITY.md): former compact/survival/Setup11 setup contracts, explicitly **superseded** for the current `setup`.
- [ELLIE_SETUP_LAYERS_HOTFIX.md](ELLIE_SETUP_LAYERS_HOTFIX.md), [ELLIE_SETUP_STARTUP_HOTFIX.md](ELLIE_SETUP_STARTUP_HOTFIX.md), [ELLIE_COMPACT_PLATFORM_UPLOAD.md](ELLIE_COMPACT_PLATFORM_UPLOAD.md): historical setup foundation and platform fixes, not authority to overwrite proven v9.
- [ELLIE_CRAFT_RECOVERY_UPLOAD.md](ELLIE_CRAFT_RECOVERY_UPLOAD.md), [ELLIE_SHARED_RECOVERY.md](ELLIE_SHARED_RECOVERY.md), [ELLIE_LOG_FINDINGS_2026-11-04.md](ELLIE_LOG_FINDINGS_2026-11-04.md): historical reproducible symptoms, with their original timestamps and intervention conditions.
- [ELLIE_NATIVE_COMMANDS.md](ELLIE_NATIVE_COMMANDS.md): **old 46-command revision**, not PR #29's 49.
- [ELLIE_PROJECT_MANAGER.md](ELLIE_PROJECT_MANAGER.md): earlier supervisor/routing architecture, not fully AGENTIC-owned job planning.
- [ELLIE_SOURCE_PROVENANCE.md](ELLIE_SOURCE_PROVENANCE.md): earlier interpreter/source import. The current custom 1.21.1 release still uses a pinned binary and tracked overlays.
- [BARITONE_RESEARCH_2026-11-02.md](BARITONE_RESEARCH_2026-11-02.md): upstream design research, not proof those changes shipped.
- [ellie/README.md](../ellie/README.md): chronological version notes; the updated banner identifies current scope.

## Confirmed drift corrected

1. **Wrong `setup` description.** The old TSV claimed iron armor, shield, buckets, bed and 32 torches. The current 1.21.1 owner `EllieSetupCommand` starts `BasicSetupTask`: **14-stage basic HOME v9**, 12 logs, wooden/stone tools, 20 cobblestone, 16 coal, 64 torches, verified HOME bench and one chest/furnace. Its 32 setup classes match the owner-tested v9 baseline.
2. **Old command count.** Baseline audit = 46; current PR #29 adds `schematic_prepare`, `schematic_build`, `excavate`, yielding **49**. The eight disabled IDs remain disabled.
3. **Backlog divergence.** `main` contained audited ELLIE-024–029 but PR #29's previous history ended at ELLIE-023. Those six entries and original text are now restored to the legacy archive; new canonical statuses cover 001–037.
4. **Bad evidence labeling.** Owner tested v9 setup in four fresh worlds and observed partial excavation/schematic gathering. PR #28/#29 CI-passing resume fixes are **not** yet proven live.
5. **Old "current" labels.** Previous setup, native command and import-provenance files now carry historical warnings, rather than silently altering their original technical claims.

## Still open

Full custom 1.21.1 source parity (ELLIE-015), in-game recovery after combat/equipment preemption (ELLIE-034/035), actual spider hit/death/drop/pickup tracing (ELLIE-036), and AGENTIC-led resource/storage/excavation logistics (ELLIE-031/032/033). No code in this documentation PR solves those items. Future changes must update source, command inventory, backlog, CI checks, and precise evidence together.
