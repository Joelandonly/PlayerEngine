/*
 * Recovered from the pinned Ellie baseline with CFR 0.152; material progress is monotonic.
 */
package com.player2.playerengine.tasks.setup;

import java.util.Locale;

public final class CoreAcquisitionProgress {
    private String materials;
    private final java.util.Map<String,Integer> highWater = new java.util.HashMap<>();
    private String phase;
    private String table;
    private int maxRank = -1;
    private int stationTransitions;
    private int stationaryPolls;

    public Sample observe(String string, String string2, String string3, boolean bl) {
        boolean bl2;
        String string4 = string == null ? "" : string.toUpperCase(Locale.ROOT);
        String string5 = string2 == null ? "" : string2;
        String string6 = string3 == null ? "" : string3;
        int n = CoreAcquisitionProgress.stageRank(string4);
        boolean bl3 = increasedMaterials(string5);
        boolean bl4 = bl2 = n > this.maxRank;
        if (n > this.maxRank) {
            this.maxRank = n;
        }
        boolean bl5 = CoreAcquisitionProgress.isStationPhase(string4);
        if (bl3 || !string6.equals(this.table) || !bl5) {
            this.stationTransitions = 0;
            this.stationaryPolls = 0;
        } else if (bl) {
            ++this.stationaryPolls;
            if (!string4.equals(this.phase)) {
                ++this.stationTransitions;
            }
        }
        boolean bl6 = bl5 && bl && this.stationaryPolls >= 18 && this.stationTransitions >= 12;
        this.materials = string5;
        this.phase = string4;
        this.table = string6;
        return new Sample(bl3, bl2, bl6, this.stationTransitions, n);
    }

    private boolean increasedMaterials(String signature) {
        boolean increased = false;
        for (String part : signature.split("\\|")) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            try {
                String key = part.substring(0, equals);
                int count = Math.max(0, Integer.parseInt(part.substring(equals + 1)));
                Integer previous = this.highWater.putIfAbsent(key, count);
                if (previous != null && count > previous) {
                    this.highWater.put(key, count);
                    increased = true;
                }
            } catch (NumberFormatException ignored) {}
        }
        return increased;
    }

    public void reset() {
        this.highWater.clear();
        this.materials = null;
        this.phase = null;
        this.table = null;
        this.maxRank = -1;
        this.stationTransitions = 0;
        this.stationaryPolls = 0;
    }

    public static boolean isStationPhase(String string) {
        return "MOVE_TO_TABLE".equals(string) || "LOOK_AT_TABLE".equals(string) || "CRAFT_3X3_OUTPUT".equals(string);
    }

    public static int stageRank(String string) {
        return switch (string) {
            case "PLAN" -> 0;
            case "COLLECT_MISSING_MATERIALS" -> 1;
            case "CRAFT_2X2_INTERMEDIATES" -> 2;
            case "FIND_OR_PLACE_TABLE" -> 3;
            case "MOVE_TO_TABLE" -> 4;
            case "LOOK_AT_TABLE" -> 5;
            case "CRAFT_3X3_OUTPUT" -> 6;
            case "VERIFY_OUTPUT" -> 7;
            case "DONE" -> 8;
            default -> -1;
        };
    }

    public record Sample(boolean materialProgress, boolean phaseMilestone, boolean stationLoop, int stationTransitions, int phaseRank) {
    }
}
