package com.player2.playerengine.tasks.construction;

import com.player2.playerengine.PlayerEngineController;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Inventory prerequisites shared by physical construction tasks. */
public enum ConstructionTools {
    AXE(Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE),
    PICKAXE(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE),
    SHOVEL(Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.GOLDEN_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL);

    private final Item[] options;

    ConstructionTools(Item... options) {
        this.options = options;
    }

    public Item starter() {
        return options[0];
    }

    public boolean available(PlayerEngineController mod) {
        return mod.getItemStorage().getItemCountInventoryOnly(options) > 0;
    }
}
