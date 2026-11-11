package com.player2.playerengine.automaton.api.selection;

import com.player2.playerengine.automaton.api.component.EntityComponentKey;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.selection.SelectionManager;
import net.minecraft.core.Direction;

public interface ISelectionManager {
   EntityComponentKey<ISelectionManager> KEY = new EntityComponentKey<>(SelectionManager::new);

   ISelection addSelection(ISelection var1);

   ISelection addSelection(BetterBlockPos var1, BetterBlockPos var2);

   ISelection removeSelection(ISelection var1);

   ISelection[] removeAllSelections();

   ISelection[] getSelections();

   ISelection getOnlySelection();

   ISelection getLastSelection();

   ISelection expand(ISelection var1, Direction var2, int var3);

   ISelection contract(ISelection var1, Direction var2, int var3);

   ISelection shift(ISelection var1, Direction var2, int var3);
}
