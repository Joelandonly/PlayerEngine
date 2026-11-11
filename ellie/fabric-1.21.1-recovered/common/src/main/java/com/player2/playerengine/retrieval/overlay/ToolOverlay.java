package com.player2.playerengine.retrieval.overlay;

import java.util.Collections;
import java.util.Map;

public final class ToolOverlay {
   public static final int SUPPORTED_SCHEMA_VERSION = 1;
   public int schemaVersion;
   public Map<String, OverlayEntry> tools;

   public Map<String, OverlayEntry> safeTools() {
      return this.tools == null ? Collections.emptyMap() : Collections.unmodifiableMap(this.tools);
   }
}
