package com.player2.playerengine.retrieval.overlay;

import java.util.Collections;
import java.util.List;

public final class OverlayEntry {
   public List<String> addKeywords;
   public List<String> addExamples;

   public List<String> safeKeywords() {
      return this.addKeywords == null ? Collections.emptyList() : Collections.unmodifiableList(this.addKeywords);
   }

   public List<String> safeExamples() {
      return this.addExamples == null ? Collections.emptyList() : Collections.unmodifiableList(this.addExamples);
   }
}
