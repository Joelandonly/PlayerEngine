package com.player2.playerengine.automaton.api.command.argparser;

import com.player2.playerengine.automaton.api.command.argument.ICommandArgument;

public interface IArgParser<T> {
   Class<T> getTarget();

   interface Stated<T, S> extends IArgParser<T> {
      Class<S> getStateType();

      T parseArg(ICommandArgument var1, S var2) throws Exception;
   }

   interface Stateless<T> extends IArgParser<T> {
      T parseArg(ICommandArgument var1) throws Exception;
   }
}
