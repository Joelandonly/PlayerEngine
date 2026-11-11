package com.player2.playerengine.player2api.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LocalAPIDiscovery {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final String APP_DIR_NAME = "game.player2.client";
   private static final String PORT_FILE_NAME = "api.port";

   public static String getLocalApiUrl() {
      Path portFile = getPortFilePath();
      if (portFile == null) {
         return null;
      }

      if (!Files.exists(portFile)) {
         LOGGER.debug("Player2 local API port file not found at: {}", portFile);
         return null;
      }

      try {
         String content = Files.readString(portFile).trim();
         int port = Integer.parseInt(content);
         String url = "http://127.0.0.1:" + port;
         LOGGER.debug("Discovered local Player2 API at: {}", url);
         return url;
      } catch (IOException e) {
         LOGGER.warn("Failed to read Player2 API port file: {}", e.getMessage());
         return null;
      } catch (NumberFormatException e) {
         LOGGER.warn("Player2 API port file contains invalid port number: {}", e.getMessage());
         return null;
      }
   }

   public static boolean isLocalAppRunning() {
      return getLocalApiUrl() != null;
   }

   public static String getPreferredApiUrl(String webApiUrl) {
      String local = getLocalApiUrl();
      if (local != null) {
         LOGGER.info("Using local Player2 API: {}", local);
         return local;
      } else {
         LOGGER.debug("Local Player2 API not available, using web API: {}", webApiUrl);
         return webApiUrl;
      }
   }

   private static Path getPortFilePath() {
      String os = System.getProperty("os.name", "").toLowerCase();

      try {
         if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return appData == null ? null : Paths.get(appData, "game.player2.client", "api.port");
         } else if (os.contains("mac")) {
            String home = System.getProperty("user.home");
            return home == null ? null : Paths.get(home, "Library", "Application Support", "game.player2.client", "api.port");
         } else {
            String xdgConfig = System.getenv("XDG_CONFIG_HOME");
            String home = System.getProperty("user.home");
            if (xdgConfig != null) {
               return Paths.get(xdgConfig, "game.player2.client", "api.port");
            } else {
               return home != null ? Paths.get(home, ".config", "game.player2.client", "api.port") : null;
            }
         }
      } catch (Exception e) {
         LOGGER.warn("Failed to determine Player2 API port file path: {}", e.getMessage());
         return null;
      }
   }
}
