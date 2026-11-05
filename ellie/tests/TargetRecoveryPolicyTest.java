import com.player2.playerengine.reliability.TargetRecoveryPolicy;
public final class TargetRecoveryPolicyTest {
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] args){
  TargetRecoveryPolicy p=new TargetRecoveryPolicy();for(int t=0;t<100;t++)check(!p.stalled("ore","stone",0,64,0,t,false),"five-second observation window");check(p.stalled("ore","stone",0,64,0,100,false),"stationary target should recover");
  p.reset();for(int t=0;t<500;t++)check(!p.stalled("ore","stone",t*.2,64,0,t,false),"genuine route movement must continue");
  p.reset();boolean exhausted=false;for(int t=0;t<1050;t++)exhausted|=p.stalled("unknown-source","same",t*.2,64,0,t,false);
  check(exhausted,"aimless endless wandering cannot count as progress forever");
  p.reset();for(int t=0;t<1050;t++)check(!p.stalled("1000,64,0","same",t*.2,64,0,t,false),"consistent movement toward a distant known block remains legitimate");
  p.reset();boolean circles=false;for(int t=0;t<250;t++)circles|=p.stalled("0,64,0","same",Math.sin(t*.05)*6,64,Math.cos(t*.05)*6,t,false);
  check(circles,"wandering around same known target triggers recovery");
  p.reset();for(int t=0;t<150;t++)p.stalled("ore","stone",t%2,64,0,t,false);check(p.stalled("ore","stone",0,64,0,150,false),"back-and-forth oscillation is not perpetual progress");
  p.reset();p.stalled("ore","stone",0,64,0,0,false);check(!p.stalled("ore","air",0,64,0,100,false),"verified block state changes reset stall");
  p.reset();for(int t=0;t<100;t++)check(!p.stalled("table","table",0,64,0,t,true),"reachable table observation window");check(p.stalled("table","table",0,64,0,100,true),"reach without completed work must recover");
  p.nextAttempt();p.resume(100,false);p.stalled("table","table",0,64,0,200,true);check(p.attempts()==1,"verified reach alone must not replenish recovery attempts");
  p.reset();for(int t=0;t<400;t++)check(!p.stalled("obsidian","same",0,64,0,t,false,t*.0003),"slow verified break progress must not be interrupted");
  for(int t=400;t<550;t++)p.stalled("obsidian","same",0,64,0,t,false,t%2==0?0:.01);check(p.stalled("obsidian","same",0,64,0,550,false,0),"damage loss/regain below high-water is not progress");
  check(p.nextAttempt()&&p.nextAttempt()&&p.nextAttempt()&&!p.nextAttempt(),"three bounded attempts");p.resume(550,true);check(p.attempts()==0,"verified work restores normal work");
  check(!p.stalled("other","new",0,64,0,551,false),"new target is independent");
  p.nextAttempt();p.nextAttempt();p.nextAttempt();p.stalled("other","verified output changed",0,64,0,600,false);check(p.nextAttempt(),"fresh verified work can authorize another bounded recovery sequence");
  p.reset();boolean pairStalled=false;
  for(int t=0;t<350;t++)pairStalled|=p.stalled(t%2==0?"8,50,-23":"-1,62,-32","ore|iron=0",0,64,0,t,false);
  check(pairStalled,"November 5 target switches cannot renew the stall timer");
  p.nextAttempt();p.stalled("third","ore|iron=0",0,64,0,351,false);
  check(p.attempts()==1,"reselection cannot replenish the shared attempt budget");
  p.reset();p.stalled("a","ore|iron=3|coal=2",0,64,0,0,false);
  p.stalled("a","ore|coal=2|iron=3",0,64,0,100,false);
  check(!p.verifiedWork(),"inventory order alone is not progress");
  p.stalled("b","ore|iron=2|coal=2",0,64,0,101,false);
  p.stalled("a","ore|iron=3|coal=2",0,64,0,102,false);
  check(!p.verifiedWork(),"loss and regain below high-water is not progress");
  p.stalled("b","ore|iron=4|coal=2",0,64,0,103,false);
  check(p.verifiedWork(),"net resource gain credits work despite target changes");
  p.reset();p.stalled("a","ore|iron=0",0,64,0,0,false);
  p.stalled("a","ore|iron=1",0,64,0,10,false);
  check(p.verifiedWork(),"first acquisition of a missing item credits progress");
  System.out.println("TargetRecoveryPolicyTest: 21 cases passed; bounded wandering, toward-target motion, oscillation, verified progress and recovery");
 }
}
