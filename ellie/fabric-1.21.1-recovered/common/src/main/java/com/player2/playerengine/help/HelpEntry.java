package com.player2.playerengine.help;

import java.util.List;

public record HelpEntry(
   String rootId,
   String path,
   String usage,
   String shortDescKey,
   String longDescKey,
   List<ArgNote> argNotes,
   int permLevel,
   String permNoteKey,
   String category
) {
   public HelpEntry(
      String rootId,
      String path,
      String usage,
      String shortDescKey,
      String longDescKey,
      List<ArgNote> argNotes,
      int permLevel,
      String permNoteKey,
      String category
   ) {
      if (rootId == null || rootId.isBlank()) {
         throw new IllegalArgumentException("HelpEntry.rootId must be non-blank");
      }

      if (path == null || path.isBlank()) {
         throw new IllegalArgumentException("HelpEntry.path must be non-blank");
      }

      if (usage == null || usage.isBlank()) {
         throw new IllegalArgumentException("HelpEntry.usage must be non-blank (English, never translated)");
      }

      if (shortDescKey != null && !shortDescKey.isBlank()) {
         path = HelpPath.normalize(path);
         argNotes = argNotes == null ? List.of() : List.copyOf(argNotes);
         this.rootId = rootId;
         this.path = path;
         this.usage = usage;
         this.shortDescKey = shortDescKey;
         this.longDescKey = longDescKey;
         this.argNotes = argNotes;
         this.permLevel = permLevel;
         this.permNoteKey = permNoteKey;
         this.category = category;
      } else {
         throw new IllegalArgumentException("HelpEntry.shortDescKey must be a non-blank translatable key");
      }
   }
}
