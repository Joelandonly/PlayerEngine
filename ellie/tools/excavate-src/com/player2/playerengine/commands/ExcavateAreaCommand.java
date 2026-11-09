package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.*;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
import com.player2.playerengine.tasks.construction.ExcavateAreaTask;

/** Owner-only, explicit 3D volume. Inclusive corners; no guessed area. */
public final class ExcavateAreaCommand extends Command {
    public ExcavateAreaCommand()throws CommandException {
        super("excavate",
            "Excavate a bounded 3D area with Ellie's native builder. "+
            "Usage: excavate x1 y1 z1 x2 y2 z2 (inclusive corners). "+
            "Rejects chests, block entities, fluids, protected depots and unloaded chunks.",
            new Arg<Integer>(Integer.class,"x1"),new Arg<Integer>(Integer.class,"y1"),
            new Arg<Integer>(Integer.class,"z1"),new Arg<Integer>(Integer.class,"x2"),
            new Arg<Integer>(Integer.class,"y2"),new Arg<Integer>(Integer.class,"z2"));
    }
    @Override protected void call(PlayerEngineController controller,ArgParser parser)throws CommandException {
        int x1=parser.get(Integer.class),y1=parser.get(Integer.class),z1=parser.get(Integer.class);
        int x2=parser.get(Integer.class),y2=parser.get(Integer.class),z2=parser.get(Integer.class);
        try {
            ProjectTaskGateway.run(controller,new ExcavateAreaTask(x1,y1,z1,x2,y2,z2),this::finish);
        } catch(Exception | LinkageError failure){
            finishWithError("Excavate: "+failure.getMessage());
        }
    }
}
