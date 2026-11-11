package com.player2.playerengine.automaton.api.command;

import com.player2.playerengine.automaton.api.command.argparser.IArgParserManager;

public interface ICommandSystem {
   IArgParserManager getParserManager();
}
