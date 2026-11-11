package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.exception.CommandException;
import java.util.stream.Stream;

public interface IDatatype {
   Stream<String> tabComplete(IDatatypeContext var1) throws CommandException;
}
