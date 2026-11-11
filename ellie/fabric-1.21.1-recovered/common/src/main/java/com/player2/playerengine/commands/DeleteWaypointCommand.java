package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.agentic.elliegps.WaypointMutationResult;
import com.player2.playerengine.agentic.elliegps.WaypointMutationStatus;
import com.player2.playerengine.agentic.elliegps.WaypointRecord;
import com.player2.playerengine.agentic.elliegps.WaypointReportFormatter;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.containeraccess.ContainerResolver;
import com.player2.playerengine.containeraccess.StorageAccessCode;
import com.player2.playerengine.player2api.AiConversationFeedback;
import com.player2.playerengine.util.Debug;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class DeleteWaypointCommand extends Command {
   public DeleteWaypointCommand() throws CommandException {
      super(
         "delete_waypoint",
         "delete_waypoint <x> <y> <z>. Removes the EllieGPS waypoint record whose stored canonical or secondary position equals (x,y,z) in the bot's current dimension. Does not navigate; works even when the container block is gone. The only explicit delete path — EllieGPS never auto-deletes records (use after audit_waypoint reports stale).",
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
            this.fail(mod, StorageAccessCode.INVALID_ARGUMENT, "usage: delete_waypoint <x> <y> <z>");
         } else {
            int x;
            int y;
            int z;
            try {
               x = Integer.parseInt(u[0].trim());
               y = Integer.parseInt(u[1].trim());
               z = Integer.parseInt(u[2].trim());
            } catch (NumberFormatException e) {
               this.fail(mod, StorageAccessCode.INVALID_ARGUMENT, "x y z must be integers");
               return;
            }

            BlockPos pos = new BlockPos(x, y, z);
            EllieGPSStore store = EllieGPSStore.get();
            if (store == null) {
               this.fail(mod, StorageAccessCode.CONTAINER_UNREACHABLE, "EllieGPS store not available (no world loaded)");
            } else {
               ServerLevel level = mod.getWorld();
               if (level == null) {
                  this.fail(mod, StorageAccessCode.CONTAINER_UNREACHABLE, "no world available");
               } else {
                  String dimensionId = level.dimension().location().toString();
                  String posStr = ContainerResolver.formatPos(pos);
                  WaypointRecord record = store.byPosition(dimensionId, pos);
                  if (record == null) {
                     mod.reportAgenticProgress(WaypointReportFormatter.noWaypointAtComponent(posStr, dimensionId), true);
                     this.finishWithError(WaypointReportFormatter.noWaypointAt(posStr, dimensionId));
                  } else {
                     String deletedId = record.id;
                     WaypointMutationResult mutation = store.delete(deletedId);
                     switch (mutation.status()) {
                        case COMMITTED:
                        case COMMITTED_INDEX_DEGRADED:
                        default:
                           boolean indexDegraded = mutation.status() == WaypointMutationStatus.COMMITTED_INDEX_DEGRADED;
                           String modelResult = WaypointReportFormatter.waypointDeleted(posStr, dimensionId);
                           if (indexDegraded) {
                              modelResult = modelResult + " " + WaypointReportFormatter.indexDegradedModel(true);
                           }

                           AiConversationFeedback.enqueueInfo(mod, WaypointReportFormatter.boundModel(modelResult));
                           mod.reportAgenticProgress(WaypointReportFormatter.waypointDeletedComponent(posStr, dimensionId), true);
                           if (indexDegraded) {
                              mod.reportAgenticProgress(WaypointReportFormatter.indexDegradedComponent(true), true);
                           }

                           Debug.logMessage("waypoint-delete ok id=" + deletedId + " bot=" + mod.getEntity().getName().getString());
                           if (indexDegraded) {
                              this.finishWithNote(WaypointReportFormatter.indexDegradedModel(true));
                           } else {
                              this.finish();
                           }

                           return;
                        case NOT_FOUND:
                        case NOT_FOUND_INDEX_DEGRADED:
                        case NO_CHANGE:
                        case NO_CHANGE_INDEX_DEGRADED:
                           mod.reportAgenticProgress(WaypointReportFormatter.mutationNotFoundComponent(), true);
                           boolean degraded = mutation.status() == WaypointMutationStatus.NOT_FOUND_INDEX_DEGRADED
                              || mutation.status() == WaypointMutationStatus.NO_CHANGE_INDEX_DEGRADED;
                           if (degraded) {
                              mod.reportAgenticProgress(WaypointReportFormatter.indexDegradedComponent(false), true);
                           }

                           this.finishWithError(
                              WaypointReportFormatter.boundModel(
                                 WaypointReportFormatter.mutationNotFoundModel() + (degraded ? " " + WaypointReportFormatter.indexDegradedModel(false) : "")
                              )
                           );
                           return;
                        case FAILED_JSON_COMMIT:
                           mod.reportAgenticProgress(WaypointReportFormatter.jsonCommitFailedComponent(), true);
                           this.finishWithError(WaypointReportFormatter.jsonCommitFailedModel());
                           return;
                        case REJECTED_TYPE_CONFLICT:
                        case REJECTED_TARGET_CONFLICT:
                           mod.reportAgenticProgress(WaypointReportFormatter.waypointConflictComponent(), true);
                           this.finishWithError(WaypointReportFormatter.waypointConflictModel(mutation.status()));
                           return;
                        case FAILED_STORE_UNAVAILABLE:
                           this.fail(mod, StorageAccessCode.CONTAINER_UNREACHABLE, "EllieGPS store not available (no world loaded)");
                     }
                  }
               }
            }
         }
      }
   }

   private void fail(PlayerEngineController mod, StorageAccessCode code, String detail) {
      String bounded = WaypointReportFormatter.boundReason(detail);
      Debug.logWarning("waypoint-delete fail code=" + code.token() + " detail=" + bounded + " bot=" + mod.getEntity().getName().getString());
      mod.reportAgenticProgress(Component.translatable("message.playerengine.elliegps.delete_fail", new Object[]{bounded}), true);
      this.finishWithError(WaypointReportFormatter.boundModel(code.token() + ": " + bounded));
   }
}
