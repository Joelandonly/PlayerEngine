package com.player2.playerengine.agentic.steps;

import com.player2.playerengine.PlayerEngineSettings;
import com.player2.playerengine.agentic.AgenticExecutionContext;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.AgenticStepFactory;
import com.player2.playerengine.agentic.AgenticStepSpec;
import com.player2.playerengine.commands.base.DescriptionIdIndex;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.smithing.SmithDeferredParams;
import com.player2.playerengine.tasks.smithing.SmithDeferredTask;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class SmithStepFactory implements AgenticStepFactory {
   private static final int DEFAULT_SMITH_MAX_BATCH = 64;

   @Override
   public Optional<Task> createTask(AgenticStepSpec step, AgenticExecutionContext context) {
      PlayerEngineSettings settings = context.settings();
      Map<String, String> args = step.args() != null ? step.args() : Map.of();
      if (settings != null && !settings.isEnableSmithing()) {
         AgenticRunRegistry.AgenticRunState run = context.runState();
         if (run != null) {
            run.setSmithDegraded(AgenticRunRegistry.DegradationLevel.SKIPPED, "smithing_disabled");
         }

         return Optional.empty();
      } else {
         Item output = resolveItem(lookup(args, "item"));
         if (output != null && output != Items.AIR) {
            int maxBatch = getSmithMaxBatch(settings);
            int count = Math.max(1, Math.min(maxBatch, parseInt(args, "count", 1)));
            SmithDeferredParams params = new SmithDeferredParams.Builder(output, count).gatherBudget(400).pollCap(12000).build();
            return Optional.of(new SmithStepFactory.AgenticSmithTask(new SmithDeferredTask(params, context.reservations()), context));
         }

         AgenticRunRegistry.AgenticRunState run = context.runState();
         if (run != null) {
            run.setSmithDegraded(AgenticRunRegistry.DegradationLevel.SKIPPED, "no_input");
         }

         return Optional.empty();
      }
   }

   private static Item resolveItem(String token) {
      if (token != null && !token.isBlank()) {
         String trimmed = token.trim().toLowerCase(Locale.ROOT);
         String resolved = DescriptionIdIndex.resolveToRegistryId(trimmed);
         if (resolved != null) {
            ResourceLocation resolvedId = ResourceLocation.tryParse(resolved);
            if (resolvedId != null) {
               return (Item)BuiltInRegistries.ITEM.get(resolvedId);
            }
         }

         ResourceLocation id = ResourceLocation.tryParse(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
         return id == null ? null : (Item)BuiltInRegistries.ITEM.get(id);
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

   private static int getSmithMaxBatch(PlayerEngineSettings settings) {
      return settings != null ? settings.getSmithMaxBatch() : 64;
   }

   static final class AgenticSmithTask extends Task {
      private final SmithDeferredTask inner;
      private final AgenticExecutionContext context;
      private boolean finished;
      private boolean recorded;

      AgenticSmithTask(SmithDeferredTask inner, AgenticExecutionContext context) {
         this.inner = inner;
         this.context = context;
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }

      @Override
      protected void onStart() {
         this.setDebugState("deferred smith (agentic)");
      }

      @Override
      protected Task onTick() {
         if (this.finished || this.recorded) {
            return null;
         }

         if (!this.inner.isFinished()) {
            return this.inner;
         }

         this.recordOutcome(this.inner.outcome());
         return null;
      }

      @Override
      protected void onStop(Task interruptTask) {
      }

      @Override
      protected boolean isEqual(Task other) {
         return other instanceof SmithStepFactory.AgenticSmithTask t && t.inner.equals(this.inner);
      }

      @Override
      protected String toDebugString() {
         return "AgenticSmith";
      }

      private void recordOutcome(SmithDeferredTask.Outcome o) {
         this.recorded = true;
         AgenticRunRegistry.AgenticRunState run = this.context.runState();
         if (o == null) {
            if (run != null) {
               run.setSmithDegraded(AgenticRunRegistry.DegradationLevel.SKIPPED, "no_outcome");
            }

            this.finished = true;
         } else {
            if (run == null || !run.isTerminal()) {
               this.context.controller().reportAgenticProgress(playerLine(o), true);
            }

            switch (o.kind()) {
               case CLEAN_SUCCESS:
                  if (run != null) {
                     run.setSmithProgress("upgraded=" + o.collected() + " item=" + o.outputName());
                  }

                  this.finished = true;
                  break;
               case PARTIAL:
                  this.degraded(run, AgenticRunRegistry.DegradationLevel.PARTIAL, "partial collected=" + o.collected() + " of " + o.expected(), o, true);
                  break;
               case TAMPERED:
                  this.degraded(run, AgenticRunRegistry.DegradationLevel.PARTIAL, "tampered collected=" + o.collected() + " of " + o.expected(), o, true);
                  break;
               case NO_TABLE:
                  this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "no_table", o, false);
                  break;
               case NO_RECIPE:
                  this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "no_recipe", o, false);
                  break;
               case MISSING_TEMPLATE:
                  this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "missing_template:" + o.reasonLabel(), o, false);
                  break;
               case MISSING_BASE:
                  this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "missing_base:" + o.reasonLabel(), o, false);
                  break;
               case MISSING_ADDITION:
                  this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "missing_addition:" + o.reasonLabel(), o, false);
                  break;
               case SETUP_FAILED:
                  this.degraded(run, AgenticRunRegistry.DegradationLevel.SKIPPED, "setup_failed:" + o.reasonLabel(), o, false);
            }
         }
      }

      private void degraded(
         AgenticRunRegistry.AgenticRunState run, AgenticRunRegistry.DegradationLevel level, String reason, SmithDeferredTask.Outcome o, boolean softSuccess
      ) {
         if (run != null) {
            run.setSmithDegraded(level, reason);
            run.setSmithProgress("upgraded=" + o.collected() + " item=" + o.outputName() + " reason=" + reason);
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

      private static String playerLine(SmithDeferredTask.Outcome o) {
         String out = o.outputItem() != null ? BuiltInRegistries.ITEM.getKey(o.outputItem()).getPath().replace('_', ' ') : "that item";

         return switch (o.kind()) {
            case CLEAN_SUCCESS -> "Upgraded " + o.collected() + " x " + out + " at the smithing table.";
            case PARTIAL -> "Upgraded " + o.collected() + " of " + o.expected() + " x " + out + " — couldn't finish the batch.";
            case TAMPERED -> "The smithing table or my inventory changed while I was working — upgraded "
               + o.collected()
               + " of "
               + o.expected()
               + " x "
               + out
               + ".";
            case NO_TABLE -> "Couldn't upgrade " + out + " — no smithing table reachable or placeable.";
            case NO_RECIPE -> "Can't upgrade to " + out + " — nothing in the smithing registry produces it.";
            case MISSING_TEMPLATE -> "Couldn't get the smithing template needed to upgrade to " + out + " (" + o.reasonLabel() + ").";
            case MISSING_BASE -> "Couldn't get the base item needed to upgrade to " + out + " (" + o.reasonLabel() + ").";
            case MISSING_ADDITION -> "Couldn't get the upgrade material needed for " + out + " (" + o.reasonLabel() + ").";
            case SETUP_FAILED -> "Couldn't start the smithing upgrade (" + o.reasonLabel() + ").";
         };
      }
   }
}
