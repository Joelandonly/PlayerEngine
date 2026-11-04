package com.player2.playerengine.nativecommands;
import com.player2.playerengine.tasks.base.Task;
import java.util.*;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
/** Select actual carried food; never start an equip loop for an absent item. */
public final class NativeFood {
 private NativeFood(){}
 public static Task task(Object controller,String requested)throws Exception {
  int hunger=((Number)call(call(call(call(controller,"getBaritone"),"getEntityContext"),"hungerManager"),"getFoodLevel")).intValue();
  if(hunger>=20)throw new IllegalArgumentException("Hunger is already full");
  Object inventory=call(controller,"getInventory");
  int size=((Number)call(inventory,"method_5439")).intValue();
  Object matches=requested.isBlank()?null:callStatic("com.player2.playerengine.TaskCatalogue","getItemMatches",requested);
  Set<Object> selected=new HashSet<>();if(matches!=null)for(int i=0;i<java.lang.reflect.Array.getLength(matches);i++)selected.add(java.lang.reflect.Array.get(matches,i));
  for(int i=0;i<size;i++) {
   Object stack=call(inventory,"method_5438",i);if(Boolean.TRUE.equals(call(stack,"method_7960")))continue;
   Object item=call(stack,"method_7909");
   if(!Boolean.TRUE.equals(callStatic("com.player2.playerengine.multiversion.item.ItemVer","isFood",item)))continue;
   if(!requested.isBlank()&&!selected.contains(item))continue;
   String name=String.valueOf(item).toLowerCase(Locale.ROOT);
   if(requested.isBlank()&&List.of("rotten_flesh","spider_eye","pufferfish","poisonous_potato","chorus_fruit").stream().anyMatch(name::contains))continue;
   Class<?> itemType=Class.forName("net.minecraft.class_1792");
   return (Task)Class.forName("com.player2.playerengine.tasks.misc.EatFoodTask").getConstructor(itemType).newInstance(item);
  }
  throw new IllegalArgumentException(requested.isBlank()?"No suitable food in inventory; use food or get bread first":"Requested food is unavailable or not edible: "+requested);
 }
}
