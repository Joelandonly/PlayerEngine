package com.player2.playerengine;

import com.player2.playerengine.automaton.utils.DirUtil;
import java.nio.file.Path;

public final class PlayerEnginePaths {
   private static final String DATA_DIR = "data";
   private static final String MOD_INTELLIGENCE_DIR = "mod_intelligence";
   private static final String RAG_INDEX_DIR = "rag_index";

   private PlayerEnginePaths() {
   }

   public static Path root() {
      return DirUtil.getGameDir().resolve("playerengine");
   }

   public static Path userConfigRoot() {
      return root();
   }

   public static Path dataRoot() {
      return root().resolve("data");
   }

   public static Path modIntelligenceRoot() {
      return dataRoot().resolve("mod_intelligence");
   }

   public static Path ragIndexRoot() {
      return dataRoot().resolve("rag_index");
   }

   public static Path userFile(String fileName) {
      return root().resolve(fileName);
   }

   public static Path legacyConfigPlayerEngineRoot() {
      return DirUtil.getConfigDir().resolve("playerengine");
   }

   public static Path legacyChatclefConfigFile() {
      return DirUtil.getConfigDir().resolve("chatclef_config.json");
   }
}
