package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.AgenticExecutionContext;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.AgenticStorageTarget;
import com.player2.playerengine.containeraccess.ContainerResolver;
import com.player2.playerengine.containeraccess.ContainerScanService;
import com.player2.playerengine.containeraccess.ScanMode;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class WaypointAutoRegistrar {
   private WaypointAutoRegistrar() {
   }

   public static void afterDeposit(AgenticExecutionContext context, PlayerEngineController mod) {
      try {
         afterDepositImpl(context, mod);
      } catch (Exception e) {
         PlayerEngine.LOGGER
            .warn("WaypointAutoRegistrar: unexpected exception (run continues): {}", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), e);

         try {
            context.runState().setWaypointDegraded(AgenticRunRegistry.DegradationLevel.SKIPPED, "registrar_exception");
            mod.reportAgenticProgress(WaypointReportFormatter.autoSkippedLine("registrar_exception"), false);
         } catch (Exception var4) {
         }
      }
   }

   private static void afterDepositImpl(AgenticExecutionContext context, PlayerEngineController mod) {
      if (!mod.getModSettings().getEllieGpsEnabled()) {
         skip(context, mod, "elliegps_disabled", AgenticRunRegistry.DegradationLevel.SKIPPED);
      } else {
         Optional<AgenticStorageTarget> targetOpt = context.memory().storageTarget();
         if (targetOpt.isEmpty()) {
            skip(context, mod, "no_storage_target", AgenticRunRegistry.DegradationLevel.SKIPPED);
         } else {
            AgenticStorageTarget target = targetOpt.get();
            Optional<WaypointOriginEvidence> evidenceOpt = context.memory().waypointOriginEvidence();
            if (evidenceOpt.isEmpty()) {
               skip(context, mod, "origin_unverified", AgenticRunRegistry.DegradationLevel.SKIPPED);
            } else {
               WaypointOriginEvidence evidence = evidenceOpt.get();
               if (!evidence.permitsRegistration()) {
                  String reasonToken = evidence.reasonToken() != null && !evidence.reasonToken().isBlank() ? evidence.reasonToken() : "origin_rejected";
                  skip(context, mod, reasonToken, AgenticRunRegistry.DegradationLevel.SKIPPED);
               } else {
                  BlockPos pos = target.pos();
                  ServerLevel level = mod.getWorld();
                  String dimensionId = level != null ? level.dimension().location().toString() : null;
                  if (dimensionId != null && dimensionId.equals(target.dimension())) {
                     EllieGPSStore preStore = EllieGPSStore.get();
                     boolean depositWasNoOp = context.runState().getDepositDegradation() == AgenticRunRegistry.DegradationLevel.SKIPPED
                        && context.runState().getDepositDegradationReason() != null
                        && context.runState().getDepositDegradationReason().contains("nothing_to_deposit");
                     if (depositWasNoOp && preStore != null && preStore.byPosition(dimensionId, pos) != null) {
                        PlayerEngine.LOGGER
                           .debug(
                              "WaypointAutoRegistrar: deposit no-op and waypoint already registered at {}; skipping redundant re-registration.",
                              ContainerResolver.formatPos(pos)
                           );
                     } else {
                        ContainerScanService.ScanOutcome outcome = ContainerScanService.scan(mod, pos, ScanMode.LIGHT, null);
                        if (!outcome.ok()) {
                           String code = outcome.code() != null ? outcome.code().token() : "scan_failed";
                           PlayerEngine.LOGGER
                              .warn(
                                 "WaypointAutoRegistrar: in-place scan failed at {} ({}): {}; recording PARTIAL degradation.",
                                 ContainerResolver.formatPos(pos),
                                 code,
                                 outcome.detail()
                              );
                           context.runState().setWaypointDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, code);
                           mod.reportAgenticProgress(WaypointReportFormatter.autoSkippedLine("scan failed: " + code), true);
                        } else {
                           String originToken = evidence.placedByBot() ? "bot_placed" : "heuristic_pass";
                           EllieGPSStore store = EllieGPSStore.get();
                           if (store == null) {
                              skip(context, mod, "store_unavailable", AgenticRunRegistry.DegradationLevel.SKIPPED);
                           } else {
                              WaypointRecord existing = store.byPosition(dimensionId, pos);
                              WaypointRecord record = WaypointIngestionService.ingest(mod, outcome.snapshot(), originToken, existing);
                              WaypointMutationResult mutation = existing != null ? store.replace(existing.id, record) : store.upsert(record);
                              switch (mutation.status()) {
                                 case COMMITTED_INDEX_DEGRADED:
                                 case NO_CHANGE_INDEX_DEGRADED:
                                    String reason = mutation.status() == WaypointMutationStatus.COMMITTED_INDEX_DEGRADED
                                       ? "index_update_failed_after_commit"
                                       : "index_repair_failed_no_change";
                                    context.runState().setWaypointDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, reason);
                                    mod.reportAgenticProgress(
                                       WaypointReportFormatter.indexDegradedComponent(mutation.status() == WaypointMutationStatus.COMMITTED_INDEX_DEGRADED),
                                       true
                                    );
                                 case COMMITTED:
                                 case NO_CHANGE:
                                 default:
                                    WaypointRecord authoritative = mutation.current() != null ? mutation.current() : record;
                                    WaypointIngestionService.schedulePolishAfterCommit(mod, authoritative);
                                    String posStr = ContainerResolver.formatPos(pos);
                                    mod.reportAgenticProgress(WaypointReportFormatter.autoRegisteredMilestone(posStr), true);
                                    PlayerEngine.LOGGER
                                       .info("WaypointAutoRegistrar: auto-registered waypoint id={} at {} origin={}.", authoritative.id, posStr, originToken);
                                    return;
                                 case FAILED_JSON_COMMIT:
                                    skip(context, mod, "json_commit_failed", AgenticRunRegistry.DegradationLevel.PARTIAL);
                                    return;
                                 case NOT_FOUND:
                                 case NOT_FOUND_INDEX_DEGRADED:
                                    skip(
                                       context,
                                       mod,
                                       mutation.status() == WaypointMutationStatus.NOT_FOUND_INDEX_DEGRADED
                                          ? "captured_waypoint_missing_index_degraded"
                                          : "captured_waypoint_missing",
                                       AgenticRunRegistry.DegradationLevel.PARTIAL
                                    );
                                    return;
                                 case REJECTED_TYPE_CONFLICT:
                                 case REJECTED_TARGET_CONFLICT:
                                    skip(context, mod, mutation.status().name().toLowerCase(Locale.ROOT), AgenticRunRegistry.DegradationLevel.PARTIAL);
                                    return;
                                 case FAILED_STORE_UNAVAILABLE:
                                    skip(context, mod, "store_unavailable", AgenticRunRegistry.DegradationLevel.SKIPPED);
                              }
                           }
                        }
                     }
                  } else {
                     skip(context, mod, "dimension_mismatch", AgenticRunRegistry.DegradationLevel.SKIPPED);
                  }
               }
            }
         }
      }
   }

   private static void skip(AgenticExecutionContext context, PlayerEngineController mod, String reasonToken, AgenticRunRegistry.DegradationLevel level) {
      try {
         context.runState().setWaypointDegraded(level, reasonToken);
         mod.reportAgenticProgress(WaypointReportFormatter.autoSkippedLine(reasonToken), false);
         PlayerEngine.LOGGER.debug("WaypointAutoRegistrar: skipped auto-registration (level={} reason={}).", level, reasonToken);
      } catch (Exception e) {
         PlayerEngine.LOGGER.warn("WaypointAutoRegistrar: skip() threw: {}", e.getMessage());
      }
   }
}
