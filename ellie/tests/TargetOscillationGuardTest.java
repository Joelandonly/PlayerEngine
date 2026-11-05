import com.player2.playerengine.reliability.TargetOscillationGuard;
public final class TargetOscillationGuardTest {
 static void yes(boolean b,String s){if(!b)throw new AssertionError(s);System.out.println("PASS "+s);}
 public static void main(String[] args){var guard=new TargetOscillationGuard();
  long time=1_000_000_000L;
  for(int i=0;i<30;i++)yes(!guard.observe("A",time+i*100_000_000L),"steady target is not a loop"+(i==0?"":""));
  boolean triggered=false;for(int i=0;i<15;i++)triggered|=guard.observe(i%2==0?"B":"A",time+3_000_000_000L+i*100_000_000L);
  yes(triggered,"alternating A-B target with no progress is detected");
  guard.reset();boolean seen=false;for(int i=0;i<30;i++)seen|=guard.observe("block"+i,time+i*100_000_000L);
  yes(!seen,"different successful targets are not blocked");
  guard.reset();seen=false;for(int i=0;i<20;i++)seen|=guard.observe(i%2==0?"A":"B",time+i*10_000_000_000L);
  yes(!seen,"slow, widely spaced revisits are not an oscillation");
  System.out.println("TargetOscillationGuardTest: all checks passed");
 }
}
