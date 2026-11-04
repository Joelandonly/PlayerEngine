package com.player2.playerengine.nativecommands;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.build_structure.VillageSiteGuard;
import java.util.Optional;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
/** Local blueprint selection followed by the original physical layer builder. */
public final class NativeBuildSupport {
 private NativeBuildSupport() {}
 public static void start(Object root) {
  try {
   if(Boolean.TRUE.equals(get(root,"isDone")))return;
   if(get(root,"actuallyRunningTask")!=null){
    if(Boolean.TRUE.equals(get(root,"nativeResumePending"))){
     Task child=(Task)get(root,"actuallyRunningTask");
     if(!child.isFinished()&&get(child,"result") instanceof Optional<?> result&&result.isEmpty()){
      child.reset();
      com.player2.playerengine.observatory.ObservatoryTelemetry.event("BUILD_LOCAL_RESUME","Resuming physical child after safety/gesture suspension; blueprint retained");
     }
     set(root,"nativeResumePending",false);
    }
    return;
   }
   com.player2.playerengine.observatory.ObservatoryTelemetry.goalStart(root);
   String code=(String)callStatic("com.player2.playerengine.tasks.construction.build_structure.FastStarterBlueprint","maybeCode",root);
   if(code==null){failed(root,"Unsupported local blueprint. Choose a starter shelter, cabin, lodge, stone cottage, workshop, storage shed, greenhouse or 47x47 fortified village. No AI request was made.");return;}
   var type=Class.forName(root.getClass().getName()+"$BuildFromCode");
   var ctor=type.getDeclaredConstructor(root.getClass(),root.getClass(),String.class);ctor.setAccessible(true);
   Task child=(Task)ctor.newInstance(root,root,code);child.controller=((Task)root).controller;
   set(root,"actuallyRunningTask",child);
  }catch(Exception|LinkageError e){failed(root,"Local builder unavailable: "+e.getClass().getSimpleName()+" "+e.getMessage());}
 }
 public static Task tick(Object root) {
  try {
   if(Boolean.TRUE.equals(get(root,"isDone")))return null;
   Task child=(Task)get(root,"actuallyRunningTask");
   if(child==null){start(root);child=(Task)get(root,"actuallyRunningTask");if(child==null)return null;}
   if(!child.isFinished()&&!child.stopped())return child;
   Object raw=get(child,"result");
   if(!(raw instanceof Optional<?> outer)||outer.isEmpty()||!(outer.get() instanceof Optional<?> result)){failed(root,"Physical builder stopped without a verified result");return null;}
   if(result.isPresent()){failed(root,String.valueOf(result.get()));return null;}
   if(((Number)get(root,"numErrors")).intValue()>2){failed(root,"Physical builder reported a failure");return null;}
   set(root,"isDone",true);return null;
  }catch(Exception|LinkageError e){failed(root,"Cannot verify physical build: "+e.getClass().getSimpleName());return null;}
 }
 public static void suspended(Object root,boolean accepted){try{set(root,"nativeResumePending",accepted);}catch(Exception e){throw new IllegalStateException(e);}}
 public static void stop(Object root){
  try{Object child=get(root,"actuallyRunningTask");if(child!=null)VillageSiteGuard.clearForJob(child);
   if(!Boolean.TRUE.equals(get(root,"isDone")))failed(root,"Physical construction interrupted; completed blocks remain in the world");
  }catch(Exception|LinkageError e){failed(root,"Cannot release physical construction");}
 }
 private static void failed(Object root,String reason){
  System.err.println("[EllieNativeBuild] "+reason);
  com.player2.playerengine.observatory.ObservatoryTelemetry.event("BUILD_LOCAL_FAILED",reason);
  try{set(root,"numErrors",3);set(root,"nativeFailure",reason);set(root,"isDone",true);}catch(Exception e){throw new IllegalStateException("Cannot record native build failure",e);}
 }
 public static boolean successful(Object root){try{return Boolean.TRUE.equals(get(root,"isDone"))&&((Number)get(root,"numErrors")).intValue()==0&&get(root,"nativeFailure")==null;}catch(Exception e){return false;}}
 public static String reason(Object root){try{Object error=get(root,"nativeFailure");return error==null?(successful(root)?"All local blueprint blocks verified":"Physical construction in progress"):String.valueOf(error);}catch(Exception e){return "Build outcome unavailable";}}
}
