package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;

public interface IDatatypeContext {
   IBaritone getBaritone();

   IArgConsumer getConsumer();
}
