package com.player2.playerengine.agentic.elliegps;

import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngineController;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;

public final class FarmWaypointService {
   private static final int MAX_DESCRIPTION_LENGTH = 320;
   private static final int MAX_DESCRIPTION_CROPS = 12;
   private static final int MAX_ORIGIN_LENGTH = 64;
   private static final Object PROVIDER_LOCK = new Object();
   private static final FarmWaypointObservationProvider DEFAULT_PROVIDER = (controller, farm) -> new FarmObservationResult(
      FarmObservationStatus.HANDLER_UNAVAILABLE, null, "farm observation handler is not installed"
   );
   private static final Deque<FarmWaypointService.ProviderOverride> TEST_OVERRIDES = new ArrayDeque<>();
   private static FarmWaypointObservationProvider productionProvider;
   private static long nextOverrideId;

   private FarmWaypointService() {
   }

   public static WaypointMutationResult registerOrRefresh(FarmWaypointObservation observation, String origin) {
      return registerOrRefresh(observation, origin, false, FarmPlantingRestrictionUpdate.preserve());
   }

   public static WaypointMutationResult registerOrRefresh(FarmWaypointObservation observation, String origin, FarmPlantingRestrictionUpdate restrictionUpdate) {
      return registerOrRefresh(observation, origin, false, restrictionUpdate);
   }

   public static WaypointMutationResult registerPrepared(FarmWaypointObservation observation, String origin) {
      return registerOrRefresh(observation, origin, true, FarmPlantingRestrictionUpdate.preserve());
   }

   private static WaypointMutationResult registerOrRefresh(
      FarmWaypointObservation observation, String origin, boolean stale, FarmPlantingRestrictionUpdate restrictionUpdate
   ) {
      Objects.requireNonNull(observation, "observation");
      Objects.requireNonNull(restrictionUpdate, "restrictionUpdate");
      EllieGPSStore store = EllieGPSStore.get();
      if (store == null) {
         return new WaypointMutationResult(WaypointMutationStatus.FAILED_STORE_UNAVAILABLE, null, null);
      }

      WaypointRecord existing = store.byPosition(observation.dimension(), observation.center());
      if (existing != null && !"farm".equals(existing.type)) {
         WaypointRecord conflict = existing.copy();
         return new WaypointMutationResult(WaypointMutationStatus.REJECTED_TYPE_CONFLICT, conflict, conflict.copy());
      }

      FarmWaypointData existingData = existing == null ? null : existing.farmData();
      if (existing == null || existingData != null && existingData.isSupportedVersion()) {
         JsonObject extras = existingData == null ? new JsonObject() : existingData.extras();
         String existingRestriction = existingData == null ? null : existingData.plantingRestrictionItemId().orElse(null);
         String nextRestriction = restrictionUpdate.apply(existingRestriction);
         FarmWaypointData data = FarmWaypointData.current(
            observation.radius(), observation.farmlandCount(), observation.openSlots(), observation.crops(), nextRestriction, extras
         );
         WaypointRecord candidate = new WaypointRecord();
         candidate.id = WaypointRecord.idFor(observation.dimension(), observation.center());
         candidate.type = "farm";
         candidate.dimension = observation.dimension();
         BlockPos center = observation.center();
         candidate.pos = new int[]{center.getX(), center.getY(), center.getZ()};
         candidate.description = buildDescription(observation, data);
         candidate.keywords = buildKeywords(data);
         candidate.origin = existing == null ? boundedOrigin(origin) : existing.origin;
         candidate.stale = stale;
         candidate.createdGameTime = existing == null ? observation.observedGameTime() : existing.createdGameTime;
         candidate.updatedGameTime = observation.observedGameTime();
         candidate.data = data;
         if (existing != null) {
            candidate.inheritUnknownFieldsFrom(existing);
         }

         return existing == null ? store.upsert(candidate) : store.replace(existing.id, candidate);
      } else {
         WaypointRecord conflict = existing.copy();
         return new WaypointMutationResult(WaypointMutationStatus.REJECTED_TYPE_CONFLICT, conflict, conflict.copy());
      }
   }

   public static FarmObservationResult observe(PlayerEngineController controller, WaypointRecord farm) {
      if (farm != null && "farm".equals(farm.type)) {
         FarmWaypointData data = farm.farmData();
         if (data != null && data.isSupportedVersion()) {
            FarmWaypointObservationProvider provider = effectiveProvider();

            try {
               FarmObservationResult result = provider.observe(controller, farm.copy());
               if (result == null) {
                  return unavailable("farm observation handler returned no result");
               }

               if (result.status() == FarmObservationStatus.OBSERVED) {
                  FarmWaypointObservation observation = result.observation();
                  BlockPos expectedCenter = farm.canonicalBlockPos();
                  if (expectedCenter == null || !Objects.equals(farm.dimension, observation.dimension()) || !expectedCenter.equals(observation.center())) {
                     return unsupported("farm observation did not match the stored center");
                  }
               }

               return result;
            } catch (RuntimeException e) {
               return unavailable("farm observation handler failed");
            }
         } else {
            return unsupported("farm waypoint data version is unsupported");
         }
      } else {
         return unsupported("waypoint is not a farm");
      }
   }

   public static void installProductionObservationProvider(FarmWaypointObservationProvider provider) {
      Objects.requireNonNull(provider, "provider");
      synchronized (PROVIDER_LOCK) {
         if (productionProvider == null) {
            productionProvider = provider;
         } else {
            if (productionProvider != provider) {
               throw new IllegalStateException("farm observation provider is already installed");
            }
         }
      }
   }

   static AutoCloseable overrideObservationProviderForTest(FarmWaypointObservationProvider provider) {
      Objects.requireNonNull(provider, "provider");
      FarmWaypointService.ProviderOverride frame;
      synchronized (PROVIDER_LOCK) {
         frame = new FarmWaypointService.ProviderOverride(++nextOverrideId, provider);
         TEST_OVERRIDES.push(frame);
      }

      AtomicBoolean closed = new AtomicBoolean();
      return () -> {
         if (closed.compareAndSet(false, true)) {
            synchronized (PROVIDER_LOCK) {
               if (TEST_OVERRIDES.peek() != frame) {
                  closed.set(false);
                  throw new IllegalStateException("farm observation test overrides must close in LIFO order");
               }

               TEST_OVERRIDES.pop();
            }
         }
      };
   }

   private static FarmWaypointObservationProvider effectiveProvider() {
      synchronized (PROVIDER_LOCK) {
         FarmWaypointService.ProviderOverride override = TEST_OVERRIDES.peek();
         if (override != null) {
            return override.provider;
         } else {
            return productionProvider == null ? DEFAULT_PROVIDER : productionProvider;
         }
      }
   }

   private static FarmObservationResult unsupported(String reason) {
      return new FarmObservationResult(FarmObservationStatus.UNSUPPORTED_DATA, null, reason);
   }

   private static FarmObservationResult unavailable(String reason) {
      return new FarmObservationResult(FarmObservationStatus.HANDLER_UNAVAILABLE, null, reason);
   }

   private static String boundedOrigin(String origin) {
      String value = origin != null && !origin.isBlank() ? origin.strip() : "bot_placed";
      StringBuilder safe = new StringBuilder(Math.min(value.length(), 64));

      for (int i = 0; i < value.length() && safe.length() < 64; i++) {
         char c = value.charAt(i);
         if (!Character.isISOControl(c)) {
            safe.append(c);
         }
      }

      return safe.isEmpty() ? "bot_placed" : safe.toString();
   }

   private static String buildDescription(FarmWaypointObservation observation, FarmWaypointData data) {
      Map<String, Integer> countByBlock = new LinkedHashMap<>();

      for (FarmCropCount crop : data.crops()) {
         countByBlock.merge(crop.blockId(), crop.count(), Math::addExact);
      }

      List<Entry<String, Integer>> entries = new ArrayList<>(countByBlock.entrySet());
      entries.sort(Entry.comparingByKey());
      BlockPos center = observation.center();
      StringBuilder out = new StringBuilder();
      out.append("farm at (")
         .append(center.getX())
         .append(',')
         .append(center.getY())
         .append(',')
         .append(center.getZ())
         .append("): ")
         .append(observation.farmlandCount())
         .append(" farmland, radius ")
         .append(observation.radius())
         .append(", ")
         .append(observation.openSlots())
         .append(" open");
      data.plantingRestrictionItemId().ifPresent(restriction -> out.append(", bot planting restricted to ").append(restriction));
      if (entries.isEmpty()) {
         out.append(", no crops growing");
      } else {
         out.append(", crops: ");
         int shown = Math.min(entries.size(), 12);

         for (int i = 0; i < shown; i++) {
            if (i > 0) {
               out.append(", ");
            }

            Entry<String, Integer> entry = entries.get(i);
            out.append(entry.getKey()).append(" x").append(entry.getValue());
         }

         if (entries.size() > shown) {
            out.append(", +").append(entries.size() - shown).append(" more crop types");
         }
      }

      return bound(out.toString(), 320);
   }

   private static List<String> buildKeywords(FarmWaypointData data) {
      Set<String> keywords = new LinkedHashSet<>();
      keywords.add("farm");
      keywords.add("crops");
      keywords.add("open slots");
      keywords.add(data.openSlots().orElse(0) > 0 ? "available" : "full");
      data.plantingRestrictionItemId().ifPresent(restriction -> {
         keywords.add("single crop");
         keywords.add("planting restricted");
         addIdKeywords(keywords, restriction);
      });
      ArrayList<FarmCropCount> sorted = new ArrayList<>(data.crops());
      sorted.sort(Comparator.comparing(FarmCropCount::blockId).thenComparing(c -> c.plantingItemId() == null ? "" : c.plantingItemId()));

      for (FarmCropCount crop : sorted) {
         addIdKeywords(keywords, crop.blockId());
         if (crop.plantingItemId() != null) {
            addIdKeywords(keywords, crop.plantingItemId());
         }
      }

      return List.copyOf(keywords);
   }

   private static void addIdKeywords(Set<String> keywords, String id) {
      keywords.add(id);
      int colon = id.indexOf(58);
      String path = colon >= 0 ? id.substring(colon + 1) : id;
      keywords.add(path.replace('_', ' '));
   }

   private static String bound(String value, int maximum) {
      return value.length() <= maximum ? value : value.substring(0, maximum);
   }

   private record ProviderOverride(long id, FarmWaypointObservationProvider provider) {
   }
}
