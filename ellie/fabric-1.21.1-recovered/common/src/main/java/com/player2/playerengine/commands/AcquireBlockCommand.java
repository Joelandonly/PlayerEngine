package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.blockknowledge.BlockKnowledge;
import com.player2.playerengine.blockknowledge.KnowledgeAcquisition;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.tasks.base.Task;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

/** Native acquisition; never bypasses crafting/mining or drops impossible blocks. */
public final class AcquireBlockCommand extends Command {
    public AcquireBlockCommand() throws CommandException {
        super("acquire_block", "acquire_block <block> [count=1]. Uses vanilla block knowledge and existing native recipe/mining tasks. Reports unsupported acquisition sources rather than faking success.",
              new Arg<>(String.class, "block"), new Arg<>(Integer.class, "count", 1, 1));
    }

    @Override protected void call(PlayerEngineController mod, ArgParser args) throws CommandException {
        String input = args.get(String.class);
        int count = args.get(Integer.class);
        if (count < 1 || count > 256) {finishWithError("Count must be 1..256 per native acquisition operation");return;}
        BlockKnowledge.Entry entry = BlockKnowledge.get(input);
        if (entry == null) {finishWithError("Unknown block " + input + ". Try block_info or locate_block; suggestions: " + String.join(", ", BlockKnowledge.suggestions(input)));return;}
        if (!entry.obtainable() || entry.itemForm() == null) {
            finishWithError(entry.id() + " has no survival obtainable item form in 1.21.1; world location is not equivalent to obtaining an item");return;
        }
        ResourceLocation key = ResourceLocation.tryParse(entry.itemForm());
        Item item = key == null ? null : (Item)BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        if (item == null) {finishWithError("Item missing from live registry: " + entry.itemForm());return;}
        if (mod.getItemStorage().getItemCountInventoryOnly(item) >= count) {
            finishWithInfo("Already carrying " + count + "+ " + entry.itemForm());return;
        }
        Optional<Task> action = KnowledgeAcquisition.task(mod, entry.itemForm(), count);
        if (action.isEmpty()) {
            finishWithError("Known but not yet executable: " + entry.id() + ". Use block_info for possible sources (e.g. shearing, loot or special tools).");return;
        }
        Task task = action.get();
        mod.runUserTaskTracked("acquire_block:" + entry.id() + "x" + count,"acquire_block",task,RollbackPolicy.NONE, () -> {
            int present = mod.getItemStorage().getItemCountInventoryOnly(item);
            if (present >= count) finishWithInfo("Verified carrying " + present + " " + entry.itemForm());
            else finishWithNote("Acquisition task ended; carrying " + present + "/" + count + " " + entry.itemForm() + ". Not declaring success; check task diagnostics and storage.");
        });
    }
}
