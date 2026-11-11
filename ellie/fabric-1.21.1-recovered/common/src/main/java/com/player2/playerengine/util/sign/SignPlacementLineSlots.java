package com.player2.playerengine.util.sign;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class SignPlacementLineSlots {
   private static final Pattern KEY_EQ_VALUE = Pattern.compile("^(?i)(f[0-3]|b[0-3])=(.*)$");
   private static final Pattern KEY_ONLY = Pattern.compile("^(?i)(f[0-3]|b[0-3])$");

   private SignPlacementLineSlots() {
   }

   public static boolean looksLikeLineSlotToken(String token) {
      if (token == null) {
         return false;
      }

      String t = token.trim();
      return KEY_EQ_VALUE.matcher(t).matches() || KEY_ONLY.matcher(t).matches();
   }

   public static SignPlacementLineSlots.ParsedTail parseTailAfterFace(String[] unitsFromIndex4, Function<String, Item> itemResolver) {
      if (unitsFromIndex4.length == 0) {
         return new SignPlacementLineSlots.ParsedTail("", emptyEight());
      } else {
         String first = unitsFromIndex4[0].trim();
         if (looksLikeLineSlotToken(first)) {
            return new SignPlacementLineSlots.ParsedTail("", parseTailTokens(Arrays.asList(unitsFromIndex4)));
         } else {
            Item it = itemResolver.apply(first);
            if (it != Items.AIR) {
               return unitsFromIndex4.length == 1
                  ? new SignPlacementLineSlots.ParsedTail(first, emptyEight())
                  : new SignPlacementLineSlots.ParsedTail(first, parseTailTokens(Arrays.asList(Arrays.copyOfRange(unitsFromIndex4, 1, unitsFromIndex4.length))));
            } else {
               return new SignPlacementLineSlots.ParsedTail("", parseTailTokens(Arrays.asList(unitsFromIndex4)));
            }
         }
      }
   }

   public static String[] parseTailTokens(List<String> tokens) {
      String[] out = emptyEight();
      int[] currentSlot = new int[]{-1};
      StringBuilder buf = new StringBuilder();
      Runnable flush = () -> {
         if (currentSlot[0] >= 0 && buf.length() > 0) {
            if (out[currentSlot[0]].isEmpty()) {
               out[currentSlot[0]] = buf.toString().trim();
            } else {
               out[currentSlot[0]] = (out[currentSlot[0]] + " " + buf.toString()).trim();
            }

            buf.setLength(0);
         }
      };

      for (String rawTok : tokens) {
         if (rawTok != null) {
            String t = rawTok.trim();
            if (!t.isEmpty()) {
               Matcher eq = KEY_EQ_VALUE.matcher(t);
               if (eq.matches()) {
                  flush.run();
                  currentSlot[0] = slotFromKey(eq.group(1));
                  String rest = eq.group(2).trim();
                  if (!rest.isEmpty()) {
                     out[currentSlot[0]] = rest;
                  }

                  currentSlot[0] = -1;
               } else {
                  Matcher key = KEY_ONLY.matcher(t);
                  if (key.matches()) {
                     flush.run();
                     currentSlot[0] = slotFromKey(key.group(1));
                  } else {
                     if (currentSlot[0] < 0) {
                        currentSlot[0] = 0;
                     }

                     if (buf.length() > 0) {
                        buf.append(' ');
                     }

                     buf.append(t);
                  }
               }
            }
         }
      }

      flush.run();
      return out;
   }

   private static String[] emptyEight() {
      return new String[]{"", "", "", "", "", "", "", ""};
   }

   private static int slotFromKey(String key) {
      String k = key.toLowerCase(Locale.ROOT);

      return switch (k) {
         case "f0" -> 0;
         case "f1" -> 1;
         case "f2" -> 2;
         case "f3" -> 3;
         case "b0" -> 4;
         case "b1" -> 5;
         case "b2" -> 6;
         case "b3" -> 7;
         default -> throw new IllegalStateException("bad sign slot key: " + key);
      };
   }

   public record ParsedTail(String itemIdOrEmpty, String[] eightLines) {
   }
}
