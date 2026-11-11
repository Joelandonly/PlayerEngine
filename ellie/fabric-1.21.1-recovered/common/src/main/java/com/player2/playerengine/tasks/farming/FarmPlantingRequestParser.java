package com.player2.playerengine.tasks.farming;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;

public final class FarmPlantingRequestParser {
   public static final int MAX_RAW_REQUEST_LENGTH = 1024;
   public static final int MAX_REQUEST_TYPES = 12;
   private static final Set<String> ALLOWED_KEYS = Set.of("requests", "x", "y", "z", "farm_policy");

   private FarmPlantingRequestParser() {
   }

   public static FarmPlantingRequestParser.Parsed parseArgs(Map<String, String> rawArgs) {
      Map<String, String> args = rawArgs == null ? Map.of() : rawArgs;

      for (String key : args.keySet()) {
         if (key == null || !ALLOWED_KEYS.contains(key)) {
            return invalid("unknown_argument");
         }
      }

      String rawRequests = args.get("requests");
      boolean hasX = args.containsKey("x");
      boolean hasY = args.containsKey("y");
      boolean hasZ = args.containsKey("z");
      if (hasX == hasY && hasX == hasZ) {
         BlockPos exact = null;
         if (hasX) {
            try {
               exact = new BlockPos(Integer.parseInt(args.get("x")), Integer.parseInt(args.get("y")), Integer.parseInt(args.get("z")));
            } catch (NumberFormatException | NullPointerException invalid) {
               return invalid("coordinates_invalid");
            }
         }

         return parse(rawRequests, exact, args.get("farm_policy"));
      } else {
         return invalid("coordinates_all_or_none");
      }
   }

   public static FarmPlantingRequestParser.Parsed parse(String rawRequests, BlockPos exactCenter, String rawPolicy) {
      try {
         List<FarmPlantingRequest> requests = parseRequests(rawRequests);
         FarmPlantingPolicy policy = FarmPlantingPolicy.parse(rawPolicy);
         if (policy.kind() != FarmPlantingPolicy.Kind.PRESERVE && exactCenter == null) {
            return invalid("policy_requires_exact_farm");
         }

         if (policy.kind() == FarmPlantingPolicy.Kind.SET) {
            String restricted = policy.plantingItemId();

            for (FarmPlantingRequest request : requests) {
               if (!restricted.equals(request.plantingItemId())) {
                  return invalid("policy_request_mismatch");
               }
            }
         }

         return new FarmPlantingRequestParser.Parsed(requests, exactCenter, policy, "");
      } catch (IllegalArgumentException | ArithmeticException invalid) {
         return invalid("requests_invalid");
      }
   }

   static List<FarmPlantingRequest> parseRequests(String raw) {
      String value = Objects.requireNonNull(raw, "requests").strip();
      if (!value.isEmpty() && value.length() <= 1024) {
         LinkedHashMap<String, Integer> merged = new LinkedHashMap<>();
         String[] entries = value.split(",", -1);
         if (entries.length > 12) {
            throw new IllegalArgumentException("too many crop request types");
         }

         String[] result = entries;
         int total = result.length;
         int var6 = 0;

         while (var6 < total) {
            String entry = result[var6];
            int equals = entry.indexOf(61);
            if (equals >= 1 && equals == entry.lastIndexOf(61) && equals != entry.length() - 1) {
               String itemId = FarmPlantingRequest.canonicalId(entry.substring(0, equals).strip());

               int count;
               try {
                  count = Integer.parseInt(entry.substring(equals + 1).strip());
               } catch (NumberFormatException invalid) {
                  throw new IllegalArgumentException("request count is not an integer", invalid);
               }

               if (count >= 1 && count <= 80) {
                  merged.merge(itemId, count, Math::addExact);
                  var6++;
                  continue;
               }

               throw new IllegalArgumentException("request count is outside the farm footprint");
            }

            throw new IllegalArgumentException("request must be item=count");
         }

         if (merged.size() > 12) {
            throw new IllegalArgumentException("too many merged crop request types");
         }

         ArrayList<FarmPlantingRequest> resultx = new ArrayList<>(merged.size());
         total = 0;

         for (Entry<String, Integer> entry : merged.entrySet()) {
            total = Math.addExact(total, entry.getValue());
            if (total > 80) {
               throw new IllegalArgumentException("requested plants exceed one farm");
            }

            resultx.add(new FarmPlantingRequest(entry.getKey(), entry.getValue()));
         }

         return List.copyOf(resultx);
      } else {
         throw new IllegalArgumentException("requests are empty or too long");
      }
   }

   private static FarmPlantingRequestParser.Parsed invalid(String token) {
      return new FarmPlantingRequestParser.Parsed(List.of(), null, FarmPlantingPolicy.preserve(), token);
   }

   public record Parsed(List<FarmPlantingRequest> requests, BlockPos exactCenter, FarmPlantingPolicy policy, String error) {
      public Parsed {
         requests = requests == null ? List.of() : List.copyOf(requests);
         exactCenter = exactCenter == null ? null : exactCenter.immutable();
         policy = policy == null ? FarmPlantingPolicy.preserve() : policy;
         error = error == null ? "" : error;
      }

      public boolean valid() {
         return this.error.isEmpty();
      }

      public int totalCount() {
         int total = 0;

         for (FarmPlantingRequest request : this.requests) {
            total = Math.addExact(total, request.count());
         }

         return total;
      }
   }
}
