package com.player2.playerengine.reliability;
/** Detect rapid A-B-A-B mining-target oscillation without treating repeated ticks
 * on ONE target as an oscillation or accumulating history across distant targets.
 * Only applies to native DestroyBlockTask targets that remain physically present.
 */
public final class TargetOscillationGuard {
 private String previous="",first="",second="";
 private int alternating;
 private long started;
 private static final long WINDOW_NS=45_000_000_000L;
 public boolean observe(String key,long now){
  if(key==null||key.isBlank())return false;
  if(key.equals(previous))return false;
  previous=key;
  if(now<started || now-started>WINDOW_NS){first=key;second="";alternating=0;started=now;return false;}
  if(first.isEmpty()){first=key;started=now;return false;}
  if(key.equals(first)||key.equals(second)){
   if(key.equals(first)&&second.isEmpty())return false;
   alternating++;
  }else if(second.isEmpty()){second=key;alternating=1;}
  else {first=key;second="";alternating=0;started=now;return false;}
  if(alternating<10)return false;
  // Stop the current alternating pair once; next observed target starts a new window.
  first="";second="";alternating=0;started=now;
  return true;
 }
 public void reset(){previous="";first="";second="";alternating=0;started=0;}
}
