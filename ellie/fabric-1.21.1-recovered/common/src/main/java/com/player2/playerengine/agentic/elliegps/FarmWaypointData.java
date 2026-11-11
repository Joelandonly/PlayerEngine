package com.player2.playerengine.agentic.elliegps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Map.Entry;

public final class FarmWaypointData {
   public static final int LEGACY_DATA_VERSION = 1;
   public static final int DATA_VERSION = 2;
   public static final int MAX_HYDRATION_RADIUS = 4;
   public static final int MAX_FARMLAND_BLOCKS = 80;
   private static final String FIELD_DATA_VERSION = "dataVersion";
   private static final String FIELD_RADIUS = "radius";
   private static final String FIELD_FARMLAND_COUNT = "farmlandCount";
   private static final String FIELD_OPEN_SLOTS = "openSlots";
   private static final String FIELD_CROPS = "crops";
   private static final String FIELD_PLANTING_RESTRICTION = "plantingRestrictionItemId";
   private final int dataVersion;
   private final int radius;
   private final int farmlandCount;
   private final Integer openSlots;
   private final List<FarmCropCount> crops;
   private final String plantingRestrictionItemId;
   private final JsonObject extras;
   private final JsonObject unsupportedRawSource;

   public FarmWaypointData(int radius, int farmlandCount, List<FarmCropCount> crops) {
      this(1, radius, farmlandCount, null, crops, null, null, null, true);
   }

   public FarmWaypointData(int radius, int farmlandCount, int openSlots, List<FarmCropCount> crops) {
      this(radius, farmlandCount, openSlots, crops, null);
   }

   public FarmWaypointData(int radius, int farmlandCount, int openSlots, List<FarmCropCount> crops, String plantingRestrictionItemId) {
      this(2, radius, farmlandCount, openSlots, crops, plantingRestrictionItemId, null, null, true);
   }

   static FarmWaypointData current(int radius, int farmlandCount, int openSlots, List<FarmCropCount> crops, String plantingRestrictionItemId, JsonObject extras) {
      return new FarmWaypointData(2, radius, farmlandCount, openSlots, crops, plantingRestrictionItemId, removeKnownCurrentFields(extras), null, true);
   }

   private FarmWaypointData(
      int dataVersion,
      int radius,
      int farmlandCount,
      Integer openSlots,
      List<FarmCropCount> crops,
      String plantingRestrictionItemId,
      JsonObject extras,
      JsonObject unsupportedRawSource,
      boolean validateReadable
   ) {
      this.dataVersion = dataVersion;
      if (validateReadable) {
         validateRadius(radius);
         validateFarmlandCount(farmlandCount);
      }

      this.radius = radius;
      this.farmlandCount = farmlandCount;
      this.crops = normalizeCrops(crops, validateReadable);
      if (validateReadable && dataVersion == 2) {
         if (openSlots == null) {
            throw new IllegalArgumentException("openSlots is required for farm data v2");
         }

         validateOpenSlots(openSlots);
         if (openSlots > farmlandCount) {
            throw new IllegalArgumentException("open slots cannot exceed observed farmland");
         }

         if (openSlots + totalCropCount(this.crops) > 80) {
            throw new IllegalArgumentException("open slots plus recognized crops cannot exceed 80");
         }

         if (plantingRestrictionItemId != null) {
            plantingRestrictionItemId = FarmCropCount.requireCanonicalId(plantingRestrictionItemId, "plantingRestrictionItemId");
         }
      } else if (validateReadable && dataVersion == 1 && (openSlots != null || plantingRestrictionItemId != null)) {
         throw new IllegalArgumentException("legacy farm data cannot expose v2 planting metadata");
      }

      this.openSlots = openSlots;
      this.plantingRestrictionItemId = plantingRestrictionItemId;
      this.extras = extras == null ? new JsonObject() : extras.deepCopy();
      this.unsupportedRawSource = unsupportedRawSource == null ? null : unsupportedRawSource.deepCopy();
   }

   public int dataVersion() {
      return this.dataVersion;
   }

   public int radius() {
      return this.radius;
   }

   public int farmlandCount() {
      return this.farmlandCount;
   }

   public OptionalInt openSlots() {
      return this.openSlots == null ? OptionalInt.empty() : OptionalInt.of(this.openSlots);
   }

   public List<FarmCropCount> crops() {
      return this.crops;
   }

   public Optional<String> plantingRestrictionItemId() {
      return Optional.ofNullable(this.plantingRestrictionItemId);
   }

   public JsonObject extras() {
      return this.extras.deepCopy();
   }

   public boolean isSupportedVersion() {
      return this.dataVersion == 1 || this.dataVersion == 2;
   }

   public boolean hasCurrentPlantingMetadata() {
      return this.dataVersion == 2 && this.openSlots != null;
   }

   public boolean acceptsPlantingItem(String canonicalItemId) {
      String checked = FarmCropCount.requireCanonicalId(canonicalItemId, "plantingItemId");
      return this.isSupportedVersion() && (this.plantingRestrictionItemId == null || this.plantingRestrictionItemId.equals(checked));
   }

   public static FarmWaypointData fromJson(JsonElement element) {
      if (element != null && element.isJsonObject()) {
         JsonObject source = element.getAsJsonObject().deepCopy();
         int version = requiredInt(source, "dataVersion");
         if (version == 1) {
            JsonObject extras = source.deepCopy();
            removeFields(extras, "dataVersion", "radius", "farmlandCount", "crops");
            return new FarmWaypointData(
               version,
               requiredInt(source, "radius"),
               requiredInt(source, "farmlandCount"),
               null,
               parseSupportedCrops(source.get("crops")),
               null,
               extras,
               null,
               true
            );
         } else if (version == 2) {
            JsonObject extras = removeKnownCurrentFields(source);
            return new FarmWaypointData(
               version,
               requiredInt(source, "radius"),
               requiredInt(source, "farmlandCount"),
               requiredInt(source, "openSlots"),
               parseSupportedCrops(source.get("crops")),
               nullableString(source, "plantingRestrictionItemId"),
               extras,
               null,
               true
            );
         } else {
            Integer bestEffortOpenSlots = optionalInteger(source, "openSlots");
            return new FarmWaypointData(
               version,
               optionalInt(source, "radius", 0),
               optionalInt(source, "farmlandCount", 0),
               bestEffortOpenSlots,
               parseCropsBestEffort(source.get("crops")),
               optionalString(source, "plantingRestrictionItemId"),
               new JsonObject(),
               source,
               false
            );
         }
      } else {
         throw new IllegalArgumentException("farm waypoint data must be a JSON object");
      }
   }

   public JsonObject toJson() {
      if (!this.isSupportedVersion() && this.unsupportedRawSource != null) {
         return this.unsupportedRawSource.deepCopy();
      }

      JsonObject out = this.extras.deepCopy();
      out.addProperty("dataVersion", this.dataVersion);
      out.addProperty("radius", this.radius);
      out.addProperty("farmlandCount", this.farmlandCount);
      if (this.dataVersion == 2) {
         out.addProperty("openSlots", this.openSlots);
         if (this.plantingRestrictionItemId != null) {
            out.addProperty("plantingRestrictionItemId", this.plantingRestrictionItemId);
         } else {
            out.remove("plantingRestrictionItemId");
         }
      }

      JsonArray cropArray = new JsonArray();

      for (FarmCropCount crop : this.crops) {
         JsonObject entry = new JsonObject();
         entry.addProperty("blockId", crop.blockId());
         if (crop.plantingItemId() != null) {
            entry.addProperty("plantingItemId", crop.plantingItemId());
         }

         entry.addProperty("count", crop.count());
         cropArray.add(entry);
      }

      out.add("crops", cropArray);
      return out;
   }

   FarmWaypointData copy() {
      return fromJson(this.toJson());
   }

   private static List<FarmCropCount> parseSupportedCrops(JsonElement element) {
      if (element != null && element.isJsonArray()) {
         ArrayList<FarmCropCount> parsed = new ArrayList<>();

         for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonObject()) {
               throw new IllegalArgumentException("farm crop entry must be a JSON object");
            }

            JsonObject crop = value.getAsJsonObject();
            parsed.add(new FarmCropCount(requiredString(crop, "blockId"), nullableString(crop, "plantingItemId"), requiredInt(crop, "count")));
         }

         return parsed;
      } else {
         throw new IllegalArgumentException("farm crops must be a JSON array");
      }
   }

   private static List<FarmCropCount> parseCropsBestEffort(JsonElement element) {
      if (element != null && element.isJsonArray()) {
         ArrayList<FarmCropCount> parsed = new ArrayList<>();
         int aggregate = 0;

         for (JsonElement value : element.getAsJsonArray()) {
            try {
               if (value.isJsonObject()) {
                  JsonObject crop = value.getAsJsonObject();
                  FarmCropCount entry = new FarmCropCount(requiredString(crop, "blockId"), nullableString(crop, "plantingItemId"), requiredInt(crop, "count"));
                  if (aggregate + entry.count() > 80) {
                     break;
                  }

                  aggregate += entry.count();
                  parsed.add(entry);
               }
            } catch (RuntimeException var7) {
            }
         }

         return parsed;
      } else {
         return List.of();
      }
   }

   private static List<FarmCropCount> normalizeCrops(List<FarmCropCount> input, boolean validateAggregate) {
      Objects.requireNonNull(input, "crops");
      Map<FarmWaypointData.CropKey, Integer> merged = new LinkedHashMap<>();
      int aggregate = 0;

      for (FarmCropCount crop : input) {
         FarmCropCount checked = Objects.requireNonNull(crop, "crop");
         if (validateAggregate) {
            aggregate = Math.addExact(aggregate, checked.count());
            if (aggregate > 80) {
               throw new IllegalArgumentException("aggregate crop count cannot exceed 80");
            }
         }

         FarmWaypointData.CropKey key = new FarmWaypointData.CropKey(checked.blockId(), checked.plantingItemId());
         merged.merge(key, checked.count(), Math::addExact);
      }

      ArrayList<FarmCropCount> normalized = new ArrayList<>(merged.size());

      for (Entry<FarmWaypointData.CropKey, Integer> entry : merged.entrySet()) {
         int count = entry.getValue();
         if (count >= 1 && count <= 80) {
            normalized.add(new FarmCropCount(entry.getKey().blockId, entry.getKey().plantingItemId, count));
         } else if (validateAggregate) {
            throw new IllegalArgumentException("merged crop count cannot exceed 80");
         }
      }

      normalized.sort(Comparator.comparing(FarmCropCount::blockId).thenComparing(c -> c.plantingItemId() == null ? "" : c.plantingItemId()));
      return List.copyOf(normalized);
   }

   private static int totalCropCount(List<FarmCropCount> crops) {
      int total = 0;

      for (FarmCropCount crop : crops) {
         total = Math.addExact(total, crop.count());
      }

      return total;
   }

   private static void validateRadius(int radius) {
      if (radius < 0 || radius > 4) {
         throw new IllegalArgumentException("radius must be between 0 and 4");
      }
   }

   private static void validateFarmlandCount(int farmlandCount) {
      if (farmlandCount < 0 || farmlandCount > 80) {
         throw new IllegalArgumentException("farmlandCount must be between 0 and 80");
      }
   }

   private static void validateOpenSlots(int openSlots) {
      if (openSlots < 0 || openSlots > 80) {
         throw new IllegalArgumentException("openSlots must be between 0 and 80");
      }
   }

   private static JsonObject removeKnownCurrentFields(JsonObject source) {
      JsonObject extras = source == null ? new JsonObject() : source.deepCopy();
      removeFields(extras, "dataVersion", "radius", "farmlandCount", "openSlots", "crops", "plantingRestrictionItemId");
      return extras;
   }

   private static void removeFields(JsonObject object, String... fields) {
      for (String field : fields) {
         object.remove(field);
      }
   }

   private static int requiredInt(JsonObject object, String field) {
      if (object.has(field) && !object.get(field).isJsonNull()) {
         try {
            return object.get(field).getAsInt();
         } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid farm data integer: " + field, e);
         }
      } else {
         throw new IllegalArgumentException("missing farm data field: " + field);
      }
   }

   private static int optionalInt(JsonObject object, String field, int fallback) {
      Integer value = optionalInteger(object, field);
      return value == null ? fallback : value;
   }

   private static Integer optionalInteger(JsonObject object, String field) {
      try {
         return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsInt() : null;
      } catch (RuntimeException ignored) {
         return null;
      }
   }

   private static String requiredString(JsonObject object, String field) {
      String value = nullableString(object, field);
      if (value == null) {
         throw new IllegalArgumentException("missing farm crop field: " + field);
      } else {
         return value;
      }
   }

   private static String nullableString(JsonObject object, String field) {
      if (object.has(field) && !object.get(field).isJsonNull()) {
         try {
            return object.get(field).getAsString();
         } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid farm data string: " + field, e);
         }
      } else {
         return null;
      }
   }

   private static String optionalString(JsonObject object, String field) {
      try {
         return nullableString(object, field);
      } catch (RuntimeException ignored) {
         return null;
      }
   }

   private record CropKey(String blockId, String plantingItemId) {
   }
}
