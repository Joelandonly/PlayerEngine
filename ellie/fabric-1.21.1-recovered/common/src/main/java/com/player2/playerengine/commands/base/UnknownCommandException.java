package com.player2.playerengine.commands.base;

public class UnknownCommandException extends CommandException {
   public UnknownCommandException(String message) {
      super(message);
   }

   public UnknownCommandException(String message, Exception child) {
      super(message, child);
   }
}
