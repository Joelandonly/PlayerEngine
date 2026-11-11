package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.InventoryCleanupStorage;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import net.minecraft.core.BlockPos;

/** Explicit one-time destination registration. No guessing about which chest is disposable. */
public final class CleanupStorageCommand extends Command {
    public CleanupStorageCommand() throws CommandException {
        super("cleanup_storage",
            "cleanup_storage <x> <y> <z> designates a non-project chest/barrel for automatic surplus unloading during gathering. "
              + "cleanup_storage status reports the saved destination; cleanup_storage off disables it. "
              + "Schematic storage chests are forbidden, including after the schematic task ends.",
            new Arg<>(String.class, "coordinates / status / off", "", 0, false));
    }
    @Override
    protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
        String[] units = parser.getArgUnits();
        if (units.length == 0 || (units.length == 1 && "status".equalsIgnoreCase(units[0]))) {
            finishWithInfo(InventoryCleanupStorage.status(mod));
        } else if (units.length == 1 && "off".equalsIgnoreCase(units[0])) {
            String result = InventoryCleanupStorage.disable(mod);
            finishWithInfo(result);
        } else if (units.length == 3) {
            try {
                BlockPos pos = new BlockPos(Integer.parseInt(units[0]), Integer.parseInt(units[1]), Integer.parseInt(units[2]));
                String result = InventoryCleanupStorage.register(mod, pos);
                if (result.startsWith("General cleanup storage registered")) finishWithInfo(result);
                else finishWithError(result);
            } catch (NumberFormatException ex) { finishWithError("Expected integer x y z coordinates"); }
        } else {
            finishWithError("Usage: cleanup_storage <x> <y> <z> | status | off");
        }
    }
}
