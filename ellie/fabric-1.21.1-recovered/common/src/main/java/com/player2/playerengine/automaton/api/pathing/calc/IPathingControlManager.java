package com.player2.playerengine.automaton.api.pathing.calc;

import com.player2.playerengine.automaton.api.process.IBaritoneProcess;
import com.player2.playerengine.automaton.api.process.PathingCommand;
import java.util.Optional;

public interface IPathingControlManager {
   void registerProcess(IBaritoneProcess var1);

   Optional<IBaritoneProcess> mostRecentInControl();

   Optional<PathingCommand> mostRecentCommand();
}
