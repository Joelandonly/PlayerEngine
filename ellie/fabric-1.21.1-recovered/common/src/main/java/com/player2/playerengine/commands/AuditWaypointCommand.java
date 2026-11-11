package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.agentic.elliegps.FarmObservationResult;
import com.player2.playerengine.agentic.elliegps.FarmWaypointObservation;
import com.player2.playerengine.agentic.elliegps.FarmWaypointService;
import com.player2.playerengine.agentic.elliegps.InventoryWaypointData;
import com.player2.playerengine.agentic.elliegps.WaypointIngestionService;
import com.player2.playerengine.agentic.elliegps.WaypointMutationResult;
import com.player2.playerengine.agentic.elliegps.WaypointMutationStatus;
import com.player2.playerengine.agentic.elliegps.WaypointRecord;
import com.player2.playerengine.agentic.elliegps.WaypointReportFormatter;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.containeraccess.ContainerResolver;
import com.player2.playerengine.containeraccess.ContainerSnapshot;
import com.player2.playerengine.containeraccess.ScanMode;
import com.player2.playerengine.containeraccess.StorageAccessCode;
import com.player2.playerengine.player2api.AiConversationFeedback;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
import com.player2.playerengine.tasks.container.ScanContainerTask;
import com.player2.playerengine.util.Debug;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class AuditWaypointCommand extends Command {
   public AuditWaypointCommand() throws CommandException {
      super(
         "audit_waypoint",
         "audit_waypoint <x> <y> <z>. Re-observes an existing EllieGPS waypoint and updates its stored inventory or farm data. Missing targets are marked stale; use delete_waypoint to remove them.",
         new Arg<>(Integer.class, "x"),
         new Arg<>(Integer.class, "y"),
         new Arg<>(Integer.class, "z")
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      if (!mod.getModSettings().getEllieGpsEnabled()) {
         mod.reportAgenticProgress(WaypointReportFormatter.ellieGpsDisabledPlayerComponent(), true);
         this.finishWithError(WaypointReportFormatter.ellieGpsDisabledModel());
      } else {
         EllieGPSStore quarantineStore = EllieGPSStore.get();
         if (quarantineStore != null && quarantineStore.consumeQuarantineNote()) {
            mod.reportAgenticProgress(WaypointReportFormatter.quarantineNoteComponent(), true);
         }

         String[] units = parser.getArgUnits();
         if (units.length < 3) {
            this.failEarly(mod, StorageAccessCode.INVALID_ARGUMENT, "usage: audit_waypoint <x> <y> <z>");
         } else {
            int x;
            int y;
            int z;
            try {
               x = Integer.parseInt(units[0].trim());
               y = Integer.parseInt(units[1].trim());
               z = Integer.parseInt(units[2].trim());
            } catch (NumberFormatException e) {
               this.failEarly(mod, StorageAccessCode.INVALID_ARGUMENT, "x y z must be integers");
               return;
            }

            BlockPos queriedPos = new BlockPos(x, y, z);
            String posStr = ContainerResolver.formatPos(queriedPos);
            EllieGPSStore store = EllieGPSStore.get();
            if (store == null) {
               this.failEarly(mod, StorageAccessCode.CONTAINER_UNREACHABLE, "EllieGPS store not available (no world loaded)");
            } else {
               ServerLevel level = mod.getWorld();
               if (level == null) {
                  this.failEarly(mod, StorageAccessCode.CONTAINER_UNREACHABLE, "no world available");
               } else {
                  String dimensionId = level.dimension().location().toString();
                  WaypointRecord existingRecord = store.byPosition(dimensionId, queriedPos);
                  if (existingRecord == null) {
                     String detail = "no waypoint at "
                        + posStr
                        + " in "
                        + dimensionId
                        + "; use create_waypoint "
                        + x
                        + " "
                        + y
                        + " "
                        + z
                        + " to register it first";
                     this.failWaypoint(mod, "waypoint_not_found", detail);
                  } else if ("farm".equals(existingRecord.type)) {
                     this.auditFarm(mod, existingRecord, posStr);
                  } else if (!"inventory".equals(existingRecord.type)) {
                     this.failWaypoint(
                        mod, "unsupported_waypoint_type", "unsupported waypoint type '" + WaypointReportFormatter.boundIdentifier(existingRecord.type) + "'"
                     );
                  } else {
                     Vec3 botPos = mod.getEntity().position();
                     double capSq = 13225.0;
                     double distSq = botPos.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
                     if (distSq > capSq) {
                        String detail = posStr + " is about " + (int)Math.sqrt(distSq) + " blocks away, beyond the 115-block travel cap";
                        this.failEarly(mod, StorageAccessCode.CONTAINER_TOO_FAR, detail);
                     } else {
                        WaypointRecord capturedRecord = existingRecord;
                        ScanContainerTask task = new ScanContainerTask(queriedPos, ScanMode.LIGHT, List.of());
                        ProjectTaskGateway.run(mod, task, () -> this.finishInventoryAudit(mod, task, capturedRecord, posStr));
                     }
                  }
               }
            }
         }
      }
   }

   private void finishInventoryAudit(PlayerEngineController mod, ScanContainerTask task, WaypointRecord capturedRecord, String posStr) {
      EllieGPSStore liveStore = EllieGPSStore.get();
      if (liveStore == null) {
         this.failWaypoint(mod, "elliegps_store_unavailable", "EllieGPS store became unavailable before the audit completed");
      } else if (!task.result().isPresent()) {
         ScanContainerTask.Failure failure = task.failure()
            .orElse(new ScanContainerTask.Failure(StorageAccessCode.CONTAINER_UNREACHABLE, "scan session ended without a result"));
         if (failure.code() == StorageAccessCode.CONTAINER_MISSING) {
            this.handleStaleMutation(mod, liveStore.markStale(capturedRecord.id), posStr, false);
         } else {
            this.failEarly(mod, failure.code(), failure.detail());
         }
      } else {
         ContainerSnapshot snapshot = task.result().get();
         String originToken = capturedRecord.origin != null && !capturedRecord.origin.isBlank() ? capturedRecord.origin : "explicit_create";
         WaypointRecord refreshed = WaypointIngestionService.ingest(mod, snapshot, originToken, capturedRecord);
         WaypointMutationResult mutation = liveStore.replace(capturedRecord.id, refreshed);
         this.handleInventoryRefreshResult(mod, mutation, refreshed, snapshot);
      }
   }

   private void auditFarm(PlayerEngineController mod, WaypointRecord capturedRecord, String posStr) {
      FarmObservationResult result = FarmWaypointService.observe(mod, capturedRecord);
      switch (result.status()) {
         case OBSERVED:
            String origin = capturedRecord.origin != null && !capturedRecord.origin.isBlank() ? capturedRecord.origin : "bot_placed";
            WaypointMutationResult mutation = FarmWaypointService.registerOrRefresh(result.observation(), origin);
            this.handleFarmRefreshResult(mod, mutation, result.observation(), posStr);
            break;
         case STALE_CENTER:
            EllieGPSStore liveStore = EllieGPSStore.get();
            if (liveStore == null) {
               this.failWaypoint(mod, "elliegps_store_unavailable", "EllieGPS store became unavailable before the farm could be marked stale");
               return;
            }

            this.handleStaleMutation(mod, liveStore.markStale(capturedRecord.id), posStr, true);
            break;
         case HANDLER_UNAVAILABLE:
         case UNLOADED:
         case UNSUPPORTED_DATA:
            this.failFarmObservation(mod, result);
      }
   }

   private void handleInventoryRefreshResult(PlayerEngineController mod, WaypointMutationResult mutation, WaypointRecord refreshed, ContainerSnapshot snapshot) {
      switch (mutation.status()) {
         case COMMITTED:
            this.finishInventoryRefresh(mod, mutation.current(), refreshed, snapshot, true, false);
            break;
         case COMMITTED_INDEX_DEGRADED:
            this.finishInventoryRefresh(mod, mutation.current(), refreshed, snapshot, true, true);
            break;
         case NO_CHANGE:
            this.finishInventoryRefresh(mod, mutation.current(), refreshed, snapshot, false, false);
            break;
         case NO_CHANGE_INDEX_DEGRADED:
            this.finishInventoryRefresh(mod, mutation.current(), refreshed, snapshot, false, true);
            break;
         case NOT_FOUND:
         case NOT_FOUND_INDEX_DEGRADED:
         case REJECTED_TYPE_CONFLICT:
         case REJECTED_TARGET_CONFLICT:
         case FAILED_STORE_UNAVAILABLE:
         case FAILED_JSON_COMMIT:
            this.finishMutationFailure(mod, mutation.status());
      }
   }

   private void finishInventoryRefresh(
      PlayerEngineController mod, WaypointRecord authoritative, WaypointRecord refreshed, ContainerSnapshot snapshot, boolean changed, boolean indexDegraded
   ) {
      WaypointRecord shown = authoritative != null ? authoritative : refreshed;
      InventoryWaypointData inventory = shown.inventoryData();
      boolean snapshotOmitted = inventory == null || inventory.snapshot == null;
      int itemTypes = !snapshotOmitted && inventory.snapshot.items != null ? inventory.snapshot.items.size() : 0;
      int keywordCount = shown.keywords == null ? 0 : shown.keywords.size();
      BlockPos shownPos = shown.canonicalBlockPos();
      String formattedPos = ContainerResolver.formatPos(shownPos != null ? shownPos : snapshot.canonicalPos());
      boolean polishScheduled = authoritative != null && WaypointIngestionService.schedulePolishAfterCommit(mod, authoritative);
      String modelMessage;
      Component playerMessage;
      if (changed) {
         modelMessage = WaypointReportFormatter.waypointRegistered(formattedPos, itemTypes, keywordCount, snapshotOmitted);
         playerMessage = WaypointReportFormatter.waypointRegisteredComponent(formattedPos, itemTypes, keywordCount, snapshotOmitted);
      } else {
         modelMessage = WaypointReportFormatter.waypointUnchanged(formattedPos);
         playerMessage = WaypointReportFormatter.waypointUnchangedComponent(formattedPos);
      }

      if (polishScheduled) {
         modelMessage = WaypointReportFormatter.boundModel(modelMessage + " " + WaypointReportFormatter.descriptionPolishScheduledNote());
      }

      ArrayList<String> completionNotes = new ArrayList<>();
      if (snapshotOmitted) {
         completionNotes.add("snapshot omitted because the used-slot threshold was exceeded");
      }

      this.finishSuccessfulMutation(mod, playerMessage, modelMessage, changed, indexDegraded, completionNotes);
      Debug.logMessage(
         "waypoint-audit ok id="
            + shown.id
            + " status="
            + (indexDegraded ? "index_degraded" : (changed ? "committed" : "no_change"))
            + " snapshotOmitted="
            + snapshotOmitted
            + " bot="
            + mod.getEntity().getName().getString()
      );
   }

   private void handleFarmRefreshResult(PlayerEngineController mod, WaypointMutationResult mutation, FarmWaypointObservation observation, String posStr) {
      switch (mutation.status()) {
         case COMMITTED:
            this.finishFarmRefresh(mod, observation, posStr, true, false);
            break;
         case COMMITTED_INDEX_DEGRADED:
            this.finishFarmRefresh(mod, observation, posStr, true, true);
            break;
         case NO_CHANGE:
            this.finishFarmRefresh(mod, observation, posStr, false, false);
            break;
         case NO_CHANGE_INDEX_DEGRADED:
            this.finishFarmRefresh(mod, observation, posStr, false, true);
            break;
         case NOT_FOUND:
         case NOT_FOUND_INDEX_DEGRADED:
         case REJECTED_TYPE_CONFLICT:
         case REJECTED_TARGET_CONFLICT:
         case FAILED_STORE_UNAVAILABLE:
         case FAILED_JSON_COMMIT:
            this.finishMutationFailure(mod, mutation.status());
      }
   }

   private void finishFarmRefresh(PlayerEngineController mod, FarmWaypointObservation observation, String posStr, boolean changed, boolean indexDegraded) {
      Component playerMessage;
      String modelMessage;
      if (changed) {
         playerMessage = WaypointReportFormatter.farmAuditUpdatedComponent(
            posStr, observation.farmlandCount(), observation.openSlots(), observation.crops().size()
         );
         modelMessage = WaypointReportFormatter.farmAuditUpdated(posStr, observation.farmlandCount(), observation.openSlots(), observation.crops().size());
      } else {
         playerMessage = WaypointReportFormatter.waypointUnchangedComponent(posStr);
         modelMessage = WaypointReportFormatter.waypointUnchanged(posStr);
      }

      this.finishSuccessfulMutation(mod, playerMessage, modelMessage, changed, indexDegraded, new ArrayList<>());
      Debug.logMessage(
         "waypoint-audit farm id="
            + WaypointRecord.idFor(observation.dimension(), observation.center())
            + " changed="
            + changed
            + " indexDegraded="
            + indexDegraded
      );
   }

   private void finishSuccessfulMutation(
      PlayerEngineController mod, Component playerMessage, String modelMessage, boolean changed, boolean indexDegraded, List<String> completionNotes
   ) {
      mod.reportAgenticProgress(playerMessage, true);
      AiConversationFeedback.enqueueInfo(mod, WaypointReportFormatter.boundModel(modelMessage));
      if (indexDegraded) {
         mod.reportAgenticProgress(WaypointReportFormatter.indexDegradedComponent(changed), true);
         completionNotes.add(WaypointReportFormatter.indexDegradedModel(changed));
      }

      if (completionNotes.isEmpty()) {
         this.finish();
      } else {
         this.finishWithNote(WaypointReportFormatter.boundModel(String.join("; ", completionNotes)));
      }
   }

   private void handleStaleMutation(PlayerEngineController mod, WaypointMutationResult mutation, String posStr, boolean farm) {
      switch (mutation.status()) {
         case COMMITTED:
            this.finishStaleSuccess(mod, posStr, false, true, farm);
            break;
         case COMMITTED_INDEX_DEGRADED:
            this.finishStaleSuccess(mod, posStr, true, true, farm);
            break;
         case NO_CHANGE:
            this.finishStaleSuccess(mod, posStr, false, false, farm);
            break;
         case NO_CHANGE_INDEX_DEGRADED:
            this.finishStaleSuccess(mod, posStr, true, false, farm);
            break;
         case NOT_FOUND:
         case NOT_FOUND_INDEX_DEGRADED:
         case REJECTED_TYPE_CONFLICT:
         case REJECTED_TARGET_CONFLICT:
         case FAILED_STORE_UNAVAILABLE:
         case FAILED_JSON_COMMIT:
            this.finishMutationFailure(mod, mutation.status());
      }
   }

   private void finishStaleSuccess(PlayerEngineController mod, String posStr, boolean indexDegraded, boolean changed, boolean farm) {
      String staleMessage = farm ? WaypointReportFormatter.farmWaypointStaleMissing(posStr) : WaypointReportFormatter.waypointStaleMissing(posStr);
      mod.reportAgenticProgress(
         (Component)(farm ? WaypointReportFormatter.farmWaypointStaleMissingComponent(posStr) : WaypointReportFormatter.waypointStaleMissingComponent(posStr)),
         true
      );
      if (indexDegraded) {
         mod.reportAgenticProgress(WaypointReportFormatter.indexDegradedComponent(changed), true);
         staleMessage = WaypointReportFormatter.boundModel(staleMessage + " " + WaypointReportFormatter.indexDegradedModel(changed));
      }

      this.finishWithNote(staleMessage);
   }

   private void finishMutationFailure(PlayerEngineController mod, WaypointMutationStatus status) {
      switch (status) {
         case COMMITTED:
         case COMMITTED_INDEX_DEGRADED:
         case NO_CHANGE:
         case NO_CHANGE_INDEX_DEGRADED:
            throw new IllegalArgumentException("not a failed mutation status: " + status);
         case NOT_FOUND:
            this.finishNotFoundMutation(mod, false);
            break;
         case NOT_FOUND_INDEX_DEGRADED:
            this.finishNotFoundMutation(mod, true);
            break;
         case REJECTED_TYPE_CONFLICT:
         case REJECTED_TARGET_CONFLICT:
            mod.reportAgenticProgress(WaypointReportFormatter.waypointConflictComponent(), true);
            this.finishWithError(WaypointReportFormatter.waypointConflictModel(status));
            break;
         case FAILED_STORE_UNAVAILABLE:
            this.failWaypoint(mod, "elliegps_store_unavailable", "EllieGPS store is unavailable; the waypoint was not changed");
            break;
         case FAILED_JSON_COMMIT:
            mod.reportAgenticProgress(WaypointReportFormatter.jsonCommitFailedComponent(), true);
            this.finishWithError(WaypointReportFormatter.jsonCommitFailedModel());
      }
   }

   private void finishNotFoundMutation(PlayerEngineController mod, boolean indexDegraded) {
      String modelMessage = WaypointReportFormatter.mutationNotFoundModel();
      mod.reportAgenticProgress(WaypointReportFormatter.mutationNotFoundComponent(), true);
      if (indexDegraded) {
         mod.reportAgenticProgress(WaypointReportFormatter.indexDegradedComponent(false), true);
         modelMessage = WaypointReportFormatter.boundModel(modelMessage + " " + WaypointReportFormatter.indexDegradedModel(false));
      }

      this.finishWithError(modelMessage);
   }

   private void failFarmObservation(PlayerEngineController mod, FarmObservationResult result) {
      Debug.logWarning(
         "waypoint-audit farm observation status=" + result.status() + " reason=" + WaypointReportFormatter.boundReason(result.controlledReason())
      );
      mod.reportAgenticProgress(WaypointReportFormatter.farmObservationFailedComponent(result), true);
      this.finishWithError(WaypointReportFormatter.farmObservationFailed(result));
   }

   private void failEarly(PlayerEngineController mod, StorageAccessCode code, String detail) {
      this.failWaypoint(mod, code.token(), detail);
   }

   private void failWaypoint(PlayerEngineController mod, String code, String detail) {
      String boundedDetail = WaypointReportFormatter.boundReason(detail);
      Debug.logWarning("waypoint-audit fail code=" + code + " detail=" + boundedDetail + " bot=" + mod.getEntity().getName().getString());
      mod.reportAgenticProgress(WaypointReportFormatter.auditFailedComponent(boundedDetail), true);
      this.finishWithError(WaypointReportFormatter.boundModel(WaypointReportFormatter.boundIdentifier(code) + ": " + boundedDetail));
   }
}
