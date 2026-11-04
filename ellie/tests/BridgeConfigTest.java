import java.nio.file.*;
import com.player2.playerengine.interpreter.LocalChatGPTBridge;
public final class BridgeConfigTest {
 public static void main(String[] args)throws Exception {
  Path work=Files.createTempDirectory("ellie-jvm-bridge-");
  String original=System.getProperty("user.dir");
  try{
   System.setProperty("user.dir",work.toString());
   if(LocalChatGPTBridge.maybeComplete("{}","DECISION")!=null)throw new AssertionError("Bridge must be disabled by default");
   Path config=work.resolve("config");Files.createDirectories(config);
   Files.writeString(config.resolve("ellie-chatgpt.properties"),"provider=player2\nsecret="+"a".repeat(64)+"\nport=38457\n");
   if(LocalChatGPTBridge.maybeComplete("{}","DECISION")!=null)throw new AssertionError("Explicit Player2 must preserve existing service");
   Files.writeString(config.resolve("ellie-chatgpt.properties"),"provider=chatgpt\nsecret="+"a".repeat(64)+"\nport=38459\n");
   boolean unavailable=false;
   try{LocalChatGPTBridge.maybeComplete("{}","DECISION");}
   catch(java.io.IOException e){unavailable=true;}
   if(!unavailable)throw new AssertionError("Unavailable ChatGPT may NOT silently call Player2");
   System.out.println("BridgeConfigTest: default disabled, explicit fallback, unavailable fail-closed PASS");
  }finally{
   System.setProperty("user.dir",original);
   try(var s=Files.walk(work)){for(Path p:s.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
  }
 }
}
