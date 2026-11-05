# Setup11 shared survival reliability v2

Build ID: `20261105-setup11-shared-reliability-v2`.
Minecraft 1.21.1 Fabric / PlayerEngine baseline 1.4.0 / Java 21.
**Automated preview; live Minecraft survival behavior remains UNVERIFIED.**

Reconciles the owner's checksum-verified Setup11 source ZIP against
`codex/shared-recovery-crash` at `ae24a84`. No Goodbird engine replacement or
full source migration. Imported storage, native commands, village handling,
observatory and server-tick diagnostics remain covered by regression checks.

- All eleven setup objectives are required, with the early stone axe and stone
  shovel. No bucket, iron-armor, diamond, bed or bread objectives.
- Three verified stations and outdoor-only block-light coverage. Existing placed
  stations and installed torches credit real outputs; sunlight does not count.
- Shared recovery retains progress and retry history across target changes.
  A/B selection, inventory reshuffling, reachability, repeated crafting phases
  and damage lost/regained below the high-water mark do not replenish retries.
- Scanner rejection selects alternative locations. Three recovery attempts and
  a 2,400 active-server-tick no-output limit bound unsuccessful resource jobs.
  Higher-priority safety chains retain control; there is no second craft watchdog.
- Canonical inventory capacity reads the actual 36 main/hotbar stacks, bypassing
  cached AIR counts and slot maps. Unknown layouts are not reported as full.
  No automatic dumping is added.
- `TASK_PROGRESS` logs identify objective, subtask, target, occupied/free slots,
  no-output age and recovery owner/attempt. `TASK_BLOCKED` preserves failure
  evidence through the supervisor even if a native receipt claims success.
- The JAR embeds build ID, exact Git revision, source-input digest and baseline
  checksum. CI verifies these and uploads one revision-named install JAR plus
  checksum, separately from a source bundle containing zero binaries.

See `docs/ELLIE_SETUP11_RELIABILITY.md` for test boundaries and live acceptance.
