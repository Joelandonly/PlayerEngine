import com.player2.playerengine.tasks.setup.*;
import com.player2.playerengine.tasks.setup.StarterBaseLayout.*;
import java.util.*;

/** Structural world predicates, not a mocked builder-success flag. */
public final class SetupShelterTest {
    static final class World implements SetupShelter.Probe {
        final Map<Pos,String> blocks=new HashMap<>();boolean obstructed;
        public boolean solid(Pos p){String id=blocks.getOrDefault(p,"air");return !id.equals("air")&&!id.endsWith("_door");}
        public boolean open(Pos p){return blocks.getOrDefault(p,"air").equals("air");}
        public boolean door(Pos p){return blocks.getOrDefault(p,"air").endsWith("_door");}
        public boolean canBuild(Placement p){return !obstructed;}
    }
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception{
        var plan=new SetupShelter.Plan(new Pos(-20,63,-10),"spruce");var cells=SetupShelter.cells(plan);
        check(cells.size()==125&&cells.stream().map(Placement::pos).distinct().count()==125,"bounded, unique 5x5x5 native plan");
        check(cells.stream().filter(p->p.resource().equals("spruce_planks")).count()==96&&cells.stream().filter(p->p.resource().equals("spruce_door")).count()==2,"local palette and real two-block entrance");
        World w=new World();for(var p:cells)w.blocks.put(p.pos(),p.resource());w.blocks.put(plan.door().offset(0,-1,-1),"stone");
        check(SetupShelter.verified(plan,w),"roof, floor, walls, doorway and interior verified together");
        var roof=plan.floor().offset(2,4,2);w.blocks.remove(roof);check(!SetupShelter.verified(plan,w),"missing roof cannot be called shelter");w.blocks.put(roof,"spruce_planks");
        w.blocks.put(plan.center(),"stone");check(!SetupShelter.verified(plan,w),"blocked interior invalidates enclosure");w.blocks.remove(plan.center());
        w.blocks.remove(plan.door());check(!SetupShelter.verified(plan,w),"missing door invalidates enclosure");w.blocks.put(plan.door(),"spruce_door");
        w.blocks.put(plan.door().offset(0,0,-1),"stone");check(!SetupShelter.verified(plan,w),"blocked external entrance invalidates enclosure");w.blocks.remove(plan.door().offset(0,0,-1));
        w.obstructed=true;check(!SetupShelter.buildable(plan,w),"foreign/protected cells cannot be bulldozed to force a fit");
        check(!SetupShelter.code(plan).contains("oak")&&!SetupShelter.code(plan).contains("torch"),"local wood generated; no hidden lighting or unrelated materials");
        System.out.println("SetupShelterTest: 9 geometry/palette/protection checks passed (world boundary substituted)");
    }
}
