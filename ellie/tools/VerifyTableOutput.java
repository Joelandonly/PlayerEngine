import java.util.*;import java.util.zip.*;import java.lang.reflect.*;
import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
import net.minecraft.*;import com.player2.playerengine.PlayerEngineController;import com.player2.playerengine.tasks.crafting.*;import com.player2.playerengine.util.RecipeTarget;
/** Execute packaged macro decisions with native reach/count boundaries substituted; not a full recipe simulation. */
public final class VerifyTableOutput implements Opcodes {
 static final String P="com/player2/playerengine/",PROBE="TableContractProbe",ITEM="Lnet/minecraft/class_1792;";
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 public static final class Loader extends ClassLoader {Class<?> define(byte[] bytes){return defineClass(PROBE,bytes,0,bytes.length);}}
 public static void main(String[] args)throws Exception {
  ClassNode macro=new ClassNode();try(ZipFile z=new ZipFile(args[0])){new ClassReader(z.getInputStream(z.getEntry(P+"tasks/crafting/CraftMacroResourceTask.class")).readAllBytes()).accept(macro,0);}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(V21,ACC_PUBLIC,PROBE,null,"java/lang/Object",null);
  w.visitField(ACC_PUBLIC,"pinnedOutputItem",ITEM,null,null).visitEnd();w.visitField(ACC_PUBLIC,"reachable","Z",null,null).visitEnd();w.visitField(ACC_PUBLIC|ACC_STATIC,"held","I",null,null).visitEnd();w.visitField(ACC_PUBLIC|ACC_STATIC,"collapsed","Z",null,null).visitEnd();
  MethodVisitor init=w.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();init.visitVarInsn(ALOAD,0);init.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitInsn(RETURN);init.visitMaxs(0,0);init.visitEnd();
  MethodVisitor reach=w.visitMethod(ACC_PUBLIC,"craftMacroTableReachable","(L"+P+"PlayerEngineController;)Z",null,null);reach.visitCode();reach.visitVarInsn(ALOAD,0);reach.visitFieldInsn(GETFIELD,PROBE,"reachable","Z");reach.visitInsn(IRETURN);reach.visitMaxs(0,0);reach.visitEnd();
  int decisions=0;
  for(MethodNode method:macro.methods)if(Set.of("shouldSkipInventoryStep","inventoryStepSatisfied","collapseTableRequirement").contains(method.name)){
   if(method.name.equals("collapseTableRequirement")){
    // Retain the exact packaged output guard; substitute the original requirements-list body.
    AbstractInsnNode normal=null;for(AbstractInsnNode n:method.instructions)if(n instanceof JumpInsnNode j&&j.getOpcode()==IF_ACMPNE){normal=j.label;break;}
    check(normal!=null,"missing requested-table requirement guard");AbstractInsnNode n=normal.getNext();while(n!=null){AbstractInsnNode next=n.getNext();method.instructions.remove(n);n=next;}
    method.tryCatchBlocks.clear();method.localVariables=null;
    method.instructions.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.add(new InsnNode(ICONST_1));method.instructions.add(new FieldInsnNode(PUTSTATIC,PROBE,"collapsed","Z"));method.instructions.add(new InsnNode(RETURN));
   }
   MethodVisitor mv=w.visitMethod(ACC_PUBLIC,method.name,method.desc,null,null);
   method.accept(new MethodVisitor(ASM8,mv){
    public void visitFieldInsn(int op,String owner,String name,String desc){super.visitFieldInsn(op,owner.equals(macro.name)?PROBE:owner,name,desc);}
    public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
     if(owner.equals(P+"PlayerEngineController")&&name.equals("getItemStorage")){super.visitInsn(POP);super.visitInsn(ACONST_NULL);return;}
     if(owner.equals(P+"trackers/storage/ItemStorageTracker")&&name.equals("getItemCount")){super.visitInsn(POP2);super.visitFieldInsn(GETSTATIC,PROBE,"held","I");return;}
     super.visitMethodInsn(op,owner.equals(macro.name)?PROBE:owner,name,desc,itf);
    }
   });decisions++;
  }
  check(decisions==3,"expected three packaged table-output decisions");w.visitEnd();Class<?> probe=new Loader().define(w.toByteArray());Object task=probe.getConstructor().newInstance();
  var output=probe.getField("pinnedOutputItem");var reachable=probe.getField("reachable");var held=probe.getField("held");var collapsed=probe.getField("collapsed");
  var skip=probe.getMethod("shouldSkipInventoryStep",PlayerEngineController.class,CraftMacroStep.class);var satisfied=probe.getMethod("inventoryStepSatisfied",PlayerEngineController.class,CraftMacroStep.class);var collapse=probe.getMethod("collapseTableRequirement",String.class);
  var step=new CraftMacroStep(CraftMacroStepKind.CRAFT_CRAFTING_TABLE_IN_INVENTORY,new RecipeTarget(class_1802.field_8465,1));reachable.setBoolean(task,true);
  output.set(task,class_1802.field_8465);held.setInt(null,0);
  check(!(boolean)skip.invoke(task,null,step),"requested table item cannot skip crafting because a table is reachable");
  check(!(boolean)satisfied.invoke(task,null,step),"world bench cannot satisfy requested inventory output");
  held.setInt(null,1);check((boolean)satisfied.invoke(task,null,step),"actual table item satisfies one-table request");
  var two=new CraftMacroStep(CraftMacroStepKind.CRAFT_CRAFTING_TABLE_IN_INVENTORY,new RecipeTarget(class_1802.field_8465,2));check(!(boolean)satisfied.invoke(task,null,two),"one table item cannot satisfy two requested outputs");
  collapse.invoke(task,"reachable table");check(!collapsed.getBoolean(null),"requested output requirement must remain in resolver budget");
  output.set(task,new class_1792("minecraft:stone_pickaxe"));held.setInt(null,0);
  check((boolean)skip.invoke(task,null,step)&&(boolean)satisfied.invoke(task,null,step),"reachable bench must still satisfy tool-only table prerequisite");
  collapse.invoke(task,"reachable table");check(collapsed.getBoolean(null),"tool-only table prerequisite may be collapsed");
  reachable.setBoolean(task,false);check(!(boolean)skip.invoke(task,null,step)&&!(boolean)satisfied.invoke(task,null,step),"unreachable bench is not a usable tool");
  System.out.println("VerifyTableOutput: 8 packaged decision checks passed; native reach, inventory and recipe descriptors substituted");
 }
}
