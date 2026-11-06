import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Narrow worker/command contracts. No setup, movement, recipe or task-scheduler method is replaced. */
public final class PatchFoodReliability implements Opcodes {
    static final String P="com/player2/playerengine/", T=P+"tasks/base/Task", C=P+"PlayerEngineController";
    static final String FOOD=P+"reliability/FoodAcquisition", FUEL=P+"reliability/FuelRecovery";
    static ClassNode read(ZipFile jar,Path out,String name)throws Exception {
        ClassNode type=new ClassNode();Path path=out.resolve(name+".class");
        new ClassReader(Files.exists(path)?Files.readAllBytes(path):jar.getInputStream(Objects.requireNonNull(jar.getEntry(name+".class"))).readAllBytes()).accept(type,0);
        return type;
    }
    static void save(ClassNode type,Path out)throws Exception {
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);
        Path path=out.resolve(type.name+".class");Files.createDirectories(path.getParent());Files.write(path,writer.toByteArray());
    }
    static MethodNode method(ClassNode type,String name) {
        return type.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();
    }
    static void reset(MethodNode method) {
        method.instructions.clear();method.tryCatchBlocks.clear();
        if(method.localVariables!=null)method.localVariables.clear();
        method.visibleLocalVariableAnnotations=null;method.invisibleLocalVariableAnnotations=null;
    }
    static void delegated(ClassNode type,String methodName,String helper,String descriptor,int opcode) {
        MethodNode method=type.methods.stream().filter(m->m.name.equals(methodName)).findFirst().orElse(null);
        if(method==null){method=new MethodNode(ACC_PUBLIC,methodName,descriptor.endsWith(")Z")?"()Z":"()Ljava/lang/String;",null,null);type.methods.add(method);}
        reset(method);method.instructions.add(new VarInsnNode(ALOAD,0));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC,FOOD,helper,descriptor,false));
        method.instructions.add(new InsnNode(opcode));
    }
    static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}

    public static void main(String[] args)throws Exception {
        try(ZipFile jar=new ZipFile(args[0])) {
            Path out=Path.of(args[1]);
            for(String name:List.of("CollectFoodTask","CollectMeatTask")) {
                ClassNode type=read(jar,out,P+"tasks/resources/"+name);
                type.fields.add(new FieldNode(ACC_PRIVATE,"ellieFoodProgress","Ljava/lang/Object;",null,null));
                type.interfaces.add(P+"tasks/base/TrackedTaskOutcomeProvider");
                delegated(type,"isFinished","terminal","(Ljava/lang/Object;)Z",IRETURN);
                delegated(type,"isTerminal","terminal","(Ljava/lang/Object;)Z",IRETURN);
                delegated(type,"isSuccessful","successful","(Ljava/lang/Object;)Z",IRETURN);
                delegated(type,"controlledReason","reason","(Ljava/lang/Object;)Ljava/lang/String;",ARETURN);
                MethodNode smelt=method(type,"getBestSmeltTarget");reset(smelt);
                smelt.instructions.add(new VarInsnNode(ALOAD,0));
                smelt.instructions.add(new MethodInsnNode(INVOKESTATIC,FOOD,"bestSmeltTarget","(Ljava/lang/Object;)Ljava/lang/Object;",false));
                smelt.instructions.add(new TypeInsnNode(CHECKCAST,P+"util/SmeltTarget"));smelt.instructions.add(new InsnNode(ARETURN));
                MethodNode tick=method(type,"onTick");int hunting=0,cooking=0;
                boolean huntingConstructor=false;
                for(AbstractInsnNode node:tick.instructions.toArray()) {
                    if(node instanceof TypeInsnNode create&&node.getOpcode()==NEW&&create.desc.equals(P+"tasks/resources/KillAndLootTask"))huntingConstructor=true;
                    if(node instanceof MethodInsnNode call&&huntingConstructor&&call.owner.equals(P+"util/ItemTarget")&&call.name.equals("<init>")) {
                        AbstractInsnNode count=node.getPrevious();require(count.getOpcode()==ICONST_1,"Expected absolute-one hunting target");
                        InsnList more=new InsnList();more.add(new InsnNode(DUP));more.add(new VarInsnNode(ALOAD,0));
                        more.add(new MethodInsnNode(INVOKESTATIC,FOOD,"nextRawCount","(Ljava/lang/Object;Ljava/lang/Object;)I",false));
                        tick.instructions.insertBefore(count,more);tick.instructions.remove(count);hunting++;
                    }
                    if(node instanceof MethodInsnNode call&&call.owner.equals(P+"tasks/resources/KillAndLootTask")&&call.name.equals("<init>"))huntingConstructor=false;
                    if(node instanceof TypeInsnNode create&&node.getOpcode()==NEW&&create.desc.equals(P+"tasks/container/SmeltInSmokerTask")) {
                        AbstractInsnNode dup=node.getNext();require(dup.getOpcode()==DUP,"Unexpected smoker constructor stack");
                        tick.instructions.remove(dup);tick.instructions.remove(node);
                    }
                    if(node instanceof MethodInsnNode call&&call.owner.equals(P+"tasks/container/SmeltInSmokerTask")&&call.name.equals("<init>")) {
                        tick.instructions.insertBefore(node,new VarInsnNode(ALOAD,0));call.setOpcode(INVOKESTATIC);call.owner=FOOD;call.name="cookingTask";
                        call.desc="(Ljava/lang/Object;Ljava/lang/Object;)L"+T+";";call.itf=false;cooking++;
                    }
                }
                require(hunting==1&&cooking==1,"Unreviewed food action construction: "+name);
                LabelNode normal=new LabelNode();InsnList gate=new InsnList();gate.add(new VarInsnNode(ALOAD,0));
                gate.add(new MethodInsnNode(INVOKESTATIC,FOOD,"before","(Ljava/lang/Object;)Z",false));gate.add(new JumpInsnNode(IFEQ,normal));
                gate.add(new InsnNode(ACONST_NULL));gate.add(new InsnNode(ARETURN));gate.add(normal);gate.add(new FrameNode(F_SAME,0,null,0,null));
                LabelNode noCooking=new LabelNode();gate.add(new VarInsnNode(ALOAD,0));gate.add(new VarInsnNode(ALOAD,0));
                gate.add(new FieldInsnNode(GETFIELD,T,"controller","L"+C+";"));
                gate.add(new MethodInsnNode(INVOKESTATIC,P+"reliability/ProductionHandoff","pending","(Ljava/lang/Object;Ljava/lang/Object;)L"+T+";",false));
                gate.add(new InsnNode(DUP));gate.add(new JumpInsnNode(IFNULL,noCooking));gate.add(new InsnNode(ARETURN));
                gate.add(noCooking);gate.add(new FrameNode(F_SAME1,0,null,1,new Object[]{T}));gate.add(new InsnNode(POP));tick.instructions.insert(gate);
                save(type,out);
            }

            for(String name:List.of("SmeltInSmokerTask","SmeltInFurnaceTask")) {
                ClassNode type=read(jar,out,P+"tasks/container/"+name);
                type.fields.add(new FieldNode(ACC_PRIVATE,"ellieFuelRecovery","Ljava/lang/Object;",null,null));
                type.fields.add(new FieldNode(ACC_PRIVATE,"ellieFoodOwner","Ljava/lang/Object;",null,null));
                MethodNode tick=method(type,"onResourceTick");int issued=0,batch=0;
                for(AbstractInsnNode node:tick.instructions.toArray()) {
                    if(node instanceof MethodInsnNode call&&call.owner.equals(P+"tasks/cooking/FuelPlanner")&&call.name.equals("gatherTaskFor")) {
                        tick.instructions.insertBefore(call,new VarInsnNode(ALOAD,0));call.owner=FUEL;call.name="issued";
                        call.desc="(Ljava/lang/Object;Ljava/lang/Object;)L"+T+";";issued++;
                    }
                    if(node instanceof MethodInsnNode call&&call.owner.equals(P+"util/ItemTarget")&&call.name.equals("getTargetCount")
                            &&node.getNext() instanceof VarInsnNode store&&store.getOpcode()==ISTORE) {
                        AbstractInsnNode item=node.getPrevious();require(item instanceof MethodInsnNode get&&get.name.equals("getItem"),"Unexpected batch target");
                        tick.instructions.remove(item);tick.instructions.insertBefore(call,new VarInsnNode(ALOAD,0));
                        call.setOpcode(INVOKESTATIC);call.owner=FOOD;call.name="remainingBatch";call.desc="(Ljava/lang/Object;Ljava/lang/Object;)I";call.itf=false;batch++;
                    }
                }
                require(issued==1&&batch==1,"Unreviewed cooking worker layout: "+name+" gather="+issued+" batch="+batch);
                LabelNode noChild=new LabelNode(),running=new LabelNode();InsnList gate=new InsnList();
                gate.add(new VarInsnNode(ALOAD,0));gate.add(new VarInsnNode(ALOAD,1));
                gate.add(new MethodInsnNode(INVOKESTATIC,FUEL,"before","(Ljava/lang/Object;Ljava/lang/Object;)L"+T+";",false));
                gate.add(new InsnNode(DUP));gate.add(new JumpInsnNode(IFNULL,noChild));gate.add(new InsnNode(ARETURN));
                gate.add(noChild);gate.add(new FrameNode(F_SAME1,0,null,1,new Object[]{T}));gate.add(new InsnNode(POP));
                gate.add(new VarInsnNode(ALOAD,0));gate.add(new MethodInsnNode(INVOKEVIRTUAL,T,"stopped","()Z",false));
                gate.add(new JumpInsnNode(IFEQ,running));gate.add(new InsnNode(ACONST_NULL));gate.add(new InsnNode(ARETURN));
                gate.add(running);gate.add(new FrameNode(F_SAME,0,null,0,null));tick.instructions.insert(gate);
                for(AbstractInsnNode node:tick.instructions.toArray())if(node.getOpcode()==ARETURN) {
                    if(node.getPrevious() instanceof MethodInsnNode call&&call.owner.equals(P+"reliability/ProductionHandoff")&&call.name.equals("invalidate"))continue;
                    InsnList fresh=new InsnList();fresh.add(new VarInsnNode(ALOAD,1));fresh.add(new MethodInsnNode(INVOKESTATIC,P+"reliability/ProductionHandoff","invalidate","(Ljava/lang/Object;)V",false));tick.instructions.insertBefore(node,fresh);
                }
                MethodNode stop=method(type,"onResourceStop");
                for(AbstractInsnNode node:stop.instructions.toArray())if(node.getOpcode()==RETURN) {
                    InsnList cleanup=new InsnList();cleanup.add(new VarInsnNode(ALOAD,0));cleanup.add(new MethodInsnNode(INVOKESTATIC,FUEL,"stopped","(Ljava/lang/Object;)V",false));stop.instructions.insertBefore(node,cleanup);
                }
                if(name.equals("SmeltInSmokerTask")) {
                    InsnList reset=new InsnList();
                    for(String field:List.of("fuelGatherAttempted","fuelGatherRan","isSmelting")){
                        reset.add(new VarInsnNode(ALOAD,0));reset.add(new InsnNode(ICONST_0));reset.add(new FieldInsnNode(PUTFIELD,type.name,field,"Z"));
                    }
                    method(type,"onResourceStart").instructions.insert(reset);
                }
                save(type,out);
            }

            ClassNode chain=read(jar,out,P+"chains/FoodChain");chain.fields.add(new FieldNode(ACC_PRIVATE,"ellieFoodRecovery","Ljava/lang/Object;",null,null));
            MethodNode priority=method(chain,"getPriority");
            for(AbstractInsnNode node:priority.instructions.toArray())if(node.getOpcode()==FRETURN) {
                InsnList adjust=new InsnList();adjust.add(new VarInsnNode(ALOAD,0));adjust.add(new InsnNode(SWAP));
                adjust.add(new MethodInsnNode(INVOKESTATIC,P+"reliability/FoodSurvival","priority","(Ljava/lang/Object;F)F",false));priority.instructions.insertBefore(node,adjust);
            }
            InsnList before=new InsnList();before.add(new VarInsnNode(ALOAD,0));before.add(new MethodInsnNode(INVOKESTATIC,P+"reliability/FoodSurvival","before","(Ljava/lang/Object;)V",false));priority.instructions.insert(before);save(chain,out);

            for(String name:List.of("FoodCommand","MeatCommand")) {
                ClassNode command=read(jar,out,P+"commands/"+name);int replaced=0,validated=0;
                for(AbstractInsnNode node:method(command,"call").instructions.toArray()) {
                    if(node instanceof MethodInsnNode call&&call.owner.equals("java/lang/Integer")&&call.name.equals("intValue")){
                        method(command,"call").instructions.insert(call,new MethodInsnNode(INVOKESTATIC,FOOD,"validateRequested","(I)I",false));validated++;
                    }
                    if(node instanceof MethodInsnNode call&&call.owner.equals(P+"util/helpers/StorageHelper")&&call.name.equals("calculateInventoryFoodScore")) {
                        method(command,"call").instructions.insertBefore(call,new InsnNode(name.equals("MeatCommand")?ICONST_1:ICONST_0));
                        call.owner=FOOD;call.name="startingScore";call.desc="(Ljava/lang/Object;Z)I";replaced++;
                    }
                }
                require(replaced==1&&validated==1,"Missing initial food score/count validation: "+name);
                int strings=0;
                for(AbstractInsnNode node:method(command,"<init>").instructions)if(node instanceof LdcInsnNode text&&text.cst instanceof String){
                    if(++strings==2){text.cst=name.equals("FoodCommand")
                            ? "Gather additional food measured in hunger points, cooking raw meat as needed. Count is NOT item count: one cooked chicken is 6 points. For an exact number of food items use get <food_item> <count>. Missing resources stop with a specific BLOCKED reason."
                            : "Gather additional cooked-meat hunger points. Count is NOT item count: cooked chicken is 6 points and cooked beef is 8. For exact item counts use get cooked_chicken <count> or get cooked_beef <count>. Failed hunting/cooking reports BLOCKED.";break;}
                }
                save(command,out);
            }

            ClassNode command=read(jar,out,P+"commands/base/Command");int invoke=0,gates=0;
            for(MethodNode method:command.methods) {
                if(method.name.equals("run"))for(AbstractInsnNode node:method.instructions.toArray()) {
                    if(node instanceof MethodInsnNode call&&call.owner.equals(command.name)&&call.name.equals("call")) {
                        call.setOpcode(INVOKESTATIC);call.owner=P+"supervisor/CommandCompletion";call.name="invoke";
                        call.desc="(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V";call.itf=false;invoke++;
                    }
                }
                if(method.name.equals("finish")||method.name.equals("finishWithNote")) {
                    LabelNode normal=new LabelNode();InsnList gate=new InsnList();gate.add(new VarInsnNode(ALOAD,0));
                    gate.add(method.name.equals("finishWithNote")?new VarInsnNode(ALOAD,1):new InsnNode(ACONST_NULL));
                    gate.add(new InsnNode(method.name.equals("finishWithNote")?ICONST_1:ICONST_0));
                    gate.add(new MethodInsnNode(INVOKESTATIC,P+"supervisor/CommandCompletion","beforeFinish","(Ljava/lang/Object;Ljava/lang/String;Z)Z",false));
                    gate.add(new JumpInsnNode(IFEQ,normal));gate.add(new InsnNode(RETURN));gate.add(normal);gate.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(gate);gates++;
                }
            }
            require(invoke==1&&gates==2,"Command lifecycle layout changed");save(command,out);

            ClassNode adapter=read(jar,out,P+"executor/TaskStepExecutorAdapter");
            MethodNode terminal=method(adapter,"mapTerminalOutcome");
            String result=P+"supervisor/CommandCompletion$TaskOutcome",decision=adapter.name+"$TerminalDecision";
            LabelNode legacy=new LabelNode();InsnList outcome=new InsnList();outcome.add(new VarInsnNode(ALOAD,0));
            outcome.add(new MethodInsnNode(INVOKESTATIC,P+"supervisor/CommandCompletion","taskOutcome","(L"+T+";)L"+result+";",false));
            outcome.add(new InsnNode(DUP));outcome.add(new JumpInsnNode(IFNULL,legacy));outcome.add(new VarInsnNode(ASTORE,1));
            outcome.add(new TypeInsnNode(NEW,decision));outcome.add(new InsnNode(DUP));outcome.add(new VarInsnNode(ALOAD,1));
            outcome.add(new MethodInsnNode(INVOKEVIRTUAL,result,"state","()L"+P+"executor/StepState;",false));
            outcome.add(new VarInsnNode(ALOAD,1));outcome.add(new MethodInsnNode(INVOKEVIRTUAL,result,"detail","()Ljava/lang/String;",false));
            outcome.add(new MethodInsnNode(INVOKESPECIAL,decision,"<init>","(L"+P+"executor/StepState;Ljava/lang/String;)V",false));outcome.add(new InsnNode(ARETURN));
            outcome.add(legacy);outcome.add(new FrameNode(F_SAME1,0,null,1,new Object[]{result}));outcome.add(new InsnNode(POP));terminal.instructions.insert(outcome);save(adapter,out);

            ClassNode sign=read(jar,out,P+"tasks/construction/PlaceSignTask");sign.fields.add(new FieldNode(ACC_PRIVATE,"ellieSignFailure","Ljava/lang/String;",null,null));
            InsnList signFailure=new InsnList();signFailure.add(new VarInsnNode(ALOAD,0));signFailure.add(new VarInsnNode(ALOAD,1));signFailure.add(new FieldInsnNode(PUTFIELD,sign.name,"ellieSignFailure","Ljava/lang/String;"));
            method(sign,"failNow").instructions.insert(signFailure);save(sign,out);

            ClassNode give=read(jar,out,P+"tasks/entity/GiveItemToPlayerTask");give.fields.add(new FieldNode(ACC_PRIVATE,"ellieGiveSucceeded","Z",null,null));
            int giveSuccess=0;
            for(AbstractInsnNode node:method(give,"onTick").instructions.toArray())if(node instanceof LdcInsnNode text&&"Finished giving items.".equals(text.cst)) {
                InsnList mark=new InsnList();mark.add(new VarInsnNode(ALOAD,0));mark.add(new InsnNode(ICONST_1));mark.add(new FieldInsnNode(PUTFIELD,give.name,"ellieGiveSucceeded","Z"));
                method(give,"onTick").instructions.insertBefore(node,mark);giveSuccess++;
            }
            require(giveSuccess==1,"Missing verified give terminal");
            MethodNode finished=new MethodNode(ACC_PUBLIC,"isFinished","()Z",null,null);finished.instructions.add(new VarInsnNode(ALOAD,0));
            finished.instructions.add(new FieldInsnNode(GETFIELD,give.name,"ellieGiveSucceeded","Z"));finished.instructions.add(new InsnNode(IRETURN));give.methods.add(finished);save(give,out);
            System.out.println("Food reliability: retained fuel children, inventory deltas, bounded FoodChain, bound command receipts, truthful give terminal");
        }
    }
}
