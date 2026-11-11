package com.player2.playerengine.agentic.steps;

import com.player2.playerengine.agentic.AgenticExecutionContext;
import com.player2.playerengine.agentic.AgenticStepFactory;
import com.player2.playerengine.agentic.AgenticStepSpec;
import com.player2.playerengine.tasks.agentic.MineBlockParams;
import com.player2.playerengine.tasks.agentic.MineBlockTask;
import com.player2.playerengine.tasks.base.Task;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;

public final class MineBlockStepFactory implements AgenticStepFactory {
   @Override
   public Optional<Task> createTask(AgenticStepSpec step, AgenticExecutionContext context) {
      Map<String, String> args = step.args() != null ? step.args() : Map.of();
      String blockId = firstNonBlank(lookup(args, "blockid"), lookup(args, "block"));
      if (blockId != null && !blockId.isBlank()) {
         MineBlockParams params = new MineBlockParams(
            blockId.trim(),
            parseInt(args, "maxblocks", 16, 1, 256),
            parseDouble(args, "radius", 32.0, 4.0, 128.0),
            parseDouble(args, "timeoutseconds", 120.0, 10.0, 600.0),
            parseInt(args, "toolacquiremaxcontainers", 8, 1, 32),
            parseInt(args, "toolacquireclimbbudget", 6, 1, 16)
         );
         return Optional.of(new MineBlockTask(params, context.runState(), context.reservations()));
      } else {
         return Optional.empty();
      }
   }

   private static String firstNonBlank(String a, String b) {
      return a != null && !a.isBlank() ? a : b;
   }

   private static double parseDouble(Map<String, String> args, String key, double def, double min, double max) {
      String raw = lookup(args, key);
      if (raw == null) {
         return def;
      }

      try {
         return Math.max(min, Math.min(max, Double.parseDouble(raw.trim())));
      } catch (NumberFormatException e) {
         return def;
      }
   }

   private static int parseInt(Map<String, String> args, String key, int def, int min, int max) {
      String raw = lookup(args, key);
      if (raw == null) {
         return def;
      }

      try {
         return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
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
}
