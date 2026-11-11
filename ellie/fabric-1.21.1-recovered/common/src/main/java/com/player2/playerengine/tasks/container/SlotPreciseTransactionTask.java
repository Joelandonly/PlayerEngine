package com.player2.playerengine.tasks.container;

import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.containeraccess.ContainerAnimationHelper;
import com.player2.playerengine.containeraccess.ContainerResolver;
import com.player2.playerengine.containeraccess.ContainerScanService;
import com.player2.playerengine.containeraccess.ResolvedContainer;
import com.player2.playerengine.containeraccess.ScanReportFormatter;
import com.player2.playerengine.containeraccess.StorageAccessCode;
import com.player2.playerengine.containeraccess.StorageItemArgs;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.crafting.DescribesProgress;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

public final class SlotPreciseTransactionTask extends Task implements DescribesProgress {
   private static final double ARRIVAL_DISTANCE_BLOCKS = 4.5;
   public static final int SLOT_AUTO = -1;
   public static final int MAX_BOT_SLOT = 35;
   private final ScanReportFormatter.TransferDirection direction;
   private final BlockPos targetPos;
   private final int requestedContainerSlot;
   private final int requestedBotSlot;
   private final int count;
   private SlotPreciseTransactionTask.Phase phase = SlotPreciseTransactionTask.Phase.PRECHECK;
   private boolean finished;
   private SlotPreciseTransactionTask.Outcome outcome;
   private StorageAccessCode code = StorageAccessCode.OK;
   private final List<ScanReportFormatter.EntryFailure> failures = new ArrayList<>();
   private ResolvedContainer resolved;
   private Task navigateChild;
   private long navigateStartMs;
   private int holdTicks;
   private boolean openIssued;
   private boolean closed;
   private boolean cacheRefreshed;
   private int movedCount;
   private String displayId = "";
   private int containerSlotUsed = -1;
   private Integer botSlotUsed;

   public SlotPreciseTransactionTask(ScanReportFormatter.TransferDirection direction, BlockPos targetPos, int containerSlot, int botSlot, int count) {
      this.direction = direction;
      this.targetPos = targetPos;
      this.requestedContainerSlot = containerSlot;
      this.requestedBotSlot = botSlot;
      this.count = count;
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   public SlotPreciseTransactionTask.Outcome outcome() {
      return this.outcome;
   }

   public StorageAccessCode code() {
      return this.code;
   }

   public List<ScanReportFormatter.EntryFailure> failures() {
      return List.copyOf(this.failures);
   }

   public ResolvedContainer resolvedContainer() {
      return this.resolved;
   }

   public int movedCount() {
      return this.movedCount;
   }

   public String displayId() {
      return this.displayId;
   }

   public int containerSlotUsed() {
      return this.containerSlotUsed;
   }

   public Integer botSlotUsed() {
      return this.botSlotUsed;
   }

   @Override
   public String describeProgress() {
      return String.format(
         Locale.ROOT,
         "slot-precise dir=%s target=%d,%d,%d containerSlot=%d botSlot=%d count=%d phase=%s",
         this.direction.name().toLowerCase(Locale.ROOT),
         this.targetPos.getX(),
         this.targetPos.getY(),
         this.targetPos.getZ(),
         this.requestedContainerSlot,
         this.requestedBotSlot,
         this.count,
         this.phase.name().toLowerCase(Locale.ROOT)
      );
   }

   @Override
   protected void onStart() {
      this.navigateStartMs = System.currentTimeMillis();
   }

   @Override
   protected Task onTick() {
      if (this.finished) {
         return null;
      }

      if (this.openIssued && !this.closed) {
         this.holdTicks++;
      }

      if (this.phase == SlotPreciseTransactionTask.Phase.PRECHECK) {
         Vec3 origin = this.controller.getEntity().position();
         double capSq = 13225.0;
         double distSq = origin.distanceToSqr(this.targetPos.getX() + 0.5, this.targetPos.getY() + 0.5, this.targetPos.getZ() + 0.5);
         if (distSq > capSq) {
            this.failSession(
               StorageAccessCode.CONTAINER_TOO_FAR,
               "target "
                  + ContainerResolver.formatPos(this.targetPos)
                  + " is "
                  + (int)Math.sqrt(distSq)
                  + " blocks away, beyond the 115-block storage travel cap"
            );
            return null;
         }

         this.navigateStartMs = System.currentTimeMillis();
         this.phase = SlotPreciseTransactionTask.Phase.NAVIGATE;
      }

      if (this.phase == SlotPreciseTransactionTask.Phase.NAVIGATE) {
         if (!this.targetPos.closerThan(this.controller.getEntity().blockPosition(), 4.5)) {
            if (System.currentTimeMillis() - this.navigateStartMs >= 45000L) {
               this.failSession(StorageAccessCode.CONTAINER_UNREACHABLE, "could not reach " + ContainerResolver.formatPos(this.targetPos) + " within 45s");
               return null;
            }

            this.setDebugState("navigating to " + this.targetPos.toShortString());
            if (this.navigateChild == null) {
               this.navigateChild = new GetToBlockTask(this.targetPos);
            }

            return this.navigateChild;
         }

         this.navigateChild = null;
         this.phase = SlotPreciseTransactionTask.Phase.OPEN;
      }

      if (this.phase == SlotPreciseTransactionTask.Phase.OPEN) {
         ContainerResolver.Resolution resolution = ContainerResolver.resolve(this.controller.getWorld(), this.targetPos);
         if (!resolution.ok()) {
            this.failSession(resolution.code(), resolution.detail());
            return null;
         } else {
            this.resolved = resolution.resolved();
            ContainerAnimationHelper.open(this.controller.getWorld(), this.resolved);
            this.openIssued = true;
            this.holdTicks = 0;
            this.phase = SlotPreciseTransactionTask.Phase.VALIDATE;
            return null;
         }
      } else if (this.phase == SlotPreciseTransactionTask.Phase.VALIDATE) {
         if (!this.containerStillPresent()) {
            this.handleMidSessionRemoval();
            return null;
         }

         if (this.direction == ScanReportFormatter.TransferDirection.WITHDRAW) {
            this.validateAndTransferWithdraw();
         } else {
            this.validateAndTransferDeposit();
         }

         this.phase = SlotPreciseTransactionTask.Phase.HOLD;
         this.setDebugState(this.outcome == SlotPreciseTransactionTask.Outcome.COMPLETED ? "slot transfer done" : "validation failed: " + this.code.token());
         return null;
      } else {
         if (this.phase == SlotPreciseTransactionTask.Phase.HOLD) {
            if (!this.containerStillPresent()) {
               this.handleMidSessionRemoval();
               return null;
            }

            if (this.holdTicks < 20) {
               this.setDebugState("holding container open (" + this.holdTicks + "/20)");
               return null;
            }

            this.phase = SlotPreciseTransactionTask.Phase.CLOSE;
         }

         if (this.phase == SlotPreciseTransactionTask.Phase.CLOSE) {
            this.closeAndRefresh();
            this.finished = true;
         }

         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (!this.finished) {
         if (this.openIssued) {
            this.closeAndRefresh();
         }
      }
   }

   private void failSession(StorageAccessCode failCode, String detail) {
      this.code = failCode;
      this.failures.add(new ScanReportFormatter.EntryFailure(failCode, this.displayId, detail));
      this.outcome = SlotPreciseTransactionTask.Outcome.FAILED;
      this.setDebugState("failed: " + failCode.token());
      if (this.openIssued && !this.closed) {
         this.phase = SlotPreciseTransactionTask.Phase.HOLD;
      } else {
         this.finished = true;
      }
   }

   private void handleMidSessionRemoval() {
      if (this.outcome == null) {
         this.code = StorageAccessCode.CONTAINER_MISSING;
         this.failures
            .add(
               new ScanReportFormatter.EntryFailure(
                  StorageAccessCode.CONTAINER_MISSING, "", "container at " + ContainerResolver.formatPos(this.targetPos) + " was removed mid-transaction"
               )
            );
         this.outcome = SlotPreciseTransactionTask.Outcome.FAILED;
      }

      this.closeAndRefresh();
      this.finished = true;
   }

   private boolean containerStillPresent() {
      if (this.resolved == null) {
         return false;
      }

      ServerLevel level = this.controller.getWorld();
      return !(level.getBlockEntity(this.resolved.canonicalPos()) instanceof Container)
         ? false
         : this.resolved.secondaryPos() == null || level.getBlockEntity(this.resolved.secondaryPos()) instanceof Container;
   }

   private void closeAndRefresh() {
      if (this.resolved != null) {
         if (!this.closed) {
            this.closed = true;
            if (this.containerStillPresent()) {
               ContainerAnimationHelper.close(this.controller.getWorld(), this.resolved);
            }
         }

         if (!this.cacheRefreshed) {
            this.cacheRefreshed = true;
            ContainerScanService.refreshLegacyCache(this.controller, this.resolved.canonicalPos(), this.resolved.secondaryPos());
         }
      }
   }

   private void validateAndTransferWithdraw() {
      Container container = this.resolved.container();
      LivingEntityInventory inv = this.controller.getInventory();
      int totalSlots = this.resolved.totalSlots();
      if (this.requestedContainerSlot >= 0 && this.requestedContainerSlot < totalSlots) {
         ItemStack src = container.getItem(this.requestedContainerSlot);
         if (src.isEmpty()) {
            this.failSession(StorageAccessCode.SLOT_EMPTY, "slot " + this.requestedContainerSlot + " is empty" + this.nonEmptyHint(container));
         } else {
            this.displayId = StorageItemArgs.displayIdFor(src.getItem());
            if (this.count > src.getCount()) {
               this.failSession(
                  StorageAccessCode.INSUFFICIENT_ITEMS,
                  "slot " + this.requestedContainerSlot + " has " + src.getCount() + "x " + this.displayId + ", requested " + this.count
               );
            } else {
               if (this.requestedBotSlot != -1) {
                  if (this.requestedBotSlot < 0 || this.requestedBotSlot > 35) {
                     this.failSession(StorageAccessCode.INVALID_SLOT, "bot inventory has slots 0-35");
                     return;
                  }

                  ItemStack dest = (ItemStack)inv.main.get(this.requestedBotSlot);
                  if (!dest.isEmpty()) {
                     if (!exactSameStack(dest, src)) {
                        this.failSession(
                           StorageAccessCode.SLOT_OCCUPIED,
                           "bot slot " + this.requestedBotSlot + " holds " + dest.getCount() + "x " + StorageItemArgs.displayIdFor(dest.getItem())
                        );
                        return;
                     }

                     int room = Math.min(inv.getMaxStackSize(), dest.getMaxStackSize()) - dest.getCount();
                     if (this.count > room) {
                        this.failSession(
                           StorageAccessCode.SLOT_OCCUPIED,
                           "bot slot " + this.requestedBotSlot + " holds " + dest.getCount() + "x " + this.displayId + ", room for " + Math.max(0, room)
                        );
                        return;
                     }
                  } else {
                     int capacity = Math.min(inv.getMaxStackSize(), src.getMaxStackSize());
                     if (this.count > capacity) {
                        this.failSession(
                           StorageAccessCode.INSUFFICIENT_SPACE, "bot slot " + this.requestedBotSlot + " can hold at most " + capacity + "x " + this.displayId
                        );
                        return;
                     }
                  }
               } else {
                  int capacity = botMainCapacityFor(inv, src);
                  if (this.count > capacity) {
                     this.failSession(
                        StorageAccessCode.INSUFFICIENT_SPACE, "bot inventory can hold only " + capacity + " of " + this.count + "x " + this.displayId
                     );
                     return;
                  }
               }

               ItemStack taken = container.removeItem(this.requestedContainerSlot, this.count);
               int takenCount = taken.getCount();
               if (this.requestedBotSlot != -1) {
                  ItemStack dest = (ItemStack)inv.main.get(this.requestedBotSlot);
                  if (dest.isEmpty()) {
                     inv.main.set(this.requestedBotSlot, taken);
                  } else {
                     dest.grow(takenCount);
                  }

                  this.movedCount = takenCount;
                  this.botSlotUsed = this.requestedBotSlot;
               } else {
                  inv.insertStack(taken);
                  int leftover = taken.getCount();
                  if (leftover > 0) {
                     ItemStack back = container.getItem(this.requestedContainerSlot);
                     if (back.isEmpty()) {
                        container.setItem(this.requestedContainerSlot, taken);
                     } else {
                        back.grow(leftover);
                     }
                  }

                  this.movedCount = takenCount - leftover;
                  this.botSlotUsed = null;
               }

               container.setChanged();
               inv.setChanged();
               this.containerSlotUsed = this.requestedContainerSlot;
               if (this.movedCount == 0) {
                  this.failSession(StorageAccessCode.INSUFFICIENT_SPACE, "bot inventory could not hold any of the " + this.count + "x " + this.displayId);
               } else {
                  this.outcome = SlotPreciseTransactionTask.Outcome.COMPLETED;
                  this.code = StorageAccessCode.OK;
               }
            }
         }
      } else {
         this.failSession(StorageAccessCode.INVALID_SLOT, this.resolved.kind().token() + " has slots 0-" + (totalSlots - 1));
      }
   }

   private void validateAndTransferDeposit() {
      Container container = this.resolved.container();
      LivingEntityInventory inv = this.controller.getInventory();
      int totalSlots = this.resolved.totalSlots();
      if (this.requestedBotSlot >= 0 && this.requestedBotSlot <= 35) {
         ItemStack src = (ItemStack)inv.main.get(this.requestedBotSlot);
         if (src.isEmpty()) {
            this.failSession(StorageAccessCode.SLOT_EMPTY, "bot slot " + this.requestedBotSlot + " is empty");
         } else {
            this.displayId = StorageItemArgs.displayIdFor(src.getItem());
            if (this.count > src.getCount()) {
               this.failSession(
                  StorageAccessCode.INSUFFICIENT_ITEMS,
                  "bot slot " + this.requestedBotSlot + " has " + src.getCount() + "x " + this.displayId + ", requested " + this.count
               );
            } else {
               ItemStack probe = src.copyWithCount(1);
               if (container instanceof WorldlyContainer worldly && !worldly.canPlaceItemThroughFace(0, probe, null)) {
                  this.failSession(StorageAccessCode.ITEM_NOT_ALLOWED, "\"" + this.displayId + "\" is not allowed in this " + this.resolved.kind().token());
               } else {
                  int chosenSlot;
                  if (this.requestedContainerSlot != -1) {
                     if (this.requestedContainerSlot < 0 || this.requestedContainerSlot >= totalSlots) {
                        this.failSession(StorageAccessCode.INVALID_SLOT, this.resolved.kind().token() + " has slots 0-" + (totalSlots - 1));
                        return;
                     }

                     if (!container.canPlaceItem(this.requestedContainerSlot, probe)) {
                        this.failSession(
                           StorageAccessCode.ITEM_NOT_ALLOWED, "slot " + this.requestedContainerSlot + " does not accept \"" + this.displayId + "\""
                        );
                        return;
                     }

                     ItemStack dest = container.getItem(this.requestedContainerSlot);
                     if (!dest.isEmpty()) {
                        if (!exactSameStack(dest, src)) {
                           this.failSession(
                              StorageAccessCode.SLOT_OCCUPIED,
                              "slot " + this.requestedContainerSlot + " holds " + dest.getCount() + "x " + StorageItemArgs.displayIdFor(dest.getItem())
                           );
                           return;
                        }

                        int clamped = Math.min(container.getMaxStackSize(), dest.getMaxStackSize());
                        int room = clamped - dest.getCount();
                        if (this.count > room) {
                           this.failSession(
                              StorageAccessCode.SLOT_OCCUPIED,
                              "slot "
                                 + this.requestedContainerSlot
                                 + " holds "
                                 + dest.getCount()
                                 + "x "
                                 + this.displayId
                                 + ", room for "
                                 + Math.max(0, room)
                           );
                           return;
                        }
                     } else {
                        int capacity = Math.min(container.getMaxStackSize(), src.getMaxStackSize());
                        if (this.count > capacity) {
                           this.failSession(
                              StorageAccessCode.INSUFFICIENT_SPACE,
                              "slot " + this.requestedContainerSlot + " can hold at most " + capacity + "x " + this.displayId
                           );
                           return;
                        }
                     }

                     chosenSlot = this.requestedContainerSlot;
                  } else {
                     chosenSlot = -1;

                     for (int i = 0; i < totalSlots; i++) {
                        ItemStack dest = container.getItem(i);
                        if (!dest.isEmpty() && container.canPlaceItem(i, probe) && exactSameStack(dest, src)) {
                           int clamped = Math.min(container.getMaxStackSize(), dest.getMaxStackSize());
                           if (clamped - dest.getCount() >= this.count) {
                              chosenSlot = i;
                              break;
                           }
                        }
                     }

                     if (chosenSlot == -1) {
                        int clampedNew = Math.min(container.getMaxStackSize(), src.getMaxStackSize());

                        for (int i = 0; i < totalSlots; i++) {
                           if (container.getItem(i).isEmpty() && container.canPlaceItem(i, probe) && clampedNew >= this.count) {
                              chosenSlot = i;
                              break;
                           }
                        }
                     }

                     if (chosenSlot == -1) {
                        this.failSession(StorageAccessCode.INSUFFICIENT_SPACE, "no single container slot can hold " + this.count + "x " + this.displayId);
                        return;
                     }
                  }

                  ItemStack chunk = src.split(this.count);
                  if (src.isEmpty()) {
                     inv.main.set(this.requestedBotSlot, ItemStack.EMPTY);
                  }

                  ItemStack dest = container.getItem(chosenSlot);
                  if (dest.isEmpty()) {
                     container.setItem(chosenSlot, chunk);
                  } else {
                     dest.grow(this.count);
                  }

                  container.setChanged();
                  inv.setChanged();
                  this.movedCount = this.count;
                  this.containerSlotUsed = chosenSlot;
                  this.botSlotUsed = this.requestedBotSlot;
                  this.outcome = SlotPreciseTransactionTask.Outcome.COMPLETED;
                  this.code = StorageAccessCode.OK;
               }
            }
         }
      } else {
         this.failSession(StorageAccessCode.INVALID_SLOT, "bot inventory has slots 0-35");
      }
   }

   private static boolean exactSameStack(ItemStack a, ItemStack b) {
      return ItemStack.matches(a.copyWithCount(1), b.copyWithCount(1));
   }

   private static int botMainCapacityFor(LivingEntityInventory inv, ItemStack src) {
      int capacity = 0;
      int maxStack = Math.min(inv.getMaxStackSize(), src.getMaxStackSize());

      for (int i = 0; i < inv.main.size(); i++) {
         ItemStack s = (ItemStack)inv.main.get(i);
         if (s.isEmpty()) {
            capacity += maxStack;
         } else if (s.is(src.getItem())) {
            capacity += Math.max(0, Math.min(inv.getMaxStackSize(), s.getMaxStackSize()) - s.getCount());
         }
      }

      return capacity;
   }

   private String nonEmptyHint(Container container) {
      List<Integer> nonEmpty = new ArrayList<>();

      for (int i = 0; i < container.getContainerSize(); i++) {
         if (!container.getItem(i).isEmpty()) {
            nonEmpty.add(i);
         }
      }

      if (nonEmpty.isEmpty()) {
         return "; container is empty";
      }

      nonEmpty.sort((a, b) -> Integer.compare(Math.abs(a - this.requestedContainerSlot), Math.abs(b - this.requestedContainerSlot)));
      StringBuilder sb = new StringBuilder("; nearest non-empty slots: ");

      for (int i = 0; i < nonEmpty.size() && i < 3; i++) {
         if (i > 0) {
            sb.append(", ");
         }

         sb.append(nonEmpty.get(i));
      }

      return sb.toString();
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof SlotPreciseTransactionTask task)
         ? false
         : task.direction == this.direction
            && Objects.equals(task.targetPos, this.targetPos)
            && task.requestedContainerSlot == this.requestedContainerSlot
            && task.requestedBotSlot == this.requestedBotSlot
            && task.count == this.count;
   }

   @Override
   protected String toDebugString() {
      return "SlotPreciseTransaction["
         + this.direction.name().toLowerCase(Locale.ROOT)
         + " "
         + this.targetPos.toShortString()
         + " cSlot="
         + this.requestedContainerSlot
         + " bSlot="
         + this.requestedBotSlot
         + " x"
         + this.count
         + "]";
   }

   public enum Outcome {
      COMPLETED,
      FAILED;
   }

   private enum Phase {
      PRECHECK,
      NAVIGATE,
      OPEN,
      VALIDATE,
      TRANSFER,
      HOLD,
      CLOSE;
   }
}
