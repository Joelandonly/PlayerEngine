package com.player2.playerengine.progression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

final class CompanionProgressData extends SavedData {
    private static final Factory<CompanionProgressData> FACTORY =
        new Factory<>(CompanionProgressData::new, CompanionProgressData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Map<String, Profile> profiles = new HashMap<>();

    static CompanionProgressData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "ellie_progression");
    }

    Profile find(String identity) {
        return this.profiles.get(identity);
    }

    Profile create(String identity) {
        return this.profiles.computeIfAbsent(identity, ignored -> new Profile());
    }

    private static CompanionProgressData load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionProgressData data = new CompanionProgressData();
        CompoundTag profiles = tag.getCompound("profiles");
        for (String identity : profiles.getAllKeys()) {
            CompoundTag saved = profiles.getCompound(identity);
            Profile profile = data.create(identity);
            profile.xp = Math.max(0L, saved.getLong("xp"));
            profile.secondWindReadyAt = Math.max(0L, saved.getLong("secondWindReadyAt"));
            profile.lastAward = saved.getString("lastAward");
            profile.rewardDay = saved.getLong("rewardDay");
            CompoundTag repeats = saved.getCompound("repeats");
            for (String kind : repeats.getAllKeys()) {
                profile.repeats.put(kind, Math.max(0, Math.min(5, repeats.getInt(kind))));
            }
            for (Tag key : saved.getList("completedProjects", Tag.TAG_STRING)) {
                profile.completedProjects.add(key.getAsString());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag profiles = new CompoundTag();
        this.profiles.forEach((identity, profile) -> {
            CompoundTag saved = new CompoundTag();
            saved.putLong("xp", profile.xp);
            saved.putLong("secondWindReadyAt", profile.secondWindReadyAt);
            saved.putString("lastAward", profile.lastAward);
            saved.putLong("rewardDay", profile.rewardDay);
            CompoundTag repeats = new CompoundTag();
            profile.repeats.forEach(repeats::putInt);
            saved.put("repeats", repeats);
            ListTag projects = new ListTag();
            profile.completedProjects.forEach(key -> projects.add(StringTag.valueOf(key)));
            saved.put("completedProjects", projects);
            profiles.put(identity, saved);
        });
        tag.put("profiles", profiles);
        return tag;
    }

    static final class Profile {
        long xp;
        private long lastComputedXp;
        private int level = 1;
        long secondWindReadyAt;
        String lastAward = "";
        long rewardDay;
        final Map<String, Integer> repeats = new HashMap<>();
        final Set<String> completedProjects = new HashSet<>();

        int level() {
            if (this.xp != this.lastComputedXp) {
                this.level = ProgressionRules.level(this.xp);
                this.lastComputedXp = this.xp;
            }
            return this.level;
        }
    }
}
