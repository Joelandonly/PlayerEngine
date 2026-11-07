package com.player2.playerengine.observatory;
import java.nio.file.*;import java.util.concurrent.*;

/** Real JVM thread capture while diagnostic monitors are held; Minecraft client/server substituted. */
public final class ServerStallCaptureTest {
 static int checks;static final long SEC=1_000_000_000L;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception {
  DistanceDiagnostics.announceBuild();
  check(ObservatoryTelemetry.view().get("buildRevision").contains("20261107-schematic-prepare-clearance-v18.1"),"actual packaged build identity recorded at startup");
  Object server=new Object(),other=new Object();Path file=Path.of("build/reports/stall-capture-regression.txt");Files.deleteIfExists(file);
  DistanceDiagnostics.serverHeartbeat(server,100*SEC);
  check(!DistanceDiagnostics.captureIfStalled(server,false,true,100*SEC,file),"healthy server produces no dump");
  check(!DistanceDiagnostics.captureIfStalled(server,true,true,131*SEC,file),"normal pause suppresses capture");
  check(!DistanceDiagnostics.captureIfStalled(server,false,true,132*SEC,file),"unpause starts fresh grace period");
  check(!DistanceDiagnostics.captureIfStalled(server,false,true,161*SEC,file),"unpause grace not yet elapsed");
  CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
  Thread stalled=new Thread(()->{
   synchronized(DistanceDiagnostics.class){synchronized(ObservatoryTelemetry.class){
    locked.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
   }}
  },"simulated-stalled-server");stalled.start();check(locked.await(2,TimeUnit.SECONDS),"simulated server holds existing diagnostic and telemetry monitors");
  ExecutorService client=Executors.newSingleThreadExecutor(r->new Thread(r,"simulated-live-client"));
  try{
   check(client.submit(()->DistanceDiagnostics.captureIfStalled(server,false,true,163*SEC,file)).get(2,TimeUnit.SECONDS),"client captures without entering server-held monitors");
   String text=Files.readString(file);
   check(text.contains("simulated-stalled-server")&&text.contains("CountDownLatch.await")&&text.contains("monitors:"),"full JVM stacks and monitor ownership recorded");
   check(!DistanceDiagnostics.captureIfStalled(server,false,true,200*SEC,file),"one dump per unchanged stalled heartbeat");
   check(!DistanceDiagnostics.captureIfStalled(other,false,true,201*SEC,file),"old heartbeat cannot accuse another server");
   check(!DistanceDiagnostics.captureIfStalled(server,false,false,202*SEC,file),"world/menu transition suppresses capture");
   DistanceDiagnostics.serverHeartbeat(server,210*SEC);
   check(!DistanceDiagnostics.captureIfStalled(server,false,true,210*SEC,file),"resumed heartbeat resets stall state");
   check(!DistanceDiagnostics.captureIfStalled(server,false,true,239*SEC,file),"under-threshold pause in heartbeat produces no dump");
   check(client.submit(()->DistanceDiagnostics.captureIfStalled(server,false,true,270*SEC,file)).get(2,TimeUnit.SECONDS),"later independent stall captured after resumed ticking");
  }finally{release.countDown();stalled.join(2000);client.shutdownNow();}
  System.out.println("ServerStallCaptureTest: "+checks+" checks passed; real thread dump/monitor test, Minecraft lifecycle substituted");
 }
}
