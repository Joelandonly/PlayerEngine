package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.agentic.AgenticExecutionContext;
import com.player2.playerengine.agentic.AgenticStorageTarget;
import com.player2.playerengine.agentic.StorageTargetSource;
import com.player2.playerengine.agentic.elliegps.WaypointOriginClassifier;
import com.player2.playerengine.agentic.elliegps.WaypointOriginEvidence;
import com.player2.playerengine.agentic.steps.ResolveStorageChestParams;
import com.player2.playerengine.agentic.storage.ChestPlacementCandidate;
import com.player2.playerengine.agentic.storage.ChestPlacementSelector;
import com.player2.playerengine.agentic.storage.StorageChestCandidate;
import com.player2.playerengine.agentic.storage.StorageChestScanner;
import com.player2.playerengine.agentic.storage.StorageChestValidation;
import com.player2.playerengine.containeraccess.ContainerResolver;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.PlaceItemBlockAtPosTask;
import com.player2.playerengine.tasks.crafting.CraftMacroPhase;
import com.player2.playerengine.tasks.crafting.CraftMacroResourceTask;
import com.player2.playerengine.tasks.crafting.CraftMacroTasks;
import com.player2.playerengine.tasks.crafting.DescribesProgress;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.trackers.storage.ItemStorageTracker;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

public final class ResolveStorageChestTask extends Task implements DescribesProgress {
   private final ResolveStorageChestParams params;
   private final AgenticExecutionContext context;
   private ResolveStorageChestTask.Phase phase = ResolveStorageChestTask.Phase.SCANNING_EXISTING;
   private boolean finished;
   private long startMs;
   private long lastProgressMs;
   private CraftMacroPhase lastObservedMacroPhase;
   private int lastObservedHeld = -1;
   private Task child;
   private StorageChestCandidate existingCandidate;
   private ChestPlacementCandidate placementCandidate;
   private BlockPos targetPos;
   private StorageTargetSource targetSource = StorageTargetSource.EXISTING_UNKNOWN;
   private int placementAttempts = 0;
   private int obtainAttempts = 0;
   private static final int MAX_OBTAIN_ATTEMPTS = 4;
   private static final double OBTAIN_STUCK_GIVE_UP_SECONDS = 25.0;
   private long obtainWanderStartMs = -1L;
   private String lastObtainFailureReason = null;

   public ResolveStorageChestTask(ResolveStorageChestParams params, AgenticExecutionContext context) {
      this.params = params;
      this.context = context;
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   @Override
   public String describeProgress() {
      String target = this.targetPos != null
         ? String.format(Locale.ROOT, "%d %d %d", this.targetPos.getX(), this.targetPos.getY(), this.targetPos.getZ())
         : "none";
      return String.format(Locale.ROOT, "phase=%s target=%s source=%s", this.phase.name().toLowerCase(Locale.ROOT), target, this.targetSource);
   }

   @Override
   protected void onStart() {
      this.startMs = System.currentTimeMillis();
      this.lastProgressMs = this.startMs;
      this.updateProgress("scanning for nearby chest");
      this.report("scanning for a nearby chest", false);
   }

   @Override
   protected Task onTick() {
      if (this.finished) {
         return null;
      }

      if (this.phase == ResolveStorageChestTask.Phase.FAILED) {
         return null;
      }

      boolean macroMakingProgress = this.phase == ResolveStorageChestTask.Phase.ENSURING_CHEST_ITEM
         && this.child instanceof CraftMacroResourceTask macro
         && macro.getPhase() != CraftMacroPhase.DONE
         && this.detectAndRefreshMacroProgress(macro);
      if (!macroMakingProgress) {
         double idleSec = (System.currentTimeMillis() - this.lastProgressMs) / 1000.0;
         if (idleSec >= this.params.timeoutSeconds()) {
            this.terminateFailed("timeout");
            return null;
         }
      }

      if (this.child != null) {
         if (this.child.isActive() && !this.child.stopped()) {
            if (this.phase == ResolveStorageChestTask.Phase.ENSURING_CHEST_ITEM) {
               if (!this.isChestObtainChildComplete()) {
                  if (this.child.containsTask(t -> t instanceof TimeoutWanderTask)) {
                     long now = System.currentTimeMillis();
                     if (this.obtainWanderStartMs < 0L) {
                        this.obtainWanderStartMs = now;
                     } else if ((now - this.obtainWanderStartMs) / 1000.0 >= 25.0) {
                        this.terminateFailed("could_not_obtain_chest_materials");
                        return null;
                     }
                  } else {
                     this.obtainWanderStartMs = -1L;
                  }

                  return this.child;
               }
            } else if (!this.child.isFinished()) {
               return this.child;
            }
         }

         this.captureObtainFailureReason();
         this.child = null;
      }
      return switch (this.phase) {
         case SCANNING_EXISTING -> this.tickScanExisting();
         case MOVING_TO_EXISTING -> this.tickMoveExisting();
         case ENSURING_CHEST_ITEM -> this.tickEnsureChestItem();
         case SELECTING_PLACEMENT -> this.tickSelectPlacement();
         case PLACING_CHEST -> this.tickPlaceChest();
         case VERIFYING_TARGET -> this.tickVerify();
         case DONE, FAILED -> null;
      };
   }

   private Task tickScanExisting() {
      if (!this.params.preferExisting()) {
         if (!this.params.allowPlacement()) {
            this.terminateFailed("no_storage_chest_found");
         } else {
            this.phase = ResolveStorageChestTask.Phase.ENSURING_CHEST_ITEM;
            this.updateProgress("no existing chest; obtaining chest item");
            this.report("no chest nearby; obtaining materials", false);
         }

         return null;
      } else {
         StorageChestScanner.ScanResult scan = StorageChestScanner.scan(this.controller, this.params);
         if (scan.best().isPresent() && !this.beyondTravelCap(scan.best().get().pos())) {
            this.existingCandidate = scan.best().get();
            this.targetPos = this.existingCandidate.pos();
            this.targetSource = this.existingCandidate.source();
            this.phase = ResolveStorageChestTask.Phase.MOVING_TO_EXISTING;
            this.updateProgress("moving to existing chest at " + formatPos(this.targetPos));
            this.report("found existing chest at " + formatPos(this.targetPos), false);
            this.child = new GetToBlockTask(this.targetPos);
            return this.child;
         } else if (!this.params.allowPlacement()) {
            this.terminateFailed("no_storage_chest_found");
            return null;
         } else {
            this.phase = ResolveStorageChestTask.Phase.ENSURING_CHEST_ITEM;
            this.updateProgress("no existing chest; obtaining chest item");
            this.report("no chest nearby; obtaining materials", false);
            return null;
         }
      }
   }

   private Task tickMoveExisting() {
      if (this.targetPos == null) {
         this.terminateFailed("no_target");
         return null;
      } else {
         Optional<String> reject = StorageChestValidation.validateExistingCandidate(
            this.controller, this.targetPos, this.params.searchRadius() * this.params.searchRadius(), this.params.avoidLootChests()
         );
         if (reject.isPresent()) {
            this.existingCandidate = null;
            this.targetPos = null;
            this.phase = ResolveStorageChestTask.Phase.SCANNING_EXISTING;
            this.updateProgress("existing chest became invalid; rescanning");
            return null;
         } else {
            this.recordTarget();
            this.succeed("resolved existing chest at " + formatPos(this.targetPos) + " (" + this.targetSource + ")");
            return null;
         }
      }
   }

   private Task tickEnsureChestItem() {
      if (this.controller.getItemStorage().hasItem(Items.CHEST)) {
         this.phase = ResolveStorageChestTask.Phase.SELECTING_PLACEMENT;
         this.updateProgress("selecting placement site");
         return null;
      } else if (this.child != null) {
         return this.child;
      } else if (this.obtainAttempts >= 4) {
         this.terminateFailed("no_chest_item");
         return null;
      } else {
         this.obtainAttempts++;
         ItemTarget chestTarget = new ItemTarget(Items.CHEST, 1);
         Task macro = CraftMacroTasks.tryCreateMacroTask(this.controller, chestTarget, this.context != null ? this.context.reservations() : null);
         if (macro != null) {
            this.updateProgress("obtaining chest item (craft macro)");
            this.child = macro;
            return this.child;
         } else {
            this.updateProgress("obtaining chest item (catalogue)");
            this.child = TaskCatalogue.getItemTask(Items.CHEST, 1);
            return this.child;
         }
      }
   }

   private void captureObtainFailureReason() {
      if (this.child instanceof CraftMacroResourceTask macro && !this.controller.getItemStorage().hasItem(Items.CHEST)) {
         String reason = macro.getFailureReason();
         if (reason != null && !reason.isBlank()) {
            this.lastObtainFailureReason = reason;
         }
      }
   }

   private boolean isChestObtainChildComplete() {
      return this.child instanceof CraftMacroResourceTask macro
         ? macro.getPhase() == CraftMacroPhase.DONE && this.controller.getItemStorage().hasItem(Items.CHEST)
         : this.child.isFinished() && this.controller.getItemStorage().hasItem(Items.CHEST);
   }

   private Task tickSelectPlacement() {
      if (!this.controller.getItemStorage().hasItem(Items.CHEST)) {
         this.terminateFailed("no_chest_item");
         return null;
      } else {
         Vec3 origin = this.controller.getPlayer().position();
         ChestPlacementSelector.SelectResult result = ChestPlacementSelector.select(this.controller, origin, this.params.placementRadius());
         if (result.best().isEmpty()) {
            this.terminateFailed("no_valid_site");
            return null;
         } else {
            this.placementCandidate = result.best().get();
            this.targetPos = this.placementCandidate.pos();
            this.phase = ResolveStorageChestTask.Phase.PLACING_CHEST;
            this.updateProgress("placing chest at " + formatPos(this.targetPos));
            this.report("placing chest at " + formatPos(this.targetPos), false);
            this.child = new PlaceItemBlockAtPosTask(this.targetPos, Blocks.CHEST);
            return this.child;
         }
      }
   }

   private Task tickPlaceChest() {
      if (this.targetPos == null) {
         this.terminateFailed("placement_failed");
         return null;
      }

      if (!WorldHelper.isBlock(this.controller, this.targetPos, Blocks.CHEST)) {
         if (this.placementAttempts++ < 1) {
            this.phase = ResolveStorageChestTask.Phase.SELECTING_PLACEMENT;
            this.updateProgress("placement retry; reselecting site");
            return null;
         } else {
            this.terminateFailed("placement_unverified");
            return null;
         }
      } else {
         this.targetSource = StorageTargetSource.PLACED_BY_BOT;
         this.phase = ResolveStorageChestTask.Phase.VERIFYING_TARGET;
         this.updateProgress("verifying chest at " + formatPos(this.targetPos));
         this.report("verifying chest placement", false);
         return null;
      }
   }

   private Task tickVerify() {
      if (this.targetPos == null || !StorageChestValidation.isVanillaStorageChestBlock(this.controller.getWorld().getBlockState(this.targetPos).getBlock())) {
         this.terminateFailed("placement_unverified");
         return null;
      } else if (!WorldHelper.canReach(this.controller, this.targetPos)) {
         this.terminateFailed("target_unreachable");
         return null;
      } else {
         this.recordTarget();
         this.succeed("placed chest at " + formatPos(this.targetPos) + " (PLACED_BY_BOT)");
         return null;
      }
   }

   private void recordTarget() {
      if (this.targetPos != null && this.context != null) {
         ServerLevel level = this.controller.getWorld();
         String blockId = ItemHelper.stripItemName(level.getBlockState(this.targetPos).getBlock().asItem());
         long gameTime = level.getGameTime();
         boolean botPlaced = this.targetSource == StorageTargetSource.PLACED_BY_BOT;
         AgenticStorageTarget target = new AgenticStorageTarget(
            this.targetPos, level.dimension().location().toString(), blockId, this.targetSource, botPlaced, gameTime
         );
         this.context.memory().setStorageTarget(target);
         if (this.context.runState() != null) {
            this.context.runState().setStorageTargetSummary(formatPos(this.targetPos) + " " + this.targetSource.name().toLowerCase(Locale.ROOT));
         }

         WaypointOriginEvidence evidence;
         if (botPlaced) {
            evidence = WaypointOriginEvidence.botPlaced(gameTime);
         } else {
            BlockPos canonical = null;
            BlockPos secondary = null;

            try {
               ContainerResolver.Resolution res = ContainerResolver.resolve(level, this.targetPos);
               if (res.ok() && res.resolved() != null) {
                  canonical = res.resolved().canonicalPos();
                  secondary = res.resolved().secondaryPos();
               }
            } catch (Exception var11) {
            }

            if (canonical == null) {
               evidence = new WaypointOriginEvidence(false, WaypointOriginEvidence.Verdict.UNKNOWN, "origin_unverified", gameTime);
            } else {
               evidence = WaypointOriginClassifier.buildEvidence(level, canonical, secondary, gameTime);
            }
         }

         this.context.memory().setWaypointOriginEvidence(evidence);
      }
   }

   private void succeed(String message) {
      this.phase = ResolveStorageChestTask.Phase.DONE;
      this.finished = true;
      this.updateProgress(message);
      this.report("chest ready at " + formatPos(this.targetPos), true);
      this.controller.log("[Agentic] resolve_storage_chest: " + message);
   }

   private void terminateFailed(String reason) {
      if (this.phase != ResolveStorageChestTask.Phase.FAILED) {
         this.phase = ResolveStorageChestTask.Phase.FAILED;
         this.finished = false;
         if (this.child != null && !this.child.stopped()) {
            this.child.stop(this);
         }

         this.captureObtainFailureReason();
         this.child = null;
         boolean obtainFailure = "no_chest_item".equals(reason) || "could_not_obtain_chest_materials".equals(reason);
         String humanReason = obtainFailure && this.lastObtainFailureReason != null && !this.lastObtainFailureReason.isBlank()
            ? humanizeObtainReason(this.lastObtainFailureReason)
            : describeResolveFailure(reason);
         this.updateProgress(humanReason);
         this.report("could not resolve chest: " + humanReason, true);
         this.controller
            .log(
               "[Agentic] resolve_storage_chest failed: "
                  + reason
                  + (this.lastObtainFailureReason != null ? " (obtain: " + this.lastObtainFailureReason + ")" : "")
            );
         if (!this.stopped()) {
            this.stop(this);
         }
      }
   }

   private static String humanizeObtainReason(String macroReason) {
      if (!macroReason.startsWith("Cannot finish crafting") && !macroReason.startsWith("Cannot obtain materials for")) {
         String r = macroReason.toLowerCase(Locale.ROOT);
         return !r.contains("materials unreachable") && !r.contains("collect required materials") && !r.contains("collect materials")
            ? "couldn't craft a chest: " + macroReason
            : "couldn't get enough wood to craft a chest";
      } else {
         return macroReason;
      }
   }

   private static String describeResolveFailure(String reason) {
      return switch (reason) {
         case "no_storage_chest_found" -> "no chest found and placement not allowed";
         case "no_chest_item" -> "could not obtain a chest";
         case "could_not_obtain_chest_materials" -> "could not obtain chest materials";
         case "no_valid_site" -> "no valid chest site nearby";
         case "placement_failed", "placement_unverified" -> "chest placement failed";
         case "target_unreachable" -> "chest site unreachable";
         case "no_target" -> "lost the chest target";
         case "timeout" -> "timed out";
         default -> reason;
      };
   }

   private void updateProgress(String message) {
      if (this.context != null && this.context.runState() != null) {
         this.context.runState().setStorageProgress(message);
      }

      this.setDebugState(this.phase.name());
   }

   private void report(String message, boolean milestone) {
      if (this.context != null) {
         if (this.context.runState() == null || !this.context.runState().isTerminal()) {
            this.context.controller().reportAgenticProgress(message, milestone);
         }
      }
   }

   private double elapsedSec() {
      return (System.currentTimeMillis() - this.startMs) / 1000.0;
   }

   private boolean detectAndRefreshMacroProgress(CraftMacroResourceTask macro) {
      long now = System.currentTimeMillis();
      boolean advanced = false;
      CraftMacroPhase macroPhase = macro.getPhase();
      if (macroPhase != this.lastObservedMacroPhase) {
         this.lastObservedMacroPhase = macroPhase;
         advanced = true;
      }

      int held = this.chestCraftMaterialHeld();
      if (held > this.lastObservedHeld) {
         advanced = true;
      }

      if (held != this.lastObservedHeld) {
         this.lastObservedHeld = held;
      }

      if (advanced) {
         this.lastProgressMs = now;
      }

      return (now - this.lastProgressMs) / 1000.0 < this.params.timeoutSeconds();
   }

   private int chestCraftMaterialHeld() {
      ItemStorageTracker storage = this.controller.getItemStorage();
      return storage.getItemCount(ItemHelper.LOG)
         + storage.getItemCount(ItemHelper.PLANKS)
         + storage.getItemCount(Items.CRAFTING_TABLE)
         + storage.getItemCount(Items.CHEST);
   }

   private static String formatPos(BlockPos pos) {
      return pos.getX() + "," + pos.getY() + "," + pos.getZ();
   }

   private boolean beyondTravelCap(BlockPos target) {
      double cap = this.controller.getModSettings().getAgenticMaxTravelRadius();
      double capSq = cap * cap;
      Vec3 origin = this.controller.getPlayer().position();
      boolean beyond = origin.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > capSq;
      if (beyond) {
         this.controller.log("[Agentic] resolve_storage_chest: ignoring existing chest at " + formatPos(target) + " beyond travel cap " + (int)cap + " blocks");
      }

      return beyond;
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (!this.finished && this.phase != ResolveStorageChestTask.Phase.DONE && this.phase != ResolveStorageChestTask.Phase.FAILED) {
         this.phase = ResolveStorageChestTask.Phase.FAILED;
         this.finished = false;
         if (this.child != null && !this.child.stopped()) {
            this.child.stop(interruptTask);
         }

         this.child = null;
         this.updateProgress("interrupted");
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof ResolveStorageChestTask task && task.params.equals(this.params);
   }

   @Override
   protected String toDebugString() {
      return "ResolveStorageChest";
   }

   private enum Phase {
      SCANNING_EXISTING,
      MOVING_TO_EXISTING,
      ENSURING_CHEST_ITEM,
      SELECTING_PLACEMENT,
      PLACING_CHEST,
      VERIFYING_TARGET,
      DONE,
      FAILED;
   }
}
