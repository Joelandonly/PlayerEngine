# Setup startup and site-preparation correction

The newly supplied log records exact setup being accepted twice at 13:35:39 and
13:35:54 (game-log timestamps), then immediately failing with “No clear supported
workshop pad near setup start.” This was a regression in our preview, not evidence
of an AI failure or a companion-mod loader conflict.

The previous implementation called site selection during setup startup and demanded
an entirely flat clear 9x7 area. The correction:

- Restores an existing recorded plan at startup without selecting a new site.
- Starts normal bootstrap acquisition before the placement phase.
- Prefers an already usable site, then considers preparable terrain at nearby surface heights.
- Queues native excavation for ordinary obstruction blocks and cobblestone foundation
  placement where support is absent. Foundations can cover fluid; living space must
  not remain filled with water/lava.
- Checks actual dimension build limits and the world border. Nonmatching block entities
  and unbreakable obstruction blocks are protected from automatic demolition.
- Verifies native task output and keeps bounded waits; placement/preparation failure
  cannot count as completed setup. Recorded layouts remain owned per world/character.

Local regression evidence: the old preview fails the supported-cell/oversized-pad
case; the new site suite passes six cases. Four additional preparation scenarios
exercise simulated excavation, deck construction over water, and protected/invalid
sites. A packaged-bytecode check requires the restore-only startup call.
All existing setup, supervisor, dispatcher, Node and ASM checks remain required.
These substitute game boundaries; actual mining/bridging and a complete survival
setup still need live acceptance.

## Supplied companion mods

The archive contains Player2NPC 1.21.1-1.4.0, Architectury 13.0.11, Fabric API
0.116.17+1.21.1, Cloth Config 15.0.140, Mod Menu 11.0.5 and LocalizedChat 5.4.0.
Their declared Minecraft/Fabric versions are compatible with the intended target.
A static comparison of Player2NPC's baseline-resolved engine member references
checks 941 references with no newly missing API members in the preview. This does
not certify all runtime interactions.

The archive also includes the older interpreter engine JAR and historical patch
scripts. Those are reference inputs, not instructions to run them or to install
both engine JARs. Install only the replacement hotfix engine alongside the companion
mods. Third-party binaries and raw user logs are not committed to this repository.
