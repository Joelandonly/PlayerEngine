# Native command inventory and validation

**Reviewed target:** Minecraft 1.21.1 Fabric, draft PR #29, source
`4626bf2e724f0b4f05ae5234968680132dc293a8`.
**49 registered NPC commands** are present: 46 in the original audit plus
`schematic_prepare`, `schematic_build` and `excavate` in later stacked PRs.
The development JAR is not the older `main` baseline. Exact owner requests
use the authenticated local route; general conversation may use Player2.

`build_structure` selects only shipped local templates. `agentic` now uses existing
local drop/storage plans, including template chest labels. GPS description polish
no longer requests AI. The original `Player2APIService` remains byte-identical to
the baseline. See [native command changes](ELLIE_NATIVE_COMMANDS.md).

The permanent no-op `dummy` command and its never-finishing test task are removed
from the shipped JAR. Eight other command classes remain unregistered. Substantial
but unverified code, such as the speedrun, is not called impossible or enabled
without acceptance evidence.

**Evidence:** the original 46 IDs were dispatcher/parser-tested; the three
new IDs have separate 1.21.1 registration/ABI checks. The PR #29 JAR passed
the final class-scope/source identity gates on 2026-11-09. No such checks
certify all 49 handlers in Minecraft. Setup v9 was owner-tested in four fresh
worlds and its 32 protected classes remain byte-identical; schematic material
acquisition and excavation have live partial observations, but complete
acceptance after interruption has not been established.
See [reconciled backlog](ELLIE_BACKLOG_CURRENT_20261109.md).
Run `cd ellie && bash build.sh` to regenerate the packaged command report.

## Reconciliation note, 2026-11-09

The historical [TSV inventory](ELLIE_COMMANDS.tsv) listed 46 IDs and the
obsolete iron-kit/shelter description for `setup`. Both files now describe
the current **49-ID PR #29 candidate**, using the 14-stage `BasicSetupTask`.
The old survival task remains in source/test fixtures but does not own the
active `setup` dispatch. This is an inventory and CI-verified API snapshot,
not a declaration that the draft PR stack is merged or live validated.

## NPC commands: full registered list

These are internal bot command IDs, not 49 independent Minecraft slash commands.
Use your existing NPC command entry point and configured prefix (the examples in original
help use `@`). Natural-language aliases such as “Ellie, build a huge cobblestone ...” are handled by the local owner route for supported templates; they are not canonical IDs in this registry.
`<...>` means required; `[...]` means optional. Constructor literals and full original
help text are recorded in [the TSV inventory](ELLIE_COMMANDS.tsv). The syntax below is a
concise guide, not proof that each game operation succeeds.

| Command / syntax | Purpose | Evidence |
| --- | --- | --- |
| `agentic <goal text>` | Local bounded drop-pickup/storage planning | Registered/lookup checked; individual live status not certified |
| `attack <player-or-mob> [count]` | Attack a named player or mob | Registered/lookup checked; individual live status not certified |
| `audit_waypoint <x> <y> <z>` | Refresh a stored waypoint observation | Registered/lookup checked; individual live status not certified |
| `bodylang <greeting / nod_head / shake_head / victory>` | Perform a gesture | Registered/lookup checked; individual live status not certified |
| `build_structure <x> <y> <z> "search query" "description"` | Select a supported local blueprint; gather materials and physically construct it | Registered/lookup checked; individual live status not certified |
| `chatclef <on / off>` | User-controlled AI bridge switch | Registered/lookup checked; individual live status not certified |
| `compare_waypoint <x> <y> <z>` | Compare stored and observed waypoint contents | Registered/lookup checked; individual live status not certified |
| `create_waypoint <x> <y> <z>` | Record a storage waypoint | Registered/lookup checked; individual live status not certified |
| `delete_waypoint <x> <y> <z>` | Delete a waypoint record | Registered/lookup checked; individual live status not certified |
| `deposit [items...]` | Deposit selected items or all nonessential items nearby | Registered/lookup checked; individual live status not certified |
| `deposit_storage_slot <x> <y> <z> <botSlot> <count> [containerSlot]` | Transfer an exact NPC inventory slot into storage | Registered/lookup checked; individual live status not certified |
| `deposit_to_storage <x> <y> <z> <items...>` | Deposit selected items at exact storage coordinates | Registered/lookup checked; individual live status not certified |
| `eat_food [item]` | Eat carried suitable food; optional exact item | Registered/lookup checked; individual live status not certified |
| `equip <items...>` | Equip armor/weapons | Registered/lookup checked; individual live status not certified |
| `excavate <x1> <y1> <z1> <x2> <y2> <z2>` | Native bounded cuboid clearArea (≤32,768 blocks); refuse unsafe/unloaded selections | Partial live digging; suspension hotfix tested in CI, live resume pending |
| `fish` | Fish; requires a fishing rod | Registered/lookup checked; individual live status not certified |
| `follow [username]` | Follow owner or named player | Registered/lookup checked; individual live status not certified |
| `food <food-units>` | Gather food by food value | Registered/lookup checked; individual live status not certified |
| `get <items...>` | Gather/craft requested items | Registered/lookup checked; individual live status not certified |
| `give [username] <item> [count]` | Give/drop items to a player; drop is an alias | Registered/lookup checked; individual live status not certified |
| `goto <coordinates/target> [dimension]` | Travel to coordinates/dimension | Registered/lookup checked; individual live status not certified |
| `harvest_farm [x y z]` | One finite pass harvesting fully grown crops on a recorded farm | Registered/lookup checked; individual live status not certified |
| `hero` | Attack hostile mobs | Registered/lookup checked; individual live status not certified |
| `idle` | Stand still | Registered/lookup checked; individual live status not certified |
| `leaveboat` | Dismount | Registered/lookup checked; individual live status not certified |
| `locate_storage` | Report nearby storage coordinates | Registered/lookup checked; individual live status not certified |
| `locate_structure <stronghold / desert_temple>` | Locate stronghold/desert temple | Registered/lookup checked; individual live status not certified |
| `locate_waypoints <query...>` | Query stored inventory/farm waypoints | Registered/lookup checked; individual live status not certified |
| `meat <food-units>` | Gather meat by food value | Registered/lookup checked; individual live status not certified |
| `mine <block> [count]` | Mine and collect a named block | Registered/lookup checked; individual live status not certified |
| `pickup_drops` | Collect nearby dropped items | Registered/lookup checked; individual live status not certified |
| `place_sign <x> <y> <z> <face> [item_id] [line instructions...]` | Place a sign on an anchor and write front/back lines | Registered/lookup checked; individual live status not certified |
| `plant_farm <item=count[,item=count]> [x y z [policy]]` | Plant ordered crop groups on a recorded farm | Registered/lookup checked; individual live status not certified |
| `read_signs [radius]` | Read nearby sign text and positions | Registered/lookup checked; individual live status not certified |
| `reload_settings` | Reload bot settings | Registered/lookup checked; individual live status not certified |
| `resetmemory` | User-only memory reset | Registered/lookup checked; individual live status not certified |
| `scan [block]` | Locate nearest matching block | Registered/lookup checked; individual live status not certified |
| `scan_storage <x> <y> <z> <light / deep / targeted> [items...]` | Inspect live storage at exact coordinates | Registered/lookup checked; individual live status not certified |
| `set_attack_hostiles <on / off>` | Toggle automatic hostile attacks | Registered/lookup checked; individual live status not certified |
| `set_follow_mode <mode>` | Change companion follow behavior | Registered/lookup checked; individual live status not certified |
| `setup` | **14-stage basic HOME**: wood/stone tools, 64 torches, HOME bench, chest/furnace; no iron kit, shield, bed or house | Owner-tested v9 in four worlds; protected classes and tests preserved |
| `setup_farm [bot / here / owner / player] OR setup_farm <x> <y> <z>` | Prepare and record a 9x9 irrigated plot; does not plant it | Registered/lookup checked; individual live status not certified |
| `schematic_build <file> <x> <y> <z>` | Build exact schematic at minimum corner in verified layers | Packaged routing tests; complete live build pending |
| `schematic_prepare <file> <x> <y> <z>` | Stage required materials into protected depot for a schematic project | Live partial acquisition; complete live acceptance pending |
| `smelt <item> [count]` | Deferred furnace/blast-furnace/smoker processing; configurable | Registered/lookup checked; individual live status not certified |
| `smith <item> [count]` | Recipe-driven smithing upgrade; configurable | Registered/lookup checked; individual live status not certified |
| `stop` | Stop automation | Registered/lookup checked; individual live status not certified |
| `withdraw_from_storage <x> <y> <z> <items...>` | Withdraw selected items at exact coordinates | Registered/lookup checked; individual live status not certified |
| `withdraw_storage_slot <x> <y> <z> <containerSlot> <count> [botSlot]` | Withdraw an exact container slot | Registered/lookup checked; individual live status not certified |

## Present but unavailable through default NPC registration

| ID | Intended purpose | Finding |
| --- | --- | --- |
| `cycle` | One cycles the dragon B) | Class exists; not registered; lookup rejected |
| `gamer` | Beats the game (Miran version) | Class exists; not registered; lookup rejected |
| `inventory` | Prints the bot's inventory OR returns how many of an item the bot has | Class exists; not registered; lookup rejected |
| `list` | List all obtainable items | Class exists; not registered; lookup rejected |
| `pause` | Pauses the bot after the task thats running (Still in development!) | Class exists; not registered; lookup rejected |
| `stash` | Store an item in a chest/container stash. Will deposit ALL non-equipped items if item list is empty. | Class exists; not registered; lookup rejected |
| `status` | Get status of currently executing step or last completed step | Class exists; not registered; lookup rejected |
| `unpause` | UnPauses the bot (Still in development!) | Class exists; not registered; lookup rejected |

`gamer` is the original “beat Minecraft” command. Its handler constructs `BeatMinecraftTask`,
which contains substantial speedrun logic; it is not simply an empty placeholder. But no
`new GamerCommand()` is registered in the default NPC command initializer, and no
`beat_minecraft` alias is defined there. Therefore normal NPC command dispatch cannot
start it in this artifact. Enabling registration alone would not prove the old speedrun
works in 1.21.1; it needs an isolated-world acceptance test before being advertised.

`pause` / `unpause` / `status` here refer to NPC commands. They must not be confused with
similarly named low-level Automatone controls or the server's chain-status diagnostics.

## Building and farm examples to validate in Minecraft

- `setup`: 14-stage batched wood/stone HOME setup, 64 torches and three verified stations. It does not create an iron kit, shield, bed, house or farm.
- `setup_farm`: choose a nearby safe plot or resume an eligible unfinished farm.
- `setup_farm bot`: exact block beneath the NPC; `owner` uses the owner, not gaze.
- `plant_farm wheat_seeds=32,carrot=16`: plant a recorded farm; setup alone does not plant.
- `harvest_farm`: harvest mature crops once; no perpetual farming guarantee.
- `get cobblestone 64`: acquire cobblestone, not build a cobblestone structure.
- `build_structure 100 64 100 "cobblestone shelter" "5 by 5 shelter with walls and roof"`:
  selects a matching local blueprint when supported, otherwise reports an unsupported local template without AI fallback. Use disposable coordinates.

- `schematic_prepare 123.litematic 100 104 286`: acquire/stage blueprint material requirements.
- `excavate 91 80 277 120 80 296`: excavate a user-selected 600-block region. Explicit owner goal, not an automatic subtask.

There is no canonical `build a huge cobblestone` command in this NPC registry. The local exact-owner route recognizes supported build phrases and starts the
corresponding local blueprint through `build_structure`. Very large builds, material availability, placement verification, interruption and
resumption all need live tests; do not infer support from a command description.

## Low-level original Automatone commands

This is a separate registry reached through the `/playerengine <command...>` fallback
in `DefaultCommands`, subject to server permissions and an available automaton NPC.
These IDs are not interchangeable with the NPC commands above: for example, low-level
`build` loads a schematic, while NPC `build_structure` selects local blueprints.
Static registration/constructor inspection only; no in-world execution test was run.

| Command class | IDs / aliases |
| --- | --- |
| AxisCommand | `axis`, `highway` |
| BlacklistCommand | `blacklist` |
| BuildCommand | `build` |
| ChestsCommand | `chests` |
| ClickCommand | `click` |
| ComeCommand | `come` |
| ETACommand | `eta` |
| ExploreCommand | `explore` |
| ExploreFilterCommand | `explorefilter` |
| FindCommand | `find` |
| FishCommand | `fish` |
| FollowCommand | `follow` |
| ForceCancelCommand | `forcecancel` |
| GcCommand | `gc` |
| GoalCommand | `goal` |
| GotoCommand | `goto` |
| HelpCommand | `help`, `?` |
| InvertCommand | `invert` |
| MineCommand | `mine` |
| PathCommand | `path` |
| ProcCommand | `proc` |
| ReloadAllCommand | `reloadall` |
| RenderCommand | `render` |
| RepackCommand | `repack`, `rescan` |
| SaveAllCommand | `saveall` |
| SchematicaCommand | `schematica` |
| SelCommand | `sel`, `selection`, `s` |
| SetCommand | `set`, `setting`, `settings` |
| SurfaceCommand | `surface`, `top` |
| ThisWayCommand | `thisway`, `forward` |
| TunnelCommand | `tunnel` |
| VersionCommand | `version` |
| WaypointsCommand | `waypoints`, `waypoint`, `wp` |

Additional registered aliases: `modified`, `mod`, `com/player2/playerengine/automaton`,
`modifiedsettings` → `set modified`; `reset` → `set reset`; `sethome` → `waypoints save home`;
`home` → `waypoints goto home`. Execution controls supply `pause`/`p`, `resume`/`r`, `paused`,
and `cancel`/`c`/`stop` through `ExecutionControlCommands.registerCommands` invoked by
`BaritoneCommandManager`. Settings can also be queried/toggled by name; that set is dynamic.
The `damn` and `orderpizza` strings are easter eggs in the player-command wrapper, not tasks.

## Server administration and diagnostics

The JAR also declares `/playerengine` routes for `help`, `relog`, `summon`, `queue clear`,
`rag` (retrieve/reload/audit tail/reset-learned/inspect), `routing probe`, `capability`
(status/query/inspect/rebuild/enrich), `resolve`, `memory status`, `player2` configuration,
`budget` settings and `chain status`. These are administrative/diagnostic command families,
not NPC project tasks; they may require operator permission or account services.

Help text advertises `tpto`, and a builder method exists, but the inspected
`MCCommands.register` chain does not call `registerTpto`. Its presence in help is therefore
not evidence of a working route in this JAR. External extensions can register their own
routes; test the actual installed combination before concluding teleport is unavailable.

## Live test record needed per game command

Record the exact JAR hash, Player2NPC version, entered text/prefix, accepted/rejected response,
`CORE_GOAL_SUBMIT` receipt where applicable, task tree/progress, actual inventory or block
changes, and final result. Check stop/replacement while running. For farms, verify irrigated
plot, planted crop counts and mature-only harvest separately. For builds, verify every final
block and interruption/reload behavior. This inventory does not certify every command LIVE / PASS. The four-world v9 setup record and observed partial excavation/schematic runs supersede the older blanket "gameplay untested" phrasing for those commands.

## Local fortified-village template

The earlier audit found `FastStarterBlueprint.maybeCode` before model requests.
The current native orchestrator calls that local selector directly and never starts
the LLM request task. `BlueprintLibrary.choose` recognizes village/settlement/fortified-town
requests and supplies a fixed 47x47 fortified-village plan. Thus the earlier blanket
statement that building always needs AI was too broad. Unsupported requests now fail locally without model-generated code.

Tested directly from the hotfix JAR: local generation and the actual
`StructureFromCode` compiler/runner produced 5,407 operations: 3,533 cobblestone,
378 dirt and 1,496 air/clearing cells. This fits the builder's actual 6,144-operation
and 240,000-character limits (the generated program is 180,038 characters).
The template includes eight buildings, four corner towers, perimeter walls, roads
and a central feature. It is a mixed-material template, not arbitrary town planning.
There is no separate registered `build_village` ID; it is a `build_structure` template.
Actual survival acquisition, construction, interruption and final world verification
were not run. These parser checks do not establish that the village completes in-game.
