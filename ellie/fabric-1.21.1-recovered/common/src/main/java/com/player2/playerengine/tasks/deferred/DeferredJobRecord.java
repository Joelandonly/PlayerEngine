package com.player2.playerengine.tasks.deferred;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngine;
import net.minecraft.core.BlockPos;

public final class DeferredJobRecord {
   public static final int JOB_SCHEMA_VERSION = 1;
   public int schemaVersion = 1;
   public String id;
   public String kind;
   public DeferredJobRecord.Status status = DeferredJobRecord.Status.PENDING;
   public int[] pos;
   public String dimension;
   public String inputItem;
   public String outputItem;
   public int inputCount;
   public int expectedOutputCount;
   public String fuelItem;
   public int fuelCount;
   public long startGameTime;
   public long etaGameTime;
   public String owningBotUuid;
   public String degradeReason = "";
   transient JsonObject rawSource;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   public static String idFor(String dimensionId, BlockPos furnacePos) {
      return dimensionId + "|" + furnacePos.getX() + "," + furnacePos.getY() + "," + furnacePos.getZ();
   }

   public BlockPos furnaceBlockPos() {
      return this.pos != null && this.pos.length >= 3 ? new BlockPos(this.pos[0], this.pos[1], this.pos[2]) : null;
   }

   public JsonElement toJson() {
      JsonObject envelope = this.rawSource != null ? this.rawSource.deepCopy() : new JsonObject();
      envelope.addProperty("schemaVersion", this.schemaVersion);
      envelope.addProperty("id", this.id != null ? this.id : "");
      envelope.addProperty("kind", this.kind != null ? this.kind : "");
      envelope.addProperty("status", this.status != null ? this.status.name() : DeferredJobRecord.Status.PENDING.name());
      JsonArray posArr = new JsonArray();
      if (this.pos != null && this.pos.length >= 3) {
         posArr.add(this.pos[0]);
         posArr.add(this.pos[1]);
         posArr.add(this.pos[2]);
      }

      envelope.add("pos", posArr);
      envelope.addProperty("dimension", this.dimension != null ? this.dimension : "");
      envelope.addProperty("inputItem", this.inputItem != null ? this.inputItem : "");
      envelope.addProperty("outputItem", this.outputItem != null ? this.outputItem : "");
      envelope.addProperty("inputCount", this.inputCount);
      envelope.addProperty("expectedOutputCount", this.expectedOutputCount);
      envelope.addProperty("fuelItem", this.fuelItem != null ? this.fuelItem : "");
      envelope.addProperty("fuelCount", this.fuelCount);
      envelope.addProperty("startGameTime", this.startGameTime);
      envelope.addProperty("etaGameTime", this.etaGameTime);
      envelope.addProperty("owningBotUuid", this.owningBotUuid != null ? this.owningBotUuid : "");
      envelope.addProperty("degradeReason", this.degradeReason != null ? this.degradeReason : "");
      return envelope;
   }

   public static DeferredJobRecord fromJson(JsonElement element) {
      if (element != null && element.isJsonObject()) {
         try {
            JsonObject obj = element.getAsJsonObject();
            DeferredJobRecord r = new DeferredJobRecord();
            r.rawSource = obj.deepCopy();
            r.schemaVersion = obj.has("schemaVersion") ? obj.get("schemaVersion").getAsInt() : 0;
            r.id = obj.has("id") ? obj.get("id").getAsString() : null;
            r.kind = obj.has("kind") ? obj.get("kind").getAsString() : null;
            if (obj.has("status")) {
               try {
                  r.status = DeferredJobRecord.Status.valueOf(obj.get("status").getAsString());
               } catch (IllegalArgumentException e) {
                  r.status = DeferredJobRecord.Status.PENDING;
               }
            } else {
               r.status = DeferredJobRecord.Status.PENDING;
            }

            if (obj.has("pos") && obj.get("pos").isJsonArray()) {
               JsonArray arr = obj.get("pos").getAsJsonArray();
               if (arr.size() == 3) {
                  r.pos = new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt()};
               }
            }

            r.dimension = obj.has("dimension") ? obj.get("dimension").getAsString() : null;
            r.inputItem = obj.has("inputItem") ? obj.get("inputItem").getAsString() : null;
            r.outputItem = obj.has("outputItem") ? obj.get("outputItem").getAsString() : null;
            r.inputCount = obj.has("inputCount") ? obj.get("inputCount").getAsInt() : 0;
            r.expectedOutputCount = obj.has("expectedOutputCount") ? obj.get("expectedOutputCount").getAsInt() : 0;
            r.fuelItem = obj.has("fuelItem") ? obj.get("fuelItem").getAsString() : null;
            r.fuelCount = obj.has("fuelCount") ? obj.get("fuelCount").getAsInt() : 0;
            r.startGameTime = obj.has("startGameTime") ? obj.get("startGameTime").getAsLong() : 0L;
            r.etaGameTime = obj.has("etaGameTime") ? obj.get("etaGameTime").getAsLong() : 0L;
            r.owningBotUuid = obj.has("owningBotUuid") ? obj.get("owningBotUuid").getAsString() : null;
            r.degradeReason = obj.has("degradeReason") ? obj.get("degradeReason").getAsString() : "";
            return r;
         } catch (Exception e) {
            PlayerEngine.LOGGER.warn("DeferredJobStore: failed to parse job record: {}", e.getMessage());
            return null;
         }
      } else {
         return null;
      }
   }

   public void inheritUnknownFieldsFrom(DeferredJobRecord other) {
      if (other != null && other.rawSource != null) {
         this.rawSource = other.rawSource;
      }
   }

   @Override
   public String toString() {
      return "DeferredJobRecord{id='"
         + this.id
         + "', kind='"
         + this.kind
         + "', status="
         + this.status
         + ", inputItem='"
         + this.inputItem
         + "', inputCount="
         + this.inputCount
         + "}";
   }

   public enum Status {
      PENDING,
      COOKING,
      AWAITING_PICKUP,
      DONE,
      DEGRADED;
   }
}
