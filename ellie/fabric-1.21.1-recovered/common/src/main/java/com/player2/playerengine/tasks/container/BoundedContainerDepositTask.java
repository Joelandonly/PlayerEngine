package com.player2.playerengine.tasks.container;

import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.crafting.DescribesProgress;
import com.player2.playerengine.trackers.storage.ContainerCache;
import com.player2.playerengine.util.ItemTarget;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;

public final class BoundedContainerDepositTask extends Task implements DescribesProgress {
   private static final int STALL_TICKS_LIMIT = 40;
   private final BlockPos containerPos;
   private final double timeoutSeconds;
   private final ItemTarget[] targets;
   private final Item[] targetItems;
   private boolean finished;
   private BoundedContainerDepositTask.Outcome outcome;
   private String message = "";
   private long startMs;
   private Task child;
   private int initialHeldCount;
   private int lastHeldCount = Integer.MAX_VALUE;
   private int noProgressTicks;
   private int depositedCount;

   public BoundedContainerDepositTask(BlockPos containerPos, double timeoutSeconds, ItemTarget... targets) {
      this.containerPos = containerPos;
      this.timeoutSeconds = timeoutSeconds;
      this.targets = targets != null ? targets : new ItemTarget[0];
      this.targetItems = ItemTarget.getMatches(this.targets);
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   public BoundedContainerDepositTask.Outcome outcome() {
      return this.outcome;
   }

   public int depositedCount() {
      return this.depositedCount;
   }

   public int remainingCount() {
      return this.heldCount();
   }

   public String message() {
      return this.message;
   }

   @Override
   public String describeProgress() {
      return String.format(
         Locale.ROOT,
         "bounded-deposit target=%d,%d,%d targets=%d deposited=%d%s",
         this.containerPos.getX(),
         this.containerPos.getY(),
         this.containerPos.getZ(),
         this.targets.length,
         this.depositedCount,
         this.message.isEmpty() ? "" : " " + this.message
      );
   }

   @Override
   protected void onStart() {
      this.startMs = System.currentTimeMillis();
      this.initialHeldCount = this.heldCount();
      this.lastHeldCount = this.initialHeldCount;
   }

   @Override
   protected Task onTick() {
      if (this.finished) {
         return null;
      }

      if (this.elapsedSec() >= this.timeoutSeconds) {
         this.depositedCount = Math.max(0, this.initialHeldCount - this.heldCount());
         this.terminate(BoundedContainerDepositTask.Outcome.TIMEOUT, "timeout");
         return null;
      }

      if (this.targets.length != 0 && this.targetItems.length != 0 && this.initialHeldCount > 0) {
         if (this.child == null) {
            this.child = new StoreInContainerTask(this.containerPos, false, this.targets);
         }

         boolean childActive = this.child.isActive() && !this.child.stopped();
         if (childActive && this.child.isFinished()) {
            this.classifyFinalOutcome();
            return null;
         }

         int held = this.heldCount();
         if (held < this.lastHeldCount) {
            this.depositedCount = this.depositedCount + (this.lastHeldCount - held);
            this.lastHeldCount = held;
            this.noProgressTicks = 0;
         } else if (childActive) {
            this.noProgressTicks++;
         }

         if (held <= 0) {
            this.depositedCount = this.initialHeldCount;
            this.terminate(BoundedContainerDepositTask.Outcome.DEPOSITED, "deposited " + this.depositedCount + " item(s)");
            return null;
         } else if (this.noProgressTicks >= 40 && this.containerIsFull()) {
            this.depositedCount = this.initialHeldCount - held;
            this.terminate(BoundedContainerDepositTask.Outcome.PARTIAL_CONTAINER_FULL, "partial: container_full");
            return null;
         } else {
            this.setDebugState("depositing (" + (this.initialHeldCount - held) + "/" + this.initialHeldCount + ")");
            return this.child;
         }
      } else {
         this.terminate(BoundedContainerDepositTask.Outcome.NOTHING, "nothing_to_deposit");
         return null;
      }
   }

   private void classifyFinalOutcome() {
      int remaining = this.heldCount();
      this.depositedCount = Math.max(0, this.initialHeldCount - remaining);
      if (remaining <= 0) {
         this.terminate(BoundedContainerDepositTask.Outcome.DEPOSITED, "deposited " + this.depositedCount + " item(s)");
      } else if (this.containerIsFull()) {
         this.terminate(BoundedContainerDepositTask.Outcome.PARTIAL_CONTAINER_FULL, "partial: container_full");
      } else {
         this.terminate(BoundedContainerDepositTask.Outcome.PARTIAL_REMAINING, "partial: " + remaining + " remaining");
      }
   }

   private void terminate(BoundedContainerDepositTask.Outcome result, String detail) {
      this.outcome = result;
      this.message = detail;
      this.finished = true;
      if (this.child != null && !this.child.stopped()) {
         this.child.stop(this);
      }

      this.child = null;
      this.setDebugState(detail);
   }

   private int heldCount() {
      return this.targetItems.length == 0 ? 0 : this.controller.getItemStorage().getItemCount(this.targetItems);
   }

   private boolean containerIsFull() {
      Optional<ContainerCache> cache = this.controller.getItemStorage().getContainerAtPosition(this.containerPos);
      return cache.isPresent() && cache.get().isFull();
   }

   private double elapsedSec() {
      return (System.currentTimeMillis() - this.startMs) / 1000.0;
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (!this.finished) {
         if (this.child != null && !this.child.stopped()) {
            this.child.stop(interruptTask);
         }

         this.child = null;
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof BoundedContainerDepositTask task)
         ? false
         : Objects.equals(task.containerPos, this.containerPos) && Arrays.equals(task.targets, this.targets);
   }

   @Override
   protected String toDebugString() {
      return "BoundedContainerDeposit[" + this.containerPos.toShortString() + "]";
   }

   public enum Outcome {
      DEPOSITED,
      PARTIAL_CONTAINER_FULL,
      PARTIAL_REMAINING,
      NOTHING,
      TIMEOUT;
   }
}
