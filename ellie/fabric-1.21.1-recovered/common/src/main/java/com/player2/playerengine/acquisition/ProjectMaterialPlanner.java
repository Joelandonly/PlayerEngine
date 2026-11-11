package com.player2.playerengine.acquisition;

import com.player2.playerengine.blockknowledge.BlockKnowledge;
import java.util.*;

/** Pure, deterministic project bill planner. No inventory/world mutations here.
 * Recomputed from the real stock after every completed acquisition or transfer.
 */
public final class ProjectMaterialPlanner {
    private ProjectMaterialPlanner() {}
    public record Need(String item, int total, int inDepots, int carried, int shortage,
                       boolean recipeBacked, List<String> otherProjectDependencies) {}
    public record Bill(List<Need> order, Map<String,Integer> reserveFromInventory,
                       Map<String,Integer> missingFromStorage,
                       Map<String,Integer> estimatedInputs, List<String> unresolvedInputs) {
        public Bill {
            order = List.copyOf(order);
            reserveFromInventory = Map.copyOf(reserveFromInventory);
            missingFromStorage = Map.copyOf(missingFromStorage);
            estimatedInputs = Map.copyOf(estimatedInputs);
            unresolvedInputs = List.copyOf(unresolvedInputs);
        }
        public List<Map.Entry<String,Integer>> orderedEntries() {
            return order.stream().map(n -> Map.entry(n.item(),n.total())).toList();
        }
    }
    public static Bill plan(Map<String,Integer> required, Map<String,Integer> depotStock,
                            Map<String,Integer> inventory) {
        Objects.requireNonNull(required);Objects.requireNonNull(depotStock);Objects.requireNonNull(inventory);
        Map<String,Integer> missing = new TreeMap<>();
        Map<String,Integer> reserved = new TreeMap<>();
        Map<String,Set<String>> deps = new TreeMap<>();
        Map<String,Boolean> recipes = new TreeMap<>();
        for (var e : required.entrySet()) {
            String id = e.getKey(); int count = checked(e.getValue());
            int stored = checked(depotStock.getOrDefault(id,0));
            int held = checked(inventory.getOrDefault(id,0));
            int shortage = Math.max(0,count-stored);
            if (shortage == 0) continue;
            missing.put(id,shortage);
            if (held > 0) reserved.put(id, Math.min(held,shortage));
            BlockKnowledge.Entry known = BlockKnowledge.byItem(id);
            boolean recipe = known != null && known.routes().stream().anyMatch(r -> r.method().startsWith("craft/"));
            recipes.put(id,recipe);
            Set<String> dependencies=new TreeSet<>();
            if(known != null) {
                // Select a recipe to estimate project dependencies. The live recipe engine
                // remains authoritative for actual crafting and alternative ingredients.
                BlockKnowledge.Route best = known.routes().stream()
                  .filter(r->r.method().startsWith("craft/"))
                  .min(Comparator.comparingInt((BlockKnowledge.Route r)->
                      (int)r.ingredients().stream().filter(ing->required.containsKey(ing)).count())
                      .thenComparingInt(r->r.ingredients().size())).orElse(null);
                if(best!=null) for(String ingredient:best.ingredients())
                    if(!id.equals(ingredient) && required.containsKey(ingredient)) dependencies.add(ingredient);
            }
            deps.put(id,dependencies);
        }
        // Topological ordering of final outputs: an ingredient needed by another
        // blueprint output is handled first. Cycles/alternatives are bounded and
        // left for Minecraft's native recipe resolver to decide.
        List<Need> ordered=new ArrayList<>();Set<String> remaining=new TreeSet<>(missing.keySet());
        while(!remaining.isEmpty()) {
            String best=null;
            for(String id:remaining) {
                boolean blocked=deps.getOrDefault(id,Set.of()).stream().anyMatch(remaining::contains);
                if(!blocked) {best=id;break;}
            }
            if(best==null) best=remaining.iterator().next();
            remaining.remove(best);
            ordered.add(new Need(best,checked(required.get(best)),
                checked(depotStock.getOrDefault(best,0)),checked(inventory.getOrDefault(best,0)),
                missing.get(best),recipes.getOrDefault(best,false),
                List.copyOf(deps.getOrDefault(best,Set.of()))));
        }
        // Expand the *entire* outstanding final-output bill for a conservative
        // input estimate. Minecraft's live RecipeManager is still authoritative:
        // this reference includes block recipes, not all vanilla item recipes.
        Map<String,Integer> projected=new TreeMap<>();
        Set<String> unresolved=new TreeSet<>();
        for (Need n : ordered) {
            int stillToProduce=Math.max(0,n.shortage()-n.carried());
            if(stillToProduce>0) expand(n.item(),stillToProduce,0,new HashSet<>(),projected,unresolved);
        }
        return new Bill(ordered,reserved,missing,projected,new ArrayList<>(unresolved));
    }
    private static void expand(String id,int count,int depth,Set<String> path,
                                Map<String,Integer> inputs,Set<String> unresolved) {
        if(count<=0)return;
        if(depth>=8||!path.add(id)) {
            unresolved.add(id+" (cycle/depth cap)");add(inputs,id,count);return;
        }
        try {
            BlockKnowledge.Entry known=BlockKnowledge.byItem(id);
            if(known==null) {
                // This block index does not include standalone items such as
                // sticks and coal. Their actual plans come from live recipes.
                unresolved.add(id+" (item recipe not indexed)");add(inputs,id,count);return;
            }
            BlockKnowledge.Route recipe=known.routes().stream()
                .filter(r->r.method().startsWith("craft/") && !r.ingredients().isEmpty())
                .filter(r->r.ingredients().stream().noneMatch(x->x.equals("unknown_ingredient")||x.startsWith("#")))
                .min(Comparator.comparingInt((BlockKnowledge.Route r)->r.ingredients().size())
                     .thenComparing(r->r.method())).orElse(null);
            if(recipe==null) {add(inputs,id,count);return;}
            int batches=(int)Math.min(1_000_000_000L,((long)count+Math.max(1,recipe.yield())-1)/Math.max(1,recipe.yield()));
            Map<String,Integer> perBatch=new TreeMap<>();
            for(String ing:recipe.ingredients())perBatch.merge(ing,1,Integer::sum);
            for(var ing:perBatch.entrySet())
                expand(ing.getKey(),(int)Math.min(1_000_000_000L,(long)batches*ing.getValue()),
                       depth+1,path,inputs,unresolved);
        } finally {path.remove(id);}
    }
    private static void add(Map<String,Integer> inputs,String id,int n) {
        inputs.merge(id,n,(a,b)->(int)Math.min(1_000_000_000L,(long)a+b));
    }
    public static List<Map.Entry<String,Integer>> orderedEntries(Map<String,Integer> required,
            Map<String,Integer> depotStock, Map<String,Integer> inventory) {
        return plan(required,depotStock,inventory).order().stream()
            .map(n->Map.entry(n.item(),n.total())).toList();
    }
    public static boolean recipeBacked(String item) {
        BlockKnowledge.Entry e = BlockKnowledge.byItem(item);
        return e != null && e.routes().stream().anyMatch(r->r.method().startsWith("craft/"));
    }
    private static int checked(Integer n) {
        if(n==null||n<0||n>1000000) throw new IllegalArgumentException("Invalid material quantity: "+n);
        return n;
    }
}
