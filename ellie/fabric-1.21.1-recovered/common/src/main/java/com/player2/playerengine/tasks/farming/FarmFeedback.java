package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.agentic.elliegps.WaypointMutationStatus;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class FarmFeedback {
   public static final int MAX_MODEL_LENGTH = 512;
   public static final int MAX_REASON_LENGTH = 160;
   public static final int MAX_IDENTIFIER_LENGTH = 96;
   public static final int MAX_CROP_ENTRIES = 12;

   private FarmFeedback() {
   }

   public static Component setupPlayer(FarmTaskOutcome outcome) {
      if (outcome == null) {
         return Component.translatable("message.playerengine.farming.setup.cancelled");
      }

      if (!outcome.terminal()) {
         return Component.translatable("message.playerengine.farming.setup.not_started");
      }

      if (outcome.successful()) {
         String key = isIndexDegraded(outcome) ? "message.playerengine.farming.setup.success_index_degraded" : "message.playerengine.farming.setup.success";
         return Component.translatable(key, new Object[]{formatCenter(outcome.center()), outcome.cleared(), outcome.filled(), outcome.tilled()});
      }

      Component phase = Component.translatable(phaseKey(outcome.phase()));
      Component reason = Component.translatable(setupPlayerReasonKey(outcome));
      if (exactSetupPlayerResultKey(outcome) != null) {
         return Component.translatable(exactSetupPlayerResultKey(outcome), new Object[]{formatCenter(outcome.center()), phase, reason});
      }

      MutableComponent result;
      if (outcome.center() == null) {
         result = Component.translatable("message.playerengine.farming.setup.failed", new Object[]{phase, reason});
      } else {
         result = Component.translatable(
            "message.playerengine.farming.setup.failed_at",
            new Object[]{formatCenter(outcome.center()), phase, reason, outcome.cleared(), outcome.filled(), outcome.tilled()}
         );
      }

      if (outcome.returnStatus().incomplete() && outcome.reason() != FarmTaskReason.RESOURCE_RETURN_FAILED) {
         result.append(" ").append(Component.translatable("message.playerengine.farming.setup.return_incomplete"));
      }

      return result;
   }

   public static String setupModel(FarmTaskOutcome outcome) {
      if (outcome == null) {
         return "farm setup was cancelled before a terminal outcome was recorded";
      }

      if (!outcome.terminal()) {
         return "farm setup could not start because the NPC was not available in-game";
      }

      String center = outcome.center() == null ? "unresolved center" : formatCenter(outcome.center());
      String counts = outcome.cleared() + " cleared, " + outcome.filled() + " filled, " + outcome.tilled() + " tilled";
      if (outcome.successful()) {
         String result = "farm setup succeeded at " + center + " (" + counts + ")";
         if (isIndexDegraded(outcome)) {
            result = result + "; the waypoint JSON was saved but EllieGPS indexing is degraded";
         }

         return bound(result, 512);
      } else if (!isExactSiteReason(outcome.reason())) {
         String result = "farm setup failed at "
            + center
            + " during "
            + humanize(outcome.phase().name())
            + ": "
            + setupFailureReasonForModel(outcome)
            + " ("
            + counts
            + ")";
         if (outcome.returnStatus().incomplete() && outcome.reason() != FarmTaskReason.RESOURCE_RETURN_FAILED) {
            result = result + "; the NPC could not return to the selected surface site and may still be away from the plot";
         }

         return bound(result, 512);
      } else {
         String result = "farm setup rejected the requested fixed 9x9 footprint centered at "
            + center
            + " during "
            + humanize(outcome.phase().name())
            + ": "
            + setupFailureReasonForModel(outcome);
         if (outcome.cleared() == 0 && outcome.filled() == 0 && outcome.tilled() == 0) {
            result = result + "; no blocks were changed";
         } else {
            result = result + " (" + counts + ")";
         }

         result = result
            + "; retrying this exact center unchanged will fail again; choose another dry, clear 9x9 center or run coordinate-less setup_farm for automatic nearby selection";
         return bound(result, 512);
      }
   }

   static String setupFailureReasonForModel(FarmTaskOutcome outcome) {
      if (outcome != null && outcome.reason() == FarmTaskReason.TYPE_CONFLICT) {
         return "another EllieGPS waypoint type already uses this center";
      }

      if (outcome != null && outcome.reason() == FarmTaskReason.RESOURCE_RETURN_FAILED) {
         return "resource acquisition succeeded, but return to the selected surface site failed";
      }

      if (outcome != null && outcome.phase() == FarmTaskPhase.ACQUIRE_RESOURCES && outcome.reason() == FarmTaskReason.INTERACTION_DENIED) {
         return "water pickup produced no verified water-bucket inventory change";
      }

      if (outcome != null && outcome.phase() == FarmTaskPhase.PLACE_WATER && outcome.reason() == FarmTaskReason.INTERACTION_DENIED) {
         return "water placement produced no verified source or bucket-inventory change";
      }

      if (outcome != null && outcome.reason() == FarmTaskReason.REPAIR_TOOL_UNAVAILABLE) {
         return "required farm-repair tools were not present in inventory or EllieGPS storage and could not be crafted";
      }

      if (outcome != null && isExactSiteReason(outcome.reason())) {
         return switch (outcome.reason()) {
            case EXACT_SITE_UNSAFE_SUPPORT -> "a support column is obstructed or has no dry, safe ground within the foundation depth";
            case EXACT_SITE_UNSAFE_OBSTRUCTION -> "the footprint contains a block entity, unbreakable block, or falling-block hazard";
            case EXACT_SITE_FLUID_CONFLICT -> "the footprint contains fluid outside the permitted center water cell";
            case EXACT_SITE_CROP_CONFLICT -> "the footprint contains a crop outside a supported farmland planting cell";
            case EXACT_SITE_REPAIR_LIMIT_EXCEEDED -> "the planned repair exceeds the farm construction budget";
            case EXACT_SITE_NO_SAFE_STANCE -> "the footprint has no safe farm-level stance for the required work";
            case EXACT_SITE_FREEZE_RISK -> "the center water would freeze at this exact site";
            default -> "the requested footprint failed an exact-site safety gate";
         };
      } else {
         if (outcome != null && outcome.reason() == FarmTaskReason.WATER_EVAPORATES) {
            return "water cannot remain placed in this ultra-warm dimension; choose a different dimension";
         }

         if (outcome != null && outcome.reason() == FarmTaskReason.NO_SUITABLE_SITE) {
            return "automatic nearby selection found no safe, sufficiently flat grass site";
         }

         FarmTaskReason reason = outcome != null && outcome.reason() != null ? outcome.reason() : FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
         return bound(humanize(reason.name()), 160);
      }
   }

   static boolean isExactSiteReason(FarmTaskReason reason) {
      return reason == FarmTaskReason.EXACT_SITE_UNSAFE_SUPPORT
         || reason == FarmTaskReason.EXACT_SITE_UNSAFE_OBSTRUCTION
         || reason == FarmTaskReason.EXACT_SITE_FLUID_CONFLICT
         || reason == FarmTaskReason.EXACT_SITE_CROP_CONFLICT
         || reason == FarmTaskReason.EXACT_SITE_REPAIR_LIMIT_EXCEEDED
         || reason == FarmTaskReason.EXACT_SITE_NO_SAFE_STANCE
         || reason == FarmTaskReason.EXACT_SITE_FREEZE_RISK;
   }

   static String exactSetupPlayerResultKey(FarmTaskOutcome outcome) {
      return outcome != null && isExactSiteReason(outcome.reason()) ? "message.playerengine.farming.setup.exact_rejected_at" : null;
   }

   public static boolean isIndexDegraded(FarmTaskOutcome outcome) {
      if (outcome != null && outcome.waypointResult() != null) {
         WaypointMutationStatus status = outcome.waypointResult().status();
         return status == WaypointMutationStatus.COMMITTED_INDEX_DEGRADED
            || status == WaypointMutationStatus.NO_CHANGE_INDEX_DEGRADED
            || status == WaypointMutationStatus.NOT_FOUND_INDEX_DEGRADED;
      } else {
         return false;
      }
   }

   public static Component harvestPlayer(HarvestFarmOutcome outcome) {
      if (outcome == null) {
         return Component.translatable("message.playerengine.farming.harvest.cancelled");
      }

      if (!outcome.terminal()) {
         return Component.translatable("message.playerengine.farming.harvest.not_started");
      }

      MutableComponent result;
      if (outcome.successful()) {
         result = Component.translatable(
            harvestBasePlayerKey(outcome),
            new Object[]{formatCenter(outcome.center()), outcome.matureFound(), outcome.harvested(), outcome.raceSkipped(), outcome.denied()}
         );
      } else if (outcome.center() == null) {
         result = Component.translatable(
            "message.playerengine.farming.harvest.failed",
            new Object[]{Component.translatable(phaseKey(outcome.phase())), Component.translatable(reasonKey(outcome.reason()))}
         );
      } else {
         result = Component.translatable(
            "message.playerengine.farming.harvest.failed_at",
            new Object[]{
               formatCenter(outcome.center()),
               Component.translatable(phaseKey(outcome.phase())),
               Component.translatable(reasonKey(outcome.reason())),
               outcome.matureFound(),
               outcome.harvested(),
               outcome.raceSkipped(),
               outcome.denied()
            }
         );
      }

      if (outcome.pickupItemUnitsLeft() > 0) {
         result.append(" ")
            .append(Component.translatable("message.playerengine.farming.harvest.pickup_incomplete", new Object[]{outcome.pickupItemUnitsLeft()}));
      }

      if (outcome.pickupEntityOverflow()) {
         result.append(" ").append(Component.translatable("message.playerengine.farming.harvest.pickup_overflow"));
      }

      if (isIndexDegraded(outcome)) {
         result.append(" ").append(Component.translatable("message.playerengine.farming.harvest.index_degraded"));
      }

      return result;
   }

   static String harvestBasePlayerKey(HarvestFarmOutcome outcome) {
      if (outcome == null) {
         return "message.playerengine.farming.harvest.cancelled";
      } else if (!outcome.terminal()) {
         return "message.playerengine.farming.harvest.not_started";
      } else if (outcome.successful()) {
         return "message.playerengine.farming.harvest.success";
      } else {
         return outcome.center() == null ? "message.playerengine.farming.harvest.failed" : "message.playerengine.farming.harvest.failed_at";
      }
   }

   public static FarmFeedback.HarvestCompletion harvestCompletion(HarvestFarmOutcome outcome) {
      if (outcome != null && outcome.terminal() && outcome.successful()) {
         return outcome.raceSkipped() <= 0 && outcome.pickupItemUnitsLeft() <= 0 && !outcome.pickupEntityOverflow() && !isIndexDegraded(outcome)
            ? FarmFeedback.HarvestCompletion.INFO
            : FarmFeedback.HarvestCompletion.NOTE;
      } else {
         return FarmFeedback.HarvestCompletion.ERROR;
      }
   }

   public static String harvestModel(HarvestFarmOutcome outcome) {
      if (outcome == null) {
         return "farm harvest was cancelled before a terminal outcome was recorded";
      }

      if (!outcome.terminal()) {
         return "farm harvest could not start because the NPC was not available in-game";
      }

      String center = outcome.center() == null ? "unresolved center" : formatCenter(outcome.center());
      String waypointStatus = outcome.waypointResult() == null ? "not_attempted" : outcome.waypointResult().status().name().toLowerCase(Locale.ROOT);
      String counts = "phase="
         + outcome.phase().name().toLowerCase(Locale.ROOT)
         + " waypoint="
         + waypointStatus
         + " mature="
         + outcome.matureFound()
         + " harvested="
         + outcome.harvested()
         + " raceSkipped="
         + outcome.raceSkipped()
         + " denied="
         + outcome.denied();
      StringBuilder result = new StringBuilder(
         outcome.successful()
            ? "farm harvest succeeded at " + center + " (" + counts + ")"
            : "farm harvest failed at "
               + center
               + " during "
               + humanize(outcome.phase().name())
               + ": "
               + bound(humanize(outcome.reason().name()), 160)
               + " ("
               + counts
               + ")"
      );
      if (outcome.pickupItemUnitsLeft() > 0) {
         result.append("; at least ").append(outcome.pickupItemUnitsLeft()).append(" newly dropped item units remain nearby");
      }

      if (outcome.pickupEntityOverflow()) {
         result.append("; additional new drop entities remain beyond the scan cap");
      }

      if (isIndexDegraded(outcome)) {
         result.append("; EllieGPS search indexing is degraded");
      }

      return bound(result.toString(), 512);
   }

   public static boolean isIndexDegraded(HarvestFarmOutcome outcome) {
      if (outcome != null && outcome.waypointResult() != null) {
         WaypointMutationStatus status = outcome.waypointResult().status();
         return status == WaypointMutationStatus.COMMITTED_INDEX_DEGRADED
            || status == WaypointMutationStatus.NO_CHANGE_INDEX_DEGRADED
            || status == WaypointMutationStatus.NOT_FOUND_INDEX_DEGRADED;
      } else {
         return false;
      }
   }

   public static String phaseKey(FarmTaskPhase phase) {
      FarmTaskPhase safe = phase == null ? FarmTaskPhase.FAILED : phase;
      return "message.playerengine.farming.phase." + safe.name().toLowerCase(Locale.ROOT);
   }

   public static String reasonKey(FarmTaskReason reason) {
      FarmTaskReason safe = reason == null ? FarmTaskReason.INTERNAL_CONTRACT_FAILURE : reason;
      return "message.playerengine.farming.reason." + safe.name().toLowerCase(Locale.ROOT);
   }

   static String setupPlayerReasonKey(FarmTaskOutcome outcome) {
      if (outcome != null && outcome.phase() == FarmTaskPhase.ACQUIRE_RESOURCES && outcome.reason() == FarmTaskReason.INTERACTION_DENIED) {
         return "message.playerengine.farming.reason.water_pickup_no_change";
      } else {
         return outcome != null && outcome.phase() == FarmTaskPhase.PLACE_WATER && outcome.reason() == FarmTaskReason.INTERACTION_DENIED
            ? "message.playerengine.farming.reason.water_placement_no_change"
            : reasonKey(outcome == null ? null : outcome.reason());
      }
   }

   public static String formatCenter(BlockPos center) {
      return center == null ? "? ? ?" : bound(center.getX() + " " + center.getY() + " " + center.getZ(), 96);
   }

   public static String bound(String value, int maximum) {
      String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').strip();
      return safe.length() <= maximum ? safe : safe.substring(0, maximum);
   }

   private static String humanize(String token) {
      return token.toLowerCase(Locale.ROOT).replace('_', ' ');
   }

   public enum HarvestCompletion {
      INFO,
      NOTE,
      ERROR;
   }
}
