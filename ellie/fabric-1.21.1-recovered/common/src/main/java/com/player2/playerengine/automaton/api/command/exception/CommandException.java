package com.player2.playerengine.automaton.api.command.exception;

public abstract class CommandException extends Exception implements ICommandException {
   protected CommandException(String reason) {
      super(reason);
   }

   protected CommandException(String reason, Throwable cause) {
      super(reason, cause);
   }
}
