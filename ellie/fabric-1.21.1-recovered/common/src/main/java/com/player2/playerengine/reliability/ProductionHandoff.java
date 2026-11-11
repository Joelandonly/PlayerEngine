package com.player2.playerengine.reliability;
import com.player2.playerengine.tasks.base.Task;
import java.lang.reflect.Array;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
/** Keep an in-flight native furnace child alive until its material is in inventory. No new craft timer. */
public final class ProductionHandoff {
 private ProductionHandoff(){}
 public static void invalidate(Object controller){try{call(call(controller,"getItemStorage"),"registerSlotAction");}catch(Exception|LinkageError ignored){}}
 public static void beforeSmeltTick(Object task,Object controller){
  try{Object position;
   try{position=get(task,"furnacePos");}catch(NoSuchFieldException smoker){position=get(task,"smokerPos");}
   if(position==null)return;Object furnace=call(call(controller,"getWorld"),"method_8321",position);if(furnace==null)return;
   if(ownsPending(task,furnace))set(task,"isSmelting",true);
  }catch(Exception|LinkageError ignored){}
 }
 private static boolean matches(Object stack,Object target)throws Exception{
  if(Boolean.TRUE.equals(call(stack,"method_7960")))return false;Object item=call(stack,"method_7909"),matches=call(target,"getMatches");
  for(int i=0;i<Array.getLength(matches);i++)if(item.equals(Array.get(matches,i)))return true;return false;
 }
 private static boolean ownsPending(Object task,Object furnace)throws Exception{
  Object targets=get(task,"targets");for(int i=0;i<Array.getLength(targets);i++){
   Object target=Array.get(targets,i);
   if(matches(call(furnace,"method_5438",2),call(target,"getItem")))return true;
   if(matches(call(furnace,"method_5438",0),call(target,"getMaterial"))&&(((Number)get(furnace,"field_11981")).intValue()>0||!Boolean.TRUE.equals(call(call(furnace,"method_5438",1),"method_7960"))))return true;
  }return false;
 }
 public static Task pending(Object macro,Object controller){
  try {
   Object top=get(macro,"sub"),node=top;
   for(int depth=0;node!=null&&depth<64;depth++,node=get(node,"sub")){
    String type=node.getClass().getName();
    if((!type.endsWith(".SmeltInFurnaceTask")&&!type.endsWith(".SmeltInSmokerTask"))||((Task)node).stopped())continue;
    Object position;
    try{position=get(node,"furnacePos");}catch(NoSuchFieldException smoker){position=get(node,"smokerPos");}
    if(position==null)return null;
    Object furnace=call(call(controller,"getWorld"),"method_8321",position);if(furnace==null)return null;
    if(!ownsPending(node,furnace)&&!Boolean.TRUE.equals(get(node,"isSmelting")))return null;
    Object storage=call(controller,"getItemStorage");call(storage,"registerSlotAction");
    Object targets=get(node,"targets");boolean missing=false;
    for(int i=0;i<Array.getLength(targets);i++){
     Object target=call(Array.get(targets,i),"getItem");
     int have=((Number)call(storage,"getItemCountInventoryOnly",call(target,"getMatches"))).intValue();
     if(have<((Number)call(target,"getTargetCount")).intValue())missing=true;
    }
    if(missing)return (Task)top;
    return null;
   }
  }catch(Exception|LinkageError error){
   // An unavailable boundary never authorizes a material grant or fabricated completion.
  }
  return null;
 }
}
