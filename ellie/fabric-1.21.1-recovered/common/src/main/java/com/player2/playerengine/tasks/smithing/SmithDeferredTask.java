package com.player2.playerengine.tasks.smithing;

import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.container.UpgradeInSmithingTableTask;
import com.player2.playerengine.tasks.smithing.resolver.SmithingRecipeAccess;
import com.player2.playerengine.tasks.smithing.resolver.SmithingRecipeAccessImpl;
import com.player2.playerengine.util.ItemTarget;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

public final class SmithDeferredTask extends Task {
   private static final SmithingRecipeAccess SMITHING_RECIPE_ACCESS = new SmithingRecipeAccessImpl();
   private final SmithDeferredParams params;
   @Nullable
   private final MaterialReservationService ledger;
   private SmithDeferredTask.Phase phase = SmithDeferredTask.Phase.RESOLVE;
   private boolean finished = false;
   @Nullable
   private SmithDeferredTask.Outcome outcome;
   @Nullable
   private SmithingRecipeAccess.SmithingResolution resolution;
   @Nullable
   private UpgradeInSmithingTableTask innerTask;
   private int consecutiveGatherTicks = 0;
   private int totalTicks = 0;
   private int outputCountAtDriveStart = -1;
   private int noTableTicks = 0;
   private static final int NO_TABLE_GIVE_UP_TICKS = 40;

   public SmithDeferredTask(SmithDeferredParams params) {
      this(params, null);
   }

   public SmithDeferredTask(SmithDeferredParams params, @Nullable MaterialReservationService ledger) {
      if (params == null) {
         throw new IllegalArgumentException("params must not be null");
      }

      this.params = params;
      this.ledger = ledger;
   }

   @Nullable
   public SmithDeferredTask.Outcome outcome() {
      return this.outcome;
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   @Override
   protected void onStart() {
      this.setDebugState("Preparing deferred smith");
   }

   @Override
   protected Task onTick() {
      if (this.finished) {
         return null;
      }

      this.totalTicks++;

      return switch (this.phase) {
         case RESOLVE -> this.tickResolve();
         case DRIVE -> this.tickDrive();
         case DONE, DEGRADED -> null;
      };
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (this.innerTask != null) {
         this.innerTask.releaseReservations();
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof SmithDeferredTask t) ? false : t.params.outputItem == this.params.outputItem && t.params.count == this.params.count;
   }

   @Override
   protected String toDebugString() {
      return "Deferred smith " + this.params.count + " x " + BuiltInRegistries.ITEM.getKey(this.params.outputItem);
   }

   private Task tickResolve() {
      if (this.controller == null) {
         return this.terminate(SmithDeferredTask.Outcome.setupFailed(this.params.outputItem, this.params.count, "no_controller"));
      }

      ServerLevel world = this.controller.getWorld();
      if (world == null) {
         return this.terminate(SmithDeferredTask.Outcome.setupFailed(this.params.outputItem, this.params.count, "no_world"));
      }

      RecipeManager mgr = world.getRecipeManager();
      RegistryAccess registries = world.registryAccess();
      Optional<SmithingRecipeAccess.SmithingResolution> resolved = SMITHING_RECIPE_ACCESS.resolve(mgr, this.params.outputItem, registries);
      if (resolved.isEmpty()) {
         return this.terminate(SmithDeferredTask.Outcome.noRecipe(this.params.outputItem, this.params.count));
      }

      this.resolution = resolved.get();
      List<Item> templateItems = this.resolution.template();
      List<Item> baseItems = this.resolution.base();
      List<Item> additionItems = this.resolution.addition();
      if (!templateItems.isEmpty() && !baseItems.isEmpty() && !additionItems.isEmpty()) {
         ItemTarget templateTarget = new ItemTarget(templateItems.toArray(Item[]::new), this.params.count);
         ItemTarget baseTarget = new ItemTarget(baseItems.toArray(Item[]::new), this.params.count);
         ItemTarget additionTarget = new ItemTarget(additionItems.toArray(Item[]::new), this.params.count);
         Item outputItem = this.resolution.output().getItem();
         if (outputItem != null && outputItem != Items.AIR) {
            ItemTarget outputTarget = new ItemTarget(outputItem, this.params.count);
            this.innerTask = new UpgradeInSmithingTableTask(baseTarget, additionTarget, templateTarget, outputTarget, this.ledger);
            this.outputCountAtDriveStart = this.controller.getItemStorage().getItemCount(outputItem);
            this.setDebugState("Recipe resolved; driving upgrade for " + BuiltInRegistries.ITEM.getKey(outputItem));
            this.phase = SmithDeferredTask.Phase.DRIVE;
            return this.tickDrive();
         } else {
            return this.terminate(SmithDeferredTask.Outcome.setupFailed(this.params.outputItem, this.params.count, "empty_output"));
         }
      } else {
         return this.terminate(SmithDeferredTask.Outcome.setupFailed(this.params.outputItem, this.params.count, "empty_ingredient_list"));
      }
   }

   private Task tickDrive() {
      if (this.innerTask == null || this.resolution == null) {
         return this.terminate(SmithDeferredTask.Outcome.setupFailed(this.params.outputItem, this.params.count, "inner_null"));
      }

      if (this.totalTicks > this.params.pollCap) {
         return this.terminate(this.buildMissingOutcome("poll_cap_exceeded"));
      }

      UpgradeInSmithingTableTask.SmithStepResult stepResult = this.innerTask.getStepResult();
      if (this.innerTask.controller != null && this.innerTask.isFinished()) {
         return this.classifyOnFinished();
      }

      if (stepResult == UpgradeInSmithingTableTask.SmithStepResult.NEEDS_GATHER) {
         this.consecutiveGatherTicks++;
         this.noTableTicks = 0;
         if (this.consecutiveGatherTicks > this.params.gatherBudget) {
            return this.terminate(this.buildMissingOutcome("gather_budget_exceeded"));
         }
      } else if (stepResult == UpgradeInSmithingTableTask.SmithStepResult.NO_TABLE) {
         this.noTableTicks++;
         this.consecutiveGatherTicks = 0;
         if (this.noTableTicks > 40) {
            int collected = this.currentCollected();
            String outputName = BuiltInRegistries.ITEM.getKey(this.params.outputItem).toString();
            return this.terminate(
               new SmithDeferredTask.Outcome(
                  SmithDeferredTask.OutcomeKind.NO_TABLE, collected, this.params.count, outputName, this.params.outputItem, "no_table"
               )
            );
         }
      } else {
         if (stepResult == UpgradeInSmithingTableTask.SmithStepResult.UPGRADED) {
            return this.classifyOnFinished();
         }

         this.consecutiveGatherTicks = 0;
         this.noTableTicks = 0;
      }

      return this.innerTask;
   }

   private Task classifyOnFinished() {
      int collected = this.currentCollected();
      String outputName = BuiltInRegistries.ITEM.getKey(this.params.outputItem).toString();
      Item outputItem = this.params.outputItem;
      if (collected >= this.params.count) {
         return this.terminate(
            new SmithDeferredTask.Outcome(SmithDeferredTask.OutcomeKind.CLEAN_SUCCESS, collected, this.params.count, outputName, outputItem, "clean")
         );
      } else if (collected > 0) {
         return this.terminate(
            new SmithDeferredTask.Outcome(SmithDeferredTask.OutcomeKind.PARTIAL, collected, this.params.count, outputName, outputItem, "partial")
         );
      } else {
         return this.outputCountAtDriveStart >= this.params.count
            ? this.terminate(
               new SmithDeferredTask.Outcome(SmithDeferredTask.OutcomeKind.CLEAN_SUCCESS, this.params.count, this.params.count, outputName, outputItem, "clean")
            )
            : this.terminate(new SmithDeferredTask.Outcome(SmithDeferredTask.OutcomeKind.TAMPERED, 0, this.params.count, outputName, outputItem, "tampered"));
      }
   }

   private SmithDeferredTask.Outcome buildMissingOutcome(String extraReason) {
      if (this.resolution == null) {
         return SmithDeferredTask.Outcome.setupFailed(this.params.outputItem, this.params.count, extraReason);
      }

      int needed = this.params.count;
      boolean templateShort = this.countItems(this.resolution.template()) < needed;
      boolean baseShort = this.countItems(this.resolution.base()) < needed;
      boolean additionShort = this.countItems(this.resolution.addition()) < needed;
      String outputName = BuiltInRegistries.ITEM.getKey(this.params.outputItem).toString();
      int collected = this.currentCollected();
      StringBuilder reason = new StringBuilder();
      if (templateShort) {
         reason.append("missing_template");
      }

      if (baseShort) {
         if (reason.length() > 0) {
            reason.append("+");
         }

         reason.append("missing_base");
      }

      if (additionShort) {
         if (reason.length() > 0) {
            reason.append("+");
         }

         reason.append("missing_addition");
      }

      if (reason.length() == 0) {
         reason.append("gather_stall");
      }

      if (extraReason != null && !extraReason.isBlank()) {
         reason.append(":").append(extraReason);
      }

      SmithDeferredTask.OutcomeKind primaryKind;
      if (templateShort) {
         primaryKind = SmithDeferredTask.OutcomeKind.MISSING_TEMPLATE;
      } else if (baseShort) {
         primaryKind = SmithDeferredTask.OutcomeKind.MISSING_BASE;
      } else if (additionShort) {
         primaryKind = SmithDeferredTask.OutcomeKind.MISSING_ADDITION;
      } else {
         primaryKind = SmithDeferredTask.OutcomeKind.MISSING_ADDITION;
      }

      return new SmithDeferredTask.Outcome(primaryKind, collected, this.params.count, outputName, this.params.outputItem, reason.toString());
   }

   private int countItems(List<Item> items) {
      return items.isEmpty() ? 0 : this.controller.getItemStorage().getItemCount(items.toArray(Item[]::new));
   }

   private int currentCollected() {
      if (this.controller == null) {
         return 0;
      }

      int currentCount = this.controller.getItemStorage().getItemCount(this.params.outputItem);
      int delta = currentCount - Math.max(0, this.outputCountAtDriveStart);
      return Math.max(0, Math.min(this.params.count, delta));
   }

   private Task terminate(SmithDeferredTask.Outcome o) {
      if (this.innerTask != null) {
         this.innerTask.releaseReservations();
      }

      this.outcome = o;
      this.finished = true;
      this.phase = o.kind() == SmithDeferredTask.OutcomeKind.CLEAN_SUCCESS ? SmithDeferredTask.Phase.DONE : SmithDeferredTask.Phase.DEGRADED;
      this.setDebugState("Terminal: " + o.kind().name().toLowerCase(Locale.ROOT));
      return null;
   }

   public record Outcome(
      SmithDeferredTask.OutcomeKind kind, int collected, int expected, String outputName, @Nullable Item outputItem, String reasonLabel
   ) {
      public boolean isCleanSuccess() {
         return this.kind == SmithDeferredTask.OutcomeKind.CLEAN_SUCCESS;
      }

      public boolean isDegraded() {
         return this.kind != SmithDeferredTask.OutcomeKind.CLEAN_SUCCESS;
      }

      public static SmithDeferredTask.Outcome noRecipe(@Nullable Item requestedItem, int count) {
         String name = requestedItem != null ? BuiltInRegistries.ITEM.getKey(requestedItem).toString() : "?";
         return new SmithDeferredTask.Outcome(SmithDeferredTask.OutcomeKind.NO_RECIPE, 0, count, name, requestedItem, "no_recipe");
      }

      public static SmithDeferredTask.Outcome setupFailed(@Nullable Item requestedItem, int count, String reason) {
         String name = requestedItem != null ? BuiltInRegistries.ITEM.getKey(requestedItem).toString() : "?";
         return new SmithDeferredTask.Outcome(SmithDeferredTask.OutcomeKind.SETUP_FAILED, 0, count, name, requestedItem, reason);
      }
   }

   public enum OutcomeKind {
      CLEAN_SUCCESS,
      PARTIAL,
      TAMPERED,
      NO_RECIPE,
      NO_TABLE,
      MISSING_TEMPLATE,
      MISSING_BASE,
      MISSING_ADDITION,
      SETUP_FAILED;
   }

   private enum Phase {
      RESOLVE,
      DRIVE,
      DONE,
      DEGRADED;
   }
}
