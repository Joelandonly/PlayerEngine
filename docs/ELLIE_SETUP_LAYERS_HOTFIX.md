# Setup foundation protection and verified layers

The latest supplied run placed foundation cobblestone and then mined those same
positions to obtain more building materials. Setup generations 1 and 2 failed at
game-log times 14:08:47 and 14:09:20 with the base safety error. Rerunning alone
cannot fix a resource collector that treats its own foundation as a source.

The village builder already has a resource-mining exclusion (`VillageSiteGuard`)
and verified structural Y layers. Setup now reuses that exact shipped guard:

- Register the full 9x7 footprint as soon as a new anchor is saved, or an existing
  anchor is restored. A rerun protects the base before bootstrap gathering.
- Exclude resource mining throughout that X/Z footprint, at every height, for this
  character. Materials outside it remain available. Intentional site clearing
  still uses native excavation tasks.
- Keep protection through all setup phases, including post-base gear acquisition.
  Whole-goal stop, completion or abandoned resume releases the owner. A stale owner
  cannot release a replacement job's footprint.
- Finish the two clearance layers and foundation before placing stations. Recheck
  preparation before each station and completion, preserving matching existing
  stations. Repair changed layers instead of immediately failing on missing support.
  At most three repair passes are allowed; repeated damage remains a reported failure.

No teleport, inventory grants or direct world edits are introduced. Existing world
limits, dry living space, protected block entities and unbreakable blocks remain
checked. This is active-job protection, not a permanent land claim or a promise
that unrelated demolition tasks cannot alter the site.

Validation includes six terrain scenarios (with earlier-foundation damage and
repeated damage), seven checks against the original village guard covering every
footprint cell and its boundaries, base/root lifetime, and packaged startup/lifecycle wiring checks.
The changed-foundation scenario fails against the previous task implementation.
All existing setup, command, supervisor, bridge, Node, ASM and packaging checks
remain required. Simulation and binary verification do not replace live gameplay.

For the next Minecraft test, install only the `ELLIE-SETUP-LAYER-HOTFIX.jar` engine
with the existing companion mods, then run `Ellie, setup`. Confirm that material
mining stays outside the recorded base, the whole deck survives later acquisitions,
and the table, furnace and eight chest blocks remain accessible. Test a stop/rerun
at the same recorded anchor as well. The AI bridge folder is unnecessary for this
exact native setup-command test.
