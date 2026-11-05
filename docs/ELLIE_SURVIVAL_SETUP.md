# Chest-anchored survival setup

Build ID: `20261105-survival-terraform-platform-v5`.
Development baseline: `b8c3dbb`, the tools-first/no-torch-placement Ellie revision,
which includes the owner's Setup11 overlay on `codex/shared-recovery-crash`.
This is still a checksum-pinned PlayerEngine 1.4.0 binary overlay, not Goodbird
1.1.11 or a complete engine-source migration.

## What setup means

Make Ellie ready to survive and work at the saved starting base, reuse verified
assets, and stop with an honest result. The existing task tree remains the sole
executor. The new source helper selects survival stages inside EllieSetupTask;
it never starts a second task runner or calls an LLM to plan the work.

The original required kit is preserved: wooden pickaxe when needed, early stone
axe, two stone pickaxes, stone shovel, crafting table, furnace, chest, shield,
iron pickaxe, iron sword, then 32 torches. Better tools already owned may satisfy
the existing quantity checks. No bucket, iron armor, diamond, village or farm
acquisition is part of setup.

## Stages and actual completion predicates

| Stage | Behavior and verification |
| --- | --- |
| Assess | Read actual hunger/health, main slots, nearby native-tracker hostiles, time/thunder and existing site records. No pillar construction. |
| Base | Persist/reuse the workshop layout; its chest is the survival anchor. Return to it when starting remotely, taking cover or finalizing. Never relocate a saved base because a remote chunk looked empty. |
| Basic tools | Keep the existing native catalogue route and early axe. Use actual inventory and scoped verified chest stock before producing matching resources. |
| Food emergency | Hunger below 12, or health below 12 HP (six hearts) with hunger below 18, can pause kit work. Obtain safe food, consume it, and verify hunger increase. Multiple meals are allowed only with real progress. |
| Shelter | After basic tools, or sooner if dusk/threats require it, use the existing physical builder for a nearby 5x5 enclosure. Recheck floor, walls, roof, two-block door, open interior and supported external entrance; a builder-success flag is insufficient. |
| Cover/rest | Return inside and close the door when conditions require cover. Wait for daylight/threat clearance or health recovery rather than issuing more mining orders. Existing survival chains retain higher priority. |
| Workshop | The table, furnace and chest must physically exist with safe standing access. Existing terrain repair and station reuse remain intact. |
| Equipment | Required shield/iron tools remain ahead of torch acquisition. At the end, verify an iron-or-better sword in the main hand and a shield in the off hand using existing equipment APIs. |
| Supplies | Carry at least 32 safe food nutrition units (about four steaks or seven bread), eight coal/charcoal fuel items and 32 unplaced torches. Reuse available matching base stock; charcoal and alternate food/fuel routes remain bounded. |
| Final check | Return to the base, recheck the kit/stations, actual enclosure, food, fuel, health/hunger and equipment; finish inside with a closed door. Report a bed as carried/unavailable, never placed or slept in unless that happened separately. |

The chest and shelter plans use the existing per-character/world CoreGoalJournal.
Shelter wood selection considers carried/stored logs/planks and nearby trees.
Its local plan goes through the existing StructureFromCode parser and physical
BuildStructureTask. No direct world-set operation is used by setup.

Shelter sites do not require flat terrain. The bounded planner can excavate
natural terrain, retain suitable existing solids, fill gaps with mixed safe
full blocks, and build a connected entrance bridge or stair. It excludes
falling blocks, containers and valuable materials from filler selection.
Protected structures and hazardous excavation still produce specific BLOCKED
reasons. Saved partial plans retain coordinates. V1 shelter journals migrate
to V2 with material and approach plans. Ten voxel terrain scenarios and four
reflective inventory-adapter checks cover this behavior; actual Minecraft
placement and navigation remain unverified.

## Ownership and bounds

Native defense/food chains retain their priority and movement ownership. A
survival stage can replace an ordinary acquisition leaf, then resolve the same
kit objective from actual inventory. It does not restart the project or discard
placed blocks. Safety interruption preserves survival retry/work budgets.

Food and fuel have at most two top-level strategies. An eating sequence permits
up to eight verified bites; a completed eating child without hunger gain cannot
be repeated indefinitely. Survival actions have a 2,400-active-tick no-output
limit and a 4,800-tick total limit, with up to 12,000 total ticks for shelter
construction. New high-water counts of actual shelter cells are reported to the
existing shared progress monitor. Work/approach limits remain additional bounds.

Cover/rest waiting has a 13,200-active-tick cap, so a disabled daylight cycle or
persistent danger cannot hang setup forever. During this explicit bounded safety
wait the resource no-output clock is held; ordinary resource work cannot use
this exemption. At nominal 20 TPS that wait cap is eleven minutes. The limits
are configurable only through source in this revision, and may need tuning from
live logs. A distant/unreachable base or missing resources can still block.

Storage restriction applies only during setup, is per NPC, and is cleared on
stop/completion. Fresh scanned inventory is required; passive sightings and
stale records do not count as stock. Transfers still use existing bounded native
tasks. No automatic dumping, emptying unrelated chests, forced base relocation,
mandatory wool hunt or new command is introduced.

## Lighting and safety limits

Automatic torch placement and all lighting completion gates remain excluded at
the owner's request. The command retains the 32 torches for manual placement.
An enclosure is **not a claim of spawn-proofing, protection against every mob,
or guaranteed first-night completion**. Sheep availability cannot block setup;
bed placement/sleep is not automated. Hostile detection uses the existing
tracker near Ellie; it is not a global threat scan.

## Validation and delivery

From `ellie/`, run `bash build.sh`, `node --test tests/*.test.mjs`, then
`python3 make_release.py`. The build preserves the pinned baseline checksum,
reviews the exact changed-class set, verifies Player2 API byte identity, and
checks packaged source revision/digest. The workflow checks out the exact head
revision before building, and publishes a single revision-named `UNVERIFIED`
install JAR plus checksum, separately from a zero-binary source archive.

The existing command, storage, supervisor, native build/food, inventory,
crafting/recovery, village and remote-tick regressions remain. New checks run
the actual setup entry/lifecycle with explicit substituted world/avatar/action
boundaries: fresh/partial setup, remote rerun, repeated meals, night/dawn,
missing food/iron/fuel, unsafe or fake shelter results, inaccessible entrance,
unverified equipment, health recovery, frozen night, removed shelter, uneven
workshop ground, defense interruption, hunger during work, and context cleanup.
Geometry tests check missing roof/door/interior clearance/entrance and palette.
Storage-scope tests ensure unrelated chests are excluded and normal commands
regain their prior policy after setup. The real native parser and physical
builder dispatch, plus packaged ABI entry points, are checked separately.

These checks do **not** prove Minecraft pathfinding, recipes, hostile defense,
door interaction, actual health regeneration or successful live survival setup.
The latest uploaded logs tested the preceding torch-stage failure, not this new
survival revision. Its embedded metadata keeps `live_minecraft_validated=false`.

Live acceptance requires the exact new JAR with the owner's existing companion
mods: run setup in a backed-up fresh survival world, keep observatory logs from
assessment through final verification, repeat at dusk/low hunger, and rerun from
away from the placed chest. Check existing structures remain intact, supplies
come from the intended chest, shelter access and door closure work, food/fuel
and actual held equipment match the report, and all torches remain unplaced.
Then force an inaccessible resource/site and check a specific bounded BLOCKED
result instead of an infinite loop or false completion.
