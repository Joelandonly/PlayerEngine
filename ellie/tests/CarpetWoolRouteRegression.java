import com.player2.playerengine.schematic.SchematicWhiteCarpetAcquisition;
import com.player2.playerengine.schematic.SchematicWhiteCarpetAcquisition.Stage;

/**
 * Exact carpet math and resource route from the real packaged acquisition
 * class, WITHOUT simulating a sheep, world, iron mining, or native NPC.
 */
public final class CarpetWoolRouteRegression {
    private static int count;
    static void test(boolean truth,String msg){count++;if(!truth)throw new AssertionError(msg);}
    public static void main(String[]args){
        test(SchematicWhiteCarpetAcquisition.woolNeeded(0,48)==32,
            "48 white carpets require 32 wool");
        test(SchematicWhiteCarpetAcquisition.woolNeeded(3,48)==30,
            "three already crafted carpets reduce the wool bill");
        test(SchematicWhiteCarpetAcquisition.woolNeeded(47,48)==2,
            "partial final batch of three carpets is valid");
        test(SchematicWhiteCarpetAcquisition.woolNeeded(48,48)==0,
            "fully produced project carpet has no new wool bill");
        test(SchematicWhiteCarpetAcquisition.choose(0,0,0,48)==Stage.ACQUIRE_SHEARS,
            "no shears should not enter spider-string farming");
        test(SchematicWhiteCarpetAcquisition.choose(0,0,1,48)==Stage.COLLECT_WHITE_WOOL,
            "with shears choose actual white wool");
        test(SchematicWhiteCarpetAcquisition.choose(0,31,1,48)==Stage.COLLECT_WHITE_WOOL,
            "31 wool is one short");
        test(SchematicWhiteCarpetAcquisition.choose(0,32,1,48)==Stage.CRAFT_CARPETS,
            "32 wool must go straight to 48 carpets");
        test(SchematicWhiteCarpetAcquisition.choose(3,30,0,48)==Stage.CRAFT_CARPETS,
            "stored wool avoids unnecessary shears");
        test(SchematicWhiteCarpetAcquisition.choose(48,0,0,48)==Stage.DONE,
            "actual carpet output completes the task regardless of stock");
        try{
            SchematicWhiteCarpetAcquisition.woolNeeded(0,65);
            throw new AssertionError("unbounded target accepted");
        }catch(IllegalArgumentException expected){count++;}
        System.out.println("CarpetWoolRouteRegression "+count+
            " pure route checks passed (no sheep or live world substituted)");
    }
}
