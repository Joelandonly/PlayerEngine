import java.nio.file.*;import java.util.*;import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
/** Exact native interaction and production handoff hooks; preserve previous overlay edits. */
public final class PatchReliability implements Opcodes {
 static final String P="com/player2/playerengine/";
 static ClassNode read(ZipFile z,Path out,String name)throws Exception{ClassNode c=new ClassNode();Path f=out.resolve(name+".class");new ClassReader(Files.isRegularFile(f)?Files.readAllBytes(f):z.getInputStream(Objects.requireNonNull(z.getEntry(name+".class"))).readAllBytes()).accept(c,0);return c;}
 static void save(ClassNode c,Path out)throws Exception{ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Path p=out.resolve(c.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());}
 public static void main(String[] a)throws Exception{try(ZipFile z=new ZipFile(a[0])){Path out=Path.of(a[1]);
  ClassNode interaction=read(z,out,P+"automaton/api/entity/LivingEntityInteractionManager");int uses=0;
  for(MethodNode m:interaction.methods)if(m.name.equals("interactBlock"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/class_1799")&&call.name.equals("method_7981")&&call.desc.equals("(Lnet/minecraft/class_1838;)Lnet/minecraft/class_1269;")){
   call.setOpcode(INVOKESTATIC);call.owner=P+"reliability/NpcItemUse";call.name="use";call.desc="(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
   m.instructions.insert(call,new TypeInsnNode(CHECKCAST,"net/minecraft/class_1269"));uses++;
  }
  if(uses!=2)throw new IllegalStateException("Expected two NPC item-use boundaries, got "+uses);save(interaction,out);
  ClassNode macro=read(z,out,P+"tasks/crafting/CraftMacroResourceTask");int hooks=0;
  for(MethodNode m:macro.methods)if(m.name.equals("onResourceTick")&&m.desc.equals("(L"+P+"PlayerEngineController;)L"+P+"tasks/base/Task;")){
   LabelNode normal=new LabelNode();InsnList i=new InsnList();i.add(new VarInsnNode(ALOAD,0));i.add(new VarInsnNode(ALOAD,1));i.add(new MethodInsnNode(INVOKESTATIC,P+"reliability/ProductionHandoff","pending","(Ljava/lang/Object;Ljava/lang/Object;)L"+P+"tasks/base/Task;",false));i.add(new InsnNode(DUP));i.add(new JumpInsnNode(IFNULL,normal));i.add(new InsnNode(ARETURN));i.add(normal);i.add(new FrameNode(F_SAME1,0,null,1,new Object[]{P+"tasks/base/Task"}));i.add(new InsnNode(POP));m.instructions.insert(i);hooks++;
  }
  if(hooks!=1)throw new IllegalStateException("Expected one production handoff");
  // A placed table is a reusable tool, never an acquired table item requested as output.
  int tableContracts=0;
  for(MethodNode m:macro.methods){
   if(m.name.equals("shouldSkipInventoryStep")||m.name.equals("collapseTableRequirement")){
    LabelNode normal=new LabelNode();InsnList guard=new InsnList();guard.add(new VarInsnNode(ALOAD,0));guard.add(new FieldInsnNode(GETFIELD,macro.name,"pinnedOutputItem","Lnet/minecraft/class_1792;"));guard.add(new FieldInsnNode(GETSTATIC,"net/minecraft/class_1802","field_8465","Lnet/minecraft/class_1792;"));guard.add(new JumpInsnNode(IF_ACMPNE,normal));if(m.name.equals("shouldSkipInventoryStep"))guard.add(new InsnNode(ICONST_0));guard.add(new InsnNode(m.name.equals("shouldSkipInventoryStep")?IRETURN:RETURN));guard.add(normal);guard.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(guard);tableContracts++;
   }
   if(m.name.equals("inventoryStepSatisfied")){
    int matches=0;
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof JumpInsnNode branch&&branch.getOpcode()==IF_ACMPNE){
     AbstractInsnNode prev=branch.getPrevious();if(prev instanceof FieldInsnNode field&&field.name.equals("CRAFT_CRAFTING_TABLE_IN_INVENTORY")){
      InsnList guard=new InsnList();guard.add(new VarInsnNode(ALOAD,0));guard.add(new FieldInsnNode(GETFIELD,macro.name,"pinnedOutputItem","Lnet/minecraft/class_1792;"));guard.add(new FieldInsnNode(GETSTATIC,"net/minecraft/class_1802","field_8465","Lnet/minecraft/class_1792;"));guard.add(new JumpInsnNode(IF_ACMPEQ,branch.label));m.instructions.insert(branch,guard);matches++;
     }
    }
    if(matches!=1)throw new IllegalStateException("Unreviewed inventory table-output decision");tableContracts++;
   }
  }
  if(tableContracts!=3)throw new IllegalStateException("Expected three table item/tool contracts");save(macro,out);

  ClassNode smelt=read(z,out,P+"tasks/container/SmeltInFurnaceTask");int smeltHooks=0;
  for(MethodNode m:smelt.methods)if(m.name.equals("onResourceTick")){
   InsnList before=new InsnList();before.add(new VarInsnNode(ALOAD,0));before.add(new VarInsnNode(ALOAD,1));before.add(new MethodInsnNode(INVOKESTATIC,P+"reliability/ProductionHandoff","beforeSmeltTick","(Ljava/lang/Object;Ljava/lang/Object;)V",false));m.instructions.insert(before);
   for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==ARETURN){InsnList fresh=new InsnList();fresh.add(new VarInsnNode(ALOAD,1));fresh.add(new MethodInsnNode(INVOKESTATIC,P+"reliability/ProductionHandoff","invalidate","(Ljava/lang/Object;)V",false));m.instructions.insertBefore(n,fresh);}
   smeltHooks++;
  }
  if(smeltHooks!=1)throw new IllegalStateException("Expected native furnace handoff boundary");save(smelt,out);
  String hook=P+"reliability/SharedTargetRecovery";
  ClassNode task=read(z,out,P+"tasks/base/Task");task.fields.add(new FieldNode(ACC_PRIVATE,"sharedRecoveryAction","L"+P+"tasks/base/Task;",null,null));int gates=0,ticks=0;
  for(MethodNode m:task.methods){
   if(m.name.equals("tick")&&m.desc.equals("(L"+P+"tasks/base/TaskChain;)V")){
    for(AbstractInsnNode n:m.instructions.toArray()){
     if(n instanceof MethodInsnNode call&&call.name.equals("addTaskToChain")){
      LabelNode normal=new LabelNode();InsnList gate=new InsnList();gate.add(new VarInsnNode(ALOAD,0));gate.add(new VarInsnNode(ALOAD,1));gate.add(new MethodInsnNode(INVOKESTATIC,hook,"beforeTask","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));gate.add(new JumpInsnNode(IFEQ,normal));gate.add(new InsnNode(RETURN));gate.add(normal);gate.add(new FrameNode(F_SAME,0,null,0,null));gate.add(new VarInsnNode(ALOAD,0));gate.add(new MethodInsnNode(INVOKESTATIC,hook,"enter","(Ljava/lang/Object;)V",false));m.instructions.insert(n,gate);gates++;
     }
     if(n instanceof MethodInsnNode call&&call.owner.equals(P+"tasks/base/Task")&&call.name.equals("onTick")){
      InsnList enter=new InsnList();enter.add(new InsnNode(DUP));enter.add(new MethodInsnNode(INVOKESTATIC,hook,"enter","(Ljava/lang/Object;)V",false));m.instructions.insertBefore(n,enter);
      InsnList after=new InsnList();after.add(new VarInsnNode(ALOAD,0));after.add(new InsnNode(SWAP));after.add(new MethodInsnNode(INVOKESTATIC,hook,"after","(Ljava/lang/Object;L"+P+"tasks/base/Task;)L"+P+"tasks/base/Task;",false));m.instructions.insert(n,after);ticks++;
     }
    }
   }
   if(m.name.equals("stop")&&m.desc.equals("(L"+P+"tasks/base/Task;)V")){
    InsnList stop=new InsnList();stop.add(new VarInsnNode(ALOAD,0));stop.add(new MethodInsnNode(INVOKESTATIC,hook,"stopped","(Ljava/lang/Object;)V",false));m.instructions.insert(stop);
   }
  }
  if(gates!=1||ticks!=1)throw new IllegalStateException("Unreviewed Task.tick layout");save(task,out);
  ClassNode look=read(z,out,P+"util/helpers/LookHelper");int lookHooks=0;
  for(MethodNode m:look.methods)if(m.name.equals("lookAt")&&(m.desc.equals("(L"+P+"PlayerEngineController;L"+P+"automaton/api/utils/Rotation;)V")||m.desc.equals("(L"+P+"PlayerEngineController;L"+P+"automaton/api/utils/Rotation;Z)V"))){
   LabelNode normal=new LabelNode();InsnList gate=new InsnList();gate.add(new VarInsnNode(ALOAD,0));gate.add(new MethodInsnNode(INVOKESTATIC,hook,"allowTaskLook","(Ljava/lang/Object;)Z",false));gate.add(new JumpInsnNode(IFNE,normal));gate.add(new InsnNode(RETURN));gate.add(normal);gate.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(gate);lookHooks++;
  }
  if(lookHooks!=2)throw new IllegalStateException("Expected both task-look boundaries");save(look,out);
  ClassNode behavior=read(z,out,P+"automaton/behavior/LookBehavior");
  for(MethodNode m:behavior.methods)if(m.name.equals("updateTarget")){InsnList record=new InsnList();record.add(new VarInsnNode(ALOAD,0));record.add(new MethodInsnNode(INVOKESTATIC,hook,"baritoneLook","(Ljava/lang/Object;)V",false));m.instructions.insert(record);}save(behavior,out);
  ClassNode input=read(z,out,P+"automaton/utils/InputOverrideHandler");int inputHooks=0;
  for(MethodNode m:input.methods)if(m.name.equals("setInputForceState")&&m.desc.equals("(L"+P+"automaton/api/utils/input/Input;Z)V")){InsnList record=new InsnList();record.add(new VarInsnNode(ALOAD,0));record.add(new VarInsnNode(ALOAD,1));record.add(new VarInsnNode(ILOAD,2));record.add(new MethodInsnNode(INVOKESTATIC,hook,"inputWrite","(Ljava/lang/Object;Ljava/lang/Object;Z)V",false));m.instructions.insert(record);inputHooks++;}
  if(inputHooks!=1)throw new IllegalStateException("Expected native input-writer boundary");save(input,out);
  ClassNode scanner=read(z,out,P+"commands/BlockScanner");int rejectHooks=0;
  for(MethodNode m:scanner.methods)if(m.name.equals("isUnreachable")&&m.desc.equals("(Lnet/minecraft/class_2338;)Z")){
   LabelNode normal=new LabelNode();InsnList gate=new InsnList();gate.add(new VarInsnNode(ALOAD,0));gate.add(new FieldInsnNode(GETFIELD,scanner.name,"mod","L"+P+"PlayerEngineController;"));gate.add(new VarInsnNode(ALOAD,1));gate.add(new MethodInsnNode(INVOKESTATIC,hook,"rejected","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));gate.add(new JumpInsnNode(IFEQ,normal));gate.add(new InsnNode(ICONST_1));gate.add(new InsnNode(IRETURN));gate.add(normal);gate.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(gate);rejectHooks++;
  }
  if(rejectHooks!=1)throw new IllegalStateException("Expected scanner rejection boundary");save(scanner,out);
  System.out.println("Reliability: NPC axe-use guard and in-flight native production handoff installed");
 }}
}
