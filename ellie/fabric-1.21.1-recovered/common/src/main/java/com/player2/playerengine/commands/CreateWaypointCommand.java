package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.agentic.elliegps.InventoryWaypointData;
import com.player2.playerengine.agentic.elliegps.WaypointIngestionService;
import com.player2.playerengine.agentic.elliegps.WaypointMutationResult;
import com.player2.playerengine.agentic.elliegps.WaypointMutationStatus;
import com.player2.playerengine.agentic.elliegps.WaypointOriginClassifier;
import com.player2.playerengine.agentic.elliegps.WaypointOriginEvidence;
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
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.MutableComponent;

public class CreateWaypointCommand extends Command {
   public CreateWaypointCommand() throws CommandException {
      super(
         "create_waypoint",
         "create_waypoint <x> <y> <z>. Registers (or refreshes) an inventory waypoint for the storage container at the given block coordinates. Navigates to the container, scans it, derives category keywords and a description, then persists the record to EllieGPS. An existing waypoint at the same position is refreshed with the latest snapshot. The waypoint can then be found via locate_waypoints or used by the counting service for material estimates.",
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

         String[] u = parser.getArgUnits();
         if (u.length < 3) {
            this.failEarly(mod, StorageAccessCode.INVALID_ARGUMENT, "usage: create_waypoint <x> <y> <z>");
         } else {
            int x;
            int y;
            int z;
            try {
               x = Integer.parseInt(u[0].trim());
               y = Integer.parseInt(u[1].trim());
               z = Integer.parseInt(u[2].trim());
            } catch (NumberFormatException e) {
               this.failEarly(mod, StorageAccessCode.INVALID_ARGUMENT, "x y z must be integers");
               return;
            }

            BlockPos queriedPos = new BlockPos(x, y, z);
            Vec3 botPos = mod.getEntity().position();
            double capSq = 13225.0;
            double distSq = botPos.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
            if (distSq > capSq) {
               String detail = ContainerResolver.formatPos(queriedPos)
                  + " is about "
                  + (int)Math.sqrt(distSq)
                  + " blocks away, beyond the 115-block travel cap";
               this.failEarly(mod, StorageAccessCode.CONTAINER_TOO_FAR, detail);
            } else {
               ServerLevel level = mod.getWorld();
               if (level == null) {
                  this.failEarly(mod, StorageAccessCode.CONTAINER_UNREACHABLE, "no world available");
               } else {
                  ContainerResolver.Resolution resolution = ContainerResolver.resolve(level, queriedPos);
                  if (!resolution.ok()) {
                     this.failEarly(mod, resolution.code(), resolution.detail());
                  } else {
                     BlockPos canonicalPos = resolution.resolved().canonicalPos();
                     BlockPos secondaryPos = resolution.resolved().secondaryPos();
                     WaypointOriginEvidence.Verdict verdict = WaypointOriginClassifier.classify(level, canonicalPos, secondaryPos);
                     if (verdict == WaypointOriginEvidence.Verdict.REJECT_WORLDGEN) {
                        String canonPosStr = ContainerResolver.formatPos(canonicalPos);
                        mod.reportAgenticProgress(WaypointReportFormatter.refusedWorldgenLootComponent(canonPosStr), true);
                        this.finishWithError(WaypointReportFormatter.refusedWorldgenLoot(canonPosStr));
                     } else if (verdict == WaypointOriginEvidence.Verdict.UNKNOWN) {
                        String canonPosStr = ContainerResolver.formatPos(canonicalPos);
                        mod.reportAgenticProgress(WaypointReportFormatter.refusedOriginUnverifiedComponent(canonPosStr), true);
                        this.finishWithError(WaypointReportFormatter.refusedOriginUnverified(canonPosStr));
                     } else {
                        boolean structureOverride = verdict == WaypointOriginEvidence.Verdict.REJECT_STRUCTURE;
                        EllieGPSStore storeSnapshot = EllieGPSStore.get();
                        String dimensionId = level.dimension().location().toString();
                        WaypointRecord existingRecord = storeSnapshot != null ? storeSnapshot.byPosition(dimensionId, canonicalPos) : null;
                        ScanContainerTask task = new ScanContainerTask(queriedPos, ScanMode.LIGHT, List.of());
                        ProjectTaskGateway.run(
                           mod,
                           task,
                           () -> {
                              if (task.result().isPresent()) {
                                 ContainerSnapshot snapshot = task.result().get();
                                 EllieGPSStore liveStore = EllieGPSStore.get();
                                 if (liveStore == null) {
                                    mod.reportAgenticProgress(Component.translatable("message.playerengine.elliegps.store_unavailable"), true);
                                    this.finishWithError("elliegps_store_unavailable: no active EllieGPS store");
                                    return;
                                 }

                                 WaypointRecord record = WaypointIngestionService.ingest(mod, snapshot, "explicit_create", existingRecord);
                                 WaypointMutationResult mutation = existingRecord != null
                                    ? liveStore.replace(existingRecord.id, record)
                                    : liveStore.upsert(record);
                                 switch (mutation.status()) {
                                    case FAILED_JSON_COMMIT:
                                    case REJECTED_TYPE_CONFLICT:
                                    case REJECTED_TARGET_CONFLICT:
                                    case NOT_FOUND:
                                    case NOT_FOUND_INDEX_DEGRADED:
                                    case FAILED_STORE_UNAVAILABLE:
                                       this.finishMutationFailure(mod, mutation.status());
                                       return;
                                    case COMMITTED:
                                    case COMMITTED_INDEX_DEGRADED:
                                    case NO_CHANGE:
                                    case NO_CHANGE_INDEX_DEGRADED:
                                    default:
                                       WaypointRecord authoritative = mutation.current() != null ? mutation.current() : record;
                                       boolean changed = mutation.status() == WaypointMutationStatus.COMMITTED
                                          || mutation.status() == WaypointMutationStatus.COMMITTED_INDEX_DEGRADED;
                                       boolean indexDegraded = mutation.status() == WaypointMutationStatus.COMMITTED_INDEX_DEGRADED
                                          || mutation.status() == WaypointMutationStatus.NO_CHANGE_INDEX_DEGRADED;
                                       boolean polishScheduled = WaypointIngestionService.schedulePolishAfterCommit(mod, authoritative);
                                       InventoryWaypointData invData = authoritative.inventoryData();
                                       boolean snapshotOmitted = invData == null || invData.snapshot == null;
                                       int itemTypes = 0;
                                       if (!snapshotOmitted && invData.snapshot.items != null) {
                                          itemTypes = invData.snapshot.items.size();
                                       }

                                       int kwCount = authoritative.keywords != null ? authoritative.keywords.size() : 0;
                                       String posStr = ContainerResolver.formatPos(snapshot.canonicalPos());
                                       String modelMsg = changed
                                          ? WaypointReportFormatter.waypointRegistered(posStr, itemTypes, kwCount, snapshotOmitted)
                                          : WaypointReportFormatter.waypointUnchanged(posStr);
                                       if (structureOverride) {
                                          modelMsg = modelMsg + " " + WaypointReportFormatter.structureOverrideNote();
                                       }

                                       if (polishScheduled) {
                                          modelMsg = modelMsg + " " + WaypointReportFormatter.descriptionPolishScheduledNote();
                                       }

                                       if (indexDegraded) {
                                          modelMsg = modelMsg + " " + WaypointReportFormatter.indexDegradedModel(changed);
                                       }

                                       AiConversationFeedback.enqueueInfo(mod, WaypointReportFormatter.boundModel(modelMsg));
                                       MutableComponent playerMsg = Component.empty();
                                       playerMsg.append(
                                          (Component)(changed
                                             ? WaypointReportFormatter.waypointRegisteredComponent(posStr, itemTypes, kwCount, snapshotOmitted)
                                             : WaypointReportFormatter.waypointUnchangedComponent(posStr))
                                       );
                                       if (structureOverride) {
                                          playerMsg.append(" ").append(WaypointReportFormatter.structureOverrideNoteComponent());
                                       }

                                       mod.reportAgenticProgress(playerMsg, true);
                                       if (indexDegraded) {
                                          mod.reportAgenticProgress(WaypointReportFormatter.indexDegradedComponent(changed), true);
                                       }

                                       Debug.logMessage(
                                          "waypoint-create ok id="
                                             + authoritative.id
                                             + " snapshotOmitted="
                                             + snapshotOmitted
                                             + " kw="
                                             + kwCount
                                             + " bot="
                                             + mod.getEntity().getName().getString()
                                       );
                                       if (!snapshotOmitted && !indexDegraded) {
                                          this.finish();
                                       } else {
                                          String note = snapshotOmitted ? "snapshot omitted (used slots > threshold); record is keyword-only" : "";
                                          if (indexDegraded) {
                                             note = note + (note.isEmpty() ? "" : "; ") + WaypointReportFormatter.indexDegradedModel(changed);
                                          }

                                          this.finishWithNote(WaypointReportFormatter.boundModel(note));
                                       }
                                 }
                              } else {
                                 ScanContainerTask.Failure failure = task.failure()
                                    .orElse(new ScanContainerTask.Failure(StorageAccessCode.CONTAINER_UNREACHABLE, "scan session ended without a result"));
                                 this.failEarly(mod, failure.code(), failure.detail());
                              }
                           }
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private void finishMutationFailure(PlayerEngineController mod, WaypointMutationStatus status) {
      switch (status) {
         case FAILED_JSON_COMMIT:
            mod.reportAgenticProgress(WaypointReportFormatter.jsonCommitFailedComponent(), true);
            this.finishWithError(WaypointReportFormatter.jsonCommitFailedModel());
            break;
         case REJECTED_TYPE_CONFLICT:
         case REJECTED_TARGET_CONFLICT:
            mod.reportAgenticProgress(WaypointReportFormatter.waypointConflictComponent(), true);
            this.finishWithError(WaypointReportFormatter.waypointConflictModel(status));
            break;
         case NOT_FOUND:
         case NOT_FOUND_INDEX_DEGRADED:
            mod.reportAgenticProgress(WaypointReportFormatter.mutationNotFoundComponent(), true);
            if (status == WaypointMutationStatus.NOT_FOUND_INDEX_DEGRADED) {
               mod.reportAgenticProgress(WaypointReportFormatter.indexDegradedComponent(false), true);
            }

            this.finishWithError(
               WaypointReportFormatter.boundModel(
                  WaypointReportFormatter.mutationNotFoundModel()
                     + (status == WaypointMutationStatus.NOT_FOUND_INDEX_DEGRADED ? " " + WaypointReportFormatter.indexDegradedModel(false) : "")
               )
            );
            break;
         case FAILED_STORE_UNAVAILABLE:
            mod.reportAgenticProgress(Component.translatable("message.playerengine.elliegps.store_unavailable"), true);
            this.finishWithError("elliegps_store_unavailable: no active EllieGPS store");
            break;
         case COMMITTED:
         case COMMITTED_INDEX_DEGRADED:
         case NO_CHANGE:
         case NO_CHANGE_INDEX_DEGRADED:
            throw new IllegalArgumentException("not a failure status: " + status);
      }
   }

   private void failEarly(PlayerEngineController mod, StorageAccessCode code, String detail) {
      String bounded = WaypointReportFormatter.boundReason(detail);
      Debug.logWarning("waypoint-create fail code=" + code.token() + " detail=" + bounded + " bot=" + mod.getEntity().getName().getString());
      mod.reportAgenticProgress(Component.translatable("message.playerengine.elliegps.create_fail", new Object[]{bounded}), true);
      this.finishWithError(WaypointReportFormatter.boundModel(code.token() + ": " + bounded));
   }
}
