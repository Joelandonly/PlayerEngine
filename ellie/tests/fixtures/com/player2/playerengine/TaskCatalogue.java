package com.player2.playerengine;
import com.player2.playerengine.tasks.base.Task;
/** Symbolic boundary: records requests, does not craft items or simulate recipes. */
public final class TaskCatalogue {
 public static String[] getItemMatches(String id){return new String[]{id};}
 public static Object getItemTarget(String id,int count){return new Target(id,count);}
 public static Task getItemTask(String id,int count){return new NativeAcquisition(id,count);}
 public record Target(String id,int count){}
 public static final class NativeAcquisition extends Task {public final String id;public final int count;NativeAcquisition(String id,int count){this.id=id;this.count=count;}}
}
