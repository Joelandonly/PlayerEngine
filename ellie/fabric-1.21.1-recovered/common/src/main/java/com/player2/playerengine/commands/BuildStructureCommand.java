package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.tasks.construction.build_structure.BuildStructureTask;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;

public class BuildStructureCommand extends Command {
   public BuildStructureCommand() throws CommandException {
      super(
         "build_structure",
         "Build a supported local blueprint using real materials and verified physical layers. Templates: starter shelter, cabin, lodge, stone cottage, workshop, storage shed, greenhouse, 47x47 fortified village. x y z search_query description. Unsupported requests fail without AI.",
         new Arg<>(Integer.class, "x"),
         new Arg<>(Integer.class, "y"),
         new Arg<>(Integer.class, "z"),
         new Arg<>(String.class, "search_query"),
         new Arg<>(String.class, "description")
      );
   }

   @Override
   protected void call(PlayerEngineController var1, ArgParser var2) throws CommandException {
      int var3 = var2.get(Integer.class);
      int var4 = var2.get(Integer.class);
      int var5 = var2.get(Integer.class);
      String var6 = var2.get(String.class);
      String var7 = var2.get(String.class);
      BuildStructureTask var8 = new BuildStructureTask(new BlockPos(var3, var4, var5), var6, var7, var1);
      String var9 = "physical_build:" + var3 + ":" + var4 + ":" + var5 + ":" + System.nanoTime();
      var1.runUserTaskTracked(var9, "build_structure", var8, RollbackPolicy.NONE, () -> {
         if (!var8.isFinished()) {
            this.finishWithError("Physical construction was interrupted; not completed");
         } else {
            try {
               Field var2x = BuildStructureTask.class.getDeclaredField("numErrors");
               var2x.setAccessible(true);
               if (var2x.getInt(var8) > 2) {
                  this.finishWithError("Physical construction failed. Check the [PlayerEngine physical builder] log messages; no completion was verified.");
                  return;
               }
            } catch (ReflectiveOperationException var3x) {
               this.finishWithError("Cannot verify construction result: " + var3x.getMessage());
               return;
            }

            this.finish();
         }
      });
   }
}
