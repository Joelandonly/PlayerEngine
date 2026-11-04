package com.player2.playerengine.tasks.construction.build_structure;
import com.player2.playerengine.tasks.base.Task;
import java.util.Optional;
/** Physical child boundary only; never packaged and does not place blocks. */
public final class BuildStructureTask$BuildFromCode extends Task {
 public final String code;public boolean done;public Optional<Optional<String>> result=Optional.empty();
 public BuildStructureTask$BuildFromCode(BuildStructureTask owner,String code){this.code=code;}
 public boolean isFinished(){return done;}
}
