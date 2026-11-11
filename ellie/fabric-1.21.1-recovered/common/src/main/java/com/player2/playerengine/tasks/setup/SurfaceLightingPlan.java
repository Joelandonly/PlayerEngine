package com.player2.playerengine.tasks.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Outdoor-only starter-workshop lighting. Survey a bounded surface grid around
 * the workshop; never descend from the selected base level and never follow a
 * dark patch into a cave. Block light (not sunlight) decides spawn safety.
 * Placement is performed by the existing native placement task, never here.
 */
public final class SurfaceLightingPlan {
    public static final int MAX_NEW_TORCHES = 12;
    private SurfaceLightingPlan() {}
    public interface Probe {
        /** The actual outdoor walking-surface AIR block, or null when excluded. */
        StarterBaseLayout.Pos surface(int x, int z, int baseY) throws Exception;
        int blockLight(StarterBaseLayout.Pos position) throws Exception;
        boolean canPlace(StarterBaseLayout.Pos position) throws Exception;
    }
    public record Survey(int eligible, int dark, Optional<StarterBaseLayout.Placement> next) {
        public boolean complete() { return eligible > 0 && dark == 0; }
    }
    public static Survey inspect(List<StarterBaseLayout.Placement> base, Probe probe,
                                 java.util.Set<StarterBaseLayout.Pos> excluded) throws Exception {
        if (base == null || base.size() != 3) throw new IllegalArgumentException("A verified three-station base is required");
        StarterBaseLayout.Pos anchor = base.getFirst().pos();
        List<StarterBaseLayout.Pos> dark = new ArrayList<>();
        int eligible = 0;
        // A surface margin around the five-block workstation row; every sample
        // stays within ~12 blocks of the worksite. No underground cells sampled.
        for (int x = anchor.x() - 7; x <= anchor.x() + 11; x++) {
            for (int z = anchor.z() - 7; z <= anchor.z() + 7; z++) {
                StarterBaseLayout.Pos p = probe.surface(x, z, anchor.y());
                if (p == null || p.y() < anchor.y()) continue;
                eligible++;
                if (probe.blockLight(p) == 0) dark.add(p);
            }
        }
        StarterBaseLayout.Pos best = null;
        long bestScore = Long.MIN_VALUE;
        for (StarterBaseLayout.Pos candidate : dark) {
            if (excluded.contains(candidate) || !probe.canPlace(candidate)) continue;
            // Prefer a new torch that covers the most currently dark samples.
            // Exact coverage is remeasured from vanilla light after placement.
            int cover = 0;
            for (StarterBaseLayout.Pos other : dark) {
                int dx = Math.abs(candidate.x() - other.x());
                int dy = Math.abs(candidate.y() - other.y());
                int dz = Math.abs(candidate.z() - other.z());
                if (dx + dy + dz <= 12) cover++;
            }
            long dist = Math.abs((long)candidate.x() - anchor.x() - 2)
                      + Math.abs((long)candidate.z() - anchor.z());
            long score = cover * 1000L - dist;
            if (score > bestScore) { best = candidate; bestScore = score; }
        }
        return new Survey(eligible, dark.size(),
            best == null ? Optional.empty() : Optional.of(new StarterBaseLayout.Placement(best, "torch")));
    }
}
