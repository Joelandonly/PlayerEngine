package com.player2.playerengine.tasks.crafting;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.reliability.AcquisitionTravelSignal;
import com.player2.playerengine.reliability.ProductionHandoff;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.PlaceBlockNearbyTask;
import com.player2.playerengine.tasks.cooking.resolver.CookingRecipeAccessImpl;
import com.player2.playerengine.tasks.crafting.resolver.IngredientInspectorImpl;
import com.player2.playerengine.tasks.crafting.resolver.MaterialResolver;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccessImpl;
import com.player2.playerengine.tasks.crafting.resolver.ResolverResult;
import com.player2.playerengine.tasks.movement.GetWithinRangeOfBlockTask;
import com.player2.playerengine.tasks.resources.MineAndCollectTask;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.RecipeTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.MaterialAvailability;
import com.player2.playerengine.util.time.TimerGame;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class CraftMacroResourceTask extends ResourceTask implements DescribesProgress {
   private CraftMacroPlan plan;
   private CraftMacroPhase phase = CraftMacroPhase.PLAN;
   private BlockPos craftingTablePos;
   private boolean tablePlacedByTask;
   private PlaceBlockNearbyTask pendingTablePlaceTask;
   private final TimerGame craftDelay = new TimerGame(0.5);
   private final TimerGame lookHold = new TimerGame(0.25);
   private boolean delayActive;
   private int inventoryStepIndex;
   private int tableCraftsRemaining;
   private String failureReason;
   private final MaterialResolver resolver = new MaterialResolver(new IngredientInspectorImpl(), new RecipeAccessImpl(), new CookingRecipeAccessImpl());
   private ResolverResult lastResult;
   private final List<MaterialResolver.BudgetTarget> requirements = new ArrayList<>();
   private int tableRequirementAdds = 0;
   private static final int MAX_TABLE_REQUIREMENT_ADDS = 3;
   private String dispatchPinKey;
   private int dispatchPinAbsolute;
   private int heldAtPin;
   private String lastDispatchDiag = "";
   private int lastDeficit = Integer.MAX_VALUE;
   private int noProgressCount = 0;
   private int resolveAttempts = 0;
   private static final int MAX_NO_PROGRESS_RESOLVES = 3;
   private static final int MAX_RESOLVE_ATTEMPTS = 64;
   private int collectStallTicks = 0;
   private int lastCollectHeld = -1;
   private static final int MAX_COLLECT_STALL_TICKS = 300;
   private int collectNoGainTicks = 0;
   private static final int MAX_COLLECT_NO_GAIN_TICKS = 1200;
   private final TimerGame collectSettleTimer = new TimerGame(1.0);
   private String invStallStepSig = null;
   private int invStallOutputHeld = -1;
   private int invStallTicks = 0;
   private static final int MAX_INV_STALL_TICKS = 60;
   private String craftFailStepSig = null;
   private int craftFailOutputHeld = -1;
   private int craftFailAttempts = 0;
   private static final int MAX_CRAFT_FAIL_ATTEMPTS = 4;
   private int tableApproachStallTicks = 0;
   private static final int MAX_TABLE_APPROACH_STALL_TICKS = 300;
   private boolean adoptedForeignTable;
   private final Item pinnedOutputItem;
   private String creationNote;
   private boolean ellieCollectHandoffGranted;
   private int ellieCollectHandoffCount;

   public CraftMacroResourceTask(CraftMacroPlan initialPlan) {
      this(initialPlan, null);
   }

   public CraftMacroResourceTask(CraftMacroPlan initialPlan, @Nullable MaterialReservationService ledger) {
      super(initialPlan.requestedOutput());
      this.plan = initialPlan;
      this.pinnedOutputItem = initialPlan.outputItem();
      this.resolver.setLedger(ledger);
   }

   @Override
   public boolean isFinished() {
      return this.controller != null
         && this.phase == CraftMacroPhase.DONE
         && this.controller.getItemStorage().getItemCount(this.plan.outputItem()) >= this.plan.targetCount();
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
      this.phase = CraftMacroPhase.PLAN;
      this.tableApproachStallTicks = 0;
      this.adoptedForeignTable = false;
      this.replan(mod);
      this.requirements.clear();
      this.requirements.add(new MaterialResolver.BudgetTarget(this.plan.outputItem(), this.plan.targetCount()));
      this.tableRequirementAdds = 0;
      this.dispatchPinKey = null;
      this.dispatchPinAbsolute = 0;
      this.heldAtPin = 0;
      this.lastDispatchDiag = "";
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      Task var10000 = ProductionHandoff.pending(this, mod);
      if (var10000 != null) {
         return var10000;
      }

      if (this.failureReason != null) {
         this.setDebugState(this.failureReason);
         return null;
      }

      if (mod.getItemStorage().getItemCount(this.plan.outputItem()) >= this.plan.targetCount()) {
         this.phase = CraftMacroPhase.DONE;
         return null;
      }

      switch (this.phase) {
         case PLAN:
            this.replan(mod);
            if (this.resolveNow(mod)) {
               return null;
            }

            if (this.lastResult.status() == ResolverResult.Status.READY) {
               this.phase = CraftMacroPhase.DONE;
               return null;
            }

            if (this.resolvedExternals().isEmpty()) {
               this.phase = CraftMacroPhase.CRAFT_2X2_INTERMEDIATES;
               this.inventoryStepIndex = 0;
            } else {
               this.enterCollectPhase(mod);
            }
            break;
         case COLLECT_MISSING_MATERIALS:
            Task collect = this.tickCollectMissingMaterials(mod);
            if (collect != null) {
               return collect;
            }
            break;
         case CRAFT_2X2_INTERMEDIATES:
            if (this.resolveNow(mod)) {
               return null;
            }

            if (!this.resolvedExternals().isEmpty() && !this.externalMaterialsMet(mod)) {
               this.enterCollectPhase(mod);
               return null;
            }

            Task t = this.runNextInventoryStep(mod);
            if (t != null) {
               return t;
            }

            if (!this.isInventoryPhaseComplete(mod)) {
               return null;
            }

            if (this.plan.requiresCraftingTable() && !this.craftMacroTableReachable(mod)) {
               this.phase = CraftMacroPhase.FIND_OR_PLACE_TABLE;
            } else if (this.plan.finalTableRecipe() != null) {
               this.syncCraftingTablePos(mod);
               this.tableCraftsRemaining = this.tableCraftsStillNeeded(mod);
               this.phase = this.craftMacroTableReachable(mod) ? CraftMacroPhase.MOVE_TO_TABLE : CraftMacroPhase.FIND_OR_PLACE_TABLE;
            } else {
               this.phase = CraftMacroPhase.VERIFY_OUTPUT;
            }
            break;
         case FIND_OR_PLACE_TABLE:
            this.adoptPlacedTable(mod);
            this.syncCraftingTablePos(mod);
            if (this.craftMacroTableReachable(mod)) {
               if (this.requirementsContainTable()) {
                  this.collapseTableRequirement("reachable table adopted");
               }

               this.phase = CraftMacroPhase.MOVE_TO_TABLE;
               return null;
            }

            BlockPos ownPlaced = this.ownPlacedTableBlock(mod);
            if (ownPlaced != null) {
               this.craftingTablePos = ownPlaced;
               this.adoptedForeignTable = false;
               this.setDebugState("Walking to placed crafting table " + ownPlaced.toShortString());
               Debug.logInternal(
                  "[[CRAFT-RESOLVER-DIAG]] FIND_OR_PLACE_TABLE: reusing own placed table at " + ownPlaced.toShortString() + " (walk, do not place another)"
               );
               this.phase = CraftMacroPhase.MOVE_TO_TABLE;
               return null;
            }

            BlockPos reusable = CraftingTableLocator.findReusableNearby(mod, mod.getModSettings().getCraftingTableReuseRadius()).orElse(null);
            if (reusable != null) {
               this.craftingTablePos = reusable;
               this.tablePlacedByTask = false;
               this.adoptedForeignTable = true;
               this.tableApproachStallTicks = 0;
               this.setDebugState("Walking to existing crafting table " + reusable.toShortString());
               Debug.logInternal(
                  "[[CRAFT-RESOLVER-DIAG]] FIND_OR_PLACE_TABLE: reusing pre-existing table at "
                     + reusable.toShortString()
                     + " within "
                     + mod.getModSettings().getCraftingTableReuseRadius()
                     + " blocks (walk, do not place another)"
               );
               this.phase = CraftMacroPhase.MOVE_TO_TABLE;
               return null;
            }

            if (mod.getItemStorage().hasItem(Items.CRAFTING_TABLE)) {
               this.setDebugState("Placing crafting table");
               this.tablePlacedByTask = true;
               this.adoptedForeignTable = false;
               Debug.logInternal("[[CRAFT-RESOLVER-DIAG]] FIND_OR_PLACE_TABLE: placing crafting table from inventory");
               if (this.pendingTablePlaceTask == null) {
                  this.pendingTablePlaceTask = new PlaceBlockNearbyTask(Blocks.CRAFTING_TABLE);
               }

               return this.pendingTablePlaceTask;
            }

            if (!this.requirementsContainTable()) {
               if (this.tableRequirementAdds >= 3) {
                  this.terminateMacro(
                     "Cannot finish crafting "
                        + this.outputName()
                        + ": the crafting table was lost repeatedly (placed "
                        + this.tableRequirementAdds
                        + " times); giving up."
                  );
                  return null;
               }

               this.tableRequirementAdds++;
               this.requirements.add(new MaterialResolver.BudgetTarget(Items.CRAFTING_TABLE, 1));
               Debug.logInternal(
                  "[[CRAFT-RESOLVER-DIAG]] REQUIREMENT-ADD crafting_table (add #"
                     + this.tableRequirementAdds
                     + ") requirements="
                     + this.describeRequirements()
                     + " sig="
                     + this.requirementSetSig()
               );
            }

            this.beginIngredientRecovery(mod);
            break;
         case MOVE_TO_TABLE:
            if (!this.ensureCraftingTablePos(mod)) {
               this.phase = CraftMacroPhase.FIND_OR_PLACE_TABLE;
               return null;
            }

            if (!CraftingTableLocator.isReachable(mod, this.craftingTablePos)) {
               this.tableApproachStallTicks++;
               if (this.tableApproachStallTicks >= 300) {
                  if (this.adoptedForeignTable && this.craftingTablePos != null) {
                     Debug.logInternal(
                        "[[CRAFT-RESOLVER-DIAG]] MOVE_TO_TABLE: adopted pre-existing table "
                           + this.craftingTablePos.toShortString()
                           + " unreachable within approach bound -> place own table instead"
                     );
                     mod.getBlockScanner().requestBlockUnreachable(this.craftingTablePos, 0);
                     this.craftingTablePos = null;
                     this.adoptedForeignTable = false;
                     this.tableApproachStallTicks = 0;
                     this.phase = CraftMacroPhase.FIND_OR_PLACE_TABLE;
                     return null;
                  }

                  this.terminateMacro("Cannot finish crafting " + this.outputName() + ": the crafting table is unreachable (approach stalled).");
                  return null;
               }

               this.setDebugState("Moving to table " + this.craftingTablePos.toShortString());
               return new GetWithinRangeOfBlockTask(this.craftingTablePos, 1);
            }

            this.tableApproachStallTicks = 0;
            this.lookHold.setInterval(mod.getModSettings().getCraftTableLookHoldSeconds());
            this.lookHold.reset();
            this.tableCraftsRemaining = this.tableCraftsStillNeeded(mod);
            this.phase = CraftMacroPhase.LOOK_AT_TABLE;
            break;
         case LOOK_AT_TABLE:
            this.lookAtTable(mod);
            if (this.lookHold.elapsed()) {
               this.phase = CraftMacroPhase.CRAFT_3X3_OUTPUT;
               this.delayActive = false;
            }
            break;
         case CRAFT_3X3_OUTPUT:
            if (mod.getItemStorage().getItemCount(this.plan.outputItem()) >= this.plan.targetCount()) {
               this.phase = CraftMacroPhase.VERIFY_OUTPUT;
               return null;
            }

            RecipeTarget tableRecipe = this.plan.finalTableRecipe();
            if (tableRecipe == null) {
               this.phase = CraftMacroPhase.VERIFY_OUTPUT;
               return null;
            }

            if (!this.ensureCraftingTablePos(mod)) {
               this.phase = CraftMacroPhase.FIND_OR_PLACE_TABLE;
               return null;
            }

            if (!CraftingTableLocator.isReachable(mod, this.craftingTablePos)) {
               this.phase = CraftMacroPhase.MOVE_TO_TABLE;
               return null;
            }

            if (!CraftingInventoryOps.hasMaterials(mod, tableRecipe)) {
               this.setDebugState("Missing ingredients for the table craft of " + this.outputName() + "; recovering");
               this.beginIngredientRecovery(mod);
               return null;
            }

            if (this.tableCraftsRemaining <= 0) {
               this.tableCraftsRemaining = this.tableCraftsStillNeeded(mod);
            }

            if (this.tableCraftsRemaining <= 0) {
               this.phase = CraftMacroPhase.VERIFY_OUTPUT;
               return null;
            }

            this.setDebugState("Table crafting " + this.plan.outputItem().getDescription().getString());
            if (!this.delayActive) {
               this.lookAtTable(mod);
               this.craftDelay.setInterval(mod.getModSettings().getCraftDelaySeconds());
               this.craftDelay.reset();
               this.delayActive = true;
               return null;
            }

            if (!this.craftDelay.elapsed()) {
               this.lookAtTable(mod);
               return null;
            }

            this.delayActive = false;
            this.lookAtTable(mod);
            RecipeTarget single = new RecipeTarget(
               tableRecipe.getOutputItem(),
               tableRecipe.getRecipe().outputCount(),
               tableRecipe.getRecipe(),
               tableRecipe.getMcRecipe(),
               tableRecipe.getMcResultStack(),
               tableRecipe.getRegistries()
            );
            if (CraftingInventoryOps.performSingleCraft(mod, single)) {
               this.tableCraftsRemaining--;
            } else {
               this.beginIngredientRecovery(mod);
            }
            break;
         case VERIFY_OUTPUT:
            if (mod.getItemStorage().getItemCount(this.plan.outputItem()) >= this.plan.targetCount()) {
               this.phase = CraftMacroPhase.DONE;
            } else {
               this.resumeAfterIncompleteOutput(mod);
            }
            break;
         case DONE:
            return null;
      }

      return null;
   }

   private void enterCollectPhase(PlayerEngineController mod) {
      this.phase = CraftMacroPhase.COLLECT_MISSING_MATERIALS;
      this.collectStallTicks = 0;
      this.collectNoGainTicks = 0;
      this.lastCollectHeld = -1;
      Debug.logInternal(
         "[[CRAFT-RESOLVER-DIAG]] enterCollectPhase: requirements="
            + this.describeRequirements()
            + " sig="
            + this.requirementSetSig()
            + " externals="
            + this.resolvedExternals()
            + " pin="
            + (this.dispatchPinKey == null ? "none" : this.dispatchPinKey + "@" + this.dispatchPinAbsolute)
      );
      this.collectSettleTimer.setInterval(mod.getModSettings().getMineCollectSettleSeconds());
      this.collectSettleTimer.forceElapse();
   }

   private Task tickCollectMissingMaterials(PlayerEngineController mod) {
      if (this.resolveNow(mod)) {
         return null;
      }

      List<ItemTarget> externals = this.resolvedExternals();
      String toolReason = this.dispatchedGatherToolFailureReason();
      if (toolReason != null) {
         this.terminateMacro(toolReason);
         return null;
      }

      int held = this.totalExternalHeld(mod, externals);
      boolean progressing = held > this.lastCollectHeld || this.matchingDropsInFlight(mod) || this.reachableSourceForAnyExternal(mod, externals);
      if (progressing) {
         this.collectStallTicks = 0;
      } else {
         this.collectStallTicks++;
      }

      boolean strictGain = held > this.lastCollectHeld;
      if (!strictGain && !this.matchingDropsInFlight(mod)) {
         this.collectNoGainTicks++;
      } else {
         this.collectNoGainTicks = 0;
      }

      this.lastCollectHeld = held;
      if (AcquisitionTravelSignal.shouldCredit(this, mod, held)) {
          this.collectStallTicks = 0;
          this.collectNoGainTicks = 0;
       }
      Debug.logInternal(
         "[[CRAFT-RESOLVER-DIAG]] COLLECT stall: held="
            + held
            + " progressing="
            + progressing
            + " collectStallTicks="
            + this.collectStallTicks
            + " collectNoGainTicks="
            + this.collectNoGainTicks
      );
      if (!externals.isEmpty()) {
         ItemTarget target = externals.get(0);
         if (this.collectStallTicks < 300 && this.collectNoGainTicks < 1200) {
            int heldMatching = mod.getItemStorage().getItemCount(target.getMatches());
            int recomputed = heldMatching + target.getTargetCount();
            String key = externalKeyOf(target) + "|" + this.requirementSetSig();
            String pinEvent;
            if (!key.equals(this.dispatchPinKey)) {
               this.dispatchPinKey = key;
               this.dispatchPinAbsolute = recomputed;
               this.heldAtPin = heldMatching;
               pinEvent = "NEW";
            } else if (heldMatching < this.heldAtPin) {
               this.dispatchPinAbsolute = recomputed;
               this.heldAtPin = heldMatching;
               pinEvent = "REPIN(inventory shrank)";
            } else if (recomputed > this.dispatchPinAbsolute) {
               pinEvent = "CLAMPED(DISPATCH-DRIFT)";
            } else {
               if (recomputed < this.dispatchPinAbsolute) {
                  this.dispatchPinAbsolute = recomputed;
                  this.heldAtPin = heldMatching;
               }

               pinEvent = "HELD";
            }

            ItemTarget absoluteTarget = new ItemTarget(target, this.dispatchPinAbsolute);
            String dispatchDiag = "[[CRAFT-RESOLVER-DIAG]] DISPATCH external="
               + target
               + " net="
               + target.getTargetCount()
               + " heldMatching="
               + heldMatching
               + " absolute="
               + this.dispatchPinAbsolute
               + " pin="
               + pinEvent
               + " requirements="
               + this.describeRequirements();
            if (!dispatchDiag.equals(this.lastDispatchDiag)) {
               this.lastDispatchDiag = dispatchDiag;
               Debug.logInternal(dispatchDiag);
            }

            this.setDebugState("Collecting " + absoluteTarget);
            ResourceTask gather = TaskCatalogue.getItemTask(absoluteTarget);
            if (gather == null) {
               return this.advanceAfterCollectFail(target);
            }

            this.collectSettleTimer.reset();
            return gather;
         } else if (this.matchingDropsInFlight(mod)) {
            this.setDebugState("Waiting for just-mined drop before deciding materials unreachable");
            return null;
         } else {
            this.terminateMacro(this.collectShortfallReason(mod, externals));
            return null;
         }
      } else if (!this.collectSettleTimer.elapsed() && this.matchingDropsInFlight(mod)) {
         this.setDebugState("Waiting for just-mined drop to be picked up before crafting");
         return null;
      } else {
         this.phase = CraftMacroPhase.CRAFT_2X2_INTERMEDIATES;
         this.inventoryStepIndex = this.findFirstOpenInventoryStep(mod);
         return null;
      }
   }

   private boolean matchingDropsInFlight(PlayerEngineController mod) {
      Vec3 origin = mod.getPlayer().position();
      double dropRadius = mod.getModSettings().getAggregateCountDropRadius();
      ItemTarget outputTarget = new ItemTarget(this.plan.outputItem(), 1);
      if (!MaterialAvailability.targetsMetSufficiency(mod, origin, dropRadius, outputTarget) && mod.getEntityTracker().itemDropped(outputTarget)) {
         return true;
      }

      for (ItemTarget ext : this.resolvedExternals()) {
         if (!ItemTarget.nullOrEmpty(ext) && mod.getEntityTracker().itemDropped(ext)) {
            return true;
         }
      }

      return false;
   }

   private boolean reachableSourceForAnyExternal(PlayerEngineController mod, List<ItemTarget> externals) {
      Vec3 origin = mod.getPlayer().position();
      double dropRadius = mod.getModSettings().getAggregateCountDropRadius();
      double localSourceBlockRadius = Math.min(mod.getModSettings().getAggregateLocalSourceBlockRadius(), mod.getModSettings().getAgenticMaxTravelRadius());

      for (ItemTarget target : externals) {
         if (!ItemTarget.nullOrEmpty(target)
            && target.getTargetCount() > 0
            && MaterialAvailability.reachableLocalSourceCoversNetDemand(mod, target, origin, dropRadius, localSourceBlockRadius)) {
            return true;
         }
      }

      return false;
   }

   private int totalExternalHeld(PlayerEngineController mod, List<ItemTarget> externals) {
      int sum = 0;

      for (ItemTarget target : externals) {
         sum += mod.getItemStorage().getItemCount(target.getMatches());
      }

      return sum;
   }

   private void terminateMacro(String reason) {
      this.failureReason = reason;
      this.setDebugState(reason);
      this.phase = CraftMacroPhase.DONE;
      this.stop();
   }

   private boolean requirementsContainTable() {
      for (MaterialResolver.BudgetTarget bt : this.requirements) {
         if (bt.item() == Items.CRAFTING_TABLE) {
            return true;
         }
      }

      return false;
   }

   private void collapseSatisfiedRequirements(PlayerEngineController mod) {
      if (this.requirementsContainTable()) {
         if (mod.getItemStorage().hasItem(Items.CRAFTING_TABLE)) {
            this.collapseTableRequirement("table item held");
         } else if (this.ownPlacedTableBlock(mod) != null) {
            this.collapseTableRequirement("own placed table standing");
         }
      }
   }

   private void collapseTableRequirement(String why) {
      if (this.pinnedOutputItem != Items.CRAFTING_TABLE) {
         this.requirements.removeIf(bt -> bt.item() == Items.CRAFTING_TABLE);
         Debug.logInternal(
            "[[CRAFT-RESOLVER-DIAG]] REQUIREMENT-COLLAPSE crafting_table ("
               + why
               + ") -> requirements="
               + this.describeRequirements()
               + " sig="
               + this.requirementSetSig()
         );
      }
   }

   private String requirementSetSig() {
      StringBuilder sb = new StringBuilder();

      for (MaterialResolver.BudgetTarget bt : this.requirements) {
         if (sb.length() > 0) {
            sb.append(',');
         }

         sb.append(ItemHelper.stripItemName(bt.item())).append(':').append(bt.count());
      }

      return sb.toString();
   }

   private String describeRequirements() {
      StringBuilder sb = new StringBuilder("[");

      for (MaterialResolver.BudgetTarget bt : this.requirements) {
         if (sb.length() > 1) {
            sb.append(", ");
         }

         sb.append(ItemHelper.stripItemName(bt.item())).append(" x").append(bt.count());
      }

      return sb.append(']').toString();
   }

   private static String externalKeyOf(ItemTarget target) {
      if (target.isCatalogueItem()) {
         return target.getCatalogueName();
      }

      Item[] matches = target.getMatches();
      String[] names = new String[matches.length];

      for (int i = 0; i < matches.length; i++) {
         names[i] = matches[i] == null ? "null" : ItemHelper.stripItemName(matches[i]);
      }

      Arrays.sort(names);
      return String.join(",", names);
   }

   private static String describeTarget(ItemTarget target) {
      if (target.isCatalogueItem() && target.getCatalogueName() != null) {
         return target.getCatalogueName();
      }

      Item[] m = target.getMatches();
      return m.length > 0 && m[0] != null ? ItemHelper.stripItemName(m[0]) : "unknown";
   }

   private String outputName() {
      return this.plan.outputItem().getDescription().getString();
   }

   private String collectShortfallReason(PlayerEngineController mod, List<ItemTarget> externals) {
      StringBuilder owed = new StringBuilder();

      for (ItemTarget t : externals) {
         if (!ItemTarget.nullOrEmpty(t) && t.getTargetCount() > 0) {
            if (owed.length() > 0) {
               owed.append(", ");
            }

            owed.append(t.getTargetCount()).append(' ').append(describeTarget(t));
         }
      }

      String subject = this.outputName() + (this.requirementsContainTable() ? " and its crafting table" : "");
      return "Cannot finish crafting "
         + subject
         + ": still need "
         + (owed.length() == 0 ? "materials" : owed)
         + " and no reachable source (holding "
         + heldWoodSummary(mod)
         + ").";
   }

   private static String heldWoodSummary(PlayerEngineController mod) {
      StringBuilder sb = new StringBuilder();
      appendHeldCounts(mod, sb, ItemHelper.LOG);
      appendHeldCounts(mod, sb, ItemHelper.PLANKS);
      return sb.length() == 0 ? "nothing" : sb.toString();
   }

   private static void appendHeldCounts(PlayerEngineController mod, StringBuilder sb, Item[] family) {
      for (Item it : family) {
         if (it != null) {
            int held = mod.getItemStorage().getItemCount(it);
            if (held > 0) {
               if (sb.length() > 0) {
                  sb.append(", ");
               }

               sb.append(ItemHelper.stripItemName(it)).append('=').append(held);
            }
         }
      }
   }

   private void beginIngredientRecovery(PlayerEngineController mod) {
      this.replan(mod);
      this.delayActive = false;
      if (!this.resolveNow(mod)) {
         if (!this.resolvedExternals().isEmpty() && !this.externalMaterialsMet(mod)) {
            this.enterCollectPhase(mod);
         } else {
            this.inventoryStepIndex = this.findFirstOpenInventoryStep(mod);
            if (this.inventoryStepIndex < this.inventorySteps(mod).size()) {
               this.phase = CraftMacroPhase.CRAFT_2X2_INTERMEDIATES;
            } else if (!this.resolvedExternals().isEmpty()) {
               this.enterCollectPhase(mod);
            } else if (this.finalTableCraftPending(mod)) {
               this.advanceToFinalTableCraft(mod);
            } else {
               this.terminateMacro("Cannot finish crafting " + this.outputName() + ": no remaining craft step or gatherable material can produce it.");
            }
         }
      }
   }

   private void adoptPlacedTable(PlayerEngineController mod) {
      if (this.pendingTablePlaceTask != null) {
         BlockPos placed = this.pendingTablePlaceTask.getPlaced();
         if (placed != null && mod.getWorld().getBlockState(placed).is(Blocks.CRAFTING_TABLE)) {
            this.craftingTablePos = placed;
            this.tablePlacedByTask = true;
            this.adoptedForeignTable = false;
            this.pendingTablePlaceTask = null;
            Debug.logInternal("[[CRAFT-RESOLVER-DIAG]] adopted just-placed crafting table at " + placed.toShortString());
         }
      }
   }

   private boolean finalTableCraftPending(PlayerEngineController mod) {
      if (this.plan.finalTableRecipe() == null) {
         return false;
      }

      if (mod.getItemStorage().getItemCount(this.plan.outputItem()) >= this.plan.targetCount()) {
         return false;
      }

      boolean tableStepRemains = this.resolvedSteps().stream().anyMatch(s -> s.kind() == CraftMacroStepKind.CRAFT_OUTPUT_IN_TABLE);
      return !tableStepRemains ? false : this.findFirstOpenInventoryStep(mod) >= this.inventorySteps(mod).size() && this.externalMaterialsMet(mod);
   }

   private void advanceToFinalTableCraft(PlayerEngineController mod) {
      this.adoptPlacedTable(mod);
      this.syncCraftingTablePos(mod);
      this.tableCraftsRemaining = this.tableCraftsStillNeeded(mod);
      boolean reachable = this.craftMacroTableReachable(mod);
      this.phase = reachable ? CraftMacroPhase.MOVE_TO_TABLE : CraftMacroPhase.FIND_OR_PLACE_TABLE;
      Debug.logInternal(
         "[[CRAFT-RESOLVER-DIAG]] advanceToFinalTableCraft: output="
            + this.plan.outputItem().getDescription().getString()
            + " tableReachable="
            + reachable
            + " -> phase="
            + this.phase
            + " craftsRemaining="
            + this.tableCraftsRemaining
      );
   }

   private boolean tableCraftInProgress() {
      boolean tablePhase = this.phase == CraftMacroPhase.FIND_OR_PLACE_TABLE
         || this.phase == CraftMacroPhase.MOVE_TO_TABLE
         || this.phase == CraftMacroPhase.LOOK_AT_TABLE
         || this.phase == CraftMacroPhase.CRAFT_3X3_OUTPUT;
      if (!tablePhase) {
         return false;
      } else {
         boolean tableStepRemains = this.resolvedSteps().stream().anyMatch(s -> s.kind() == CraftMacroStepKind.CRAFT_OUTPUT_IN_TABLE);
         if (!tableStepRemains) {
            return false;
         } else {
            return this.delayActive ? true : this.tableApproachStallTicks < 300;
         }
      }
   }

   private void syncCraftingTablePos(PlayerEngineController mod) {
      if (this.craftingTablePos == null
         || !mod.getWorld().getBlockState(this.craftingTablePos).is(Blocks.CRAFTING_TABLE)
         || !CraftingTableLocator.isReachable(mod, this.craftingTablePos)) {
         CraftingTableLocator.findReachable(mod, this.craftingTablePos).filter(t -> CraftingTableLocator.isReachable(mod, t.pos())).ifPresent(t -> {
            this.craftingTablePos = t.pos();
            this.tablePlacedByTask = t.placedByThisTask();
         });
      }
   }

   private boolean ensureCraftingTablePos(PlayerEngineController mod) {
      this.syncCraftingTablePos(mod);
      return this.craftingTablePos != null && mod.getWorld().getBlockState(this.craftingTablePos).is(Blocks.CRAFTING_TABLE);
   }

   private void lookAtTable(PlayerEngineController mod) {
      if (this.craftingTablePos != null) {
         LookHelper.lookAt(mod, this.craftingTablePos.getCenter());
      }
   }

   private int tableCraftsStillNeeded(PlayerEngineController mod) {
      RecipeTarget tableRecipe = this.plan.finalTableRecipe();
      if (tableRecipe == null) {
         return 0;
      }

      int outHave = mod.getItemStorage().getItemCount(this.plan.outputItem());
      int outNeed = this.plan.targetCount() - outHave;
      return outNeed <= 0 ? 0 : (int)Math.ceil((double)outNeed / tableRecipe.getRecipe().outputCount());
   }

   private void resumeAfterIncompleteOutput(PlayerEngineController mod) {
      this.beginIngredientRecovery(mod);
   }

   private boolean externalMaterialsMet(PlayerEngineController mod) {
      return this.resolvedExternals().isEmpty();
   }

   private List<CraftMacroStep> inventorySteps(PlayerEngineController mod) {
      return this.resolvedSteps()
         .stream()
         .filter(s -> s.kind() != CraftMacroStepKind.CRAFT_OUTPUT_IN_TABLE)
         .filter(s -> !this.shouldSkipInventoryStep(mod, s))
         .toList();
   }

   private int findFirstOpenInventoryStep(PlayerEngineController mod) {
      List<CraftMacroStep> steps = this.inventorySteps(mod);

      for (int i = 0; i < steps.size(); i++) {
         if (!this.inventoryStepSatisfied(mod, steps.get(i))) {
            return i;
         }
      }

      return steps.size();
   }

   private boolean needsMoreInventorySteps(PlayerEngineController mod) {
      return this.findFirstOpenInventoryStep(mod) < this.inventorySteps(mod).size();
   }

   private boolean inventoryStepStuck(PlayerEngineController mod, boolean acquisitionInFlight) {
      List<CraftMacroStep> steps = this.inventorySteps(mod);
      int open = this.findFirstOpenInventoryStep(mod);
      if (open >= steps.size()) {
         this.invStallStepSig = null;
         this.invStallOutputHeld = -1;
         this.invStallTicks = 0;
         return false;
      } else {
         CraftMacroStep step = steps.get(open);
         String sig = step.kind() + ":" + step.recipeTarget().getOutputItem().getDescriptionId();
         int held = this.inventoryStepOutputHeld(mod, step);
         if (this.delayActive || acquisitionInFlight) {
            this.invStallStepSig = sig;
            this.invStallOutputHeld = held;
            this.invStallTicks = 0;
            return false;
         } else if (sig.equals(this.invStallStepSig) && held <= this.invStallOutputHeld) {
            this.invStallTicks++;
            return this.invStallTicks >= 60;
         } else {
            this.invStallStepSig = sig;
            this.invStallOutputHeld = held;
            this.invStallTicks = 0;
            return false;
         }
      }
   }

   private int inventoryStepOutputHeld(PlayerEngineController mod, CraftMacroStep step) {
      return step.outputMatches() != null && step.outputMatches().length > 0
         ? mod.getItemStorage().getItemCount(step.outputMatches())
         : mod.getItemStorage().getItemCount(step.recipeTarget().getOutputItem());
   }

   private boolean isInventoryPhaseComplete(PlayerEngineController mod) {
      return this.delayActive ? false : !this.needsMoreInventorySteps(mod);
   }

   private boolean craftMacroTableReachable(PlayerEngineController mod) {
      return this.craftingTablePos != null && CraftingTableLocator.isReachable(mod, this.craftingTablePos)
         ? true
         : CraftingTableLocator.findReachable(mod, this.craftingTablePos).filter(t -> CraftingTableLocator.isReachable(mod, t.pos())).isPresent();
   }

   private BlockPos ownPlacedTableBlock(PlayerEngineController mod) {
      return this.tablePlacedByTask && this.craftingTablePos != null && mod.getWorld().getBlockState(this.craftingTablePos).is(Blocks.CRAFTING_TABLE)
         ? this.craftingTablePos
         : null;
   }

   private boolean shouldSkipInventoryStep(PlayerEngineController mod, CraftMacroStep step) {
      return this.pinnedOutputItem == Items.CRAFTING_TABLE
         ? false
         : step.kind() == CraftMacroStepKind.CRAFT_CRAFTING_TABLE_IN_INVENTORY && this.craftMacroTableReachable(mod);
   }

   private Task advanceAfterCollectFail(ItemTarget undispatchable) {
      this.terminateMacro(
         "Cannot finish crafting "
            + this.outputName()
            + ": no gather task can obtain "
            + undispatchable.getTargetCount()
            + " "
            + describeTarget(undispatchable)
            + "."
      );
      return null;
   }

   private Task runNextInventoryStep(PlayerEngineController mod) {
      List<CraftMacroStep> steps = this.inventorySteps(mod);

      for (this.inventoryStepIndex = 0; this.inventoryStepIndex < steps.size(); this.delayActive = false) {
         CraftMacroStep step = steps.get(this.inventoryStepIndex);
         if (!this.inventoryStepSatisfied(mod, step)) {
            this.setDebugState("Crafting " + step.debugLabel());
            if (!this.delayActive) {
               this.craftDelay.setInterval(mod.getModSettings().getCraftDelaySeconds());
               this.craftDelay.reset();
               this.delayActive = true;
               return null;
            }

            if (!this.craftDelay.elapsed()) {
               return null;
            }

            this.delayActive = false;
            int completed = CraftingInventoryOps.performCrafts(mod, step.recipeTarget(), step.craftsNeeded());
            if (completed > 0 || this.inventoryStepSatisfied(mod, step)) {
               this.resetCraftFailTracker();
               this.inventoryStepIndex++;
            } else if (this.recordCraftFailAndShouldTerminate(mod, step)) {
               this.terminateMacro(this.unsatisfiableStepReason(step));
            } else {
               this.beginIngredientRecovery(mod);
            }

            return null;
         }

         this.inventoryStepIndex++;
      }

      return null;
   }

   private boolean recordCraftFailAndShouldTerminate(PlayerEngineController mod, CraftMacroStep step) {
      String sig = step.kind() + ":" + step.recipeTarget().getOutputItem().getDescriptionId();
      int held = this.inventoryStepOutputHeld(mod, step);
      if (sig.equals(this.craftFailStepSig) && held <= this.craftFailOutputHeld) {
         this.craftFailAttempts++;
         return this.craftFailAttempts >= 4;
      } else {
         this.craftFailStepSig = sig;
         this.craftFailOutputHeld = held;
         this.craftFailAttempts = 1;
         return false;
      }
   }

   private void resetCraftFailTracker() {
      this.craftFailStepSig = null;
      this.craftFailOutputHeld = -1;
      this.craftFailAttempts = 0;
   }

   private String unsatisfiableStepReason(CraftMacroStep step) {
      String output = step.recipeTarget().getOutputItem().getDescription().getString();
      return "Cannot finish crafting " + this.outputName() + ": unable to craft " + output + " (required ingredients are not obtainable).";
   }

   private boolean inventoryStepSatisfied(PlayerEngineController mod, CraftMacroStep step) {
      if (step.kind() == CraftMacroStepKind.CRAFT_CRAFTING_TABLE_IN_INVENTORY && this.pinnedOutputItem != Items.CRAFTING_TABLE) {
         if (this.craftMacroTableReachable(mod)) {
            return true;
         }

         if (mod.getItemStorage().getItemCount(Items.CRAFTING_TABLE) >= 1) {
            return true;
         }
      }

      int need = step.recipeTarget().getTargetCount();
      if (step.outputMatches() != null && step.outputMatches().length > 0) {
         return mod.getItemStorage().getItemCount(step.outputMatches()) >= need;
      }

      Item output = step.recipeTarget().getOutputItem();
      return mod.getItemStorage().getItemCount(output) >= need;
   }

   private void replan(PlayerEngineController mod) {
      ItemTarget request = this.plan.requestedOutput();
      if (this.pinnedOutputItem != null && request.getMatches().length != 1) {
         request = new ItemTarget(this.pinnedOutputItem, request.getTargetCount());
      }

      CraftMacroPlanner.plan(mod, request).ifPresent(p -> this.plan = p);
   }

   private boolean resolveNow(PlayerEngineController mod) {
      this.collapseSatisfiedRequirements(mod);
      this.lastResult = this.resolver.resolveBudget(mod, this.requirements);
      this.resolveAttempts++;
      if (this.lastResult.status() == ResolverResult.Status.UNOBTAINABLE) {
         this.terminateMacro(this.meaningfulReason(this.lastResult));
         return true;
      }

      if (this.ellieCollectionHandoff()) {
         return false;
      }

      int deficit = this.lastResult.remainingDeficit();
      boolean acquisitionInFlight = this.phase == CraftMacroPhase.COLLECT_MISSING_MATERIALS && !this.resolvedExternals().isEmpty();
      List<ItemTarget> owedExternals = this.resolvedExternals();
      boolean obtainableGatherOwed = !owedExternals.isEmpty() && (this.reachableSourceForAnyExternal(mod, owedExternals) || this.matchingDropsInFlight(mod));
      boolean invStuck = this.inventoryStepStuck(mod, acquisitionInFlight);
      boolean tableCraftInProgress = this.tableCraftInProgress();
      boolean craftOrGatherPending = this.delayActive
         || acquisitionInFlight
         || obtainableGatherOwed
         || tableCraftInProgress
         || this.needsMoreInventorySteps(mod) && !invStuck;
      if (deficit < this.lastDeficit) {
         this.noProgressCount = 0;
      } else if (deficit == this.lastDeficit && !craftOrGatherPending) {
         this.noProgressCount++;
      }

      this.lastDeficit = deficit;
      if (this.noProgressCount >= 3 && !craftOrGatherPending) {
         this.terminateMacro(this.meaningfulReason(this.lastResult));
         return true;
      } else if (this.resolveAttempts >= 64 && this.noProgressCount > 0 && !craftOrGatherPending) {
         this.terminateMacro(this.meaningfulReason(this.lastResult));
         return true;
      } else {
         return false;
      }
   }

   private String meaningfulReason(ResolverResult result) {
      return result != null && result.failureReason() != null && !result.failureReason().isBlank()
         ? result.failureReason()
         : "Cannot finish crafting " + this.outputName() + ": no further progress toward the craft from current materials.";
   }

   private List<CraftMacroStep> resolvedSteps() {
      return this.lastResult == null ? List.of() : this.lastResult.remainingSteps();
   }

   private List<ItemTarget> resolvedExternals() {
      return this.lastResult == null ? List.of() : this.lastResult.externalNeeded();
   }

   private String dispatchedGatherToolFailureReason() {
      String[] holder = new String[1];
      this.thisOrChildSatisfies(t -> {
         if (t instanceof MineAndCollectTask gather && gather.toolAcquisitionFailed()) {
            holder[0] = gather.toolAcquisitionMachineReason().orElse("could_not_acquire_tool");
            return true;
         } else {
            return false;
         }
      });
      return holder[0];
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
      this.craftingTablePos = null;
      this.pendingTablePlaceTask = null;
      this.tablePlacedByTask = false;
      this.tableApproachStallTicks = 0;
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CraftMacroResourceTask task && task.plan.outputItem().equals(this.plan.outputItem());
   }

   @Override
   protected String toDebugStringName() {
      return "CraftMacro: " + this.plan.outputItem().getDescription().getString() + " (" + this.phase + ")";
   }

   @Override
   public String describeProgress() {
      String table = this.craftingTablePos != null ? this.craftingTablePos.toShortString() : "none";
      return "phase=" + this.phase + " target=" + ItemHelper.stripItemName(this.plan.outputItem()) + " table=" + table;
   }

   public CraftMacroPhase getPhase() {
      return this.phase;
   }

   public String getFailureReason() {
      return this.failureReason;
   }

   public void setCreationNote(String note) {
      this.creationNote = note;
   }

   public String getCreationNote() {
      return this.creationNote;
   }

   public BlockPos getCraftingTablePos() {
      return this.craftingTablePos;
   }

   private boolean ellieCollectionHandoff() {
      if (this.phase != CraftMacroPhase.COLLECT_MISSING_MATERIALS) {
         this.ellieCollectHandoffGranted = false;
         return false;
      } else if (!this.ellieCollectHandoffGranted
         && this.ellieCollectHandoffCount < 8
         && this.resolvedExternals().isEmpty()
         && this.lastResult.status() == ResolverResult.Status.NEEDS_WORK) {
         this.ellieCollectHandoffGranted = true;
         this.ellieCollectHandoffCount++;
         this.noProgressCount = 0;
         this.resolveAttempts = 0;
         this.lastDeficit = this.lastResult.remainingDeficit();
         return true;
      } else {
         return false;
      }
   }
}
