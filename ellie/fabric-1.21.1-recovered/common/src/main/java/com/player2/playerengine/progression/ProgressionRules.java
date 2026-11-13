package com.player2.playerengine.progression;

import com.player2.playerengine.PlayerEnginePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;

/** Survival balance for companion progression. XP thresholds extend beyond the designed perks. */
public final class ProgressionRules {
    public static final int MAX_WORK_PER_TASK = 256;
    private static final int MAX_REWARD = 400;
    private static final int FULL_STATS_LEVEL = 40;
    private static final Balance BALANCE = Balance.load();

    private ProgressionRules() {}

    public static long threshold(int level) {
        long previous = Math.max(0L, (long) level - 1);
        long base = BALANCE.firstLevelCost * previous;
        long steps = previous * (previous - 1) / 2;
        if (steps > (Long.MAX_VALUE - base) / BALANCE.levelCostIncrease) return Long.MAX_VALUE;
        return base + BALANCE.levelCostIncrease * steps;
    }

    public static int level(long xp) {
        int low = 1;
        int high = Integer.MAX_VALUE - 1;
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (threshold(middle) <= xp) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    public static double movementBonus(int level) { return fraction(level) * 0.10; }
    public static double miningMultiplier(int level) { return 1.0 + fraction(level) * 0.25; }
    public static double damageBonus(int level) { return fraction(level) * 0.12; }
    public static int bonusHealth(int level) { return 2 * ((Math.min(level, FULL_STATS_LEVEL) - 1) / 9); }
    public static int bonusArmor(int level) { return (Math.min(level, FULL_STATS_LEVEL) - 1) / 13; }

    private static double fraction(int level) {
        return (Math.min(level, FULL_STATS_LEVEL) - 1.0) / (FULL_STATS_LEVEL - 1);
    }

    public enum Perk {
        SUREFOOTED("Surefooted"), BUILDERS_REACH("Builder's Reach"),
        NETHER_VETERAN("Nether Veteran"), SECOND_WIND("Second Wind");

        private final String title;

        Perk(String title) { this.title = title; }
        public String title() { return this.title; }
        public int unlockLevel() { return BALANCE.perkLevels[this.ordinal()]; }
    }

    public static long secondWindCooldownTicks() { return 20L * BALANCE.secondWindSeconds; }

    public static int reward(int base, int breaks, int placements, int kills) {
        return Math.min(MAX_REWARD, base + workBonus(breaks) + workBonus(placements)
            + 4 * Math.min(20, kills));
    }

    private static int workBonus(int count) {
        return Math.min(32, count) + Math.max(0, count - 32) / 8;
    }

    public static int repeatAdjusted(int xp, int earlierAwardsToday) {
        return Math.max(1, xp / (1 + Math.min(5, earlierAwardsToday)));
    }

    private record Balance(int firstLevelCost, int levelCostIncrease, int[] perkLevels, int secondWindSeconds) {
        private static Balance load() {
            Balance defaults = new Balance(50, 10, new int[]{10, 20, 30, 40}, 300);
            try {
                var path = PlayerEnginePaths.userFile("ellie_progression.properties");
                if (!Files.isRegularFile(path)) return defaults;
                Properties values = new Properties();
                try (var input = Files.newBufferedReader(path)) {
                    values.load(input);
                }
                int[] levels = {
                    read(values, "surefootedLevel", 10), read(values, "buildersReachLevel", 20),
                    read(values, "netherVeteranLevel", 30), read(values, "secondWindLevel", 40)
                };
                if (levels[0] < 2 || levels[0] >= levels[1] || levels[1] >= levels[2] || levels[2] >= levels[3]) {
                    throw new IllegalArgumentException("Perk levels must start after level 1 and increase in order");
                }
                return new Balance(read(values, "firstLevelCost", 50), read(values, "levelCostIncrease", 10),
                    levels, read(values, "secondWindSeconds", 300));
            } catch (IOException | RuntimeException | LinkageError failure) {
                LogManager.getLogger(ProgressionRules.class).warn("Using default Ellie progression balance: {}", failure.getMessage());
                return defaults;
            }
        }

        private static int read(Properties values, String key, int fallback) {
            int value = Integer.parseInt(values.getProperty(key, Integer.toString(fallback)).strip());
            if (value < 1 || value > 100_000) throw new IllegalArgumentException(key + " must be between 1 and 100000");
            return value;
        }
    }
}
