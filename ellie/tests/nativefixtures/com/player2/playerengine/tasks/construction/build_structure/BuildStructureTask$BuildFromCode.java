package com.player2.playerengine.tasks.construction.build_structure;
import com.player2.playerengine.tasks.base.Task;
import java.util.Optional;
/** Physical child boundary only; never packaged and does not place blocks. */
public final class BuildStructureTask$BuildFromCode extends Task {
 public final String code;public boolean done;public boolean halted;public Optional<Optional<String>> result=Optional.empty();
 public BuildStructureTask$BuildFromCode(BuildStructureTask owner,String code){this.code=code;}
 public boolean isFinished(){return done;}
 public boolean stopped(){return halted;}
 public void reset(){halted=false;}

 protected void onStart(){}
 protected com.player2.playerengine.tasks.base.Task onTick(){return null;}
 protected void onStop(com.player2.playerengine.tasks.base.Task next){}
 protected boolean isEqual(com.player2.playerengine.tasks.base.Task other){return this==other;}
 protected String toDebugString(){return "fixture";}
}
