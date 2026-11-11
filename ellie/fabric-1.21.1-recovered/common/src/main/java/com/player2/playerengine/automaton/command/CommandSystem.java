package com.player2.playerengine.automaton.command;

import com.player2.playerengine.automaton.api.command.ICommandSystem;
import com.player2.playerengine.automaton.api.command.argparser.IArgParserManager;
import com.player2.playerengine.automaton.command.argparser.ArgParserManager;

public enum CommandSystem implements ICommandSystem {
   INSTANCE;

   @Override
   public IArgParserManager getParserManager() {
      return ArgParserManager.INSTANCE;
   }
}
