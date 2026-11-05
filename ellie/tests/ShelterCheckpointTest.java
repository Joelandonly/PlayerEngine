package com.player2.playerengine.tasks.setup;
import java.nio.file.*;
import java.util.*;
import static com.player2.playerengine.tasks.setup.StarterBaseLayout.*;
/** Real journal persistence, including the v5 truncated checkpoint reproduction. */
public final class ShelterCheckpointTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("ellie-shelter-journal");Pos chest=new Pos(19,68,-41);var plan=new SetupShelter.Plan(new Pos(9,67,-41),"oak");
  var route=List.of(new Pos(11,67,-42),new Pos(12,67,-42),new Pos(13,67,-42),new Pos(14,67,-42),new Pos(15,67,-42),new Pos(16,67,-42),new Pos(16,67,-41));
  var j=CoreGoalJournal.open(root,"ellie","setup_survival");
  j.put("SETUP_SHELTER_V2",0,"minecraft:overworld","19,68,-41|9,67,-41|oak|"+"minecraft:dirt,".repeat(110)+"|11,67,-42");
  var old=CoreGoalJournal.open(root,"ellie","setup_survival");check(old.get("detail","").length()==360,"reproduce actual journal truncation");
  String[] recovered=SetupSurvivalWorld.restoreSite("SETUP_SHELTER_V2",old.get("detail",""),"minecraft:overworld",old.get("resource",""),chest);
  check(recovered[1].equals("9,67,-41"),"truncated v2 retains shelter coordinates");
  String detail=SetupSurvivalWorld.siteDetail(chest,plan,route);j.put("SETUP_SHELTER_V3",0,"minecraft:overworld",detail);
  var restored=CoreGoalJournal.open(root,"ellie","setup_survival");
  check(restored.get("detail","").equals(detail),"compact v3 survives actual journal writer");
  var fields=SetupSurvivalWorld.restoreSite("SETUP_SHELTER_V3",restored.get("detail",""),"minecraft:overworld","minecraft:overworld",chest);
  check(fields[3].split(";").length==7,"full approach retained");
  boolean blocked=false;try{SetupSurvivalWorld.restoreSite("SETUP_SHELTER_V3",detail,"minecraft:overworld","minecraft:overworld",chest.offset(1,0,0));}catch(IllegalStateException expected){blocked=true;}check(blocked,"genuine changed chest must still block relocation");
  System.out.println("ShelterCheckpointTest: 5 actual-journal persistence/migration checks passed");
 }
}
