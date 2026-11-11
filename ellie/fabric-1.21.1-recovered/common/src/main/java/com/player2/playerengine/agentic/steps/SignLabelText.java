package com.player2.playerengine.agentic.steps;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.sign.SignTextFormatting;
import net.minecraft.network.chat.Component;

public final class SignLabelText {
   private SignLabelText() {
   }

   public static Component[] frontLines(PlayerEngineController mod, String[] lines) {
      String[] four = new String[]{safe(lines, 0), safe(lines, 1), safe(lines, 2), safe(lines, 3)};
      return SignTextFormatting.fourLinesFromStrings(four, mod.getWorld().registryAccess());
   }

   public static Component[] emptyBack() {
      return new Component[]{Component.empty(), Component.empty(), Component.empty(), Component.empty()};
   }

   private static String safe(String[] lines, int i) {
      return lines != null && i < lines.length && lines[i] != null ? lines[i] : "";
   }
}
