import com.player2.playerengine.reliability.FoodAcquisition;
import java.util.*;

/** Real shared constructor resolver. These are signature/value probes, not Minecraft simulation. */
public final class NativeConstructorTest {
 static int checks;static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 public record Flags(boolean value,boolean second){}
 public record Primitives(boolean bool,int integer,long lng,float flt,double dbl,byte byt,short shrt,char chr){}
 public record References(CharSequence text,List<String> entries,String[] array){}
 public record NullReference(Object value){}
 public static class NullOverload {public final String chosen;public NullOverload(boolean ignored){chosen="primitive";}public NullOverload(Object ignored){chosen="reference";}}
 public static class Throwing {public Throwing(boolean value){throw new IllegalStateException("native constructor failure");}}
 public static void main(String[] args)throws Exception {
  Flags f=(Flags)FoodAcquisition.construct(Flags.class.getName(),false,true);check(!f.value()&&f.second(),"both Boolean arguments bind primitive flags without changing their values");
  Primitives p=(Primitives)FoodAcquisition.construct(Primitives.class.getName(),true,3,4L,5f,6d,(byte)7,(short)8,'z');check(p.equals(new Primitives(true,3,4L,5f,6d,(byte)7,(short)8,'z')),"all eight boxed primitive types bind");
  References r=(References)FoodAcquisition.construct(References.class.getName(),new StringBuilder("hello"),new ArrayList<>(List.of("item")),new String[]{"one","two"});check(r.text().toString().equals("hello")&&r.entries().equals(List.of("item"))&&r.array().length==2,"assignable interfaces and typed arrays retain values");
  check(((NullReference)FoodAcquisition.construct(NullReference.class.getName(),(Object)null)).value()==null,"null reference remains valid");
  check(((NullOverload)FoodAcquisition.construct(NullOverload.class.getName(),(Object)null)).chosen.equals("reference"),"null cannot select a primitive overload");
  for(Object[] invalid:new Object[][]{{null,true},{"false",true},{1,true},{false},{false,true,false}}){
   try{FoodAcquisition.construct(Flags.class.getName(),invalid);throw new AssertionError("Invalid flags accepted");}catch(NoSuchMethodException expected){check(expected.getMessage().contains(Flags.class.getName())&&expected.getMessage().contains("constructor for"),"missing constructor reports argument types");}
  }
  try{FoodAcquisition.construct(Throwing.class.getName(),false);throw new AssertionError("native exception swallowed");}catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException&&expected.getCause().getMessage().equals("native constructor failure"),"constructor failures propagate without trying another action");}
  System.out.println("NativeConstructorTest: "+checks+" shared resolver type/value/failure checks passed");
 }
}
