package com.player2.playerengine.agentic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgenticDegradationSummary {
   private AgenticDegradationSummary() {
   }

   public static String forModel(AgenticRunRegistry.AgenticRunState s) {
      if (s == null) {
         return "";
      }

      List<String> clauses = new ArrayList<>();
      if (s.getGatherDegradation() != AgenticRunRegistry.DegradationLevel.CLEAN) {
         clauses.add(gatherClause(s.getGatherDegradationReason()));
      } else {
         String clause = gatherSuccessClause(s.getGatherProgress());
         if (clause != null && !clause.isBlank()) {
            clauses.add(clause);
         }
      }

      if (s.getDepositDegradation() != AgenticRunRegistry.DegradationLevel.CLEAN) {
         clauses.add(depositClause(s.getDepositDegradationReason()));
      } else {
         String clause = depositSuccessClause(s.getDepositProgress(), s.getStorageTargetSummary());
         if (clause != null && !clause.isBlank()) {
            clauses.add(clause);
         }
      }

      if (s.getLabelDegradation() != AgenticRunRegistry.DegradationLevel.CLEAN) {
         clauses.add(labelClause(s.getLabelDegradationReason()));
      }

      if (s.getWaypointDegradation() != AgenticRunRegistry.DegradationLevel.CLEAN) {
         clauses.add(waypointClause(s.getWaypointDegradation(), s.getWaypointDegradationReason()));
      }

      if (s.getSmeltDegradation() != AgenticRunRegistry.DegradationLevel.CLEAN) {
         clauses.add(smeltClause(s.getSmeltDegradationReason()));
      } else {
         String clause = smeltSuccessClause(s.getSmeltProgress());
         if (clause != null && !clause.isBlank()) {
            clauses.add(clause);
         }
      }

      if (s.getSmithDegradation() != AgenticRunRegistry.DegradationLevel.CLEAN) {
         clauses.add(smithClause(s.getSmithDegradationReason()));
      } else {
         String clause = smithSuccessClause(s.getSmithProgress());
         if (clause != null && !clause.isBlank()) {
            clauses.add(clause);
         }
      }

      if (s.getMineDegradation() != AgenticRunRegistry.DegradationLevel.CLEAN) {
         clauses.add(mineClause(s.getMineDegradationReason(), s.getMineProgress()));
      } else {
         String clause = mineSuccessClause(s.getMineProgress());
         if (clause != null && !clause.isBlank()) {
            clauses.add(clause);
         }
      }

      if (s.getFarmDegradation() != AgenticRunRegistry.DegradationLevel.CLEAN) {
         clauses.add(farmClause(s.getFarmDegradationReason(), s.getFarmProgress()));
      } else if (s.getFarmProgress() != null && !s.getFarmProgress().isBlank()) {
         clauses.add(boundFarm(s.getFarmProgress()));
      }

      return String.join("; ", clauses);
   }

   private static String depositSuccessClause(String progress, String storageTarget) {
      int n = parseToken(progress, "deposited=");
      if (n <= 0) {
         return "";
      }

      String clause = "deposited " + n + " item(s)";
      if (storageTarget != null && !storageTarget.isBlank()) {
         clause = clause + " into " + storageTarget.trim();
      }

      return clause;
   }

   private static String gatherSuccessClause(String progress) {
      int n = parseToken(progress, "gathered=");
      return n > 0 ? "gathered " + n + " item(s)" : "";
   }

   private static String smeltSuccessClause(String progress) {
      int n = parseToken(progress, "smelted=");
      if (n <= 0) {
         return "";
      }

      String item = parseStringToken(progress, "item=");
      return item != null && !item.isBlank() ? "smelted " + n + " " + item : "smelted " + n + " item(s)";
   }

   private static String smithSuccessClause(String progress) {
      int n = parseToken(progress, "upgraded=");
      if (n <= 0) {
         return "";
      }

      String item = parseStringToken(progress, "item=");
      return item != null && !item.isBlank() ? "upgraded " + n + " " + item + " at the smithing table" : "upgraded " + n + " item(s) at the smithing table";
   }

   private static String mineSuccessClause(String progress) {
      int n = parseToken(progress, "mined=");
      if (n <= 0) {
         return "";
      }

      String block = parseStringToken(progress, "block=");
      return block != null && !block.isBlank() ? "mined " + n + " " + block : "mined " + n + " block(s)";
   }

   private static int parseToken(String progress, String token) {
      if (progress != null && token != null) {
         int i = progress.indexOf(token);
         if (i < 0) {
            return -1;
         }

         int start = i + token.length();
         int end = start;

         while (end < progress.length() && Character.isDigit(progress.charAt(end))) {
            end++;
         }

         if (end == start) {
            return -1;
         }

         try {
            return Integer.parseInt(progress.substring(start, end));
         } catch (NumberFormatException e) {
            return -1;
         }
      } else {
         return -1;
      }
   }

   private static String parseStringToken(String progress, String token) {
      if (progress != null && token != null) {
         int i = progress.indexOf(token);
         if (i < 0) {
            return null;
         }

         int start = i + token.length();
         int end = start;

         while (end < progress.length() && !Character.isWhitespace(progress.charAt(end))) {
            end++;
         }

         return end == start ? null : progress.substring(start, end);
      } else {
         return null;
      }
   }

   private static String gatherClause(String reason) {
      if (reason == null) {
         reason = "";
      }

      String r = reason.toLowerCase(Locale.ROOT);
      if (r.contains("full")) {
         return "gather partial: inventory full, some drops left on the ground";
      } else if (r.contains("timeout")) {
         return "gather partial: timed out, some reachable drops not collected";
      } else {
         return reason.isBlank() ? "gather partial" : "gather partial: " + reason;
      }
   }

   private static String depositClause(String reason) {
      if (reason == null) {
         reason = "";
      }

      String r = reason.toLowerCase(Locale.ROOT);
      if (r.contains("container_full")) {
         return "deposit partial: chest full, some items not stored";
      } else if (r.contains("remaining")) {
         return "deposit partial: some items of the request not stored";
      } else if (r.contains("nothing_to_deposit")) {
         return "deposit no-op: nothing was deposited — the requested item was not in inventory and agentic cannot mine or gather raw resources (it only picks up loose drops and stores them). Use 'get <item> <count>' to obtain it first, then deposit.";
      } else {
         return reason.isBlank() ? "deposit partial" : "deposit partial: " + reason;
      }
   }

   private static String waypointClause(AgenticRunRegistry.DegradationLevel level, String reason) {
      if (reason == null) {
         reason = "";
      }

      String r = reason.toLowerCase(Locale.ROOT);
      if (r.contains("index_update_failed_after_commit")) {
         return "waypoint saved after deposit, but its EllieGPS search index update failed";
      } else if (r.contains("index_repair_failed_no_change")) {
         return "waypoint was already current after deposit, but its EllieGPS search index repair failed";
      } else if (r.contains("json_commit_failed")) {
         return "waypoint not registered: EllieGPS could not commit authoritative waypoint data";
      } else if (r.contains("captured_waypoint_missing")) {
         return r.contains("index_degraded")
            ? "waypoint not registered: captured record changed before commit and the search index is degraded"
            : "waypoint not registered: captured record changed before commit";
      } else if (r.contains("rejected_type_conflict") || r.contains("rejected_target_conflict")) {
         return "waypoint not registered: a conflicting EllieGPS record was preserved";
      } else if (level == AgenticRunRegistry.DegradationLevel.PARTIAL) {
         return reason.isBlank() ? "waypoint not registered: scan failed after deposit" : "waypoint not registered: scan failed (" + reason + ")";
      } else if (r.contains("worldgen_loot")) {
         return "waypoint not registered: container is worldgen loot (origin_rejected)";
      } else if (r.contains("structure_piece")) {
         return "waypoint not registered: container is inside a structure piece (origin_rejected)";
      } else if (r.contains("origin_rejected") || r.contains("origin_unverified")) {
         return "waypoint not registered: origin could not be verified; use create_waypoint to register explicitly";
      } else if (r.contains("no_storage_target")) {
         return "waypoint not registered: no storage target was recorded for this run";
      } else if (r.contains("store_unavailable")) {
         return "waypoint not registered: EllieGPS store was unavailable";
      } else if (r.contains("dimension_mismatch")) {
         return "waypoint not registered: dimension mismatch between target and current world";
      } else if (r.contains("registrar_exception")) {
         return "waypoint not registered: internal error in auto-registrar (run was not affected)";
      } else {
         return reason.isBlank() ? "waypoint not registered" : "waypoint not registered: " + reason;
      }
   }

   private static String smeltClause(String reason) {
      if (reason == null) {
         reason = "";
      }

      String r = reason.toLowerCase(Locale.ROOT);
      if (r.contains("fuel_gather")) {
         if (r.contains("no_acquirable")) {
            return "smelt failed: tried to gather fuel automatically but none was acquirable (no coal/chests in range, no trees, or no pickaxe)";
         }

         Matcher m = Pattern.compile("collected=(\\d+)\\s+of\\s+(\\d+)").matcher(r);
         return m.find()
            ? "smelt partial: auto-gathered fuel but only enough for " + m.group(1) + " of " + m.group(2)
            : "smelt partial: auto-gathered fuel but only enough for part of the batch (" + reason + ")";
      } else if (r.contains("partial") || r.contains("out_of_fuel")) {
         return "smelt partial: ran out of fuel mid-batch — collected what finished (" + reason + ")";
      } else if (r.contains("no_fuel")) {
         return "smelt failed: no fuel was available to start the cook";
      } else if (r.contains("no_recipe")) {
         return "smelt failed: that item has no furnace/blast/smoke recipe (nothing was cooked)";
      } else if (r.contains("furnace_gone") || r.contains("station_gone")) {
         return "smelt failed: the furnace was removed while I was away (couldn't finish)";
      } else if (r.contains("tampered")) {
         return "smelt partial: the furnace contents changed while I was away — collected what I could";
      } else if (r.contains("stalled_timeout")) {
         return "smelt failed: the furnace chunk stopped ticking and the job timed out before it finished";
      } else if (r.contains("stalled")) {
         return "smelt incomplete: the furnace chunk stopped ticking — will finish when back near the furnace";
      } else if (r.contains("no_furnace")) {
         return "smelt failed: no furnace, blast furnace, or smoker was reachable";
      } else if (r.contains("no_input")) {
         return "smelt skipped: I don't have that item to smelt";
      } else {
         return reason.isBlank() ? "smelt incomplete" : "smelt incomplete: " + reason;
      }
   }

   private static String smithClause(String reason) {
      if (reason == null) {
         reason = "";
      }

      String r = reason.toLowerCase(Locale.ROOT);
      if (r.contains("partial")) {
         return "smith partial: upgraded some items but could not complete the full batch (" + reason + ")";
      } else if (r.contains("tampered")) {
         return "smith partial: smithing table or inventory changed mid-run — upgraded what was possible";
      } else if (r.contains("no_table")) {
         return "smith failed: no smithing table was reachable or placeable";
      } else if (r.contains("no_recipe")) {
         return "smith failed: nothing in the smithing registry produces that item";
      } else if (r.contains("missing_template")) {
         return "smith failed: could not gather the required template item (" + reason + ")";
      } else if (r.contains("missing_base")) {
         return "smith failed: could not gather the required base item (" + reason + ")";
      } else if (r.contains("missing_addition")) {
         return "smith failed: could not gather the required upgrade material (" + reason + ")";
      } else if (r.contains("setup_failed")) {
         return "smith failed: could not start the upgrade (" + reason + ")";
      } else if (r.contains("no_input")) {
         return "smith skipped: no item was specified or resolved";
      } else {
         return reason.isBlank() ? "smith incomplete" : "smith incomplete: " + reason;
      }
   }

   private static String mineClause(String reason, String progress) {
      if (reason == null) {
         reason = "";
      }

      String r = reason.toLowerCase(Locale.ROOT);
      if (r.contains("incorrect_tool") || r.contains("no_drops")) {
         int noDrops = parseToken(progress, "noDrops=");
         String block = parseStringToken(progress, "block=");
         String what = block != null && !block.isBlank() ? block : "block(s)";
         return noDrops > 0
            ? "broke " + noDrops + " " + what + " with an insufficient tool — no drops collected"
            : "broke a " + what + " with an insufficient tool — no drops collected";
      }

      if (r.contains("no_more_blocks_in_range")) {
         int mined = parseToken(progress, "mined=");
         String block = parseStringToken(progress, "block=");
         String what = block != null && !block.isBlank() ? block : "block(s)";
         return mined > 0
            ? "mine partial: mined " + mined + " " + what + " then exhausted range — no more within reach, relocate to mine more"
            : "mine partial: no more " + what + " within reach — relocate to mine more";
      }

      if (r.contains("timeout")) {
         int mined = parseToken(progress, "mined=");
         String block = parseStringToken(progress, "block=");
         String what = block != null && !block.isBlank() ? block : "block(s)";
         return mined > 0
            ? "mine partial: timed out after " + mined + " " + what + " — more may remain, retrying can continue"
            : "mine partial: timed out — more may remain, retrying can continue";
      }

      if (!r.contains("drops_unreachable")) {
         return reason.isBlank() ? "mine partial" : "mine partial: " + reason;
      }

      int mined = parseToken(progress, "mined=");
      if (mined < 0) {
         mined = 0;
      }

      int lost = parseToken(progress, "lostDrops=");
      if (lost < 0) {
         lost = 0;
      }

      String block = parseStringToken(progress, "block=");
      String what = block != null && !block.isBlank() ? block : "block(s)";
      return "mine partial: mined "
         + mined
         + " "
         + what
         + " but "
         + lost
         + " drop(s) were unreachable (fell into water/void) — the ore WAS broken but those items could not be collected";
   }

   private static String labelClause(String reason) {
      if (reason == null) {
         reason = "";
      }

      if (reason.startsWith("sign_craft_failed: ")) {
         return "chest labeling skipped: tried to craft a sign but could not ("
            + reason.substring("sign_craft_failed: ".length())
            + ") (chest stored, not labeled)";
      } else if (reason.equals("sign_craft_unavailable")) {
         return "chest labeling skipped: no sign item and sign crafting is disabled (chest stored, not labeled)";
      } else if (reason.equals("no_sign_item")) {
         return "chest labeling skipped: no sign item in inventory (chest stored, not labeled)";
      } else if (reason.equals("no_label_anchor")) {
         return "chest labeling skipped: no valid anchor to place a sign";
      } else {
         return reason.isBlank() ? "chest labeling skipped" : "chest labeling skipped: " + reason;
      }
   }

   private static String farmClause(String reason, String progress) {
      String safeReason = boundFarm(reason);
      String safeProgress = boundFarm(progress);
      if (!safeProgress.isBlank() && !safeReason.isBlank()) {
         return safeProgress + " (degraded: " + safeReason + ")";
      } else if (!safeProgress.isBlank()) {
         return safeProgress;
      } else {
         return safeReason.isBlank() ? "farming operation degraded" : "farming degraded: " + safeReason;
      }
   }

   private static String boundFarm(String value) {
      if (value == null) {
         return "";
      }

      String safe = value.replace('\n', ' ').replace('\r', ' ').strip();
      return safe.length() <= 512 ? safe : safe.substring(0, 512);
   }
}
