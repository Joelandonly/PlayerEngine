package com.player2.playerengine.progression;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.progression.ProgressionRules.Perk;
import com.player2.playerengine.reliability.SharedTargetRecovery;
import com.player2.playerengine.schematic.SchematicProjectTask;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.agentic.DepositItemsTask;
import com.player2.playerengine.tasks.agentic.GatherLooseItemsTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.construction.ExcavateAreaTask;
import com.player2.playerengine.tasks.construction.build_structure.BuildStructureTask;
import com.player2.playerengine.tasks.farming.HarvestFarmTask;
import com.player2.playerengine.tasks.farming.PlantFarmTask;
import com.player2.playerengine.tasks.farming.SetupFarmTask;
import com.player2.playerengine.warehouse.WarehouseBuildTask;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CompanionProgression {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ResourceLocation BONUS = ResourceLocation.fromNamespaceAndPath("playerengine", "ellie_progression");
    private static final int MAX_PLAN_REWARD = 500;
    private final PlayerEngineController controller;
    private CompanionProgressData data;
    private CompanionProgressData.Profile profile;
    private String identity;
    private int appliedLevel = 1;
    private GlobalPos danger;
    private long retreatUntil;

    public CompanionProgression(PlayerEngineController controller) {
        this.controller = controller;
    }

    public static CompanionProgression of(LivingEntity entity) {
        if (entity instanceof Player || entity.level().isClientSide()) return null;
        PlayerEngineController controller = PlayerEngineController.staticControllers.get(entity.getUUID());
        return controller != null && controller.getEntity() == entity ? controller.progression() : null;
    }

    private void bind() {
        if (this.controller.getAIPersistantData() == null || this.controller.getEntity().getServer() == null) return;
        String character = this.controller.getAIPersistantData().getCharacterId();
        UUID owner = this.controller.getAIPersistantData().progressionOwnerId();
        if (owner == null || character == null || character.isBlank()) {
            this.identity = null;
            this.profile = null;
            return;
        }
        String key = owner + "/" + character;
        if (!key.equals(this.identity)) {
            this.data = CompanionProgressData.get(this.controller.getEntity().getServer());
            this.identity = key;
        }
        this.profile = this.data.find(key);
    }

    public int level() {
        return this.profile == null ? 1 : this.profile.level();
    }

    public String status() {
        this.bind();
        long xp = this.profile == null ? 0 : this.profile.xp;
        int level = this.level();
        String last = this.profile == null || this.profile.lastAward.isBlank() ? "" : " | Last XP: " + this.profile.lastAward;
        String nextPerk = "";
        for (Perk perk : Perk.values()) {
            if (perk.unlockLevel() > level) {
                nextPerk = " | Next perk at " + perk.unlockLevel() + ": " + perk.title();
                break;
            }
        }
        return "Level " + level + " | " + xp + " total XP | " + Math.max(0, ProgressionRules.threshold(level + 1) - xp)
            + " XP to next level" + nextPerk + last;
    }

    public void tick() {
        if (this.identity == null || this.controller.getWorld().getGameTime() % 20 == 0) this.bind();
        int level = this.level();
        if (level == this.appliedLevel) return;
        LivingEntity entity = this.controller.getEntity();
        this.apply(entity, Attributes.MOVEMENT_SPEED, ProgressionRules.movementBonus(level), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.apply(entity, Attributes.ATTACK_DAMAGE, ProgressionRules.damageBonus(level), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.apply(entity, Attributes.MAX_HEALTH, ProgressionRules.bonusHealth(level), AttributeModifier.Operation.ADD_VALUE);
        this.apply(entity, Attributes.ARMOR, ProgressionRules.bonusArmor(level), AttributeModifier.Operation.ADD_VALUE);
        entity.setHealth(Math.min(entity.getHealth(), entity.getMaxHealth()));
        this.appliedLevel = level;
    }

    private void apply(LivingEntity entity, Holder<Attribute> key, double amount, AttributeModifier.Operation operation) {
        var attribute = entity.getAttribute(key);
        if (attribute == null) return;
        if (amount == 0) attribute.removeModifier(BONUS);
        else attribute.addOrUpdateTransientModifier(new AttributeModifier(BONUS, amount, operation));
    }

    public static double miningMultiplier(LivingEntity entity) {
        CompanionProgression progress = of(entity);
        return progress == null ? 1.0 : ProgressionRules.miningMultiplier(progress.level());
    }

    public static double extraBlockReach(LivingEntity entity) {
        CompanionProgression progress = of(entity);
        return progress != null && progress.level() >= Perk.BUILDERS_REACH.unlockLevel() ? 1.0 : 0.0;
    }

    public boolean immuneToFire() {
        return this.level() >= Perk.NETHER_VETERAN.unlockLevel();
    }

    public boolean preventsDamage(DamageSource source) {
        if (this.identity == null) this.bind();
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            && (source.is(DamageTypeTags.IS_FALL) && this.level() >= Perk.SUREFOOTED.unlockLevel()
                || source.is(DamageTypeTags.IS_FIRE) && this.immuneToFire());
    }

    public boolean secondWind(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || this.level() < Perk.SECOND_WIND.unlockLevel()) return false;
        long now = this.controller.getWorld().getGameTime();
        if (now < this.profile.secondWindReadyAt) return false;
        this.profile.secondWindReadyAt = now + ProgressionRules.secondWindCooldownTicks();
        this.data.setDirty();
        LivingEntity entity = this.controller.getEntity();
        entity.setHealth(1);
        Vec3 sourcePosition = source.getSourcePosition();
        BlockPos origin = sourcePosition == null ? entity.blockPosition() : BlockPos.containing(sourcePosition);
        this.danger = GlobalPos.of(this.controller.getWorld().dimension(), origin);
        this.retreatUntil = now + 120;
        this.notifyOwner("Second Wind saved " + entity.getName().getString() + ". Retreating.");
        return true;
    }

    public BlockPos retreatFrom() {
        if (this.danger == null || this.retreatUntil <= this.controller.getWorld().getGameTime()
                || !this.danger.dimension().equals(this.controller.getWorld().dimension())) return null;
        return this.danger.pos();
    }

    public void finishRetreat() {
        this.danger = null;
    }

    private static TaskWork currentWork(LivingEntity entity) {
        CompanionProgression progress = of(entity);
        if (progress == null) return null;
        var chain = progress.controller.getUserTaskChain();
        if (progress.controller.getTaskRunner().getCurrentTaskChain() != chain || !chain.hasActiveNonIdleUserTask()) return null;
        Task task = chain.getCurrentTask();
        return task.isActive() && !task.stopped() ? task.progressionWork() : null;
    }

    public static void blockChanged(LivingEntity entity, BlockPos pos, boolean placed) {
        TaskWork work = currentWork(entity);
        if (work == null) return;
        GlobalPos key = GlobalPos.of(entity.level().dimension(), pos.immutable());
        if (placed) work.placeBlock(key);
        else work.breakBlock(key);
    }

    public static void killed(LivingEntity attacker, LivingEntity target) {
        if (target instanceof Player || attacker == target) return;
        CompanionProgression progress = of(attacker);
        if (progress != null && target instanceof net.minecraft.world.entity.Mob
                && target.level() instanceof ServerLevel level) {
            var task = progress.controller.getUserTaskChain().getCurrentTask();
            if (task != null && task.isActive() && !task.stopped()
                    && (task instanceof com.player2.playerengine.tasks.entity.HeroTask
                        || task instanceof com.player2.playerengine.tasks.entity.PatrolTask)) {
                String species = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
                int xp = Math.max(1, target.getExperienceReward(level, attacker));
                progress.award(List.of(new Reward("Guard kill " + species, null, xp)),
                        "Defeated " + target.getType().getDescription().getString());
                return;
            }
        }
        TaskWork work = currentWork(attacker);
        if (work != null) work.kill(target.getUUID());
    }

    public record Reward(String category, String project, int xp) {}

    /** Called only on a successful root receipt, never when a child action finishes. */
    public static Reward successfulReward(Task task) {
        if (SharedTargetRecovery.failure(task) != null
                || task instanceof TrackedTaskOutcomeProvider result && !result.isSuccessful()
                || task instanceof ResourceTask resource && resource.getAggregatedFailureReason().isPresent()) return null;
        TaskWork work = task.takeProgressionWork();
        if (work == null) return null;
        String category = "Work";
        String project = null;
        int base = 0;
        if (task instanceof SchematicProjectTask schematic) {
            project = schematic.progressionProject();
            category = project == null ? "Schematic preparation" : "Schematic build";
            base = project == null ? 12 : 145;
        } else if (task instanceof WarehouseBuildTask warehouse) {
            project = warehouse.progressionProject();
            category = "Warehouse construction";
            base = 130;
        } else if (task instanceof BuildStructureTask) {
            category = "Structure construction";
            base = work.placements() > 0 ? 145 : 0;
        } else if (task instanceof SetupFarmTask) {
            category = "Farm setup";
            base = work.breaks() + work.placements() > 0 ? 65 : 0;
        } else if (task instanceof HarvestFarmTask harvest) {
            category = "Harvest";
            base = harvest.outcome().harvested() == 0 ? 0 : 8 + harvest.outcome().harvested() / 4;
        } else if (task instanceof PlantFarmTask) {
            category = "Planting";
            base = work.placements() > 0 ? 8 : 0;
        } else if (task instanceof ExcavateAreaTask) {
            category = "Excavation";
        } else if (task instanceof DepositItemsTask) {
            category = "Storage";
            base = 5;
        } else if (work.kills() > 0) {
            category = "Hunting";
        } else if (task instanceof ResourceTask || task instanceof GatherLooseItemsTask) {
            category = "Resource gathering";
            base = 8;
        }
        int xp = ProgressionRules.reward(base, work.breaks(), work.placements(), work.kills());
        return xp > 0 ? new Reward(category, project, xp) : null;
    }

    public void award(Reward reward) {
        if (reward != null) this.award(List.of(reward), reward.category());
    }

    public void award(List<Reward> rewards, String label) {
        try {
            this.applyRewards(rewards, label);
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not record Ellie progression for {}", label, failure);
        }
    }

    private void applyRewards(List<Reward> rewards, String label) {
        if (rewards.isEmpty()) return;
        this.bind();
        if (this.identity == null) return;
        if (this.profile == null) this.profile = this.data.create(this.identity);
        int oldLevel = this.level();
        long day = this.controller.getWorld().getGameTime() / 24000;
        if (this.profile.rewardDay != day) {
            this.profile.repeats.clear();
            this.profile.rewardDay = day;
        }
        int payout = 0;
        for (Reward reward : rewards) {
            if (payout >= MAX_PLAN_REWARD) break;
            if (reward.project() != null && !this.profile.completedProjects.add(reward.project())) continue;
            int earlier = this.profile.repeats.getOrDefault(reward.category(), 0);
            payout = Math.min(MAX_PLAN_REWARD, payout + ProgressionRules.repeatAdjusted(reward.xp(), earlier));
            this.profile.repeats.put(reward.category(), Math.min(5, earlier + 1));
        }
        if (payout == 0) return;
        this.profile.xp = Math.min(Long.MAX_VALUE - payout, this.profile.xp) + payout;
        this.profile.lastAward = label + " (+" + payout + " XP)";
        this.data.setDirty();
        this.tick();
        int newLevel = this.level();
        ObservatoryTelemetry.event("ELLIE_PROGRESSION", this.profile.lastAward + " | level " + newLevel + " | total XP " + this.profile.xp);
        if (newLevel > oldLevel) {
            this.notifyOwner(this.controller.getEntity().getName().getString() + " reached level " + newLevel + " (+" + payout + " XP)");
            for (Perk perk : Perk.values()) {
                if (oldLevel < perk.unlockLevel() && newLevel >= perk.unlockLevel()) this.notifyOwner("Unlocked " + perk.title() + ".");
            }
        } else if (this.controller.getOwner() instanceof ServerPlayer owner) {
            owner.displayClientMessage(Component.literal("Ellie Lv. " + newLevel + "  +" + payout + " XP"), true);
        }
    }

    private void notifyOwner(String message) {
        if (this.controller.getOwner() instanceof ServerPlayer owner) {
            owner.sendSystemMessage(Component.literal("[Ellie] " + message));
        }
    }
}
