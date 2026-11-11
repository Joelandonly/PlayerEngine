package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.exception.CommandException;

public interface IDatatypePostFunction<T, O> {
   T apply(O var1) throws CommandException;
}
