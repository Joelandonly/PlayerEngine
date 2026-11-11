package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.agentic.AgenticExecutionContext;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.AgenticStorageTarget;
import com.player2.playerengine.agentic.steps.DepositItemsParams;
import com.player2.playerengine.agentic.storage.StorageChestValidation;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.container.BoundedContainerDepositTask;
import com.player2.playerengine.tasks.crafting.DescribesProgress;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class DepositItemsTask extends Task implements DescribesProgress {
   private static final double REVALIDATE_RADIUS_SQ = 65536.0;
   private final DepositItemsParams params;
   private final AgenticExecutionContext context;
   private DepositItemsTask.Phase phase = DepositItemsTask.Phase.READING_TARGET;
   private boolean finished;
   private long startMs;
   private BoundedContainerDepositTask depositCore;
   private BlockPos targetPos;
   private ItemTarget[] targets = new ItemTarget[0];
   private Item[] targetItems = new Item[0];
   private int initialHeldCount;
   private int depositedCount;
   private String resultDetail = "";

   public DepositItemsTask(DepositItemsParams params, AgenticExecutionContext context) {
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
      return String.format(
         Locale.ROOT,
         "phase=%s target=%s targets=%d deposited=%d%s",
         this.phase.name().toLowerCase(Locale.ROOT),
         target,
         this.targets.length,
         this.depositedCount,
         this.resultDetail.isEmpty() ? "" : " " + this.resultDetail
      );
   }

   @Override
   protected void onStart() {
      this.startMs = System.currentTimeMillis();
      this.updateProgress("reading storage target");
   }

   @Override
   protected Task onTick() {
      if (!this.finished && this.phase != DepositItemsTask.Phase.FAILED) {
         if (this.elapsedSec() >= this.params.timeoutSeconds()) {
            this.terminateFailed("timeout");
            return null;
         }

         return switch (this.phase) {
            case READING_TARGET -> this.tickReadingTarget();
            case REVALIDATING -> this.tickRevalidating();
            case DEPOSITING -> this.tickDepositing();
            case DONE, FAILED -> null;
         };
      } else {
         return null;
      }
   }

   private Task tickReadingTarget() {
      Optional<AgenticStorageTarget> t = this.context != null ? this.context.memory().storageTarget() : Optional.empty();
      if (t.isEmpty()) {
         this.terminateFailed("no_storage_target");
         return null;
      } else {
         AgenticStorageTarget target = t.get();
         String currentDim = this.controller.getWorld().dimension().location().toString();
         if (target.dimension() != null && !target.dimension().equals(currentDim)) {
            this.terminateFailed("target_wrong_dimension");
            return null;
         } else {
            this.targetPos = target.pos();
            this.phase = DepositItemsTask.Phase.REVALIDATING;
            this.updateProgress("revalidating chest at " + formatPos(this.targetPos));
            return null;
         }
      }
   }

   private Task tickRevalidating() {
      if (this.targetPos == null) {
         this.terminateFailed("no_storage_target");
         return null;
      }

      Optional<String> reject = StorageChestValidation.validateExistingCandidate(this.controller, this.targetPos, 65536.0, false);
      if (reject.isPresent()) {
         String reason = reject.get();
         switch (reason) {
            case "not_chest":
            case "chunk_unloaded":
               this.terminateFailed("target_block_changed");
               return null;
            case "unreachable":
            case "cannot_reach":
            case "blocked_top":
               this.terminateFailed("unreachable");
               return null;
            case "wrong_dimension":
               this.terminateFailed("target_wrong_dimension");
               return null;
            case "container_full":
         }
      }

      this.targets = this.buildTargets();
      this.targetItems = ItemTarget.getMatches(this.targets);
      if (this.targets.length != 0 && this.targetItems.length != 0) {
         this.initialHeldCount = this.heldCount();
         if (this.initialHeldCount <= 0) {
            this.succeed("nothing_to_deposit");
            return null;
         } else {
            this.phase = DepositItemsTask.Phase.DEPOSITING;
            this.updateProgress("depositing " + this.targets.length + " item type(s) into " + formatPos(this.targetPos));
            this.report(Component.translatable("message.playerengine.deposit.progress.moving", new Object[]{formatPos(this.targetPos)}), false);
            return null;
         }
      } else {
         this.succeed("nothing_to_deposit");
         return null;
      }
   }

   private Task tickDepositing() {
      if (this.depositCore == null) {
         double remaining = Math.max(0.0, this.params.timeoutSeconds() - this.elapsedSec());
         this.depositCore = new BoundedContainerDepositTask(this.targetPos, remaining, this.targets);
      }

      if (this.depositCore.isFinished()) {
         this.mapCoreOutcome();
         return null;
      } else {
         this.depositedCount = this.depositCore.depositedCount();
         this.updateProgress("depositing (" + (this.initialHeldCount - this.heldCount()) + "/" + this.initialHeldCount + ")");
         this.report(
            Component.translatable(
               "message.playerengine.deposit.progress.depositing", new Object[]{this.initialHeldCount - this.heldCount(), this.initialHeldCount}
            ),
            false
         );
         return this.depositCore;
      }
   }

   private void mapCoreOutcome() {
      this.depositedCount = this.depositCore.depositedCount();
      switch (this.depositCore.outcome()) {
         case DEPOSITED:
            this.succeed("deposited " + this.depositedCount + " item(s)");
            break;
         case PARTIAL_CONTAINER_FULL:
            this.succeed("partial: container_full");
            break;
         case PARTIAL_REMAINING:
            this.succeed("partial: " + this.depositCore.remainingCount() + " remaining");
            break;
         case NOTHING:
            this.succeed("nothing_to_deposit");
            break;
         case TIMEOUT:
            this.terminateFailed("timeout");
      }
   }

   private ItemTarget[] buildTargets() {
      List<ItemTarget> out = new ArrayList<>();
      if (this.params.itemIds() != null && !this.params.itemIds().isEmpty()) {
         for (String id : this.params.itemIds()) {
            Item item = resolveItemById(id);
            if (item != null && item != Items.AIR) {
               int held = this.controller.getItemStorage().getItemCount(item);
               if (held > 0) {
                  out.add(new ItemTarget(item, held));
               }
            }
         }

         return out.toArray(new ItemTarget[0]);
      } else {
         if (!this.params.depositAll()) {
            return new ItemTarget[0];
         }

         Map<Item, Integer> counts = new LinkedHashMap<>();
         LivingEntityInventory inv = this.controller.getInventory();

         for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
               Item item = stack.getItem();
               if (item != Items.AIR && (!this.params.keepTools() || !StorageHelper.isEssentialItem(item))) {
                  counts.merge(item, stack.getCount(), Integer::sum);
               }
            }
         }

         for (Entry<Item, Integer> e : counts.entrySet()) {
            if (e.getValue() > 0) {
               out.add(new ItemTarget(e.getKey(), e.getValue()));
            }
         }

         return out.toArray(new ItemTarget[0]);
      }
   }

   private static Item resolveItemById(String idStr) {
      if (idStr != null && !idStr.isBlank()) {
         String trimmed = idStr.trim().toLowerCase(Locale.ROOT);
         ResourceLocation id = ResourceLocation.tryParse(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
         return id == null ? null : (Item)BuiltInRegistries.ITEM.get(id);
      } else {
         return null;
      }
   }

   private int heldCount() {
      return this.targetItems.length == 0 ? 0 : this.controller.getItemStorage().getItemCount(this.targetItems);
   }

   private void succeed(String message) {
      this.phase = DepositItemsTask.Phase.DONE;
      this.finished = true;
      this.resultDetail = message;
      if (this.depositCore != null && !this.depositCore.stopped()) {
         this.depositCore.stop(this);
      }

      this.depositCore = null;
      this.updateProgress(message);
      if (this.context != null && this.context.runState() != null) {
         if (!message.equals("partial: container_full") && (!message.startsWith("partial: ") || !message.endsWith(" remaining"))) {
            if (message.equals("nothing_to_deposit")) {
               this.context.runState().setDepositDegraded(AgenticRunRegistry.DegradationLevel.SKIPPED, message);
            }
         } else {
            this.context.runState().setDepositDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, message);
         }
      }

      this.report(this.describeDepositOutcome(message), true);
      this.controller.log("[Agentic] deposit_items: " + message);
   }

   private void terminateFailed(String reason) {
      if (this.phase != DepositItemsTask.Phase.FAILED) {
         this.phase = DepositItemsTask.Phase.FAILED;
         this.finished = false;
         this.resultDetail = reason;
         if (this.depositCore != null && !this.depositCore.stopped()) {
            this.depositCore.stop(this);
         }

         this.depositCore = null;
         this.updateProgress(reason);
         this.report(Component.translatable("message.playerengine.deposit.failed", new Object[]{describeDepositFailureComponent(reason)}), true);
         this.controller.log("[Agentic] deposit_items failed: " + reason);
         if (!this.stopped()) {
            this.stop(this);
         }
      }
   }

   private Component describeDepositOutcome(String message) {
      if (message == null) {
         return Component.translatable("message.playerengine.deposit.success.done");
      } else if (message.equals("partial: container_full")) {
         return Component.translatable("message.playerengine.deposit.success.partial_container_full", new Object[]{this.depositedCount});
      } else if (message.startsWith("partial: ") && message.endsWith(" remaining")) {
         String n = message.substring("partial: ".length(), message.length() - " remaining".length());
         return Component.translatable("message.playerengine.deposit.success.partial_remaining", new Object[]{this.depositedCount, n});
      } else if (message.equals("nothing_to_deposit")) {
         return Component.translatable("message.playerengine.deposit.success.nothing_to_deposit");
      } else {
         return message.startsWith("deposited ")
            ? Component.translatable("message.playerengine.deposit.success.deposited", new Object[]{this.depositedCount})
            : Component.literal(message);
      }
   }

   private static Component describeDepositFailureComponent(String reason) {
      return switch (reason) {
         case "no_storage_target" -> Component.translatable("message.playerengine.deposit.fail.no_storage_target");
         case "target_wrong_dimension" -> Component.translatable("message.playerengine.deposit.fail.wrong_dimension");
         case "target_block_changed" -> Component.translatable("message.playerengine.deposit.fail.block_changed");
         case "unreachable" -> Component.translatable("message.playerengine.deposit.fail.unreachable");
         case "container_full" -> Component.translatable("message.playerengine.deposit.fail.container_full");
         case "timeout" -> Component.translatable("message.playerengine.deposit.fail.timeout");
         default -> Component.literal(reason);
      };
   }

   private void updateProgress(String message) {
      if (this.context != null && this.context.runState() != null) {
         this.context.runState().setDepositProgress(this.describeProgress() + " — " + message);
      }

      this.setDebugState(this.phase.name());
   }

   private void report(Component message, boolean milestone) {
      if (this.context != null) {
         if (this.context.runState() == null || !this.context.runState().isTerminal()) {
            this.context.controller().reportAgenticProgress(message, milestone);
         }
      }
   }

   private double elapsedSec() {
      return (System.currentTimeMillis() - this.startMs) / 1000.0;
   }

   private static String formatPos(BlockPos pos) {
      return pos.getX() + "," + pos.getY() + "," + pos.getZ();
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (!this.finished && this.phase != DepositItemsTask.Phase.DONE && this.phase != DepositItemsTask.Phase.FAILED) {
         this.phase = DepositItemsTask.Phase.FAILED;
         this.finished = false;
         if (this.depositCore != null && !this.depositCore.stopped()) {
            this.depositCore.stop(interruptTask);
         }

         this.depositCore = null;
         this.updateProgress("interrupted");
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof DepositItemsTask task && task.params.equals(this.params);
   }

   @Override
   protected String toDebugString() {
      return "DepositItems";
   }

   private enum Phase {
      READING_TARGET,
      REVALIDATING,
      DEPOSITING,
      DONE,
      FAILED;
   }
}
