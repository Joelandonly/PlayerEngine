package com.player2.playerengine.automaton.api.command.exception;

public class CommandInvalidStateException extends CommandErrorMessageException {
   public CommandInvalidStateException(String reason) {
      super(reason);
   }
}
