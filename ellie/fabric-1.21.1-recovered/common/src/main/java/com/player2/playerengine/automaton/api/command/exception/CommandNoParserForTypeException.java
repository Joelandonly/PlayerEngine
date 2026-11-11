package com.player2.playerengine.automaton.api.command.exception;

public class CommandNoParserForTypeException extends RuntimeException {
   public CommandNoParserForTypeException(Class<?> klass) {
      super(String.format("Could not find a handler for type %s", klass.getSimpleName()));
   }
}
