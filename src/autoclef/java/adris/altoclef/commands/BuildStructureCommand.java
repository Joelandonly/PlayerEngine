package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.construction.build_structure.BuildStructureTask;
import adris.altoclef.player2api.Event;
import adris.altoclef.player2api.manager.ConversationManager;

public class BuildStructureCommand extends Command {
    public BuildStructureCommand() throws CommandException {
        super("build_structure",
                "Build a Minecraft survival base, shelter, house or other structure from a description and position. This automatically plans a blueprint, gathers/crafts supported materials, navigates and places blocks physically, and checks for completion. The description should clearly specify the requested structure.\\n"
                        + //
                        "IMPORTANT: You must put a position into the description. If the player you are talking to doesn't give any hints on where to build it, put in that player's position into the description, or some positional information. You MUST give a coordinate to build at. If you don't know the player's position, then put your own position. \\n"
                        + //
                        " Example call would be `build_structure a compact oak-plank starter shelter with a cobblestone floor. Build near position (-305, 72, 406)`",
                new Arg<>(String.class, "description"));
    }

    @Override
    protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
        String description = parser.get(String.class);
        BuildStructureTask task = new BuildStructureTask(description, mod);
        mod.runUserTask(task, () -> {
            if (!task.succeeded()) {
                ConversationManager.getOrCreateEventQueueData(mod).onEvent(
                        new Event.InfoMessage("Construction FAILED: " + task.getFailureReason()
                                + ". Do not tell the owner the building was completed."));
            }
            this.finish();
        });
    }

}