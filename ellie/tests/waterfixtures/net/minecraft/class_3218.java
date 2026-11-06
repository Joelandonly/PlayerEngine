package net.minecraft;
import java.util.*;import java.util.concurrent.*;
/** Simulate a live-world request waiting for the server, versus available chunk data. */
public class class_3218 extends class_1937 {
 public final Map<String,class_2680> states=new HashMap<>();
 public final Set<String> loaded=new HashSet<>();
 public int liveReads,cachedReads;public boolean holdLiveReads;
 public final CountDownLatch liveReadEntered=new CountDownLatch(1),releaseLiveRead=new CountDownLatch(1);
 public class_2680 method_8320(class_2338 p){
  liveReads++;liveReadEntered.countDown();
  if(holdLiveReads)try{releaseLiveRead.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
  return cached(p.x,p.y,p.z);
 }
 public class_2680 cached(int x,int y,int z){cachedReads++;return states.getOrDefault(x+","+y+","+z,new class_2680(false));}
 public void water(int x,int y,int z){states.put(x+","+y+","+z,new class_2680(true));}
 public boolean loaded(int x,int z){return loaded.contains((x>>4)+","+(z>>4));}
}
