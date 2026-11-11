package com.player2.playerengine.tasks.cooking;

import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.cooking.resolver.CookingRecipeAccess;
import com.player2.playerengine.tasks.cooking.resolver.CookingRecipeAccessImpl;
import com.player2.playerengine.tasks.crafting.DescribesProgress;
import com.player2.playerengine.tasks.deferred.DeferredDegradation;
import com.player2.playerengine.tasks.deferred.DeferredJobRecord;
import com.player2.playerengine.tasks.deferred.DeferredJobStore;
import com.player2.playerengine.tasks.deferred.DeferredProgress;
import com.player2.playerengine.tasks.movement.GetCloseToBlockTask;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

public final class SmeltDeferredTask extends Task implements DescribesProgress {
   private static final CookingRecipeAccess COOKING_RECIPE_ACCESS = new CookingRecipeAccessImpl();
   private static final double AT_FURNACE_RANGE = 4.5;
   private final SmeltDeferredParams params;
   @Nullable
   private final BlockPos resumeFurnacePos;
   @Nullable
   private final MaterialReservationService ledger;
   @Nullable
   private Item reservedInput;
   private int reservedInputCount = 0;
   private int loadShortfall = 0;
   private SmeltDeferredTask.Phase phase = SmeltDeferredTask.Phase.LOCATE_FURNACE;
   private boolean finished = false;
   private BlockPos furnacePos;
   private CookingRecipeAccess.CookResolution resolution;
   private CookingRecipeAccess.CookKind chosenKind;
   private FuelPlanner.FuelPlan fuelPlan;
   private DeferredCookProcess process;
   private FurnaceStateReader reader;
   private int collectedCount = 0;
   @Nullable
   private SmeltDeferredTask.Outcome outcome;

   public SmeltDeferredTask(SmeltDeferredParams params) {
      this(params, null, null);
   }

   public SmeltDeferredTask(SmeltDeferredParams params, @Nullable MaterialReservationService ledger) {
      this(params, null, ledger);
   }

   public SmeltDeferredTask(SmeltDeferredParams params, @Nullable BlockPos resumeFurnacePos, @Nullable MaterialReservationService ledger) {
      this.params = params;
      this.resumeFurnacePos = resumeFurnacePos != null ? resumeFurnacePos.immutable() : null;
      this.ledger = ledger;
   }

   @Nullable
   public SmeltDeferredTask.Outcome outcome() {
      return this.outcome;
   }

   public int loadShortfall() {
      return this.loadShortfall;
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   @Override
   public String describeProgress() {
      String fp = this.furnacePos != null ? this.furnacePos.toShortString() : "none";
      return String.format(
         Locale.ROOT,
         "phase=%s furnace=%s collected=%d expected=%d%s",
         this.phase.name().toLowerCase(Locale.ROOT),
         fp,
         this.collectedCount,
         this.resolution != null ? this.params.count : 0,
         this.outcome != null ? " outcome=" + this.outcome.kind().name().toLowerCase(Locale.ROOT) : ""
      );
   }

   @Override
   protected void onStart() {
      this.reader = new FurnaceStateReader(this.controller);
      this.setDebugState("Preparing deferred smelt");
   }

   @Override
   protected Task onTick() {
      if (this.finished) {
         return null;
      }

      return switch (this.phase) {
         case LOCATE_FURNACE -> this.tickLocate();
         case GO_TO_FURNACE -> this.tickGoToFurnace();
         case LOAD -> this.tickLoad();
         case WAIT_FOR_COOK -> this.tickWait();
         case COLLECT -> this.tickCollect();
         case DONE, DEGRADED -> null;
      };
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.releaseInputReservation();
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof SmeltDeferredTask t) ? false : t.params.inputItem == this.params.inputItem && t.params.count == this.params.count;
   }

   @Override
   protected String toDebugString() {
      return "Deferred smelt " + this.params.count + " x " + BuiltInRegistries.ITEM.getKey(this.params.inputItem);
   }

   private Task tickLocate() {
      ServerLevel world = this.controller.getWorld();
      if (world == null) {
         return this.terminate(new SmeltDeferredTask.Outcome(SmeltDeferredTask.OutcomeKind.SETUP_FAILED, null, 0, this.params.count, null, 0, null, "no_world"));
      }

      RecipeManager mgr = world.getRecipeManager();
      RegistryAccess registries = world.registryAccess();
      Optional<CookingRecipeAccess.CookResolution> resolved = this.params.preferredKind != null
         ? COOKING_RECIPE_ACCESS.resolve(mgr, this.params.inputItem, this.params.preferredKind, registries)
         : COOKING_RECIPE_ACCESS.resolveAny(mgr, this.params.inputItem, registries);
      if (resolved.isEmpty()) {
         return this.terminate(new SmeltDeferredTask.Outcome(SmeltDeferredTask.OutcomeKind.NO_RECIPE, null, 0, this.params.count, null, 0, null, "no_recipe"));
      }

      this.resolution = resolved.get();
      this.chosenKind = this.resolution.kind();
      if (this.resumeFurnacePos != null) {
         this.furnacePos = this.resumeFurnacePos;
      } else {
         Optional<BlockPos> nearest = this.controller
            .getBlockScanner()
            .getNearestBlock(Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER);
         if (nearest.isEmpty()) {
            return this.terminate(
               new SmeltDeferredTask.Outcome(
                  SmeltDeferredTask.OutcomeKind.SETUP_FAILED,
                  this.resolution.output().getItem(),
                  0,
                  this.params.count,
                  null,
                  0,
                  null,
                  "no_furnace_in_range"
               )
            );
         }

         this.furnacePos = nearest.get().immutable();
      }

      this.setDebugState("Located furnace at " + this.furnacePos.toShortString());
      this.phase = SmeltDeferredTask.Phase.GO_TO_FURNACE;
      return this.tickGoToFurnace();
   }

   private Task tickGoToFurnace() {
      if (!this.isAtFurnace()) {
         this.setDebugState("Going to furnace " + this.furnacePos.toShortString());
         return new GetCloseToBlockTask(this.furnacePos);
      }

      FurnaceStateReader.FurnaceState s = this.reader.read(this.furnacePos);
      if (!s.present()) {
         return this.terminate(
            new SmeltDeferredTask.Outcome(
               SmeltDeferredTask.OutcomeKind.FURNACE_GONE,
               this.resolution.output().getItem(),
               0,
               this.params.count,
               null,
               0,
               this.furnacePos,
               DeferredDegradation.STATION_GONE.label()
            )
         );
      }

      this.phase = SmeltDeferredTask.Phase.LOAD;
      return this.tickLoad();
   }

   private Task tickLoad() {
      ServerLevel world = this.controller.getWorld();
      if (world != null && world.getBlockEntity(this.furnacePos) instanceof AbstractFurnaceBlockEntity furnace) {
         Item var16 = this.params.inputItem;
         int cookTicks = this.resolution.cookTicks();
         int heldInput = this.ledger != null ? this.ledger.free(this.controller, var16) : this.controller.getItemStorage().getItemCount(var16);
         int loadCount = Math.min(this.params.count, heldInput);
         if (loadCount < 1) {
            return this.terminate(
               new SmeltDeferredTask.Outcome(
                  SmeltDeferredTask.OutcomeKind.SETUP_FAILED,
                  this.resolution.output().getItem(),
                  0,
                  this.params.count,
                  null,
                  0,
                  this.furnacePos,
                  "no_input_items"
               )
            );
         }

         Optional<FuelPlanner.FuelPlan> planned = new FuelPlanner().plan(loadCount, cookTicks, this.controller, this.ledger);
         if (planned.isEmpty()) {
            int fuelNeededHint = estimateFuelHint(loadCount, cookTicks);
            return this.terminate(
               new SmeltDeferredTask.Outcome(
                  SmeltDeferredTask.OutcomeKind.NO_FUEL,
                  this.resolution.output().getItem(),
                  0,
                  this.params.count,
                  null,
                  fuelNeededHint,
                  this.furnacePos,
                  DeferredDegradation.OUT_OF_FUEL.label()
               )
            );
         }

         this.fuelPlan = planned.get();
         if (furnace.getItem(0).isEmpty() && furnace.getItem(1).isEmpty()) {
            LivingEntityInventory inv = ((IInventoryProvider)this.controller.getEntity()).getLivingInventory();
            int fuelSlotIdx = inv.getSlotWithStack(new ItemStack(this.fuelPlan.fuelItem()));
            if (fuelSlotIdx != -1) {
               furnace.setItem(1, inv.removeItem(fuelSlotIdx, this.fuelPlan.fuelCount()));
               furnace.setChanged();
            }

            int actualLoadCount = 0;
            int inputSlotIdx = inv.getSlotWithStack(new ItemStack(var16));
            if (inputSlotIdx != -1) {
               ItemStack removedInputStack = inv.removeItem(inputSlotIdx, loadCount);
               actualLoadCount = removedInputStack.getCount();
               furnace.setItem(0, removedInputStack);
               furnace.setChanged();
            }

            if (actualLoadCount < 1) {
               return this.terminate(
                  new SmeltDeferredTask.Outcome(
                     SmeltDeferredTask.OutcomeKind.SETUP_FAILED,
                     this.resolution.output().getItem(),
                     0,
                     this.params.count,
                     this.fuelPlan.fuelItem(),
                     0,
                     this.furnacePos,
                     "no_input_after_fuel"
                  )
               );
            }

            if (this.ledger != null) {
               this.reservedInput = var16;
               this.reservedInputCount = this.ledger.reserve(this.controller, var16, actualLoadCount);
            }

            long startGameTime = world.getGameTime();
            int expectedOutput = actualLoadCount * this.resolution.output().getCount();
            Item outputItem = this.resolution.output().getItem();
            this.process = new DeferredCookProcess(
               this.furnacePos,
               this.chosenKind,
               var16,
               actualLoadCount,
               outputItem,
               expectedOutput,
               cookTicks,
               this.fuelPlan.fuelItem(),
               this.fuelPlan.fuelCount(),
               startGameTime,
               this.params.stallPolls,
               this.params.timeoutSeconds,
               this.controller
            );
            this.persistJobRecord(world, startGameTime, expectedOutput, outputItem, var16, actualLoadCount);
            this.loadShortfall = this.params.count - actualLoadCount;
            this.setDebugState("Loaded furnace; waiting for cook");
            this.phase = SmeltDeferredTask.Phase.WAIT_FOR_COOK;
            return null;
         } else {
            return this.terminate(
               new SmeltDeferredTask.Outcome(
                  SmeltDeferredTask.OutcomeKind.FURNACE_IN_USE,
                  this.resolution.output().getItem(),
                  0,
                  this.params.count,
                  this.fuelPlan.fuelItem(),
                  0,
                  this.furnacePos,
                  "furnace_in_use"
               )
            );
         }
      } else {
         return this.terminate(
            new SmeltDeferredTask.Outcome(
               SmeltDeferredTask.OutcomeKind.FURNACE_GONE,
               this.resolution.output().getItem(),
               0,
               this.params.count,
               null,
               0,
               this.furnacePos,
               DeferredDegradation.STATION_GONE.label()
            )
         );
      }
   }

   private Task tickWait() {
      DeferredProgress progress = this.process.poll(this.controller);
      if (this.process.isComplete(progress)) {
         this.phase = SmeltDeferredTask.Phase.COLLECT;
         return this.tickCollect();
      }

      DeferredDegradation degradation = this.process.degradationOf(progress);
      if (degradation != null) {
         switch (degradation) {
            case STATION_GONE:
               return this.terminate(
                  new SmeltDeferredTask.Outcome(
                     SmeltDeferredTask.OutcomeKind.FURNACE_GONE,
                     this.process.outputItem(),
                     progress.outputCount(),
                     this.process.expectedOutputCount(),
                     this.process.fuelItem(),
                     0,
                     this.furnacePos,
                     degradation.label()
                  )
               );
            case OUT_OF_FUEL:
               return this.tickCollectPartial(progress, degradation);
            case STALLED_TIMEOUT:
               return this.terminate(
                  new SmeltDeferredTask.Outcome(
                     SmeltDeferredTask.OutcomeKind.STALLED_TIMEOUT,
                     this.process.outputItem(),
                     progress.outputCount(),
                     this.process.expectedOutputCount(),
                     this.process.fuelItem(),
                     0,
                     this.furnacePos,
                     degradation.label()
                  )
               );
            case STALLED_RETRYING:
               if (!this.isAtFurnace()) {
                  this.setDebugState("Returning to furnace to resume cook");
                  return new GetCloseToBlockTask(this.furnacePos);
               }

               this.setDebugState("Waiting for chunk to tick");
               return null;
            case TAMPERED_OUTPUT:
         }
      }

      if (!progress.simulated() && !this.isAtFurnace()) {
         this.setDebugState("Returning to furnace to keep it ticking");
         return new GetCloseToBlockTask(this.furnacePos);
      } else {
         this.setDebugState("Waiting for items to smelt");
         return null;
      }
   }

   private Task tickCollect() {
      ServerLevel world = this.controller.getWorld();
      if (!(world != null && world.getBlockEntity(this.furnacePos) instanceof AbstractFurnaceBlockEntity furnace)) {
         return this.terminate(
            new SmeltDeferredTask.Outcome(
               SmeltDeferredTask.OutcomeKind.FURNACE_GONE,
               this.process.outputItem(),
               this.collectedCount,
               this.process.expectedOutputCount(),
               this.process.fuelItem(),
               0,
               this.furnacePos,
               DeferredDegradation.STATION_GONE.label()
            )
         );
      } else if (!this.isAtFurnace()) {
         this.setDebugState("Returning to furnace to collect");
         return new GetCloseToBlockTask(this.furnacePos);
      } else {
         this.collectFromOutputSlot(furnace);
         int expected = this.process.expectedOutputCount();
         SmeltDeferredTask.OutcomeKind kind = this.collectedCount >= expected
            ? SmeltDeferredTask.OutcomeKind.CLEAN_SUCCESS
            : SmeltDeferredTask.OutcomeKind.TAMPERED;
         String reason = kind == SmeltDeferredTask.OutcomeKind.TAMPERED ? DeferredDegradation.TAMPERED_OUTPUT.label() : "clean";
         return this.terminate(
            new SmeltDeferredTask.Outcome(kind, this.process.outputItem(), this.collectedCount, expected, this.process.fuelItem(), 0, this.furnacePos, reason)
         );
      }
   }

   private Task tickCollectPartial(DeferredProgress progress, DeferredDegradation degradation) {
      ServerLevel world = this.controller.getWorld();
      if (world != null && world.getBlockEntity(this.furnacePos) instanceof AbstractFurnaceBlockEntity furnace) {
         if (!this.isAtFurnace()) {
            this.setDebugState("Returning to furnace to collect partial");
            return new GetCloseToBlockTask(this.furnacePos);
         }

         this.collectFromOutputSlot(furnace);
      }

      int expected = this.process.expectedOutputCount();
      return this.collectedCount > 0
         ? this.terminate(
            new SmeltDeferredTask.Outcome(
               SmeltDeferredTask.OutcomeKind.PARTIAL_OUT_OF_FUEL,
               this.process.outputItem(),
               this.collectedCount,
               expected,
               this.process.fuelItem(),
               0,
               this.furnacePos,
               degradation.label()
            )
         )
         : this.terminate(
            new SmeltDeferredTask.Outcome(
               SmeltDeferredTask.OutcomeKind.NO_FUEL, this.process.outputItem(), 0, expected, this.process.fuelItem(), 0, this.furnacePos, degradation.label()
            )
         );
   }

   private void collectFromOutputSlot(AbstractFurnaceBlockEntity furnace) {
      ItemStack output = furnace.getItem(2);
      if (!output.isEmpty()) {
         int before = output.getCount();
         LivingEntityInventory inv = ((IInventoryProvider)this.controller.getEntity()).getLivingInventory();
         if (inv.insertStack(output)) {
            int remaining = output.getCount();
            this.collectedCount += before - remaining;
            furnace.setChanged();
         }
      }
   }

   private boolean isAtFurnace() {
      return this.furnacePos == null
         ? false
         : this.furnacePos
            .closerThan(
               new Vec3i(
                  (int)this.controller.getEntity().position().x,
                  (int)this.controller.getEntity().position().y,
                  (int)this.controller.getEntity().position().z
               ),
               4.5
            );
   }

   private static int estimateFuelHint(int count, int cookTicksPerItem) {
      int total = count * cookTicksPerItem;
      return Math.max(1, (total + 1600 - 1) / 1600);
   }

   private void persistJobRecord(ServerLevel world, long startGameTime, int expectedOutput, Item outputItem, Item input, int loadCount) {
      DeferredJobStore store = DeferredJobStore.get();
      if (store == null) {
         this.controller.logWarning("DeferredJobStore unavailable; smelt job not persisted.");
      } else {
         try {
            String dimensionId = world.dimension().location().toString();
            DeferredJobRecord rec = new DeferredJobRecord();
            rec.id = DeferredJobRecord.idFor(dimensionId, this.furnacePos);
            rec.kind = this.process.kind();
            rec.status = DeferredJobRecord.Status.COOKING;
            rec.pos = new int[]{this.furnacePos.getX(), this.furnacePos.getY(), this.furnacePos.getZ()};
            rec.dimension = dimensionId;
            rec.inputItem = BuiltInRegistries.ITEM.getKey(input).toString();
            rec.outputItem = BuiltInRegistries.ITEM.getKey(outputItem).toString();
            rec.inputCount = loadCount;
            rec.expectedOutputCount = expectedOutput;
            rec.fuelItem = BuiltInRegistries.ITEM.getKey(this.fuelPlan.fuelItem()).toString();
            rec.fuelCount = this.fuelPlan.fuelCount();
            rec.startGameTime = startGameTime;
            rec.etaGameTime = this.process.etaGameTime();
            rec.owningBotUuid = this.controller.getEntity().getUUID().toString();
            rec.degradeReason = "";
            DeferredJobRecord existing = store.get(rec.id);
            if (existing != null) {
               rec.inheritUnknownFieldsFrom(existing);
            }

            store.upsert(rec);
         } catch (Exception e) {
            this.controller.logWarning("Failed to persist deferred smelt job: " + e.getMessage());
         }
      }
   }

   private Task terminate(SmeltDeferredTask.Outcome o) {
      this.outcome = o;
      this.finished = true;
      this.phase = o.kind() == SmeltDeferredTask.OutcomeKind.CLEAN_SUCCESS ? SmeltDeferredTask.Phase.DONE : SmeltDeferredTask.Phase.DEGRADED;
      this.releaseInputReservation();
      this.updateJobStatus(o);
      this.setDebugState("Terminal: " + o.kind().name().toLowerCase(Locale.ROOT));
      return null;
   }

   private void releaseInputReservation() {
      if (this.ledger != null && this.reservedInput != null && this.reservedInputCount > 0) {
         this.ledger.release(this.reservedInput, this.reservedInputCount);
         this.reservedInputCount = 0;
         this.reservedInput = null;
      }
   }

   private void updateJobStatus(SmeltDeferredTask.Outcome o) {
      if (this.furnacePos != null) {
         DeferredJobStore store = DeferredJobStore.get();
         if (store != null) {
            ServerLevel world = this.controller.getWorld();
            if (world != null) {
               try {
                  String id = DeferredJobRecord.idFor(world.dimension().location().toString(), this.furnacePos);
                  DeferredJobRecord rec = store.get(id);
                  if (rec == null) {
                     return;
                  }

                  if (o.kind() == SmeltDeferredTask.OutcomeKind.CLEAN_SUCCESS) {
                     store.delete(id);
                     return;
                  }

                  rec.status = DeferredJobRecord.Status.DEGRADED;
                  rec.degradeReason = o.reasonLabel();
                  store.upsert(rec);
               } catch (Exception e) {
                  this.controller.logWarning("Failed to update deferred smelt job status: " + e.getMessage());
               }
            }
         }
      }
   }

   public record Outcome(
      SmeltDeferredTask.OutcomeKind kind,
      @Nullable Item outputItem,
      int collected,
      int expected,
      @Nullable Item fuelItem,
      int fuelNeeded,
      @Nullable BlockPos furnacePos,
      String reasonLabel
   ) {
      public boolean isCleanSuccess() {
         return this.kind == SmeltDeferredTask.OutcomeKind.CLEAN_SUCCESS;
      }

      public String outputName() {
         return this.outputItem != null ? BuiltInRegistries.ITEM.getKey(this.outputItem).toString() : "?";
      }

      public String fuelName() {
         return this.fuelItem != null ? BuiltInRegistries.ITEM.getKey(this.fuelItem).toString() : "?";
      }
   }

   public enum OutcomeKind {
      CLEAN_SUCCESS,
      PARTIAL_OUT_OF_FUEL,
      NO_FUEL,
      TAMPERED,
      FURNACE_GONE,
      STALLED_RETRYING,
      STALLED_TIMEOUT,
      NO_RECIPE,
      FURNACE_IN_USE,
      SETUP_FAILED;
   }

   private enum Phase {
      LOCATE_FURNACE,
      GO_TO_FURNACE,
      LOAD,
      WAIT_FOR_COOK,
      COLLECT,
      DONE,
      DEGRADED;
   }
}
