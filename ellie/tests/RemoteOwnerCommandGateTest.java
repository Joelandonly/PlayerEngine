import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.*;
import java.util.UUID;

/** Security and range-policy test. The actual packaged bytecode hook has a separate audit. */
public final class RemoteOwnerCommandGateTest {
 static void yes(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] args) {
  UUID owner=UUID.randomUUID(),intruder=UUID.randomUUID();
  AgentConversationData data=new AgentConversationData(new PlayerEngineController(null),owner);
  yes(RemoteOwnerCommandGate.include(false,new Event.UserMessage("Ellie, setup",owner),data),"distant verified owner setup");
  yes(RemoteOwnerCommandGate.include(false,new Event.UserMessage("Ellie, agentic get 300 cobblestone",owner),data),"distant owner resource");
  yes(RemoteOwnerCommandGate.include(false,new Event.UserMessage("Ellie, please gather 300 cobblestone",owner),data),"distant owner friendly syntax");
  yes(!RemoteOwnerCommandGate.include(false,new Event.UserMessage("Ellie, setup",intruder),data),"non-owner rejected");
  yes(!RemoteOwnerCommandGate.include(false,new Event.UserMessage("Ellie, setup",null),data),"untrusted sender rejected");
  yes(!RemoteOwnerCommandGate.include(false,new Event.UserMessage("setup",owner),data),"unaddressed commands stay local");
  yes(!RemoteOwnerCommandGate.include(false,new Event.UserMessage("Ellie, how are you?",owner),data),"conversation stays in radius");
  yes(!RemoteOwnerCommandGate.include(false,new Event.UserMessage("Tell Ellie, setup",owner),data),"indirect mention not elevated");
  yes(!RemoteOwnerCommandGate.include(false,new Event.UserMessage("EllieOops, setup",owner),data),"name boundary");
  yes(RemoteOwnerCommandGate.include(true,new Event.UserMessage("Ellie, how are you?",intruder),data),"nearby conversation unchanged");
  yes(RemoteOwnerCommandGate.include(false,new Event.UserMessage("Ellie, halt",owner),data),"remote aliases");
  System.out.println("RemoteOwnerCommandGateTest: 11 authenticated route/range checks passed");
 }
}
