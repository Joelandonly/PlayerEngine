package com.player2.playerengine.nativecommands;
import java.util.*;
/** Conservative exact-command parser. Ordinary conversation is left to optional chat. */
public final class NativeCommandText {
 private NativeCommandText(){}
 public static final Set<String> IDS=Set.of("agentic","attack","audit_waypoint","bodylang","build_structure","chatclef","compare_waypoint","create_waypoint","delete_waypoint","deposit","deposit_storage_slot","deposit_to_storage","eat_food","equip","fish","follow","food","get","give","goto","harvest_farm","hero","idle","leaveboat","locate_storage","locate_structure","locate_waypoints","meat","mine","pickup_drops","place_sign","plant_farm","read_signs","reload_settings","resetmemory","scan","scan_storage","set_attack_hostiles","set_follow_mode","setup","setup_farm","smelt","smith","stop","withdraw_from_storage","withdraw_storage_slot");
 public static String resolve(String input){
  if(input==null)return null;
  String text=input.trim().replaceFirst("(?i)^ellie\\s*[,!:]\\s*","").replaceFirst("(?i)^ellie\\s+","").replaceFirst("^@","").trim();
  String lower=text.toLowerCase(Locale.ROOT),alias=lower.replaceAll("[.!?]+$","").trim().replaceAll("\\s+"," ");
  if(Set.of("setup","get started","prepare yourself","start setup","begin setup","resume setup","continue setup").contains(alias))return "setup";
  if(Set.of("make a farm","make a farm here","build a farm","build a farm here","set up a farm").contains(alias))return "setup_farm";
  if(lower.matches("(?:build|make) (?:a |an )?.*(?:village|settlement|shelter|cabin|cottage|lodge|workshop|storage shed|greenhouse|house).*$"))return "__local_build__ "+text.replaceFirst("(?i)^(?:build|make)\\s+","");
  int split=0;while(split<text.length()&&!Character.isWhitespace(text.charAt(split)))split++;
  String id=text.substring(0,split).toLowerCase(Locale.ROOT);
  if(split==text.length())id=id.replaceAll("[.!?]+$","");
  if(id.equals("drop"))id="give";
  if(IDS.contains(id)||Set.of("dummy","cycle","gamer","beat_minecraft","inventory","list","pause","unpause","stash","status").contains(id))return id+text.substring(split);
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
