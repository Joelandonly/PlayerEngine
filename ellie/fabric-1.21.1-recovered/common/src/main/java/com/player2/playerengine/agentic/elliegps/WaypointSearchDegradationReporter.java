package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.AiConversationFeedback;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;

public final class WaypointSearchDegradationReporter {
   private static final Map<PlayerEngineController, EnumSet<WaypointSearchStatus>> REPORTED = new WeakHashMap<>();

   private WaypointSearchDegradationReporter() {
   }

   public static void reportOnce(PlayerEngineController controller, WaypointSearchStatus status) {
      if (controller != null && isDegraded(status)) {
         synchronized (REPORTED) {
            EnumSet<WaypointSearchStatus> statuses = REPORTED.computeIfAbsent(controller, ignored -> EnumSet.noneOf(WaypointSearchStatus.class));
            if (!statuses.add(status)) {
               return;
            }
         }

         Component playerMessage;
         String modelMessage;
         switch (status) {
            case FULL_STORE_FALLBACK:
               playerMessage = WaypointReportFormatter.searchFallbackComponent();
               modelMessage = WaypointReportFormatter.searchFallbackModel();
               break;
            case FAILED_SCAN_LIMIT:
               playerMessage = WaypointReportFormatter.searchScanLimitComponent();
               modelMessage = WaypointReportFormatter.searchScanLimitModel();
               break;
            case FAILED_STORE_UNAVAILABLE:
               playerMessage = WaypointReportFormatter.searchStoreUnavailableComponent();
               modelMessage = WaypointReportFormatter.searchStoreUnavailableModel();
               break;
            case FAILED_SEARCH_ERROR:
               playerMessage = WaypointReportFormatter.searchFailedComponent();
               modelMessage = WaypointReportFormatter.searchFailedModel();
               break;
            default:
               return;
         }

         controller.reportAgenticProgress(playerMessage, true);
         AiConversationFeedback.enqueueInfo(controller, WaypointReportFormatter.boundModel(modelMessage));
      }
   }

   private static boolean isDegraded(WaypointSearchStatus status) {
      return status == WaypointSearchStatus.FULL_STORE_FALLBACK
         || status == WaypointSearchStatus.FAILED_SCAN_LIMIT
         || status == WaypointSearchStatus.FAILED_STORE_UNAVAILABLE
         || status == WaypointSearchStatus.FAILED_SEARCH_ERROR;
   }
}
