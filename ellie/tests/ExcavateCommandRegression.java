import com.player2.playerengine.tasks.construction.ExcavateAreaTask;
import com.player2.playerengine.nativecommands.ExcavationCommandRoute;
import java.util.Arrays;

/** Pure geometry and command grammar checks; no world or NPC substituted. */
public final class ExcavateCommandRegression {
    static int checks;
    static void check(boolean valid,String reason){checks++;if(!valid)throw new AssertionError(reason);}
    static void reject(Runnable action,String reason){
        try{action.run();throw new AssertionError(reason);}
        catch(IllegalArgumentException expected){checks++;}
    }
    public static void main(String[]args){
        int[] bounds=ExcavateAreaTask.normalize(120,74,120,100,64,100);
        check(Arrays.equals(bounds,new int[]{100,64,100,120,74,120,4851}),
            "inclusive corners/dimensions must be normalized");
        check(ExcavateAreaTask.normalize(100,64,100,100,64,100)[6]==1,
            "single block allowed");
        check(ExcavateAreaTask.normalize(100,-64,100,100,-64,100)[6]==1,
            "vanilla minimum Y allowed");
        reject(()->ExcavateAreaTask.normalize(0,64,0,100,84,100),
            "unbounded huge area should be refused");
        reject(()->ExcavateAreaTask.normalize(0,320,0,1,320,1),
            "above max build height should be refused");
        reject(()->ExcavateAreaTask.normalize(0,-65,0,1,-65,1),
            "below min build height should be refused");
        check(ExcavationCommandRoute.exact("Ellie, excavate 100 64 100 120 74 120")
                .equals("excavate 100 64 100 120 74 120"),
            "exact NPC owner syntax");
        check(ExcavationCommandRoute.exact("excavate 120 74 120 100 64 100")
                .equals("excavate 120 74 120 100 64 100"),
            "reversed corners still retained for native normalization");
        check(ExcavationCommandRoute.exact("@excavate -30 64 -40 12 68 0")
                .equals("excavate -30 64 -40 12 68 0"),
            "signed coordinates preserve absolute meaning");
        check(ExcavationCommandRoute.exact("excavate 1 2 3")==null,
            "never guess missing coordinates");
        check(ExcavationCommandRoute.exact("excavate this valley")==null,
            "never invent a huge selection from natural language");
        check(ExcavationCommandRoute.exact("excavate 1 2 3 4 5 9999999999")==null,
            "overflow is refused");
        check(ExcavationCommandRoute.exact("get string 64")==null,
            "unrelated material commands unchanged");
        System.out.println("ExcavateCommandRegression "+checks+
            " bounds and exact-command checks; no game simulation");
    }
}
