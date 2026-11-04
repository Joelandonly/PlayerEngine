package com.player2.playerengine.reliability;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
/** Vanilla axe stripping requires a PlayerEntity; NPC block placement uses a separate supported context. */
public final class NpcItemUse {
 private NpcItemUse(){}
 public static Object use(Object stack,Object context)throws Exception {
  if(call(context,"method_8036")==null&&Class.forName("net.minecraft.class_1743").isInstance(call(stack,"method_7909"))){
   com.player2.playerengine.observatory.ObservatoryTelemetry.event("NPC_ITEM_USE_SKIPPED","Skipped player-only axe stripping on a non-player NPC");
   return Class.forName("net.minecraft.class_1269").getField("field_5811").get(null);
  }
  return call(stack,"method_7981",context);
 }
}
