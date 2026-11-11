package com.player2.playerengine.automaton.utils;

import dev.architectury.platform.Platform;
import java.nio.file.Path;

public class DirUtil {
   public static Path getGameDir() {
      return Platform.getGameFolder();
   }

   public static Path getConfigDir() {
      return Platform.getConfigFolder();
   }
}
