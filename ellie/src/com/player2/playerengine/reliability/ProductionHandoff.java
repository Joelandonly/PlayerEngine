package com.player2.playerengine.reliability;
import com.player2.playerengine.tasks.base.Task;
import java.lang.reflect.Array;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
/** Keep an in-flight native furnace child alive until its material is in inventory. No new craft timer. */
public final class ProductionHandoff {
 private ProductionHandoff(){}
 public static Task pending(Object macro,Object controller){
  try {
   Object top=get(macro,"sub"),node=top;
   for(int depth=0;node!=null&&depth<64;depth++,node=get(node,"sub")){
    if(!node.getClass().getName().endsWith(".SmeltInFurnaceTask")||((Task)node).stopped())continue;
    Object position=get(node,"furnacePos");if(position==null)return null;
    Object furnace=call(call(controller,"getWorld"),"method_8321",position);if(furnace==null)return null;
    boolean pendingInput=!Boolean.TRUE.equals(call(call(furnace,"method_5438",0),"method_7960"));
    boolean pendingOutput=!Boolean.TRUE.equals(call(call(furnace,"method_5438",2),"method_7960"));
    if(!pendingInput&&!pendingOutput&&!Boolean.TRUE.equals(get(node,"isSmelting")))return null;
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
