# Schematic preparation clearance hotfix v18.1

The supplied run used v18, revision `240796b`, with `schematic_prepare
123.litematic 100 104 286`. The file imported successfully: 20×133×20 with
2,130 non-air targets. The next scan found a protected west-facing wall torch at
**104, 106, 289**, where the blueprint has air. No gathering, crafting, depot
placement or native builder had started. NPC/controller tick telemetry continued
normally after the task stopped. This evidence identifies a task gate failure;
it does not show a Minecraft freeze.

The failure was coupling material preparation to build-site clearance. The
preparation command should be able to stage supplies while the owner arranges a
clear site. This hotfix changes only `SchematicProjectTask` and `SchematicWorld`:

- Preflight collects protected-site conflicts separately from the remaining
  material bill. It retains a count and up to eight coordinate/block samples.
- `schematic_prepare` warns about those conflicts, then acquires and deposits the
  required supplies. Its terminal message says materials were verified and
  construction has not started; unresolved site issues remain visible.
- `schematic_build` retains the protection gate before acquiring/building, checks
  it again after stocking and during layer scans, and reports
  `BLOCKED_SCHEMATIC_PROTECTED_SITE` with coordinates and a remedy. It does not
  remove protected torches, containers or parts of the existing base.
- Already-correct protected targets, including water, are reusable. A protected
  container in a required water cell remains a conflict.
- `schematicBuildSite` in observatory status separates site readiness from the
  active material objective. Unchanged warnings are not repeatedly announced.

The commands and file/origin conventions are unchanged. In this build the full
selection's air still counts as clearance for construction. There is no new
margin heuristic, implicit demolition permission, alternative scheduler or
replacement setup. With the exact logged origin, preparation can proceed;
construction still needs the conflicting torch moved/removed by the owner or a
clearer origin. More conflicts may be found after the first one.

## Validation

The complete historical Java and Node suites are required for the delivered
revision. Added regression coverage includes:

- Real coordinator preparation with a protected-site conflict: supplies staged,
  one warning, original blocks untouched, and a truthful prepared-only result.
- The corresponding build refuses before acquisition, depot placement or builder
  activation. New conflicts during final preparation or active building stop it.
- A 20×133×20 synthetic selection replays the logged origin, torch coordinates,
  west-facing state and protection through the production world preflight and
  root coordinator. It completes material preparation, preserves the torch and
  blocks construction. Removing the conflict lets a fresh build begin work.
- A container introduced after preflight is detected by the actual layer adapter;
  a fresh scan clears a resolved conflict.
- All 32 protected setup classes retain their proven hashes. Every other v18
  class except the six emitted from these two feature sources remains
  byte-identical; the native builder and placement adapters are unchanged.

The replay uses substituted game registries, world/protection events and native
worker actions. It does not reproduce survival gameplay or prove the mob farm
can be built. Live checks remain: rerun preparation at the logged origin, inspect
stock and the site warning, then clear the chosen build site and test the small
included smoke schematic before attempting the tall farm.
