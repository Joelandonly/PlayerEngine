package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.DestroyBlockTask;
import com.player2.playerengine.tasks.movement.PickupDroppedItemTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder.Reference;

public final class MineBlockTask extends Task {
   private final MineBlockParams params;
   private final AgenticRunRegistry.AgenticRunState runState;
   private final MaterialReservationService ledger;
   private MineBlockTask.Outcome outcome;
   private MineBlockTask.Phase phase = MineBlockTask.Phase.RESOLVING;
   private boolean finished;
   private final Set<Block> targetBlocks = new LinkedHashSet<>();
   private MiningRequirement requirement = MiningRequirement.HAND;
   private static final long PICKUP_BUDGET_MS = 10000L;
   private long startMs;
   private long settleStartMs = -1L;
   private long pickupStartMs = -1L;
   private Item pickupItem;
   private int pickupPreCount = -1;
   private int pickupExpected = -1;
   private ItemEntity pickupTarget;
   private int minedTotal;
   private int minedNoDrops;
   private int lostDrops;
   private BlockPos activePos;
   private DestroyBlockTask breakChild;
   private PickupDroppedItemTask pickupChild;
   private boolean usedWrongTool;
   private ToolAcquisitionTask acquireChild;

   public MineBlockTask(MineBlockParams params, AgenticRunRegistry.AgenticRunState runState, MaterialReservationService ledger) {
      this.params = params;
      this.runState = runState;
      this.ledger = ledger;
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   public MineBlockTask.Outcome outcome() {
      return this.outcome;
   }

   @Override
   protected void onStart() {
      this.startMs = System.currentTimeMillis();
      this.phase = MineBlockTask.Phase.RESOLVING;
      this.setDebugState(this.phase.name());
      this.report("mining " + this.describeTarget(), false);
   }

   @Override
   protected Task onTick() {
      if (this.finished) {
         return null;
      }

      double elapsedSec = (System.currentTimeMillis() - this.startMs) / 1000.0;
      if (elapsedSec >= this.params.timeoutSeconds() && this.phase != MineBlockTask.Phase.RESOLVING) {
         if (this.minedTotal > 0) {
            this.finishPartial("timeout after partial mine", MineBlockTask.PartialCause.TIMEOUT);
         } else {
            this.terminateFailed("mine_timeout");
         }

         return null;
      } else {
         switch (this.phase) {
            case RESOLVING:
               return this.resolve();
            case ACQUIRING_TOOL:
               return this.acquireTool();
            case FINDING_BLOCK:
               return this.findBlock();
            case BREAKING:
               return this.breakBlock();
            case COLLECTING:
               return this.collect();
            case SETTLING:
               return this.settle();
            case DONE:
            case PARTIAL:
            case FAILED:
            default:
               return null;
         }
      }
   }

   private Task resolve() {
      if (!this.resolveTargetBlocks()) {
         this.terminateFailed("unknown_block_id");
         return null;
      }

      this.requirement = MiningRequirement.HAND;

      for (Block block : this.targetBlocks) {
         MiningRequirement req = ToolRequirementResolver.requirementFor(block);
         if (req.ordinal() > this.requirement.ordinal()) {
            this.requirement = req;
         }
      }

      this.phase = ToolRequirementResolver.isHandOnly(this.requirement) ? MineBlockTask.Phase.FINDING_BLOCK : MineBlockTask.Phase.ACQUIRING_TOOL;
      this.setDebugState(this.phase.name() + " req=" + this.requirement);
      return null;
   }

   private boolean resolveTargetBlocks() {
      this.targetBlocks.clear();
      String raw = this.params.blockId();
      if (raw != null && !raw.isBlank()) {
         String token = raw.trim();
         boolean tagSyntax = token.startsWith("#");
         if (tagSyntax) {
            token = token.substring(1);
         }

         boolean looksLikeTag = tagSyntax || !token.contains(":") && token.contains("/");
         String normalised = token.contains(":") ? token : "minecraft:" + token;
         ResourceLocation parsed = ResourceLocation.tryParse(normalised);
         if (parsed == null) {
            return false;
         } else if (looksLikeTag) {
            return this.resolveTag(parsed);
         } else {
            Optional<Block> direct = BuiltInRegistries.BLOCK.getOptional(parsed);
            if (direct.isPresent()) {
               this.targetBlocks.add(direct.get());
               return true;
            } else {
               return this.resolveTag(parsed);
            }
         }
      } else {
         return false;
      }
   }

   private boolean resolveTag(ResourceLocation tagId) {
      TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);

      for (Block block : BuiltInRegistries.BLOCK) {
         boolean inTag = BuiltInRegistries.BLOCK
            .getResourceKey(block)
            .<Reference>flatMap(BuiltInRegistries.BLOCK::getHolder)
            .map(holder -> holder.is(tag))
            .orElse(false);
         if (inTag) {
            this.targetBlocks.add(block);
         }
      }

      return !this.targetBlocks.isEmpty();
   }

   private Task acquireTool() {
      if (ToolRequirementResolver.isHeldInventoryOnly(this.controller, this.requirement)) {
         this.acquireChild = null;
         this.phase = MineBlockTask.Phase.FINDING_BLOCK;
         this.setDebugState(this.phase.name());
         return null;
      }

      if (this.acquireChild != null) {
         if (!this.acquireChild.isFinished() && !this.acquireChild.stopped()) {
            return this.acquireChild;
         } else {
            boolean acquired = ToolRequirementResolver.isHeldInventoryOnly(this.controller, this.requirement);
            this.acquireChild = null;
            if (!acquired) {
               this.terminateFailed("could_not_acquire_tool:" + this.tierWord());
               return null;
            } else {
               this.phase = MineBlockTask.Phase.FINDING_BLOCK;
               this.setDebugState(this.phase.name());
               return null;
            }
         }
      } else {
         this.acquireChild = new ToolAcquisitionTask(this.requirement, this.params, this.runState, this.ledger);
         this.report("getting a " + this.tierWord() + " pickaxe before mining...", false);
         this.setDebugState(this.phase.name());
         return this.acquireChild;
      }
   }

   private Task findBlock() {
      if (this.minedTotal >= this.params.maxBlocks()) {
         this.settleStartMs = -1L;
         this.phase = MineBlockTask.Phase.SETTLING;
         this.setDebugState(this.phase.name());
         return null;
      }

      Optional<BlockPos> nearest = this.getClosestBlock();
      if (nearest.isEmpty()) {
         if (this.minedTotal > 0) {
            this.finishPartial("no more target blocks in range", MineBlockTask.PartialCause.RANGE_EXHAUSTED);
         } else {
            this.terminateFailed("no_target_block_in_range");
         }

         return null;
      } else {
         this.activePos = nearest.get();
         this.phase = MineBlockTask.Phase.BREAKING;
         this.setDebugState(this.phase.name() + "@" + this.activePos.toShortString());
         return null;
      }
   }

   private Optional<BlockPos> getClosestBlock() {
      Vec3 origin = this.controller.getPlayer().position();
      Block[] blocks = this.targetBlocks.toArray(Block[]::new);
      Optional<BlockPos> found = this.controller
         .getBlockScanner()
         .getNearestBlock(origin, check -> !this.controller.getBlockScanner().isUnreachable(check) && WorldHelper.canBreak(this.controller, check), blocks);
      double radiusSq = this.params.radius() * this.params.radius();
      if (found.isPresent()) {
         BlockPos p = found.get();
         double dx = p.getX() + 0.5 - origin.x;
         double dy = p.getY() + 0.5 - origin.y;
         double dz = p.getZ() + 0.5 - origin.z;
         if (dx * dx + dy * dy + dz * dz > radiusSq) {
            return Optional.empty();
         }
      }

      return found;
   }

   private Task breakBlock() {
      if (this.activePos == null) {
         this.phase = MineBlockTask.Phase.FINDING_BLOCK;
         this.setDebugState(this.phase.name());
         return null;
      }

      if (this.breakChild == null) {
         this.usedWrongTool = !StorageHelper.miningRequirementMetInventory(this.controller, this.requirement);
         this.breakChild = new DestroyBlockTask(this.activePos);
         this.report("breaking " + this.blockNameAt(this.activePos), false);
         this.setDebugState(this.phase.name() + "@" + this.activePos.toShortString());
         return this.breakChild;
      }

      if (!this.breakChild.isFinished() && !this.breakChild.stopped()) {
         return this.breakChild;
      }

      this.breakChild = null;
      this.phase = MineBlockTask.Phase.COLLECTING;
      this.settleStartMs = -1L;
      this.pickupStartMs = -1L;
      this.minedTotal++;
      this.updateProgress();
      this.setDebugState(this.phase.name());
      return null;
   }

   private Task collect() {
      if (this.usedWrongTool) {
         this.minedNoDrops++;
         this.usedWrongTool = false;
         this.clearPickupState();
         this.activePos = null;
         this.report("broke " + this.describeTarget() + " but my " + this.tierWord() + " tool isn't strong enough — got no drops.", true);
         this.updateProgress();
         this.phase = MineBlockTask.Phase.FINDING_BLOCK;
         this.setDebugState(this.phase.name());
         return null;
      }

      Vec3 origin = this.controller.getPlayer().position();
      if (this.pickupChild == null) {
         double pickupRadius = Math.min(8.0, this.params.radius());
         Optional<ItemEntity> nearby = this.controller
            .getEntityTracker()
            .getItemDropsWithin(origin, pickupRadius, e -> !e.isRemoved())
            .stream()
            .min(Comparator.comparingDouble(e -> e.distanceToSqr(origin)));
         if (nearby.isEmpty()) {
            if (this.settleStartMs < 0L) {
               this.settleStartMs = System.currentTimeMillis();
            }

            double settled = (System.currentTimeMillis() - this.settleStartMs) / 1000.0;
            if (settled < 0.5) {
               this.setDebugState(this.phase.name() + ":waiting-for-drop");
               return null;
            } else {
               this.activePos = null;
               this.updateProgress();
               this.phase = MineBlockTask.Phase.FINDING_BLOCK;
               this.setDebugState(this.phase.name());
               return null;
            }
         } else {
            ItemEntity target = nearby.get();
            Item item = target.getItem().getItem();
            int count = Math.max(1, target.getItem().getCount());
            this.pickupItem = item;
            this.pickupPreCount = this.controller.getItemStorage().getItemCount(item);
            this.pickupExpected = count;
            this.pickupTarget = target;
            this.pickupChild = new PickupDroppedItemTask(new ItemTarget(item, count), true);
            this.report("collecting " + ItemHelper.stripItemName(item) + " drop", false);
            this.setDebugState(this.phase.name() + ":pickup");
            return this.pickupChild;
         }
      } else {
         if (this.pickupItem != null) {
            int delta = this.controller.getItemStorage().getItemCount(this.pickupItem) - this.pickupPreCount;
            boolean targetGone = this.pickupTarget != null && this.pickupTarget.isRemoved();
            if (delta > 0 && (targetGone || delta >= this.pickupExpected)) {
               this.clearPickupState();
               this.activePos = null;
               this.settleStartMs = -1L;
               this.updateProgress();
               this.phase = MineBlockTask.Phase.FINDING_BLOCK;
               this.setDebugState(this.phase.name());
               return null;
            }
         }

         if (!this.pickupChild.isFinished() && !this.pickupChild.stopped()) {
            if (this.pickupStartMs < 0L) {
               this.pickupStartMs = System.currentTimeMillis();
            }

            if (System.currentTimeMillis() - this.pickupStartMs <= 10000L) {
               return this.pickupChild;
            }

            boolean gone = this.pickupTarget != null && this.pickupTarget.isRemoved();
            if (!this.pickupChild.stopped()) {
               this.pickupChild.stop(this);
            }

            this.clearPickupState();
            this.activePos = null;
            this.settleStartMs = -1L;
            this.lostDrops++;
            this.report(
               "broke "
                  + this.describeTarget()
                  + " but the drop is unreachable ("
                  + (gone ? "gone — destroyed or taken by another entity" : "fell into water/void")
                  + ") — lost the drop, moving on.",
               true
            );
            this.updateProgress();
            this.phase = MineBlockTask.Phase.FINDING_BLOCK;
            this.setDebugState(this.phase.name());
            return null;
         } else {
            boolean gone = this.pickupTarget != null && this.pickupTarget.isRemoved();
            this.clearPickupState();
            this.activePos = null;
            this.settleStartMs = -1L;
            this.lostDrops++;
            this.report(
               "broke "
                  + this.describeTarget()
                  + " but the drop is unreachable ("
                  + (gone ? "gone — destroyed or taken by another entity" : "fell into water/void")
                  + ") — lost the drop, moving on.",
               true
            );
            this.updateProgress();
            this.phase = MineBlockTask.Phase.FINDING_BLOCK;
            this.setDebugState(this.phase.name());
            return null;
         }
      }
   }

   private void clearPickupState() {
      this.pickupChild = null;
      this.pickupItem = null;
      this.pickupPreCount = -1;
      this.pickupExpected = -1;
      this.pickupTarget = null;
      this.pickupStartMs = -1L;
   }

   private Task settle() {
      if (this.settleStartMs < 0L) {
         this.settleStartMs = System.currentTimeMillis();
      }

      double settled = (System.currentTimeMillis() - this.settleStartMs) / 1000.0;
      if (settled < 0.5) {
         return null;
      }

      if (this.minedNoDrops > 0) {
         this.finishPartial("mined with insufficient tool", MineBlockTask.PartialCause.RANGE_EXHAUSTED);
      } else if (this.lostDrops > 0) {
         this.finishPartial("drops unreachable", MineBlockTask.PartialCause.DROPS_UNREACHABLE);
      } else {
         this.finishDone();
      }

      return null;
   }

   private void finishDone() {
      this.phase = MineBlockTask.Phase.DONE;
      this.finished = true;
      this.outcome = new MineBlockTask.Outcome(MineBlockTask.OutcomeKind.CLEAN_SUCCESS, this.minedTotal, 0, 0, this.targetLabel(), null);
      this.recordProgress();
      this.setDebugState(MineBlockTask.Phase.DONE.name());
      this.controller.log("[Agentic] mine_block: done (mined=" + this.minedTotal + " block=" + this.targetLabel() + ")");
      this.report("mined " + this.minedTotal + " " + this.describeTarget(), true);
   }

   private void finishPartial(String message, MineBlockTask.PartialCause cause) {
      this.phase = MineBlockTask.Phase.PARTIAL;
      this.finished = true;
      if (this.minedNoDrops > 0) {
         this.outcome = new MineBlockTask.Outcome(
            MineBlockTask.OutcomeKind.PARTIAL_NO_DROPS, this.minedTotal, this.minedNoDrops, this.lostDrops, this.targetLabel(), "incorrect_tool_no_drops"
         );
      } else if (cause == MineBlockTask.PartialCause.DROPS_UNREACHABLE || this.lostDrops > 0) {
         this.outcome = new MineBlockTask.Outcome(
            MineBlockTask.OutcomeKind.PARTIAL_DROPS_LOST, this.minedTotal, 0, this.lostDrops, this.targetLabel(), "drops_unreachable"
         );
      } else if (cause == MineBlockTask.PartialCause.RANGE_EXHAUSTED) {
         this.outcome = new MineBlockTask.Outcome(
            MineBlockTask.OutcomeKind.PARTIAL_RANGE_EXHAUSTED, this.minedTotal, 0, 0, this.targetLabel(), "no_more_blocks_in_range"
         );
      } else {
         this.outcome = new MineBlockTask.Outcome(MineBlockTask.OutcomeKind.PARTIAL_TIMEOUT, this.minedTotal, 0, 0, this.targetLabel(), "timeout");
      }

      this.recordProgress();
      if (this.runState != null) {
         if (this.minedNoDrops > 0) {
            this.runState.setMineDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, "incorrect_tool_no_drops");
         } else if (cause == MineBlockTask.PartialCause.DROPS_UNREACHABLE || this.lostDrops > 0) {
            this.runState.setMineDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, "drops_unreachable");
         } else if (cause == MineBlockTask.PartialCause.RANGE_EXHAUSTED) {
            this.runState.setMineDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, "no_more_blocks_in_range");
         } else {
            this.runState.setMineDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, "timeout");
         }
      }

      this.setDebugState(MineBlockTask.Phase.PARTIAL.name());
      this.controller
         .log(
            "[Agentic] mine_block: partial: "
               + message
               + " (mined="
               + this.minedTotal
               + " noDrops="
               + this.minedNoDrops
               + " lostDrops="
               + this.lostDrops
               + " block="
               + this.targetLabel()
               + ")"
         );
      if (this.minedNoDrops > 0) {
         this.report("mined " + this.minedTotal + " " + this.describeTarget() + " but " + this.minedNoDrops + " gave no drops (tool too weak).", true);
      } else if (cause != MineBlockTask.PartialCause.DROPS_UNREACHABLE && this.lostDrops <= 0) {
         this.report("mined " + this.minedTotal + " " + this.describeTarget() + " (" + message + ").", true);
      } else {
         this.report("mined " + this.minedTotal + " " + this.describeTarget() + ", lost " + this.lostDrops + " drop(s) (unreachable).", true);
      }
   }

   private void terminateFailed(String reason) {
      this.phase = MineBlockTask.Phase.FAILED;
      this.finished = false;
      this.outcome = new MineBlockTask.Outcome(MineBlockTask.OutcomeKind.FAILED, this.minedTotal, this.minedNoDrops, this.lostDrops, this.targetLabel(), reason);
      if (this.runState != null) {
         this.runState.setMineProgress(reason);
      }

      this.stopChildren();
      this.setDebugState("FAILED:" + reason);
      this.controller.log("[Agentic] mine_block: " + reason + " (block=" + this.targetLabel() + ")");
      this.report("couldn't mine " + this.describeTarget() + ": " + reason, true);
      if (!this.stopped()) {
         this.stop(this);
      }
   }

   private void stopChildren() {
      if (this.breakChild != null && !this.breakChild.stopped()) {
         this.breakChild.stop(this);
      }

      if (this.pickupChild != null && !this.pickupChild.stopped()) {
         this.pickupChild.stop(this);
      }

      if (this.acquireChild != null && !this.acquireChild.stopped()) {
         this.acquireChild.stop(this);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (!this.finished && !this.isTerminalPhase()) {
         this.phase = MineBlockTask.Phase.FAILED;
         if (this.runState != null) {
            this.runState.setMineProgress("interrupted");
         }

         this.setDebugState("FAILED:interrupted");
      }
   }

   private boolean isTerminalPhase() {
      return this.phase == MineBlockTask.Phase.DONE || this.phase == MineBlockTask.Phase.PARTIAL || this.phase == MineBlockTask.Phase.FAILED;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof MineBlockTask task && Objects.equals(task.params, this.params);
   }

   @Override
   protected String toDebugString() {
      return "MineBlock[" + this.targetLabel() + "]";
   }

   private void recordProgress() {
      if (this.runState != null) {
         this.runState
            .setMineProgress("mined=" + this.minedTotal + " noDrops=" + this.minedNoDrops + " lostDrops=" + this.lostDrops + " block=" + this.targetLabel());
      }
   }

   private void updateProgress() {
      this.recordProgress();
      this.setDebugState(this.phase.name() + " mined=" + this.minedTotal);
   }

   private String tierWord() {
      return this.requirement.name().toLowerCase(Locale.ROOT);
   }

   private String describeTarget() {
      return this.targetLabel();
   }

   private String targetLabel() {
      String raw = this.params.blockId();
      return raw != null ? raw : "block";
   }

   private String blockNameAt(BlockPos pos) {
      try {
         Block block = this.controller.getWorld().getBlockState(pos).getBlock();
         ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
         return id != null ? id.getPath() : this.describeTarget();
      } catch (Exception e) {
         return this.describeTarget();
      }
   }

   private void report(String message, boolean milestone) {
      if (this.runState == null || !this.runState.isTerminal()) {
         this.controller.reportAgenticProgress(message, milestone);
      }
   }

   public record Outcome(MineBlockTask.OutcomeKind kind, int mined, int noDrops, int lostDrops, String blockId, String reasonToken) {
   }

   public enum OutcomeKind {
      CLEAN_SUCCESS,
      PARTIAL_NO_DROPS,
      PARTIAL_TIMEOUT,
      PARTIAL_RANGE_EXHAUSTED,
      PARTIAL_DROPS_LOST,
      FAILED;
   }

   private enum PartialCause {
      TIMEOUT,
      RANGE_EXHAUSTED,
      DROPS_UNREACHABLE;
   }

   private enum Phase {
      RESOLVING,
      ACQUIRING_TOOL,
      FINDING_BLOCK,
      BREAKING,
      COLLECTING,
      SETTLING,
      DONE,
      PARTIAL,
      FAILED;
   }
}
