package com.player2.playerengine.progression;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;

public final class TaskWork {
    private final Set<GlobalPos> broken = new HashSet<>();
    private final Set<GlobalPos> placed = new HashSet<>();
    private final Set<UUID> kills = new HashSet<>();

    void breakBlock(GlobalPos pos) {
        if (this.broken.size() < ProgressionRules.MAX_WORK_PER_TASK && !this.placed.contains(pos)) this.broken.add(pos);
    }

    void placeBlock(GlobalPos pos) {
        if (this.placed.size() < ProgressionRules.MAX_WORK_PER_TASK) this.placed.add(pos);
    }

    void kill(UUID id) {
        if (this.kills.size() < 20) this.kills.add(id);
    }

    int breaks() { return this.broken.size(); }
    int placements() { return this.placed.size(); }
    int kills() { return this.kills.size(); }
}
