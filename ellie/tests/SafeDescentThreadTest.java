package ellie.descent;
import java.util.concurrent.*;
import com.player2.playerengine.reliability.SafeDescent;
import ellie.descent.SafeDescentTest.*;
import static ellie.descent.SafeDescentTest.check;

/** Collision callbacks model neighbor reads; no real chunk scheduler or Minecraft server. */
public final class SafeDescentThreadTest {
 public static void main(String[] args)throws Exception {
  World first=world(),second=world();Context a=new Context(first),b=new Context(second);
  ExecutorService worker=Executors.newSingleThreadExecutor();
  try{
   for(int i=0;i<20;i++){
    Context c=i%2==0?a:b;
    Result result=worker.submit(()->{Result r=new Result(4);SafeDescent.filter(c,7,r);return r;}).get(2,TimeUnit.SECONDS);
    check(result.cost<SafeDescent.IMPOSSIBLE,"safe downward worker edge must survive collision neighbor lookup without live world access");
   }
   check(first.liveReads==0&&second.liveReads==0,"workers never query either live world");
   check(first.cachedCollisionReads==10&&second.cachedCollisionReads==10,"collision reads use the matching context view across alternating worlds");
   first.loaded=false;
   Result unloaded=worker.submit(()->{Result r=new Result(4);SafeDescent.filter(a,7,r);return r;}).get(2,TimeUnit.SECONDS);
   check(unloaded.cost==SafeDescent.IMPOSSIBLE,"unloaded old-world context rejects without fetching chunks");
   Result current=worker.submit(()->{Result r=new Result(4);SafeDescent.filter(b,7,r);return r;}).get(2,TimeUnit.SECONDS);
   check(current.cost<SafeDescent.IMPOSSIBLE&&second.cachedCollisionReads==11,"new-world planning remains independent of old context");
   System.out.println("SafeDescentThreadTest: "+SafeDescentTest.checks+" worker/view checks passed; chunk scheduler and world switching substituted, not a reproduced Minecraft freeze");
  }finally{worker.shutdownNow();}
 }
 static World world(){
  World w=new World();w.forbidLiveReads=true;w.put(3,"stone",1);
  w.cells.get(new Pos(1,3,0)).inspectNeighbors=true;
  return w;
 }
}
