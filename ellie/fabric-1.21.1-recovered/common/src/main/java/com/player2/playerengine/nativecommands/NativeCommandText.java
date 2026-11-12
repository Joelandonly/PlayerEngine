package com.player2.playerengine.nativecommands;
import java.util.*;
/** Conservative exact-command parser. Ordinary conversation is left to optional chat. */
public final class NativeCommandText {
 private NativeCommandText(){}
 public static final Set<String> IDS=Set.of("acquire_block","agentic","attack","audit_waypoint","block_info","bodylang","build_structure","chatclef","cleanup_storage","compare_waypoint","create_waypoint","delete_waypoint","deposit","deposit_storage_slot","deposit_to_storage","eat_food","equip","excavate","fish","follow","food","get","give","goto","harvest_farm","hero","idle","leaveboat","locate_block","locate_storage","locate_structure","locate_waypoints","meat","mine","pickup_drops","place_sign","plant_farm","read_signs","reload_settings","resetmemory","scan","safezone","scan_storage","schematic_build","schematic_prepare","set_attack_hostiles","set_follow_mode","setup","setup_farm","smelt","smith","stop","withdraw_from_storage","withdraw_storage_slot","waterfill");
 /**
  * Input-only normalization. Never make the executor guess: preserve all exact
  * registered commands, coordinates, names, quoted strings and command chains.
  * Native get and AGENTIC share one verified item/count parser for safe reorders.
  */
 public static String resolve(String input){
  if(input==null)return null;
  String text=input.trim().replaceFirst("(?i)^ellie\\s*[,!:]\\s*", "")
      .replaceFirst("(?i)^ellie\\s+", "")
      .replaceFirst("^@","").trim();
  text=text.replaceFirst("(?i)^please\\s+","")
      .replaceFirst("(?i)^(?:can|could|would)\\s+you\\s+(?:please\\s+)?","").trim();
  if(text.isEmpty())return null;
  text=text.replaceFirst("(?i)^schematica?\\s+(prepare|build)\\s+", "schematic_$1 ");
  // Owner-only native aliases; model is never asked to select these.
  text=text.replaceFirst("(?i)^(?:locate|find)\\s+block\\s+", "locate_block ")
      .replaceFirst("(?i)^block\\s+info\\s+", "block_info ")
      .replaceFirst("(?i)^(?:acquire|obtain)\\s+block\\s+", "acquire_block ");
  String lower=text.toLowerCase(Locale.ROOT);
  String alias=lower.replaceAll("[.!?]+$","").trim().replaceAll("\\s+"," ");
  if(Set.of("setup","get started","prepare yourself","start setup","begin setup",
            "resume setup","continue setup").contains(alias))return "setup";
  if(Set.of("make a farm","make a farm here","build a farm","build a farm here","set up a farm").contains(alias))
   return "setup_farm";
  if(Set.of("stop working","stop the task","stop that","halt","cancel task","cancel current task").contains(alias))
   return "stop";
  if(Set.of("put items away","put stuff away","store inventory","deposit items").contains(alias))
   return "deposit";
  if(Set.of("pick up drops","pick up loose drops","collect loose drops nearby").contains(alias))
   return "pickup_drops";
  if(alias.matches("go to -?\\d+ -?\\d+ -?\\d+"))
   return "goto "+text.replaceFirst("(?i)^go\\s+to\\s+","");
  if(lower.matches("(?:build|make) (?:a |an )?.*(?:village|settlement|shelter|cabin|cottage|lodge|workshop|storage shed|greenhouse|house).*$"))
   return "__local_build__ "+text.replaceFirst("(?i)^(?:build|make)\\s+","");
  int split=0;while(split<text.length()&&!Character.isWhitespace(text.charAt(split)))split++;
  String id=text.substring(0,split).toLowerCase(Locale.ROOT).replace('-','_');
  if(split==text.length())id=id.replaceAll("[.!?]+$","");
  if(id.equals("drop"))id="give";
  String argument=text.substring(split).trim();
  // Collect, gather, fetch, craft and obtain map only when the item and count
  // are unambiguous. No stone/cobblestone or other material guessing.
  if(Set.of("gather","collect","obtain","acquire","fetch","craft").contains(id)) {
   var resource=com.player2.playerengine.agentic.ResourceAgenticIntegration.parse(text);
   if(resource!=null)return "agentic get "+resource.item()+" "+resource.count();
   return null; // ordinary conversation still uses conversational routing
  }
  if(id.equals("agentic")) {
   var resource=com.player2.playerengine.agentic.ResourceAgenticIntegration.parse(argument);
   if(resource!=null)return "agentic get "+resource.item()+" "+resource.count();
  }
  if(id.equals("get") && argument.matches("(?i)^[0-9]{1,6}\\s+(?:of\\s+)?(?:minecraft:)?[a-z][a-z0-9_]{1,55}$")) {
   var resource=com.player2.playerengine.agentic.ResourceAgenticIntegration.parse(text);
   if(resource!=null)return "get "+resource.item()+" "+resource.count();
   // Even an excessive request goes to the validated AGENTIC error path,
   // never to GetCommand with a number in the item-name position.
   return "agentic "+text;
  }
  if(IDS.contains(id)||Set.of("dummy","cycle","gamer","beat_minecraft","inventory",
      "list","pause","unpause","stash","status").contains(id))
   return id+text.substring(split);
  return null;
 }
 /** Preserve semicolons inside quoted arguments, including sign text. */
 public static String[] splitCommands(String text){
  List<String> parts=new ArrayList<>();boolean quoted=false,escaped=false;int start=0;
  for(int i=0;i<text.length();i++){char c=text.charAt(i);
   if(escaped){escaped=false;continue;}if(c=='\\'){escaped=true;continue;}if(c=='"'){quoted=!quoted;continue;}
   if(c==';'&&!quoted){parts.add(text.substring(start,i));start=i+1;}
  }
  if(quoted)throw new IllegalArgumentException("Unclosed quote in command");
  parts.add(text.substring(start));return parts.toArray(String[]::new);
 }
 public static String localBuild(Object controller,String description)throws Exception{
  Object player=NativeAccess.call(controller,"getPlayer");
  int x=(int)Math.floor(((Number)NativeAccess.call(player,"method_23317")).doubleValue())+3;
  int y=(int)Math.floor(((Number)NativeAccess.call(player,"method_23318")).doubleValue());
  int z=(int)Math.floor(((Number)NativeAccess.call(player,"method_23321")).doubleValue())+3;
  if(description.contains("\"")||description.contains(";")||description.length()>512)throw new IllegalArgumentException("Use build_structure with explicit quoted arguments for this request");
  return "build_structure "+x+" "+y+" "+z+" \""+description+"\" \""+description+"\"";
 }
}
