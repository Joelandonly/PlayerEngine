package com.player2.playerengine.agentic;

import com.player2.playerengine.PlayerEngineSettings;
import com.player2.playerengine.tasks.farming.FarmPlantingRequestParser;
import com.player2.playerengine.util.helpers.AutomaticMiningSourcePolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public final class AgenticPlanValidator {
   private static final Pattern STEP_ID = Pattern.compile("[a-zA-Z0-9_-]{1,32}");
   private static final int GOAL_SUMMARY_MAX = 120;
   private static final int RATIONALE_MAX = 180;
   private static final int PLANNER_NOTE_MAX = 180;

   private AgenticPlanValidator() {
   }

   public static AgenticPlanValidationResult validate(AgenticPlan plan, PlayerEngineSettings settings) {
      List<String> errors = new ArrayList<>();
      List<String> warnings = new ArrayList<>();
      if (plan == null) {
         return AgenticPlanValidationResult.invalid(List.of("plan_missing"));
      }

      if (plan.schemaVersion() != 1) {
         errors.add(plan.schemaVersion() > 1 ? "unsupported_schema" : "missing_or_invalid_schema_version");
         return AgenticPlanValidationResult.invalid(errors);
      }

      String goalSummary = sanitizeSingleLine(plan.goalSummary(), 120, "goal_summary", errors);
      if (goalSummary == null && errors.isEmpty()) {
         errors.add("goal_summary_required");
      }

      String plannerNote = sanitizeOptionalSingleLine(plan.plannerNote(), 180, warnings);
      List<AgenticStepSpec> steps = plan.steps();
      if (steps != null && !steps.isEmpty()) {
         int maxSteps = settings.getAgenticPlannerMaxSteps();
         if (steps.size() > maxSteps) {
            errors.add("too_many_steps");
            return AgenticPlanValidationResult.invalid(errors, warnings);
         }

         List<AgenticStepSpec> sanitizedSteps = new ArrayList<>();
         Set<String> seenKinds = new HashSet<>();

         for (int i = 0; i < steps.size(); i++) {
            AgenticStepSpec step = steps.get(i);
            if (step == null) {
               errors.add("step_" + i + "_null");
            } else {
               String id = sanitizeStepId(step.id(), errors, i);
               String kind = step.kind() != null ? step.kind().trim() : "";
               String kindLower = kind.toLowerCase(Locale.ROOT);
               if (AgenticSchemas.FORBIDDEN_FUTURE_STEP_KINDS.contains(kindLower) || kindLower.contains("elliegps") || kindLower.contains("waypoint")) {
                  errors.add("forbidden_step_kind:" + kind);
               } else if (!AgenticSchemas.ALLOWED_STEP_KINDS.contains(kind)) {
                  errors.add("unknown_step_kind:" + kind);
               } else if (!seenKinds.add(kind)) {
                  errors.add("duplicate_step_kind:" + kind);
               } else {
                  Map<String, String> args = sanitizeArgs(step.args());
                  if ("mine_block".equals(kind) && AutomaticMiningSourcePolicy.isProtectedAgenticMineTarget(mineBlockId(args))) {
                     errors.add("protected_mining_source:chest");
                  } else if (("setup_farm".equals(kind) || "harvest_farm".equals(kind)) && !validSetupFarmCoordinates(args)) {
                     errors.add(kind + "_coordinates_all_or_none");
                  } else if ("plant_farm".equals(kind) && !FarmPlantingRequestParser.parseArgs(args).valid()) {
                     errors.add("plant_farm_arguments_invalid");
                  } else {
                     String rationale = sanitizeOptionalSingleLine(step.rationale(), 180, warnings);
                     if (id != null) {
                        sanitizedSteps.add(new AgenticStepSpec(id, kind, args, rationale));
                     }
                  }
               }
            }
         }

         if (!errors.isEmpty()) {
            return AgenticPlanValidationResult.invalid(errors, warnings);
         } else if (sanitizedSteps.isEmpty()) {
            return AgenticPlanValidationResult.invalid(List.of("no_valid_steps"), warnings);
         } else {
            String sequenceError = validateSequence(sanitizedSteps);
            if (sequenceError != null) {
               errors.add(sequenceError);
               return AgenticPlanValidationResult.invalid(errors, warnings);
            } else {
               AgenticPlan sanitized = new AgenticPlan(1, goalSummary, List.copyOf(sanitizedSteps), plannerNote);
               return AgenticPlanValidationResult.ok(sanitized, warnings);
            }
         }
      } else {
         errors.add("steps_required");
         return AgenticPlanValidationResult.invalid(errors, warnings);
      }
   }

   static String validateSequence(List<AgenticStepSpec> steps) {
      List<String> kinds = new ArrayList<>(steps.size());

      for (AgenticStepSpec step : steps) {
         kinds.add(step.kind());
      }

      String g = "gather_loose_items";
      String r = "resolve_storage_chest";
      String d = "deposit_items";
      String l = "label_chest";
      String m = "mine_block";
      String f = "setup_farm";
      String h = "harvest_farm";
      String p = "plant_farm";

      for (List<String> seq : ResourceAgenticIntegration.addResourceSequence(
         new List[]{
            List.of(g),
            List.of(r),
            List.of(g, r),
            List.of(r, d),
            List.of(g, r, d),
            List.of(r, d, l),
            List.of(g, r, d, l),
            List.of(m),
            List.of(g, m),
            List.of(m, g),
            List.of(m, r, d),
            List.of(m, r, d, l),
            List.of(f),
            List.of(h),
            List.of(p)
         }
      )) {
         if (seq.equals(kinds)) {
            return null;
         }
      }

      return "invalid_step_sequence";
   }

   private static String sanitizeStepId(String raw, List<String> errors, int index) {
      if (raw != null && !raw.isBlank()) {
         String id = raw.trim();
         if (!STEP_ID.matcher(id).matches()) {
            errors.add("step_" + index + "_id_invalid");
            return null;
         } else {
            return id;
         }
      } else {
         errors.add("step_" + index + "_id_required");
         return null;
      }
   }

   private static Map<String, String> sanitizeArgs(Map<String, String> raw) {
      if (raw != null && !raw.isEmpty()) {
         Map<String, String> out = new LinkedHashMap<>();

         for (Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() != null && !e.getKey().isBlank()) {
               String key = e.getKey().trim().toLowerCase(Locale.ROOT);
               String val = e.getValue() != null ? e.getValue().trim() : "";
               if (!val.isEmpty()) {
                  out.put(key, val);
               }
            }
         }

         return Map.copyOf(out);
      } else {
         return Map.of();
      }
   }

   private static String mineBlockId(Map<String, String> args) {
      if (args != null && !args.isEmpty()) {
         String blockId = null;

         for (Entry<String, String> entry : args.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT).replace("_", "");
            if (key.equals("blockid")) {
               return entry.getValue();
            }

            if (key.equals("block")) {
               blockId = entry.getValue();
            }
         }

         return blockId;
      } else {
         return null;
      }
   }

   static boolean validSetupFarmCoordinates(Map<String, String> args) {
      if (args != null && !args.isEmpty()) {
         if (args.size() == 3 && args.keySet().containsAll(Set.of("x", "y", "z"))) {
            try {
               Integer.parseInt(args.get("x"));
               Integer.parseInt(args.get("y"));
               Integer.parseInt(args.get("z"));
               return true;
            } catch (NumberFormatException e) {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return true;
      }
   }

   private static String sanitizeSingleLine(String raw, int maxLen, String field, List<String> errors) {
      if (raw != null && !raw.isBlank()) {
         String line = raw.replace('\n', ' ').replace('\r', ' ').trim();
         if (line.length() > maxLen) {
            line = line.substring(0, maxLen);
         }

         return line;
      } else {
         errors.add(field + "_required");
         return null;
      }
   }

   private static String sanitizeOptionalSingleLine(String raw, int maxLen, List<String> warnings) {
      if (raw != null && !raw.isBlank()) {
         String line = raw.replace('\n', ' ').replace('\r', ' ').trim();
         if (line.length() > maxLen) {
            warnings.add("truncated_field");
            line = line.substring(0, maxLen);
         }

         return line;
      } else {
         return null;
      }
   }
}
