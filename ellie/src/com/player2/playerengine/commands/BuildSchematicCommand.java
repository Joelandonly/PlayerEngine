package com.player2.playerengine.commands;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.*;
import com.player2.playerengine.schematic.SchematicProjectTask;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
public final class BuildSchematicCommand extends Command {
 public BuildSchematicCommand()throws CommandException{super("schematic_build","Survival schematic build: filename x y z. Files belong in the game schematics folder; coordinates select the minimum corner.",new Arg<String>(String.class,"file"),new Arg<Integer>(Integer.class,"x"),new Arg<Integer>(Integer.class,"y"),new Arg<Integer>(Integer.class,"z"));}
 protected void call(PlayerEngineController controller,ArgParser parser)throws CommandException {
  String file=parser.get(String.class);int x=parser.get(Integer.class),y=parser.get(Integer.class),z=parser.get(Integer.class);
  try{ProjectTaskGateway.run(controller,new SchematicProjectTask(file,x,y,z,true),this::finish);}
  catch(Exception|LinkageError e){finishWithError("Schematic: "+e.getMessage());}
 }
}
