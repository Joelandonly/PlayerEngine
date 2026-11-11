package com.player2.playerengine.agentic.steps;

import com.player2.playerengine.PlayerEngineSettings;
import com.player2.playerengine.agentic.AgenticExecutionContext;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.AgenticStepFactory;
import com.player2.playerengine.agentic.AgenticStepSpec;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.tasks.agentic.FuelGatherParams;
import com.player2.playerengine.tasks.agentic.FuelGatherTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.cooking.FuelPlanner;
import com.player2.playerengine.tasks.cooking.SmeltDeferredParams;
import com.player2.playerengine.tasks.cooking.SmeltDeferredTask;
import com.player2.playerengine.tasks.cooking.resolver.CookingRecipeAccess;
import com.player2.playerengine.tasks.cooking.resolver.CookingRecipeAccessImpl;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

public final class SmeltStepFactory implements AgenticStepFactory {
   @Override
   public Optional<Task> createTask(AgenticStepSpec step, AgenticExecutionContext context) {
      PlayerEngineSettings settings = context.settings();
      Map<String, String> args = step.args() != null ? step.args() : Map.of();
      Item input = resolveItem(lookup(args, "item"));
      if (input != null && input != Items.AIR) {
         int maxBatch = settings.getDeferredSmeltMaxBatch();
         int count = Math.max(1, Math.min(maxBatch, parseInt(args, "count", 1)));
         CookingRecipeAccess.CookKind kind = parseKind(lookup(args, "kind"));
         MaterialReservationService ledger = context.reservations();
         int free = ledger != null ? ledger.free(context.controller(), input) : count;
         int clampedCount = Math.min(count, free);
         if (clampedCount < 1) {
            AgenticRunRegistry.AgenticRunState run = context.runState();
            if (run != null) {
               run.setSmeltDegraded(AgenticRunRegistry.DegradationLevel.SKIPPED, "no_input_reserved");
            }

            if (run == null || !run.isTerminal()) {
               context.controller().reportAgenticProgress("Couldn't smelt " + itemName(input) + " — all of it is reserved for another step.", true);
            }

            return Optional.empty();
         } else {
            return Optional.of(new SmeltStepFactory.AgenticSmeltTask(input, clampedCount, kind, ledger, context));
         }
      } else {
         AgenticRunRegistry.AgenticRunState run = context.runState();
         if (run != null) {
            run.setSmeltDegraded(AgenticRunRegistry.DegradationLevel.SKIPPED, "no_input");
         }

         return Optional.empty();
      }
   }

   private static SmeltDeferredParams buildParams(Item input, int count, CookingRecipeAccess.CookKind kind, PlayerEngineSettings settings) {
      return new SmeltDeferredParams.Builder(input, Math.max(1, count))
         .preferredKind(kind)
         .stallPolls(settings.getDeferredSmeltStallPolls())
         .timeoutSeconds(settings.getDeferredSmeltTimeoutSeconds())
         .build();
   }

   public static Optional<SmeltStepFactory.SmeltReservation> forwardReservation(AgenticStepSpec step, AgenticExecutionContext context) {
      Map<String, String> args = step.args() != null ? step.args() : Map.of();
      Item input = resolveItem(lookup(args, "item"));
      if (input != null && input != Items.AIR) {
         PlayerEngineSettings settings = context.settings();
         int maxBatch = settings != null ? settings.getDeferredSmeltMaxBatch() : 64;
         int count = Math.max(1, Math.min(maxBatch, parseInt(args, "count", 1)));
         return Optional.of(new SmeltStepFactory.SmeltReservation(input, count));
      } else {
         return Optional.empty();
      }
   }

   private static String itemName(Item item) {
      return item != null ? BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' ') : "that item";
   }

   private static Item resolveItem(String token) {
      if (token != null && !token.isBlank()) {
         String trimmed = token.trim().toLowerCase(Locale.ROOT);
         ResourceLocation id = ResourceLocation.tryParse(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
         return id == null ? null : (Item)BuiltInRegistries.ITEM.get(id);
      } else {
         return null;
      }
   }

   private static CookingRecipeAccess.CookKind parseKind(String raw) {
      if (raw != null && !raw.isBlank()) {
         String k = raw.trim().toLowerCase(Locale.ROOT);

         return switch (k) {
            case "blast", "blasting", "blast_furnace" -> CookingRecipeAccess.CookKind.BLASTING;
            case "smoke", "smoking", "smoker" -> CookingRecipeAccess.CookKind.SMOKING;
            case "smelt", "smelting", "furnace" -> CookingRecipeAccess.CookKind.SMELTING;
            default -> null;
         };
      } else {
         return null;
      }
   }

   private static int parseInt(Map<String, String> args, String key, int def) {
      String raw = lookup(args, key);
      if (raw == null) {
         return def;
      }

      try {
         return Integer.parseInt(raw.trim());
      } catch (NumberFormatException e) {
         return def;
      }
   }

   private static String lookup(Map<String, String> args, String key) {
      if (args == null) {
         return null;
      }

      String normalized = key.toLowerCase(Locale.ROOT).replace("_", "");

      for (Entry<String, String> e : args.entrySet()) {
         String k = e.getKey().toLowerCase(Locale.ROOT).replace("_", "");
         if (k.equals(normalized)) {
            return e.getValue();
         }
      }

      return null;
   }

   private static final class AgenticSmeltTask extends Task {
      private static final CookingRecipeAccess COOKING_RECIPE_ACCESS = new CookingRecipeAccessImpl();
      private final Item input;
      private final int requestedCount;
      private final CookingRecipeAccess.CookKind kind;
      private final MaterialReservationService ledger;
      private final AgenticExecutionContext context;
      private SmeltDeferredTask inner;
      private Task fuelGatherChild;
      private boolean fuelGatherAttempted;
      private int clampedSmeltCount = -1;
      private boolean fuelGatherNoAcquirable;
      private boolean finished;
      private boolean recorded;

      AgenticSmeltTask(
         Item input, int requestedCount, CookingRecipeAccess.CookKind kind, MaterialReservationService ledger, AgenticExecutionContext context
      ) {
         this.input = input;
         this.requestedCount = Math.max(1, requestedCount);
         this.kind = kind;
         this.ledger = ledger;
         this.context = context;
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }

      @Override
      protected void onStart() {
         this.setDebugState("deferred smelt (agentic)");
      }

      @Override
      protected Task onTick() {
         if (!this.finished && !this.recorded) {
            if (this.context.runState() != null && (!this.fuelGatherAttempted || this.fuelGatherChild != null)) {
               Task pre = this.drivePreGather();
               if (pre != null) {
                  return pre;
               }
            }

            if (this.fuelGatherNoAcquirable && !this.recorded) {
               this.recordNoAcquirable();
               return null;
            }

            if (this.inner == null) {
               int count = this.clampedSmeltCount > 0 ? this.clampedSmeltCount : this.requestedCount;
               this.inner = new SmeltDeferredTask(SmeltStepFactory.buildParams(this.input, count, this.kind, this.context.settings()), this.ledger);
            }

            if (!this.inner.isFinished()) {
               return this.inner;
            }

            this.recordOutcome(this.inner.outcome(), this.inner.loadShortfall());
            return null;
         } else {
            return null;
         }
      }

      private Task drivePreGather() {
         if (this.fuelGatherChild != null) {
            if (!this.fuelGatherChild.isFinished() && !this.fuelGatherChild.stopped()) {
               return this.fuelGatherChild;
            }

            this.fuelGatherChild = null;
            this.resolveAfterGather();
            return null;
         } else {
            int cookTicks = this.resolveCookTicks();
            if (cookTicks < 1) {
               this.fuelGatherAttempted = true;
               this.clampedSmeltCount = this.requestedCount;
               return null;
            } else {
               Optional<FuelPlanner.FuelPlan> full = new FuelPlanner().plan(this.requestedCount, cookTicks, this.context.controller(), this.peekLedger());
               if (full.isPresent()) {
                  this.fuelGatherAttempted = true;
                  this.clampedSmeltCount = this.requestedCount;
                  return null;
               } else {
                  Optional<FuelPlanner.DeficitCandidate> deficit = new FuelPlanner()
                     .deficitFor(this.requestedCount, cookTicks, this.context.controller(), this.peekLedger());
                  if (deficit.isEmpty()) {
                     this.fuelGatherAttempted = true;
                     this.fuelGatherNoAcquirable = true;
                     return null;
                  } else {
                     FuelPlanner.DeficitCandidate c = deficit.get();
                     this.fuelGatherAttempted = true;
                     FuelGatherParams gp = new FuelGatherParams(c.item(), c.deficit(), 32.0, 60.0, 1, 1);
                     this.fuelGatherChild = new FuelGatherTask(gp, this.context.runState(), this.ledger);
                     this.context
                        .controller()
                        .log("[Agentic] smelt: pre-gathering fuel " + BuiltInRegistries.ITEM.getKey(c.item()) + " deficit=" + c.deficit());
                     return this.fuelGatherChild;
                  }
               }
            }
         }
      }

      private void resolveAfterGather() {
         int cookTicks = this.resolveCookTicks();
         if (cookTicks < 1) {
            this.clampedSmeltCount = this.requestedCount;
         } else {
            Optional<FuelPlanner.FuelPlan> full = new FuelPlanner().plan(this.requestedCount, cookTicks, this.context.controller(), this.peekLedger());
            if (full.isPresent()) {
               this.clampedSmeltCount = this.requestedCount;
            } else {
               int coverable = this.maxFuelCoverableCount(cookTicks);
               if (coverable >= 1) {
                  this.clampedSmeltCount = coverable;
               } else {
                  this.fuelGatherNoAcquirable = true;
               }
            }
         }
      }

      private int maxFuelCoverableCount(int cookTicks) {
         if (cookTicks < 1) {
            return 0;
         }

         FuelPlanner planner = new FuelPlanner();

         for (int count = this.requestedCount - 1; count >= 1; count--) {
            if (planner.plan(count, cookTicks, this.context.controller(), this.peekLedger()).isPresent()) {
               return count;
            }
         }

         return 0;
      }

      private int resolveCookTicks() {
         ServerLevel world = this.context.controller().getWorld();
         if (world == null) {
            return 0;
         }

         RecipeManager mgr = world.getRecipeManager();
         RegistryAccess registries = world.registryAccess();
         Optional<CookingRecipeAccess.CookResolution> resolved = this.kind != null
            ? COOKING_RECIPE_ACCESS.resolve(mgr, this.input, this.kind, registries)
            : COOKING_RECIPE_ACCESS.resolveAny(mgr, this.input, registries);
         return resolved.map(CookingRecipeAccess.CookResolution::cookTicks).orElse(0);
      }

      private MaterialReservationService peekLedger() {
         return null;
      }

      private void recordNoAcquirable() {
         this.recorded = true;
         AgenticRunRegistry.AgenticRunState run = this.context.runState();
         if (run != null) {
            run.setSmeltDegraded(AgenticRunRegistry.DegradationLevel.SKIPPED, "fuel_gather no_acquirable_fuel");
            run.setSmeltProgress(
               "smelted=0 requested=" + this.requestedCount + " item=" + BuiltInRegistries.ITEM.getKey(this.input) + " reason=fuel_gather_no_acquirable"
            );
         }

         if (run == null || !run.isTerminal()) {
            this.context
               .controller()
               .reportAgenticProgress(
                  "Tried to gather fuel automatically but couldn't get any — smelted 0 " + SmeltStepFactory.itemName(this.input) + ".", true
               );
         }

         this.finished = true;
      }

      @Override
      protected void onStop(Task interruptTask) {
      }

      @Override
      protected boolean isEqual(Task other) {
         return other instanceof SmeltStepFactory.AgenticSmeltTask t && t.input == this.input && t.requestedCount == this.requestedCount;
      }

      @Override
      protected String toDebugString() {
         return "AgenticSmelt";
      }

      private void recordOutcome(SmeltDeferredTask.Outcome o, int loadShortfall) {
         this.recorded = true;
         AgenticRunRegistry.AgenticRunState run = this.context.runState();
         if (o == null) {
            if (run != null) {
               run.setSmeltDegraded(AgenticRunRegistry.DegradationLevel.SKIPPED, "no_outcome");
            }

            this.finished = true;
         } else {
            boolean willDowngrade = o.kind() == SmeltDeferredTask.OutcomeKind.CLEAN_SUCCESS
               && (loadShortfall > 0 || this.clampedSmeltCount > 0 && this.clampedSmeltCount < this.requestedCount);
            if (!willDowngrade && (run == null || !run.isTerminal())) {
               this.context.controller().reportAgenticProgress(playerLine(o), true);
            }

            if (loadShortfall > 0 && o.kind() == SmeltDeferredTask.OutcomeKind.CLEAN_SUCCESS) {
               int requested = o.collected() + loadShortfall;
               if (run == null || !run.isTerminal()) {
                  this.context
                     .controller()
                     .reportAgenticProgress(
                        "Only smelted "
                           + o.collected()
                           + " of "
                           + requested
                           + " "
                           + SmeltStepFactory.itemName(o.outputItem())
                           + " — couldn't load the rest (it was also used as fuel or reserved).",
                        true
                     );
               }

               if (run != null) {
                  run.setSmeltDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, "input_shortfall loaded=" + o.collected() + " of " + requested);
                  run.setSmeltProgress("smelted=" + o.collected() + " requested=" + requested + " item=" + o.outputName() + " reason=input_shortfall");
               }

               this.finished = true;
            } else if (this.clampedSmeltCount > 0 && this.clampedSmeltCount < this.requestedCount && o.kind() == SmeltDeferredTask.OutcomeKind.CLEAN_SUCCESS) {
               int collected = o.collected();
               if (run == null || !run.isTerminal()) {
                  this.context
                     .controller()
                     .reportAgenticProgress(
                        "Auto-gathered fuel but only enough to smelt "
                           + collected
                           + " of "
                           + this.requestedCount
                           + " "
                           + SmeltStepFactory.itemName(o.outputItem())
                           + ".",
                        true
                     );
               }

               if (run != null) {
                  run.setSmeltDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, "fuel_gather collected=" + collected + " of " + this.requestedCount);
                  run.setSmeltProgress("smelted=" + collected + " requested=" + this.requestedCount + " item=" + o.outputName() + " reason=fuel_gather");
               }

               this.finished = true;
            } else {
               switch (o.kind()) {
                  case CLEAN_SUCCESS:
                     if (run != null) {
                        run.setSmeltProgress("smelted=" + o.collected() + " item=" + o.outputName());
                     }

                     this.finished = true;
                     break;
                  case PARTIAL_OUT_OF_FUEL:
                     this.degraded(
                        run, AgenticRunRegistry.DegradationLevel.PARTIAL, "partial out_of_fuel collected=" + o.collected() + " of " + o.expected(), o, true
                     );
                     break;
                  case TAMPERED:
                     this.degraded(run, AgenticRunRegistry.DegradationLevel.PARTIAL, "tampered collected=" + o.collected() + " of " + o.expected(), o, true);
                     break;
                  case STALLED_RETRYING:
                     this.degraded(
                        run, AgenticRunRegistry.DegradationLevel.PARTIAL, "stalled_needs_rerun collected=" + o.collected() + " of " + o.expected(), o, true
                     );
                     break;
                  case FURNACE_IN_USE:
                     this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "furnace_in_use", o, false);
                     break;
                  case NO_FUEL:
                     this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "no_fuel", o, false);
                     break;
                  case STALLED_TIMEOUT:
                     this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "stalled_timeout", o, false);
                     break;
                  case FURNACE_GONE:
                     this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "furnace_gone", o, false);
                     break;
                  case NO_RECIPE:
                     this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "no_recipe", o, false);
                     break;
                  case SETUP_FAILED:
                     this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "setup_failed:" + o.reasonLabel(), o, false);
               }
            }
         }
      }

      private void degraded(
         AgenticRunRegistry.AgenticRunState run, AgenticRunRegistry.DegradationLevel level, String reason, SmeltDeferredTask.Outcome o, boolean softSuccess
      ) {
         if (run != null) {
            run.setSmeltDegraded(level, reason);
            run.setSmeltProgress("smelted=" + o.collected() + " item=" + o.outputName() + " reason=" + reason);
         }

         if (softSuccess) {
            this.finished = true;
         } else {
            this.finished = false;
            if (!this.stopped()) {
               this.stop(this);
            }
         }
      }

      private static String playerLine(SmeltDeferredTask.Outcome o) {
         String out = o.outputItem() != null ? BuiltInRegistries.ITEM.getKey(o.outputItem()).getPath().replace('_', ' ') : "that item";

         return switch (o.kind()) {
            case CLEAN_SUCCESS -> "Smelted " + o.collected() + " x " + out + ".";
            case PARTIAL_OUT_OF_FUEL -> "Smelted " + o.collected() + " of " + o.expected() + " x " + out + " — ran out of fuel.";
            case TAMPERED -> "The furnace contents changed while I was away — collected what I could (" + o.collected() + ").";
            case STALLED_RETRYING -> "The furnace chunk stopped ticking, so it can't finish on its own. I smelted "
               + o.collected()
               + " of "
               + o.expected()
               + " — ask me to smelt again near the furnace to finish the rest.";
            case FURNACE_IN_USE -> "That furnace already has something in it — I didn't load anything to avoid mixing batches.";
            case NO_FUEL -> "Couldn't smelt — no fuel available (need about " + o.fuelNeeded() + ").";
            case STALLED_TIMEOUT -> "Gave up smelting — the furnace chunk wasn't ticking and the job timed out.";
            case FURNACE_GONE -> "The furnace I was using is gone — couldn't finish.";
            case NO_RECIPE -> "I can't smelt that — it has no furnace, blast furnace, or smoker recipe.";
            case SETUP_FAILED -> "Couldn't start the smelt (" + o.reasonLabel() + ").";
         };
      }
   }

   public record SmeltReservation(Item input, int count) {
   }
}
