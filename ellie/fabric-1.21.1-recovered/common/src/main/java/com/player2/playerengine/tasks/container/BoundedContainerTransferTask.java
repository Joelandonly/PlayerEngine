package com.player2.playerengine.tasks.container;

import com.player2.playerengine.agentic.StorageIntelligence;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

public final class BoundedContainerTransferTask extends Task implements DescribesProgress {
   private static final double ARRIVAL_DISTANCE_BLOCKS = 4.5;
   private final ScanReportFormatter.TransferDirection direction;
   private final BlockPos targetPos;
   private final List<StorageItemArgs.ItemQuery> entries;
   private final BoundedContainerTransferTask.WithdrawQuantity withdrawQuantity;
   private BoundedContainerTransferTask.Phase phase = BoundedContainerTransferTask.Phase.PRECHECK;
   private boolean finished;
   private BoundedContainerTransferTask.Outcome outcome;
   private StorageAccessCode code = StorageAccessCode.OK;
   private final List<ScanReportFormatter.EntryFailure> failures = new ArrayList<>();
   private final List<ScanReportFormatter.EntryOutcome> entryOutcomes = new ArrayList<>();
   private final List<ScanReportFormatter.EntryFailure> zeroMoveFailures = new ArrayList<>();
   private ResolvedContainer resolved;
   private Task navigateChild;
   private long navigateStartMs;
   private int holdTicks;
   private boolean openIssued;
   private boolean closed;
   private boolean cacheRefreshed;
   private boolean containerChanged;
   private boolean transferDone;
   private boolean ellieAutonomyReported;

   public BoundedContainerTransferTask(ScanReportFormatter.TransferDirection direction, BlockPos targetPos, List<StorageItemArgs.ItemQuery> entries) {
      this(direction, targetPos, entries, BoundedContainerTransferTask.WithdrawQuantity.EXACT);
   }

   private BoundedContainerTransferTask(
      ScanReportFormatter.TransferDirection direction,
      BlockPos targetPos,
      List<StorageItemArgs.ItemQuery> entries,
      BoundedContainerTransferTask.WithdrawQuantity withdrawQuantity
   ) {
      this.direction = Objects.requireNonNull(direction, "direction");
      this.targetPos = Objects.requireNonNull(targetPos, "targetPos").immutable();
      this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
      this.withdrawQuantity = Objects.requireNonNull(withdrawQuantity, "withdrawQuantity");
      if (withdrawQuantity == BoundedContainerTransferTask.WithdrawQuantity.UP_TO
         && (direction != ScanReportFormatter.TransferDirection.WITHDRAW || this.entries.size() != 1 || this.entries.get(0).isAll())) {
         throw new IllegalArgumentException("UP_TO requires one explicit withdrawal entry");
      }
   }

   public static BoundedContainerTransferTask withdrawUpTo(BlockPos targetPos, Item item, int maxCount) {
      Objects.requireNonNull(item, "item");
      if (maxCount >= 1 && maxCount <= 3456) {
         return new BoundedContainerTransferTask(
            ScanReportFormatter.TransferDirection.WITHDRAW,
            targetPos,
            List.of(new StorageItemArgs.ItemQuery(item, StorageItemArgs.displayIdFor(item), maxCount)),
            BoundedContainerTransferTask.WithdrawQuantity.UP_TO
         );
      } else {
         throw new IllegalArgumentException("maxCount must be within 1..3456");
      }
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   public BoundedContainerTransferTask.Outcome outcome() {
      return this.outcome;
   }

   public StorageAccessCode code() {
      return this.code;
   }

   public List<ScanReportFormatter.EntryFailure> failures() {
      return List.copyOf(this.failures);
   }

   public List<ScanReportFormatter.EntryOutcome> entryOutcomes() {
      return List.copyOf(this.entryOutcomes);
   }

   public ResolvedContainer resolvedContainer() {
      return this.resolved;
   }

   @Override
   public String describeProgress() {
      return String.format(
            Locale.ROOT,
            "bounded-transfer dir=%s target=%d,%d,%d entries=%d phase=%s",
            this.direction.name().toLowerCase(Locale.ROOT),
            this.targetPos.getX(),
            this.targetPos.getY(),
            this.targetPos.getZ(),
            this.entries.size(),
            this.phase.name().toLowerCase(Locale.ROOT)
         )
         + (this.withdrawQuantity == BoundedContainerTransferTask.WithdrawQuantity.UP_TO ? " quantity=up_to" : "");
   }

   @Override
   protected void onStart() {
      this.navigateStartMs = System.currentTimeMillis();
   }

   @Override
   protected Task onTick() {
      if (this.finished) {
         StorageIntelligence.afterTransfer(this);
         return null;
      }

      if (this.openIssued && !this.closed) {
         this.holdTicks++;
      }

      if (this.phase == BoundedContainerTransferTask.Phase.PRECHECK) {
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
            StorageIntelligence.afterTransfer(this);
            return null;
         }

         this.navigateStartMs = System.currentTimeMillis();
         this.phase = BoundedContainerTransferTask.Phase.NAVIGATE;
      }

      if (this.phase == BoundedContainerTransferTask.Phase.NAVIGATE) {
         if (!this.targetPos.closerThan(this.controller.getEntity().blockPosition(), 4.5)) {
            if (System.currentTimeMillis() - this.navigateStartMs >= 45000L) {
               this.failSession(StorageAccessCode.CONTAINER_UNREACHABLE, "could not reach " + ContainerResolver.formatPos(this.targetPos) + " within 45s");
               StorageIntelligence.afterTransfer(this);
               return null;
            }

            this.setDebugState("navigating to " + this.targetPos.toShortString());
            if (this.navigateChild == null) {
               this.navigateChild = new GetToBlockTask(this.targetPos);
            }

            Task var10000 = this.navigateChild;
            StorageIntelligence.afterTransfer(this);
            return var10000;
         }

         this.navigateChild = null;
         this.phase = BoundedContainerTransferTask.Phase.OPEN;
      }

      if (this.phase == BoundedContainerTransferTask.Phase.OPEN) {
         ContainerResolver.Resolution resolution = ContainerResolver.resolve(this.controller.getWorld(), this.targetPos);
         if (!resolution.ok()) {
            this.failSession(resolution.code(), resolution.detail());
            StorageIntelligence.afterTransfer(this);
            return null;
         } else {
            this.resolved = resolution.resolved();
            ContainerAnimationHelper.open(this.controller.getWorld(), this.resolved);
            this.openIssued = true;
            this.holdTicks = 0;
            this.phase = BoundedContainerTransferTask.Phase.VALIDATE;
            StorageIntelligence.afterTransfer(this);
            return null;
         }
      } else {
         if (this.phase == BoundedContainerTransferTask.Phase.VALIDATE) {
            if (!this.containerStillPresent()) {
               this.handleMidSessionRemoval();
               StorageIntelligence.afterTransfer(this);
               return null;
            }

            Container container = this.resolved.container();
            LivingEntityInventory inv = this.controller.getInventory();
            List<ScanReportFormatter.EntryFailure> validationFailures = this.direction == ScanReportFormatter.TransferDirection.WITHDRAW
               ? this.validateWithdraw(container, inv)
               : this.validateDeposit(container, inv);
            if (!validationFailures.isEmpty()) {
               this.failures.addAll(validationFailures);
               this.code = validationFailures.get(0).code();
               this.outcome = BoundedContainerTransferTask.Outcome.FAILED;
               this.phase = BoundedContainerTransferTask.Phase.HOLD;
               this.setDebugState("validation failed: " + this.code.token());
               StorageIntelligence.afterTransfer(this);
               return null;
            }

            this.phase = BoundedContainerTransferTask.Phase.TRANSFER;
         }

         if (this.phase == BoundedContainerTransferTask.Phase.TRANSFER) {
            Container container = this.resolved.container();
            LivingEntityInventory inv = this.controller.getInventory();
            if (this.direction == ScanReportFormatter.TransferDirection.WITHDRAW) {
               this.transferWithdraw(container, inv);
            } else {
               this.transferDeposit(container, inv);
            }

            this.transferDone = true;
            this.classifyOutcome();
            this.phase = BoundedContainerTransferTask.Phase.HOLD;
            this.setDebugState("transfer done: " + (this.outcome == null ? "?" : this.outcome.name().toLowerCase(Locale.ROOT)));
            StorageIntelligence.afterTransfer(this);
            return null;
         } else {
            if (this.phase == BoundedContainerTransferTask.Phase.HOLD) {
               if (!this.containerStillPresent()) {
                  this.handleMidSessionRemoval();
                  StorageIntelligence.afterTransfer(this);
                  return null;
               }

               if (this.holdTicks < 20) {
                  this.setDebugState("holding container open (" + this.holdTicks + "/20)");
                  StorageIntelligence.afterTransfer(this);
                  return null;
               }

               this.phase = BoundedContainerTransferTask.Phase.CLOSE;
            }

            if (this.phase == BoundedContainerTransferTask.Phase.CLOSE) {
               this.closeAndRefresh();
               this.finished = true;
            }

            StorageIntelligence.afterTransfer(this);
            return null;
         }
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
      this.failures.add(new ScanReportFormatter.EntryFailure(failCode, "", detail));
      this.outcome = BoundedContainerTransferTask.Outcome.FAILED;
      this.setDebugState("failed: " + failCode.token());
      if (this.openIssued && !this.closed) {
         this.phase = BoundedContainerTransferTask.Phase.HOLD;
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
         this.outcome = BoundedContainerTransferTask.Outcome.FAILED;
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

   private List<ScanReportFormatter.EntryFailure> validateWithdraw(Container container, LivingEntityInventory inv) {
      List<ScanReportFormatter.EntryFailure> fails = new ArrayList<>();
      int emptyBudget = countEmptyMainSlots(inv);

      for (StorageItemArgs.ItemQuery entry : this.entries) {
         int present = container.countItem(entry.item());
         int headroom = botMainHeadroom(inv, entry.item());
         int maxStack = maxStackFor(entry.item());
         if (this.withdrawQuantity == BoundedContainerTransferTask.WithdrawQuantity.UP_TO) {
            int bounded = Math.min(present, entry.countOrAll());
            int fromEmpty = Math.max(0, bounded - headroom);
            int slotsNeeded = (fromEmpty + maxStack - 1) / maxStack;
            emptyBudget -= Math.min(slotsNeeded, emptyBudget);
         } else if (entry.isAll()) {
            int fromEmpty = Math.max(0, present - headroom);
            int slotsNeeded = (fromEmpty + maxStack - 1) / maxStack;
            emptyBudget -= Math.min(slotsNeeded, emptyBudget);
         } else {
            int requested = entry.countOrAll();
            if (present < requested) {
               fails.add(
                  new ScanReportFormatter.EntryFailure(
                     StorageAccessCode.INSUFFICIENT_ITEMS,
                     entry.displayId(),
                     "requested " + requested + "x " + entry.displayId() + ", container has " + present
                  )
               );
            } else {
               int fromEmpty = Math.max(0, requested - headroom);
               int slotsNeeded = (fromEmpty + maxStack - 1) / maxStack;
               if (slotsNeeded > emptyBudget) {
                  int capacity = headroom + emptyBudget * maxStack;
                  fails.add(
                     new ScanReportFormatter.EntryFailure(
                        StorageAccessCode.INSUFFICIENT_SPACE,
                        entry.displayId(),
                        "bot inventory can hold only " + capacity + " of " + requested + "x " + entry.displayId()
                     )
                  );
               } else {
                  emptyBudget -= slotsNeeded;
               }
            }
         }
      }

      return fails;
   }

   private List<ScanReportFormatter.EntryFailure> validateDeposit(Container container, LivingEntityInventory inv) {
      List<ScanReportFormatter.EntryFailure> fails = new ArrayList<>();
      int emptiesConsumed = 0;

      for (StorageItemArgs.ItemQuery entry : this.entries) {
         if (entry.isAll()) {
            ItemStack allProbe = entry.item().getDefaultInstance();
            if (itemAllowedInContainer(container, allProbe)) {
               int botHas = countBotMain(inv, entry.item());
               int clampedAll = Math.min(container.getMaxStackSize(), maxStackFor(entry.item()));
               int headroom = containerHeadroom(container, entry.item(), allProbe);
               int emptiesAvailable = Math.max(0, countEmptyContainerSlots(container, allProbe) - emptiesConsumed);
               int fromEmpty = Math.max(0, botHas - headroom);
               int slotsNeeded = (fromEmpty + clampedAll - 1) / clampedAll;
               emptiesConsumed += Math.min(slotsNeeded, emptiesAvailable);
            }
         } else {
            int requested = entry.countOrAll();
            ItemStack probe = entry.item().getDefaultInstance();
            if (!itemAllowedInContainer(container, probe)) {
               fails.add(
                  new ScanReportFormatter.EntryFailure(
                     StorageAccessCode.ITEM_NOT_ALLOWED,
                     entry.displayId(),
                     "\"" + entry.displayId() + "\" is not allowed in this " + this.resolved.kind().token()
                  )
               );
            } else {
               int botHas = countBotMain(inv, entry.item());
               if (botHas < requested) {
                  fails.add(
                     new ScanReportFormatter.EntryFailure(
                        StorageAccessCode.INSUFFICIENT_ITEMS,
                        entry.displayId(),
                        "requested " + requested + "x " + entry.displayId() + ", bot inventory has " + botHas
                     )
                  );
               } else {
                  int clampedNew = Math.min(container.getMaxStackSize(), maxStackFor(entry.item()));
                  int headroom = containerHeadroom(container, entry.item(), probe);
                  int emptiesAvailable = countEmptyContainerSlots(container, probe) - emptiesConsumed;
                  int fromEmpty = Math.max(0, requested - headroom);
                  int slotsNeeded = (fromEmpty + clampedNew - 1) / clampedNew;
                  if (slotsNeeded > emptiesAvailable) {
                     int capacity = headroom + Math.max(0, emptiesAvailable) * clampedNew;
                     fails.add(
                        new ScanReportFormatter.EntryFailure(
                           StorageAccessCode.INSUFFICIENT_SPACE,
                           entry.displayId(),
                           "container has room for only " + capacity + " of " + requested + "x " + entry.displayId()
                        )
                     );
                  } else {
                     emptiesConsumed += slotsNeeded;
                  }
               }
            }
         }
      }

      return fails;
   }

   private void transferWithdraw(Container container, LivingEntityInventory inv) {
      for (StorageItemArgs.ItemQuery entry : this.entries) {
         Item item = entry.item();
         int present = container.countItem(item);
         boolean all = entry.isAll();
         boolean upTo = this.withdrawQuantity == BoundedContainerTransferTask.WithdrawQuantity.UP_TO;
         int requested = all ? -1 : entry.countOrAll();
         int want = all ? present : (upTo ? Math.min(present, requested) : requested);
         int moved = 0;
         boolean botFull = false;

         for (int i = 0; i < container.getContainerSize() && moved < want; i++) {
            ItemStack slotStack = container.getItem(i);
            if (!slotStack.isEmpty() && slotStack.is(item)) {
               int take = Math.min(slotStack.getCount(), want - moved);
               ItemStack taken = container.removeItem(i, take);
               int takenCount = taken.getCount();
               inv.insertStack(taken);
               int leftover = taken.getCount();
               moved += takenCount - leftover;
               if (leftover > 0) {
                  ItemStack back = container.getItem(i);
                  if (back.isEmpty()) {
                     container.setItem(i, taken);
                  } else {
                     back.grow(leftover);
                  }

                  botFull = true;
                  break;
               }
            }
         }

         container.setChanged();
         inv.setChanged();
         String shortReason = "";
         if (upTo) {
            if (present == 0) {
               shortReason = "none in container";
               this.zeroMoveFailures
                  .add(new ScanReportFormatter.EntryFailure(StorageAccessCode.INSUFFICIENT_ITEMS, entry.displayId(), "container has no " + entry.displayId()));
            } else if (moved < requested) {
               shortReason = moved < want ? "bot inventory full" : "container held only " + present;
               if (moved == 0) {
                  this.zeroMoveFailures
                     .add(
                        new ScanReportFormatter.EntryFailure(
                           StorageAccessCode.INSUFFICIENT_SPACE,
                           entry.displayId(),
                           "bot inventory cannot hold any " + entry.displayId() + " (container has " + present + ")"
                        )
                     );
               }
            }
         } else if (all) {
            if (present == 0) {
               shortReason = "none in container";
               this.zeroMoveFailures
                  .add(new ScanReportFormatter.EntryFailure(StorageAccessCode.INSUFFICIENT_ITEMS, entry.displayId(), "container has no " + entry.displayId()));
            } else if (moved < present) {
               shortReason = "bot inventory full";
               if (moved == 0) {
                  this.zeroMoveFailures
                     .add(
                        new ScanReportFormatter.EntryFailure(
                           StorageAccessCode.INSUFFICIENT_SPACE,
                           entry.displayId(),
                           "bot inventory cannot hold any " + entry.displayId() + " (container has " + present + ")"
                        )
                     );
               }
            }
         } else if (moved < want) {
            this.containerChanged = true;
            shortReason = botFull ? "bot inventory filled mid-transaction" : "container contents changed mid-transaction";
         }

         this.entryOutcomes.add(new ScanReportFormatter.EntryOutcome(entry.displayId(), moved, requested, present, shortReason));
      }
   }

   private void transferDeposit(Container container, LivingEntityInventory inv) {
      label106:
      for (StorageItemArgs.ItemQuery entry : this.entries) {
         Item item = entry.item();
         ItemStack probe = item.getDefaultInstance();
         int botHas = countBotMain(inv, item);
         boolean all = entry.isAll();
         if (itemAllowedInContainer(container, probe)) {
            int want = all ? botHas : entry.countOrAll();
            int clampedNew = Math.min(container.getMaxStackSize(), maxStackFor(item));
            int moved = 0;

            for (int i = 0; i < container.getContainerSize() && moved < want; i++) {
               ItemStack dst = container.getItem(i);
               if (!dst.isEmpty() && dst.is(item) && container.canPlaceItem(i, probe)) {
                  int clamped = Math.min(container.getMaxStackSize(), dst.getMaxStackSize());
                  int room = clamped - dst.getCount();
                  if (room > 0) {
                     int got = takeFromBotMainExact(inv, dst, Math.min(room, want - moved));
                     if (got > 0) {
                        dst.grow(got);
                        moved += got;
                     }
                  }
               }
            }

            for (int i = 0; i < container.getContainerSize() && moved < want; i++) {
               if (container.getItem(i).isEmpty() && container.canPlaceItem(i, probe)) {
                  ItemStack seed = extractSeedFromBot(inv, item, Math.min(clampedNew, want - moved));
                  if (seed.isEmpty()) {
                     container.setChanged();
                     inv.setChanged();
                     String shortReason = "";
                     if (all) {
                        if (botHas == 0) {
                           shortReason = "none carried";
                           this.zeroMoveFailures
                              .add(
                                 new ScanReportFormatter.EntryFailure(
                                    StorageAccessCode.INSUFFICIENT_ITEMS, entry.displayId(), "bot inventory has no " + entry.displayId()
                                 )
                              );
                        } else if (moved < botHas) {
                           shortReason = "container full";
                           if (moved == 0) {
                              this.zeroMoveFailures
                                 .add(
                                    new ScanReportFormatter.EntryFailure(
                                       StorageAccessCode.INSUFFICIENT_SPACE,
                                       entry.displayId(),
                                       "container cannot fit any " + entry.displayId() + " (bot has " + botHas + ")"
                                    )
                                 );
                           }
                        }
                     } else if (moved < want) {
                        this.containerChanged = true;
                        shortReason = "container contents changed mid-transaction";
                     }

                     this.entryOutcomes.add(new ScanReportFormatter.EntryOutcome(entry.displayId(), moved, all ? -1 : want, botHas, shortReason));
                     continue label106;
                  }

                  int placed = seed.getCount();

                  while (placed < clampedNew && moved + placed < want) {
                     int got = takeFromBotMainExact(inv, seed, Math.min(clampedNew - placed, want - moved - placed));
                     if (got <= 0) {
                        break;
                     }

                     seed.grow(got);
                     placed += got;
                  }

                  container.setItem(i, seed);
                  moved += placed;
               }
            }
         }

         this.entryOutcomes.add(new ScanReportFormatter.EntryOutcome(entry.displayId(), 0, -1, botHas, "item not allowed in " + this.resolved.kind().token()));
         this.zeroMoveFailures
            .add(
               new ScanReportFormatter.EntryFailure(
                  StorageAccessCode.ITEM_NOT_ALLOWED, entry.displayId(), "\"" + entry.displayId() + "\" is not allowed in this " + this.resolved.kind().token()
               )
            );
      }
   }

   private void classifyOutcome() {
      int totalMoved = 0;
      boolean anyShort = false;

      for (ScanReportFormatter.EntryOutcome eo : this.entryOutcomes) {
         totalMoved += eo.moved();
         if (!eo.shortReason().isEmpty()) {
            anyShort = true;
         }
      }

      if (this.containerChanged) {
         this.outcome = BoundedContainerTransferTask.Outcome.MOVED_PARTIAL;
         this.code = StorageAccessCode.CONTAINER_CHANGED;
      } else if (totalMoved == 0) {
         this.outcome = BoundedContainerTransferTask.Outcome.NOTHING;
         this.failures.addAll(this.zeroMoveFailures);
         this.code = this.failures.isEmpty() ? StorageAccessCode.INSUFFICIENT_ITEMS : this.failures.get(0).code();
      } else if (anyShort) {
         this.outcome = BoundedContainerTransferTask.Outcome.MOVED_PARTIAL;
         this.code = StorageAccessCode.OK;
      } else {
         this.outcome = BoundedContainerTransferTask.Outcome.MOVED_ALL;
         this.code = StorageAccessCode.OK;
      }
   }

   private static boolean itemAllowedInContainer(Container container, ItemStack probe) {
      return !(container instanceof WorldlyContainer worldly && !worldly.canPlaceItemThroughFace(0, probe, null));
   }

   private static int maxStackFor(Item item) {
      return item.getDefaultInstance().getMaxStackSize();
   }

   private static int countEmptyMainSlots(LivingEntityInventory inv) {
      int empty = 0;

      for (int i = 0; i < inv.main.size(); i++) {
         if (((ItemStack)inv.main.get(i)).isEmpty()) {
            empty++;
         }
      }

      return empty;
   }

   private static int countBotMain(LivingEntityInventory inv, Item item) {
      int count = 0;

      for (int i = 0; i < inv.main.size(); i++) {
         ItemStack s = (ItemStack)inv.main.get(i);
         if (!s.isEmpty() && s.is(item)) {
            count += s.getCount();
         }
      }

      return count;
   }

   static int upToWithdrawAmount(int present, int requested, int capacity) {
      if (present >= 0 && requested >= 0 && capacity >= 0) {
         return Math.min(present, Math.min(requested, capacity));
      } else {
         throw new IllegalArgumentException("withdraw quantities cannot be negative");
      }
   }

   private static int botMainHeadroom(LivingEntityInventory inv, Item item) {
      int headroom = 0;

      for (int i = 0; i < inv.main.size(); i++) {
         ItemStack s = (ItemStack)inv.main.get(i);
         if (!s.isEmpty() && s.is(item)) {
            int cap = Math.min(inv.getMaxStackSize(), s.getMaxStackSize());
            headroom += Math.max(0, cap - s.getCount());
         }
      }

      return headroom;
   }

   private static int containerHeadroom(Container container, Item item, ItemStack probe) {
      int headroom = 0;

      for (int i = 0; i < container.getContainerSize(); i++) {
         ItemStack s = container.getItem(i);
         if (!s.isEmpty() && s.is(item) && container.canPlaceItem(i, probe)) {
            int clamped = Math.min(container.getMaxStackSize(), s.getMaxStackSize());
            headroom += Math.max(0, clamped - s.getCount());
         }
      }

      return headroom;
   }

   private static int countEmptyContainerSlots(Container container, ItemStack probe) {
      int empty = 0;

      for (int i = 0; i < container.getContainerSize(); i++) {
         if (container.getItem(i).isEmpty() && container.canPlaceItem(i, probe)) {
            empty++;
         }
      }

      return empty;
   }

   private static int takeFromBotMainExact(LivingEntityInventory inv, ItemStack template, int n) {
      int taken = 0;

      for (int j = 0; j < inv.main.size() && taken < n; j++) {
         ItemStack s = (ItemStack)inv.main.get(j);
         if (!s.isEmpty() && exactSameStack(s, template)) {
            int t = Math.min(s.getCount(), n - taken);
            s.shrink(t);
            if (s.isEmpty()) {
               inv.main.set(j, ItemStack.EMPTY);
            }

            taken += t;
         }
      }

      return taken;
   }

   private static boolean exactSameStack(ItemStack a, ItemStack b) {
      return ItemStack.matches(a.copyWithCount(1), b.copyWithCount(1));
   }

   private static ItemStack extractSeedFromBot(LivingEntityInventory inv, Item item, int cap) {
      for (int j = 0; j < inv.main.size(); j++) {
         ItemStack s = (ItemStack)inv.main.get(j);
         if (!s.isEmpty() && s.is(item)) {
            ItemStack seed = s.split(Math.min(s.getCount(), cap));
            if (s.isEmpty()) {
               inv.main.set(j, ItemStack.EMPTY);
            }

            return seed;
         }
      }

      return ItemStack.EMPTY;
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof BoundedContainerTransferTask task)
         ? false
         : task.direction == this.direction
            && Objects.equals(task.targetPos, this.targetPos)
            && Objects.equals(task.entries, this.entries)
            && task.withdrawQuantity == this.withdrawQuantity;
   }

   @Override
   protected String toDebugString() {
      return "BoundedContainerTransfer["
         + this.direction.name().toLowerCase(Locale.ROOT)
         + " "
         + this.targetPos.toShortString()
         + (this.withdrawQuantity == BoundedContainerTransferTask.WithdrawQuantity.UP_TO ? " up-to" : "")
         + "]";
   }

   public enum Outcome {
      MOVED_ALL,
      MOVED_PARTIAL,
      NOTHING,
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

   public enum WithdrawQuantity {
      EXACT,
      UP_TO;
   }
}
