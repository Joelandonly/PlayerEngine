package com.player2.playerengine.tasks.container;

import com.player2.playerengine.agentic.StorageIntelligence;
import com.player2.playerengine.containeraccess.ContainerAnimationHelper;
import com.player2.playerengine.containeraccess.ContainerResolver;
import com.player2.playerengine.containeraccess.ContainerScanService;
import com.player2.playerengine.containeraccess.ContainerSnapshot;
import com.player2.playerengine.containeraccess.ResolvedContainer;
import com.player2.playerengine.containeraccess.ScanMode;
import com.player2.playerengine.containeraccess.StorageAccessCode;
import com.player2.playerengine.containeraccess.StorageItemArgs;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;

public final class ScanContainerTask extends Task {
   private static final double PROXIMITY_BLOCKS = 4.5;
   private final BlockPos containerPos;
   private final ScanMode mode;
   private final List<StorageItemArgs.ItemQuery> targets;
   private ScanContainerTask.Phase phase = ScanContainerTask.Phase.NAVIGATE;
   private long startMs;
   private Task navChild;
   private ResolvedContainer resolved;
   private ContainerSnapshot snapshot;
   private ScanContainerTask.Failure failure;
   private boolean finished;
   private boolean opened;
   private boolean sessionClosed;
   private boolean cacheRefreshed;
   private int holdTicks;
   private boolean ellieAutonomyReported;

   public ScanContainerTask(BlockPos containerPos, ScanMode mode, List<StorageItemArgs.ItemQuery> targets) {
      this.containerPos = containerPos;
      this.mode = mode;
      this.targets = targets == null ? List.of() : List.copyOf(targets);
   }

   public Optional<ContainerSnapshot> result() {
      return this.finished && this.failure == null ? Optional.ofNullable(this.snapshot) : Optional.empty();
   }

   public Optional<ScanContainerTask.Failure> failure() {
      return Optional.ofNullable(this.failure);
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   @Override
   protected void onStart() {
      this.startMs = System.currentTimeMillis();
   }

   @Override
   protected Task onTick() {
      if (this.finished) {
         StorageIntelligence.afterScan(this);
         return null;
      }

      switch (this.phase) {
         case NAVIGATE:
            Task var10000 = this.tickNavigate();
            StorageIntelligence.afterScan(this);
            return var10000;
         case OPEN:
            this.tickOpen();
            StorageIntelligence.afterScan(this);
            return null;
         case HOLD_AND_READ:
            this.tickHoldAndRead();
            StorageIntelligence.afterScan(this);
            return null;
         case CLOSE:
            this.tickClose();
            StorageIntelligence.afterScan(this);
            return null;
         default:
            StorageIntelligence.afterScan(this);
            return null;
      }
   }

   private Task tickNavigate() {
      if (this.withinReach()) {
         ContainerResolver.Resolution resolution = ContainerResolver.resolve(this.controller.getWorld(), this.containerPos);
         if (!resolution.ok()) {
            this.terminate(new ScanContainerTask.Failure(resolution.code(), resolution.detail()));
            return null;
         } else {
            this.resolved = resolution.resolved();
            this.navChild = null;
            this.phase = ScanContainerTask.Phase.OPEN;
            this.setDebugState("opening container at " + ContainerResolver.formatPos(this.containerPos));
            return null;
         }
      } else {
         if (System.currentTimeMillis() - this.startMs >= 45000L) {
            this.terminate(
               new ScanContainerTask.Failure(
                  StorageAccessCode.CONTAINER_UNREACHABLE, "could not reach " + ContainerResolver.formatPos(this.containerPos) + " within 45s"
               )
            );
            return null;
         }

         if (this.navChild == null) {
            this.navChild = new GetToBlockTask(this.containerPos);
         }

         this.setDebugState("navigating to " + ContainerResolver.formatPos(this.containerPos));
         return this.navChild;
      }
   }

   private void tickOpen() {
      if (!this.failIfContainerGone()) {
         ContainerAnimationHelper.open(this.controller.getWorld(), this.resolved);
         this.opened = true;
         this.holdTicks = 0;
         this.phase = ScanContainerTask.Phase.HOLD_AND_READ;
         this.setDebugState("reading container (" + this.mode.token() + ")");
      }
   }

   private void tickHoldAndRead() {
      if (!this.failIfContainerGone()) {
         if (this.snapshot == null) {
            this.snapshot = ContainerScanService.snapshot(this.controller.getWorld(), this.resolved, this.mode, this.targets);
         }

         this.holdTicks++;
         if (this.holdTicks >= 20) {
            this.phase = ScanContainerTask.Phase.CLOSE;
         }
      }
   }

   private void tickClose() {
      if (!this.failIfContainerGone()) {
         this.closeSession();
         this.finished = true;
         this.setDebugState("scan complete");
      }
   }

   private boolean failIfContainerGone() {
      if (this.containerStillLive()) {
         return false;
      }

      this.terminate(
         new ScanContainerTask.Failure(
            StorageAccessCode.CONTAINER_MISSING, "container at " + ContainerResolver.formatPos(this.containerPos) + " was removed mid-scan"
         )
      );
      return true;
   }

   private boolean containerStillLive() {
      ServerLevel level = this.controller.getWorld();
      return !(level.getBlockEntity(this.resolved.canonicalPos()) instanceof Container)
         ? false
         : this.resolved.secondaryPos() == null || level.getBlockEntity(this.resolved.secondaryPos()) instanceof Container;
   }

   private void terminate(ScanContainerTask.Failure reason) {
      this.failure = reason;
      this.closeSession();
      this.navChild = null;
      this.finished = true;
      this.setDebugState(reason.code().token() + ": " + reason.detail());
   }

   private void closeSession() {
      if (this.resolved != null) {
         if (!this.sessionClosed) {
            this.sessionClosed = true;
            if (this.opened && this.containerStillLive()) {
               ContainerAnimationHelper.close(this.controller.getWorld(), this.resolved);
            }
         }

         if (!this.cacheRefreshed) {
            this.cacheRefreshed = true;
            ContainerScanService.refreshLegacyCache(this.controller, this.resolved.canonicalPos(), this.resolved.secondaryPos());
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (this.opened) {
         this.closeSession();
      }

      this.navChild = null;
   }

   private boolean withinReach() {
      return this.containerPos
         .closerThan(
            new Vec3i(
               (int)this.controller.getEntity().position().x,
               (int)this.controller.getEntity().position().y,
               (int)this.controller.getEntity().position().z
            ),
            4.5
         );
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof ScanContainerTask task)
         ? false
         : Objects.equals(task.containerPos, this.containerPos) && task.mode == this.mode && Objects.equals(task.targets, this.targets);
   }

   @Override
   protected String toDebugString() {
      return "ScanContainer[" + this.mode.token() + " @ " + this.containerPos.toShortString() + "]";
   }

   public record Failure(StorageAccessCode code, String detail) {
   }

   private enum Phase {
      NAVIGATE,
      OPEN,
      HOLD_AND_READ,
      CLOSE;
   }
}
