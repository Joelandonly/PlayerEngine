import com.player2.playerengine.agentic.StorageIntelligence;

public final class SetupStorageScopeTest {
    public record Position(int x,int y,int z){public int method_10263(){return x;}public int method_10264(){return y;}public int method_10260(){return z;}}
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){
        Object npc=new Object(),other=new Object();var home=new Position(10,64,10);var unrelated=new Position(11,64,10);
        check(StorageIntelligence.allowedSetupStorage(npc,unrelated),"ordinary resource commands retain existing source selection");
        StorageIntelligence.setupChest(npc,home);
        check(StorageIntelligence.allowedSetupStorage(npc,new Position(10,64,10)),"same canonical chest coordinates reused");
        check(!StorageIntelligence.allowedSetupStorage(npc,unrelated),"setup cannot withdraw from unrelated remembered storage");
        check(StorageIntelligence.allowedSetupStorage(other,unrelated),"setup restriction is per NPC");
        check(!StorageIntelligence.allowedSetupStorage(npc,new Object()),"unreadable position cannot bypass setup scope");
        StorageIntelligence.setupChest(npc,null);
        check(StorageIntelligence.allowedSetupStorage(npc,unrelated),"completion/cancellation restores normal source policy");
        System.out.println("SetupStorageScopeTest: 6 chest-scope checks passed");
    }
}
