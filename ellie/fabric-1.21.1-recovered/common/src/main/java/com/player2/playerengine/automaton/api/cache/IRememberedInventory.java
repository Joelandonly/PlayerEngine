package com.player2.playerengine.automaton.api.cache;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public interface IRememberedInventory {
   List<ItemStack> getContents();

   int getSize();
}
