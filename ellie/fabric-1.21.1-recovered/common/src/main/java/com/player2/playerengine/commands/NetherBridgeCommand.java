package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.tasks.construction.NetherBridgeTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class NetherBridgeCommand extends Command {
   public NetherBridgeCommand() {
      super("netherbridge", "Build a three-wide, three-high Nether highway with a netherrack floor.");
   }

   @Override protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      String[] args = parser.getArgUnits();
      if (args.length != 2 && args.length != 5) {
         throw new CommandException("Expected netherbridge <north|south|east|west> <length> [originX originY originZ]");
      }
      Direction direction = Direction.byName(args[0]);
      if (direction == null || !direction.getAxis().isHorizontal()) throw new CommandException("Choose north, south, east or west.");
      if (!mod.getWorld().dimension().equals(Level.NETHER)) throw new CommandException("Ellie must be in the Nether.");
      BlockPos origin = mod.getEntity().blockPosition();
      int length;
      try {
         length = Integer.parseInt(args[1]);
         if (args.length == 5) origin = new BlockPos(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
      } catch (NumberFormatException invalid) {
         throw new CommandException("Length and origin coordinates must be whole numbers.");
      }
      if (length < 1 || length > NetherBridgeTask.MAX_LENGTH) throw new CommandException("Length must be 1–256 blocks.");
      if (!mod.getWorld().isInWorldBounds(origin) || !mod.getWorld().getWorldBorder().isWithinBounds(origin)
         || args.length == 5 && !mod.getWorld().hasChunkAt(origin)) {
         throw new CommandException("Choose a loaded launch point inside the world border.");
      }
      var task = new NetherBridgeTask(origin, direction, length);
      mod.reportAgenticProgress("Netherbridge " + direction + " " + length + " from " + origin.toShortString(), true);
      mod.runUserTaskTracked("netherbridge", "netherbridge", task, RollbackPolicy.NONE, this::finish);
   }
}
