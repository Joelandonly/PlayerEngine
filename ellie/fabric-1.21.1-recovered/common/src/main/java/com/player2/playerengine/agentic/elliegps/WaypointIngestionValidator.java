package com.player2.playerengine.agentic.elliegps;

public final class WaypointIngestionValidator {
   private WaypointIngestionValidator() {
   }

   public static String validate(String reply) {
      if (reply != null && !reply.isBlank()) {
         String trimmed = reply.trim();
         if (trimmed.length() > 200) {
            throw new IllegalArgumentException("description:len=" + trimmed.length() + ":max=200");
         }

         for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isISOControl(c)) {
               throw new IllegalArgumentException("description:control_char:code=" + c);
            }
         }

         return trimmed;
      } else {
         throw new IllegalArgumentException("description:blank");
      }
   }
}
