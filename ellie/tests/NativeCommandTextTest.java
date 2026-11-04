import com.player2.playerengine.nativecommands.NativeCommandText;
import com.player2.playerengine.commands.base.*;
import java.util.*;
public final class NativeCommandTextTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  for(String id:NativeCommandText.IDS)check(NativeCommandText.resolve("Ellie, "+id+" ArgX").equals(id+" ArgX"),"exact command bypass: "+id);
  check(NativeCommandText.resolve("Ellie, Get minecraft:oak_log 4").equals("get minecraft:oak_log 4"),"arguments and registry IDs must survive");
  check(NativeCommandText.resolve("Ellie, stop!").equals("stop"),"control command punctuation");
  check(NativeCommandText.resolve("Ellie, follow MyPlayer").equals("follow MyPlayer"),"player name case");
  check(NativeCommandText.resolve("Ellie, resume setup!").equals("setup"),"setup aliases");
  check(NativeCommandText.resolve("Ellie, make a farm here").equals("setup_farm"),"coordinate-less safe farm alias");
  check(NativeCommandText.resolve("Ellie, build a huge cobblestone village").startsWith("__local_build__ "),"local village alias");
  check(NativeCommandText.resolve("Ellie, tell me a joke")==null,"ordinary conversation is not guessed into commands");
  check(Arrays.equals(NativeCommandText.splitCommands("place_sign 1 2 3 up oak_sign f0=\"Hi; friend\"; get bread 2"),new String[]{"place_sign 1 2 3 up oak_sign f0=\"Hi; friend\""," get bread 2"}),"quoted semicolon must not become another command");
  try{NativeCommandText.splitCommands("get \"oops");throw new AssertionError("invalid quote accepted");}catch(IllegalArgumentException expected){}
  check(NativeCommandText.resolve("Ellie, gamer").equals("gamer"),"unavailable command is recognized for explicit refusal");
  System.out.println("NativeCommandTextTest: all 46 command IDs and 10 parser/alias checks pass");
 }
}
