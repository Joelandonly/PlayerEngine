# Ellie 1.21.1: wool-first white carpet acquisition (2026-11-08)

**Parent:** PR #26 (draft), commit `620c200841244c3a25c6874ec836e36f5448b3d5`.
**Minecraft world run:** same exact revision shown by `BUILD_IDENTITY` in
`latest(20261108-225109).log`; user executed
`ellie schematic_prepare 123.litematic 100 104 286`.

## Live failure, not a simulated hypothesis

At 23:44:33 local Minecraft log time, preparation attempted 48
`minecraft:white_carpet` through `route=dynamic_recipe`.
The native child expanded it into 128 `minecraft:string`, then repeatedly
tried to hunt spiders. The project logged repeated `ACQUIRE` restarts,
`TRAVEL` to a project chest, Mob Defense interruptions by zombies/skeletons,
and rejected high-risk descents. Neither white carpet nor string was verified.
At 23:49:44, the shared recovery watchdog blocked the objective after
2,400 ticks with no resource/world output.

Minecraft arithmetic: **48 white carpets = 32 white wool** (two wool make
three carpets). The string route would need 128 string (four string make one
wool), which is a poor primary choice compared with shearing white sheep.

The live log contains 63 `SCHEMATIC_ACTION_VERIFIED` entries for
cobblestone acquisition/deposit and travel. It does **not** verify the
earlier oak-trapdoor patch in this particular run. Do not claim trapdoor
gameplay success on the strength of this file alone.

## Scoped change

`SchematicWorld.acquire()` now routes **only**
`minecraft:white_carpet` into a single native child
`SchematicWhiteCarpetAcquisition`. No independent command scheduler.

The child reads actual inventory on each tick:

1. If target white carpets are already present, finish.
2. If not enough white wool and no shears, use the existing native
   `TaskCatalogue` shears acquisition (iron for shears may need mining).
3. With shears, use the existing native white-wool
   `CollectWoolTask`. The native worker can shear nearby sheep, and the
   task explicitly checks that **white** wool actually enters inventory.
4. With enough white wool, construct the exact native 2×2 vanilla recipe
   (two white wool → three white carpets). Its
   `CraftInInventoryTask` is run with recursive collection **disabled**.
5. The parent schematic coordinator verifies the actual carpet count before
   stock deposit. No fake material grants.

The overlay modifies exactly one packaged native class,
`SchematicWorld.class`, after all earlier v18.3/P0 checks, adds only
the tracked carpet helper classes, and requires the input class SHA
`ae14bf7a89033bdfdc202f1b19db12e42b536b3e34fab1a1f93d74d66e6080fc`.

## Validation and exclusions

`CarpetWoolRouteRegression` tests exact 48/32 arithmetic, stage transitions,
and direct crafting. The real Minecraft registry constructor suite checks
the native shears and white-wool catalogue routes and direct 2×2 carpet
recipe. `verify_carpet_packaging.py` checks the shipped native callsite,
scope, and unchanged stop-loop, specific-plank and 14-stage setup classes.

- Does **not** solve general `KillAndLootTask` mob-farming problems.
  During the failed run, native spider hunting approached targets without
  verified string gains and was repeatedly interrupted by Mob Defense.
- Does not guarantee reachable sheep, successful shearing, iron availability
  for shears, or live navigation. If the native stage cannot obtain required
  materials it must stay blocked/explicit, not return fake success or loop
  through 128 string.
- Native `CollectWoolTask` may shear other sheep colors while looking for
  white wool. Its success is gated by the actual white wool count, but an
  optimized color-aware shearing selector is future work.
- Project-specific chest protection and lifecycle fixes remain in place;
  no merge to stale `main` and no alteration of 14-stage setup.

**Live acceptance:** install the exact CI artifact; rerun schematic
preparation at `100 104 286`; observe events
`SCHEMATIC_CARPET_ROUTE` progressing through shears, white wool and
native crafting, `SCHEMATIC_CARPET_VERIFIED` at 48 and actual project
storage DEPOSIT. Record inability to obtain shears, incorrect colored
wool, or failed sheep approach as separate native execution defects.
