package com.player2.playerengine.help;

public record ArgNote(String argName, String noteKey) {
   public ArgNote(String argName, String noteKey) {
      if (argName == null || argName.isBlank()) {
         throw new IllegalArgumentException("ArgNote.argName must be non-blank");
      }

      if (noteKey != null && !noteKey.isBlank()) {
         this.argName = argName;
         this.noteKey = noteKey;
      } else {
         throw new IllegalArgumentException("ArgNote.noteKey must be a non-blank translatable key");
      }
   }
}
