package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.blockknowledge.BlockKnowledge;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

/** Human-readable preview of available acquisition routes, not permission to fabricate items. */
public final class BlockInfoCommand extends Command {
    public BlockInfoCommand() throws CommandException {
        super("block_info", "block_info <block>. Shows item form and ranked vanilla crafting, mining, and loot methods for any 1.21.1 block. Does not move or edit the world.",
              new Arg<>(String.class, "block"));
    }

    @Override protected void call(PlayerEngineController mod, ArgParser args) throws CommandException {
        String input = args.get(String.class);
        BlockKnowledge.Entry block = BlockKnowledge.get(input);
        if (block == null) {
            finishWithNote("Unknown vanilla 1.21.1 block: " + input + ". Possible IDs: " + String.join(", ", BlockKnowledge.suggestions(input)));
            return;
        }
        Set<String> inventory = new HashSet<>();
        // Only positive presence is used for ranking; not a guarantee of enough quantity.
        for (BlockKnowledge.Route route : block.routes()) for (String id : route.ingredients()) {
            if (!id.startsWith("minecraft:")) continue;
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
                Item item = (Item)BuiltInRegistries.ITEM.get(key);
                if (mod.getItemStorage().getItemCountInventoryOnly(item) > 0) inventory.add(id);
            }
        }
        StringBuilder lines = new StringBuilder(block.id());
        lines.append(" | item=").append(block.itemForm() == null ? "none" : block.itemForm());
        if (!block.obtainable()) lines.append(" | NOT obtainable as an item in vanilla survival");
        if (!block.harvestTool().isBlank()) lines.append(" | tool=").append(block.harvestTool());
        var ranked = BlockKnowledge.ranked(block, inventory, Set.of());
        for (int i = 0; i < Math.min(6, ranked.size()); i++) lines.append("\n").append(i+1).append(". ").append(ranked.get(i).summary());
        if (ranked.isEmpty() && !block.note().isBlank()) lines.append("\n").append(block.note());
        lines.append("\nListed possibilities are not verified local availability.");
        finishWithInfo(lines.toString());
    }
}
