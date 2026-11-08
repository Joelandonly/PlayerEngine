import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.crafting.CraftMacroPhase;
import com.player2.playerengine.tasks.crafting.resolver.ResolverResult;
import java.util.*;
import java.util.zip.ZipFile;
import java.lang.reflect.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.commons.*;

/** Executes the packaged convergence guard; resolver/world/action signals are explicit substitutes. */
public final class MacroConvergenceTest implements Opcodes {
 static final String MACRO="com/player2/playerengine/tasks/crafting/CraftMacroResourceTask";
 static final String PROBE="MacroConvergenceTest$Probe";
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 public static class Probe {
  public Object resolver;
  public List<?> requirements=List.of(),externals=List.of(new Object());
  public ResolverResult lastResult;
  public CraftMacroPhase phase=CraftMacroPhase.COLLECT_MISSING_MATERIALS;
  public int resolveAttempts=63,lastDeficit=34,noProgressCount,ellieCollectHandoffCount;
  public boolean delayActive,inventoryPending,inventoryStuck,sourceReachable,dropInFlight,tablePending,failed,ellieCollectHandoffGranted;
  public void collapseSatisfiedRequirements(PlayerEngineController c){}
  public List<?> resolvedExternals(){return externals;}
  public boolean reachableSourceForAnyExternal(PlayerEngineController c,List<?> e){return sourceReachable;}
  public boolean matchingDropsInFlight(PlayerEngineController c){return dropInFlight;}
  public boolean inventoryStepStuck(PlayerEngineController c,boolean gathering){return inventoryStuck;}
  public boolean tableCraftInProgress(){return tablePending;}
  public boolean needsMoreInventorySteps(PlayerEngineController c){return inventoryPending;}
  public String meaningfulReason(ResolverResult r){return "bounded native craft failure";}
  public void terminateMacro(String reason){failed=true;}
  public boolean resolveNow(PlayerEngineController c){throw new AssertionError("packaged method must replace template");}
 }
 public static ResolverResult next;
 public static ResolverResult resolve(Object ignored,PlayerEngineController c,List<?> requirements){return next;}
 static class Loader extends ClassLoader {Loader(){super(MacroConvergenceTest.class.getClassLoader());}Class<?> define(byte[] b){return defineClass(PROBE.replace('/','.'),b,0,b.length);}}
 static Class<?> probe(String jar)throws Exception {
  ClassNode nativeType=new ClassNode();try(ZipFile z=new ZipFile(jar)){new ClassReader(z.getInputStream(z.getEntry(MACRO+".class")).readAllBytes()).accept(nativeType,0);}
  ClassNode template=new ClassNode();try(var in=MacroConvergenceTest.class.getResourceAsStream("/"+PROBE+".class")){new ClassReader(in.readAllBytes()).accept(template,0);}
  template.nestHostClass=null;template.innerClasses.clear();template.methods.removeIf(m->m.name.equals("resolveNow"));
  for(MethodNode method:nativeType.methods)if(method.name.equals("resolveNow")||method.name.equals("ellieCollectionHandoff")){
   MethodNode copy=new MethodNode(ASM8,method.name.equals("resolveNow")?ACC_PUBLIC:method.access,method.name,method.desc,method.signature,method.exceptions.toArray(String[]::new));
   method.accept(new MethodRemapper(copy,new Remapper(){public String map(String name){return name.equals(MACRO)?PROBE:name;}}));
   for(AbstractInsnNode insn:copy.instructions.toArray()){
    if(insn instanceof FieldInsnNode f&&f.owner.equals(PROBE)&&f.name.equals("resolver"))f.desc="Ljava/lang/Object;";
    if(insn instanceof MethodInsnNode call&&call.owner.endsWith("/MaterialResolver")&&call.name.equals("resolveBudget")){
     call.setOpcode(INVOKESTATIC);call.owner="MacroConvergenceTest";call.name="resolve";call.desc="(Ljava/lang/Object;Lcom/player2/playerengine/PlayerEngineController;Ljava/util/List;)Lcom/player2/playerengine/tasks/crafting/resolver/ResolverResult;";
    }
   }
   template.methods.add(copy);
  }
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);template.accept(writer);return new Loader().define(writer.toByteArray());
 }
 static void field(Object p,String name,Object value)throws Exception{p.getClass().getField(name).set(p,value);}
 static Object value(Object p,String name)throws Exception{return p.getClass().getField(name).get(p);}
 static boolean tick(Object p,int deficit,boolean owed,ResolverResult.Status status)throws Exception {
  field(p,"externals",owed?List.of(new Object()):List.of());next=new ResolverResult(status,List.of(),List.of(),deficit,null);
  return (boolean)p.getClass().getMethod("resolveNow",PlayerEngineController.class).invoke(p,new Object[]{null});
 }
 static boolean tick(Object p,int deficit,boolean owed)throws Exception{return tick(p,deficit,owed,ResolverResult.Status.NEEDS_WORK);}
 public static void main(String[] args)throws Exception{
  Class<?> type=probe(args[0]);Object p=type.getConstructor().newInstance();
  // More than 64 resolves while actually collecting, then a pickup has already
  // reduced the deficit before the macro leaves COLLECT: the next flat resolve
  // is a phase handoff, not a failed action (the two logged slab runs).
  for(int deficit=34;deficit>1;deficit--)check(!tick(p,deficit,true),"productive collection may run past 64 resolves");
  field(p,"lastDeficit",1);check(!tick(p,1,false),"completed ingredients must reach the next crafting phase after a long gather");
  check(!(boolean)value(p,"failed"),"handoff must not terminate the native macro");
  field(p,"phase",CraftMacroPhase.CRAFT_2X2_INTERMEDIATES);check(!tick(p,1,false),"inventory-to-table transition is allowed");
  field(p,"phase",CraftMacroPhase.FIND_OR_PLACE_TABLE);field(p,"tablePending",true);
  for(int i=0;i<80;i++)check(!tick(p,1,false),"real table action retains its existing pending guard");
  field(p,"tablePending",false);boolean ended=false;for(int i=0;i<4;i++)ended|=tick(p,1,false);
  check(ended&&(boolean)value(p,"failed"),"stalled table action remains bounded");
  p=type.getConstructor().newInstance();field(p,"lastDeficit",1);field(p,"resolveAttempts",10000);
  check(!tick(p,1,false),"one handoff is allowed after an arbitrarily long successful gather");
  ended=false;for(int i=0;i<4;i++)ended|=tick(p,1,false);
  check(ended,"repeating the same empty collection phase cannot receive endless handoff grace");
  p=type.getConstructor().newInstance();
  for(int episode=0;episode<8;episode++){
   field(p,"phase",CraftMacroPhase.PLAN);field(p,"tablePending",true);check(!tick(p,1,false),"intermediate native work can enter another collection episode");
   field(p,"phase",CraftMacroPhase.COLLECT_MISSING_MATERIALS);field(p,"tablePending",false);field(p,"resolveAttempts",10000);field(p,"lastDeficit",1);
   check(!tick(p,1,false),"bounded additional collection episode may hand off");
  }
  field(p,"phase",CraftMacroPhase.PLAN);field(p,"tablePending",true);tick(p,1,false);
  field(p,"phase",CraftMacroPhase.COLLECT_MISSING_MATERIALS);field(p,"tablePending",false);field(p,"resolveAttempts",10000);
  check(tick(p,1,false),"cycling between phases cannot rearm handoff grace indefinitely");
  p=type.getConstructor().newInstance();check(tick(p,1,false,ResolverResult.Status.UNOBTAINABLE)&&(boolean)value(p,"failed"),"unobtainable ingredients fail immediately before handoff grace");
  p=type.getConstructor().newInstance();field(p,"phase",CraftMacroPhase.CRAFT_2X2_INTERMEDIATES);field(p,"lastDeficit",1);field(p,"inventoryStuck",true);field(p,"inventoryPending",true);
  check(tick(p,1,false),"unsatisfiable inventory recipe retains its existing bounded failure");
  System.out.println("MacroConvergenceTest: "+checks+" packaged convergence checks; resolver/world/action boundaries substituted, no live gameplay claim");
 }
}
