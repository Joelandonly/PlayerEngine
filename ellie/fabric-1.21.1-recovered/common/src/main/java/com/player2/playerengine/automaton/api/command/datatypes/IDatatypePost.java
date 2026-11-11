package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.exception.CommandException;

public interface IDatatypePost<T, O> extends IDatatype {
   T apply(IDatatypeContext var1, O var2) throws CommandException;
}
