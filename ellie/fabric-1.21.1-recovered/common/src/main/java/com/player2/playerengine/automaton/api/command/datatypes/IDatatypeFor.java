package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.exception.CommandException;

public interface IDatatypeFor<T> extends IDatatype {
   T get(IDatatypeContext var1) throws CommandException;
}
