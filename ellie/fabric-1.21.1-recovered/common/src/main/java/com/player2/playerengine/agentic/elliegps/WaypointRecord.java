package com.player2.playerengine.agentic.elliegps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class WaypointRecord {
   public static final int WAYPOINT_SCHEMA_VERSION = 1;
   public int schemaVersion = 1;
   public String id;
   public String type;
   public String dimension;
   public int[] pos;
   public String description;
   public List<String> keywords;
   public String origin;
   public boolean stale;
   public long createdGameTime;
   public long updatedGameTime;
   public Object data;
   transient JsonObject rawSource;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   public static final String ORIGIN_BOT_PLACED = "bot_placed";
   public static final String ORIGIN_EXPLICIT_CREATE = "explicit_create";
   public static final String ORIGIN_HEURISTIC_PASS = "heuristic_pass";

   public static String idFor(String dimensionId, BlockPos canonicalPos) {
      return dimensionId + "|" + canonicalPos.getX() + "," + canonicalPos.getY() + "," + canonicalPos.getZ();
   }

   public BlockPos canonicalBlockPos() {
      return this.pos != null && this.pos.length >= 3 ? new BlockPos(this.pos[0], this.pos[1], this.pos[2]) : null;
   }

   public BlockPos secondaryBlockPos() {
      if (this.data instanceof InventoryWaypointData inv) {
         int[] sp = inv.secondaryPos;
         return sp != null && sp.length >= 3 ? new BlockPos(sp[0], sp[1], sp[2]) : null;
      } else {
         return null;
      }
   }

   public InventoryWaypointData inventoryData() {
      return this.data instanceof InventoryWaypointData inv ? inv : null;
   }

   public FarmWaypointData farmData() {
      return this.data instanceof FarmWaypointData farm ? farm : null;
   }

   public JsonElement toJson() {
      JsonObject envelope = this.rawSource != null ? this.rawSource.deepCopy() : new JsonObject();
      envelope.addProperty("schemaVersion", this.schemaVersion);
      envelope.addProperty("id", this.id);
      envelope.addProperty("type", this.type);
      envelope.addProperty("dimension", this.dimension);
      JsonArray posArr = new JsonArray();
      if (this.pos != null && this.pos.length >= 3) {
         posArr.add(this.pos[0]);
         posArr.add(this.pos[1]);
         posArr.add(this.pos[2]);
      }

      envelope.add("pos", posArr);
      envelope.addProperty("description", this.description != null ? this.description : "");
      JsonArray kw = new JsonArray();
      if (this.keywords != null) {
         for (String k : this.keywords) {
            kw.add(k);
         }
      }

      envelope.add("keywords", kw);
      envelope.addProperty("origin", this.origin != null ? this.origin : "");
      envelope.addProperty("stale", this.stale);
      envelope.addProperty("createdGameTime", this.createdGameTime);
      envelope.addProperty("updatedGameTime", this.updatedGameTime);
      if ("inventory".equals(this.type) && this.data instanceof InventoryWaypointData inv) {
         envelope.add("data", GSON.toJsonTree(inv));
      } else if ("farm".equals(this.type) && this.data instanceof FarmWaypointData farm) {
         envelope.add("data", farm.toJson());
      } else if (this.data instanceof JsonElement je) {
         envelope.add("data", je);
      } else if (this.data != null) {
         envelope.add("data", GSON.toJsonTree(this.data));
      } else if (!envelope.has("data")) {
         envelope.add("data", new JsonObject());
      }

      return envelope;
   }

   void inheritUnknownFieldsFrom(WaypointRecord other) {
      if (other != null && other.rawSource != null) {
         this.rawSource = other.rawSource.deepCopy();
      }
   }

   public WaypointRecord copy() {
      WaypointRecord copied = fromJson(this.toJson());
      if (copied == null) {
         throw new IllegalStateException("could not copy waypoint record");
      } else {
         return copied;
      }
   }

   public boolean semanticallyEquals(WaypointRecord other) {
      if (other == null) {
         return false;
      }

      JsonElement left = this.toJson().deepCopy();
      JsonElement right = other.toJson().deepCopy();
      if (left.isJsonObject()) {
         left.getAsJsonObject().remove("updatedGameTime");
      }

      if (right.isJsonObject()) {
         right.getAsJsonObject().remove("updatedGameTime");
      }

      return left.equals(right);
   }

   public static WaypointRecord fromJson(JsonElement element) {
      if (element != null && element.isJsonObject()) {
         try {
            JsonObject obj = element.getAsJsonObject();
            WaypointRecord r = new WaypointRecord();
            r.rawSource = obj.deepCopy();
            r.schemaVersion = obj.has("schemaVersion") ? obj.get("schemaVersion").getAsInt() : 0;
            r.id = obj.has("id") ? obj.get("id").getAsString() : null;
            r.type = obj.has("type") ? obj.get("type").getAsString() : null;
            r.dimension = obj.has("dimension") ? obj.get("dimension").getAsString() : null;
            if (obj.has("pos") && obj.get("pos").isJsonArray()) {
               JsonArray arr = obj.get("pos").getAsJsonArray();
               if (arr.size() == 3) {
                  r.pos = new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt()};
               }
            }

            r.description = obj.has("description") ? obj.get("description").getAsString() : "";
            if (obj.has("keywords") && obj.get("keywords").isJsonArray()) {
               JsonArray arr = obj.get("keywords").getAsJsonArray();
               ArrayList<String> kws = new ArrayList<>();

               for (JsonElement e : arr) {
                  kws.add(e.getAsString());
               }

               r.keywords = Collections.unmodifiableList(kws);
            } else {
               r.keywords = List.of();
            }

            r.origin = obj.has("origin") ? obj.get("origin").getAsString() : "";
            r.stale = obj.has("stale") && obj.get("stale").getAsBoolean();
            r.createdGameTime = obj.has("createdGameTime") ? obj.get("createdGameTime").getAsLong() : 0L;
            r.updatedGameTime = obj.has("updatedGameTime") ? obj.get("updatedGameTime").getAsLong() : 0L;
            if (obj.has("data") && obj.get("data").isJsonObject()) {
               JsonElement dataElem = obj.get("data");
               if ("inventory".equals(r.type)) {
                  r.data = GSON.fromJson(dataElem, InventoryWaypointData.class);
               } else if ("farm".equals(r.type)) {
                  r.data = FarmWaypointData.fromJson(dataElem);
               } else {
                  r.data = dataElem;
               }
            }

            return r;
         } catch (Exception e) {
            PlayerEngine.LOGGER.warn("EllieGPS: failed to parse waypoint record: {}", e.getMessage());
            return null;
         }
      } else {
         return null;
      }
   }
}
