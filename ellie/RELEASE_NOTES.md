# Setup11: tools first, no automatic torch placement

Build ID: `20261105-setup11-tools-first-no-torch-placement-v3`.

The owner's live run crafted all 32 torches, then setup stopped because its
surface-light survey found one dark sample with no acceptable torch location.
This revision follows the owner's updated contract: remove automatic torch
placement and surface-light verification from setup entirely. Do not claim the
site is lit or spawn-proof. The three workshop stations are still verified.

Required order: wooden pickaxe, early stone axe, two stone pickaxes, stone shovel,
crafting table, furnace, chest, shield, iron pickaxe, iron sword, then 32 torches
retained in inventory for manual placement. No optional skipping, buckets,
iron armor or diamond acquisition is added. Existing saved station layouts can
be resumed and reused. No new setup_base command or base relocation is added.

Regression checks cover the full setup chain with an inventory of partial tools,
existing stations, uneven terrain, missing iron and placement failures. A light
survey boundary that throws verifies setup never calls it; dark/unplaceable
samples cannot block shield or iron-tool acquisition. Shared recovery, Player2,
storage, command and construction checks remain in the complete suite.

Live survival completion still requires a Minecraft test of this exact revision.
The previous live logs establish the torch-stage failure, not a successful run
of this change.

---

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
