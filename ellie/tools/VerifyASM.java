import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;
public final class VerifyASM {
 public static void main(String[] args)throws Exception {
   int count=0;
   for(String file:args){ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(file))).accept(c,0);
    for(MethodNode m:c.methods)if(m.instructions.size()>0){try{new Analyzer<>(new BasicVerifier()).analyze(c.name,m);count++;}catch(Exception e){throw new IllegalStateException(file+" #"+m.name+m.desc,e);}}
   }
   System.out.println("ASM verified "+count+" methods");
 }
}
