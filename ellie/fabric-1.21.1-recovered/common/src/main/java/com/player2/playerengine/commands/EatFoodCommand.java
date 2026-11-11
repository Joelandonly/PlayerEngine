package com.player2.playerengine.commands;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.*;
import com.player2.playerengine.nativecommands.NativeFood;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
public final class EatFoodCommand extends Command {
 public EatFoodCommand()throws CommandException{super("eat_food","Eat carried food. With no item, selects suitable inventory food; optionally specify an item ID.",new Arg<String>(String.class,"item","",0));}
 protected void call(PlayerEngineController controller,ArgParser parser)throws CommandException {
  String requested=parser.get(String.class);
  try{ProjectTaskGateway.run(controller,NativeFood.task(controller,requested),this::finish);}
  catch(Exception|LinkageError e){finishWithError("Cannot eat: "+e.getMessage());}
 }
}
