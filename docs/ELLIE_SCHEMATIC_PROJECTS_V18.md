# Schematic preparation and building v18 — live Minecraft unverified

This adds file-backed survival projects through the existing task supervisor and
native acquisition, crafting, navigation, exact container transfer and Baritone
builder workers. It does not replace the 14-stage setup or the existing
`build_structure` command. No blocks, inventories, saved mobs or chest loot are
created from schematic NBT.

## Commands

Put the blueprint in the **game/server's `schematics` folder**. A dedicated server
needs the file on that server. Litematica is not required for the importer.
Use the filename, including its extension, followed by the **minimum corner** of
the combined selection:

```text
ellie schematic_prepare "small.litematic" 100 64 -20
ellie schematic_build "small.litematic" 100 64 -20
```

`schematic prepare ...`, `schematic build ...` and `schematica prepare/build ...`
are input aliases. `stop` uses the existing cancellation mechanism. Repeat the
same file and coordinates to resume: placed blocks and saved depot coordinates
are re-read; a saved completion flag never bypasses world or inventory checks.
There is no rotation/mirror option in this version. The origin includes the
selection's empty margins, rather than the first non-air block.

**Preparation:** validate registered block/property values and the loaded site,
calculate the remaining finished-item bill, make independent project chests
outside the footprint, then acquire/craft and deposit that bill in batches.
Already-correct blocks reduce the bill. Capacity comes from actual 27-slot chest
and 36-slot carrying inventories, with actual stack limits. A one-block supported
foundation can be placed for a chest. It does not require a flat 5×5 area. Gear and
unrelated inventory are retained. Four free carrying slots are required for
acquisition; insufficient capacity is reported instead of automatic dumping.

**Building:** verify/prep supplies, withdraw working batches, then run native
construction one horizontal layer at a time. Exact block states and required air
are checked in bounded world scans. Native process inactivity is not completion.
Open doors/trapdoors, double slabs and fluids have explicit verified finishing
actions. Water sources/waterlogging are deferred until the solids are built;
flowing cells must then settle to their expected states. A final whole-selection
pass catches damage to earlier layers. Correct target blocks and project chests
are protected from path excavation while the project owns them.

Project stock is excluded from acquisition sources during the job. Bucket refill
collection excludes the blueprint column so it cannot steal and move already
placed source water between targets. Opportunistic workshops stay outside the
footprint. These exclusions release when the root task ends. Temporary movement
suspension uses the existing resumable-task contract; no new scheduler or recovery
controller is added. Existing fall protection and outward bridging remain in use;
a bucket is not treated as validated fall protection.

## Bounds and failure behavior

Supported imports are `.litematic` and modern `.schem` files whose blocks/states
exist in Minecraft 1.21.1. Convert legacy numeric-ID `.schematic` files first.
Litematic negative axes and separated regions are normalized from decoded region
data, correcting the bundled parser's signed-bounds and global-index errors.
Overlapping regions are rejected as ambiguous. Limits are 64×384×64, one million
selection cells, 100,000 non-air targets, 4,096 distinct states, 64 regions/chests,
8 MiB compressed and 32 MiB expanded NBT. Structural NBT validation and region
allocation budgets run before decoding, off the server thread. World scans run on
the server thread, at most 500 cells per call.

Unsupported non-block placement items, unknown states, lava, occupied beds,
impossible waterlogged double slabs, protected conflicts, missing chunks, changed
worlds, inaccessible sites/supplies and changed or obstructed depots end with
specific `BLOCKED_SCHEMATIC_*` reasons. Native action budgets and a cumulative
active-work budget bound retries. Depot placement can try two different sites;
resource acquisition retains the existing shared recovery. Clicks need a verified
world result; inventory decreases need a verified chest increase. Construction
needs new verified block states, rather than a new target or a busy builder.

This is not a universal redstone/state solver: states maintained by power,
entities, growth or physics may be impossible at a chosen site. The builder can
report BLOCKED rather than force them. Large palettes can exceed carrying room;
split such projects. Fluids, directional placements, double chests and tall access
still require live validation. Temporary access blocks outside the selection are
not automatically dismantled. Surplus recipe output remains in inventory; there
is no automatic disposal policy.

## Uploaded mob farm

The supplied file imports as **20×133×20**, with **2,130 non-air cells**:
1,340 cobblestone, 404 bottom cobblestone slabs, 200 ladders, 64 open top oak
trapdoors, 48 white carpet, six hoppers, four chest blocks and 64 water cells
(eight sources). The finished-item plan also reserves 64 cobblestone for access
and one reusable filled water bucket. Extra depot chests, workshop supplies,
fuel, tools, food and any additional access are separate costs. Saved drops,
mobs and container contents are ignored. This count is not evidence the farm has
been built or produces drops.

## Validation and first live test

`bash ellie/build.sh` runs all historical regressions plus:

- 48 full project-coordinator checks: complete prepare/build, partial inventory
  and world, existing completed blocks, multiple depots, lost supplies, full
  carrying inventory, false acquisition/deposit/click completion, obstruction,
  stalled building, suspension/cancellation, changed/unloaded worlds, late damage
  and scoped source reservations. Native task execution/worlds are substituted.
- 22 import/data checks using the actual bundled parser and independently encoded
  NBT fixtures: normal, negative and multiple regions, modern Sponge data,
  malformed lengths/nesting, allocation bounds, path escape, legacy refusal,
  immutable bills and multi-part item counts. Only logging is substituted.
- 17 real layer-proxy/property/NPC-context checks with substituted block/entity
  boundaries, plus 14 production server-adapter checks for actual chest slot
  snapshots, changing stock, stack room, unstackable items, removed/merged/blocked
  depots, unloaded chunks, partial world bills and dry/wet finishing phases.
- Packaged command/hook audits, restoration/comparison of all original native
  builder methods and original placement-context injections, byte-identical
  protected setup classes and a v17 scope freeze for every other existing class.

The historical Node sidecar suite is run separately and is not shipped in the
native JAR. Packaged build metadata records the exact Git revision and source
checksum and explicitly says `live_minecraft_validated: false`.

First use the generated `small.litematic` from the JAR artifact's smoke-test
folder. In a survival test world, choose a loaded, unobstructed 3×2×3 site with
solid ground beneath the target water source. It has a nine-block cobblestone
floor, one trapdoor and one water source. Run preparation, inspect project chests,
then building. Verify orientation/open state, water, stock changes, stop/resume,
and that an obstruction or missing supply reports BLOCKED. Verify ordinary setup,
mining/bridging and food commands still work in that session. Then test a taller
ladder/slab sample before the 133-block farm. No Minecraft client/server gameplay
or live NPC placement is claimed by the automated tests.
