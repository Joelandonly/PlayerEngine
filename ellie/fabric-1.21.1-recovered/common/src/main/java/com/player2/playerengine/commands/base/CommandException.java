package com.player2.playerengine.commands.base;

public class CommandException extends Exception {
   public static final class Cancelled extends CommandException {
      public Cancelled(String message) {
         super(message);
      }
   }

   public CommandException(String message) {
      super(message);
   }

   public CommandException(String message, Exception child) {
      super(message, child);
   }
}
