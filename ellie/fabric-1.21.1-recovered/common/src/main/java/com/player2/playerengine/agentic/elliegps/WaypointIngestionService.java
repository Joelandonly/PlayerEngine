package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.containeraccess.ContainerSnapshot;
import com.player2.playerengine.containeraccess.ItemCount;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

public final class WaypointIngestionService {
   private static final ExecutorService INGESTION_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r, "elliegps-ingestion");
         t.setDaemon(true);
         return t;
      }
   });
   private static final int MAX_DESC_ITEMS = 5;

   private WaypointIngestionService() {
   }

   public static WaypointRecord ingest(PlayerEngineController mod, ContainerSnapshot snapshot, String originToken, WaypointRecord existingRecord) {
      String dimensionId = snapshot.dimensionId();
      BlockPos canonical = snapshot.canonicalPos();
      BlockPos secondary = snapshot.secondaryPos();
      String kindToken = snapshot.kind().token();
      int usedSlots = snapshot.totalSlots() - snapshot.emptySlots();
      int threshold = mod.getModSettings().getEllieGpsSnapshotSlotThreshold();
      boolean snapshotOmitted = usedSlots > threshold;
      List<String> keywords = WaypointItemCategorizer.categorize(snapshot.aggregate());
      InventoryWaypointData.Snapshot snapshotPayload = null;
      if (!snapshotOmitted) {
         snapshotPayload = new InventoryWaypointData.Snapshot(
            snapshot.totalSlots(), snapshot.emptySlots(), List.copyOf(snapshot.aggregate()), snapshot.gameTime()
         );
      }

      int[] secondaryArr = null;
      if (secondary != null) {
         secondaryArr = new int[]{secondary.getX(), secondary.getY(), secondary.getZ()};
      }

      InventoryWaypointData invData = new InventoryWaypointData(secondaryArr, kindToken, snapshotPayload);
      String deterministicDesc = buildDeterministicDescription(kindToken, canonical, snapshot.aggregate(), snapshot.emptySlots(), snapshotOmitted);
      WaypointRecord record = new WaypointRecord();
      record.id = WaypointRecord.idFor(dimensionId, canonical);
      record.type = "inventory";
      record.dimension = dimensionId;
      record.pos = new int[]{canonical.getX(), canonical.getY(), canonical.getZ()};
      record.description = deterministicDesc;
      record.keywords = keywords;
      record.origin = originToken != null ? originToken : "explicit_create";
      record.stale = false;
      record.data = invData;
      long now = snapshot.gameTime();
      record.createdGameTime = existingRecord != null && existingRecord.createdGameTime > 0L ? existingRecord.createdGameTime : now;
      record.updatedGameTime = now;
      record.inheritUnknownFieldsFrom(existingRecord);
      return record;
   }

   public static boolean schedulePolishAfterCommit(PlayerEngineController mod, WaypointRecord committedRecord) {
      return false;
   }

   private static String buildDeterministicDescription(
      String kindToken, BlockPos canonical, List<ItemCount> aggregate, int emptySlots, boolean snapshotOmitted
   ) {
      StringBuilder sb = new StringBuilder();
      sb.append(kindToken.replace('_', ' '));
      sb.append(" at (");
      sb.append(canonical.getX()).append(',').append(canonical.getY()).append(',').append(canonical.getZ()).append(')');
      if (!aggregate.isEmpty()) {
         sb.append(": ");
         int shown = 0;

         for (ItemCount ic : aggregate) {
            if (shown >= 5) {
               sb.append(", ...");
               break;
            }

            if (shown > 0) {
               sb.append(", ");
            }

            String displayName = displayName(ic.registryId());
            sb.append(ic.count()).append(' ').append(displayName);
            shown++;
         }
      }

      if (emptySlots > 0) {
         sb.append(aggregate.isEmpty() ? ": " : ", ");
         sb.append(emptySlots).append(" empty slot").append(emptySlots == 1 ? "" : "s");
      }

      if (snapshotOmitted) {
         sb.append(" (keyword-only; snapshot threshold exceeded)");
      }

      return sb.toString();
   }

   private static String displayName(String registryId) {
      if (registryId == null) {
         return "";
      }

      int colon = registryId.indexOf(58);
      String path = colon >= 0 ? registryId.substring(colon + 1) : registryId;
      return path.replace('_', ' ');
   }

   private static boolean schedulePolish(PlayerEngineController mod, WaypointRecord record, List<String> keywords) {
      return false;
   }

   private static MinecraftServer resolveServer(PlayerEngineController mod) {
      try {
         if (mod.getPlayer() != null && mod.getPlayer().getServer() != null) {
            return mod.getPlayer().getServer();
         }
      } catch (Exception e) {
         PlayerEngine.LOGGER.debug("EllieGPS ingestion: could not resolve server: {}", e.getMessage());
      }

      return null;
   }
}
