package com.player2.playerengine.player2api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@Deprecated
public final class ClientChatCompletionBridge {
   private ClientChatCompletionBridge() {
   }

   public static Map<String, JsonElement> toResponseMap(JsonObject response) {
      Map<String, JsonElement> responseMap = new HashMap<>();

      for (Entry<String, JsonElement> entry : response.entrySet()) {
         responseMap.put(entry.getKey(), entry.getValue());
      }

      return responseMap;
   }
}
