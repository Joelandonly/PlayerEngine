import com.player2.playerengine.nativecommands.NativeCommandText;
import com.player2.playerengine.commands.ExcavateAreaCommand;
import com.player2.playerengine.tasks.construction.ExcavateAreaTask;
import java.lang.reflect.Method;

/** Uses actual Minecraft 1.21.1 intermediaries and packaged Ellie classes. */
public final class NativeExcavateBindingsTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[]args)throws Exception {
        Class.forName("net.minecraft.class_155").getMethod("method_36208").invoke(null);
        Class.forName("net.minecraft.class_2966").getMethod("method_12851").invoke(null);

        Class<?> pos=Class.forName("net.minecraft.class_2338");
        Class<?> world=Class.forName("net.minecraft.class_1937");
        Class<?> state=Class.forName("net.minecraft.class_2680");
        Class<?> builder=Class.forName("com.player2.playerengine.automaton.process.BuilderProcess");
        check(builder.getMethod("clearArea",pos,pos).getReturnType()==void.class,
            "native 1.21.1 Baritone builder must own clearArea for two BlockPos corners");
        check(builder.getMethod("isActive").getReturnType()==boolean.class,
            "native builder activity must be observed");
        check(builder.getMethod("onLostControl").getReturnType()==void.class,
            "native builder must support owner-safe cancellation");
        check(world.getMethod("method_22340",pos).getReturnType()==boolean.class,
            "chunk loaded test is required before any world scan");
        check(world.getMethod("method_8321",pos)!=null,
            "must inspect block entities and prevent storage destruction");
        check(world.getMethod("method_8320",pos).getReturnType()==state,
            "must inspect real Minecraft block states");
        check(state.getMethod("method_26215").getReturnType()==boolean.class,
            "block-state isAir check must be exact");
        check(new ExcavateAreaTask(100,64,100,120,74,120) instanceof ExcavateAreaTask,
            "native construction task instantiates with six coordinates");
        check(new ExcavateAreaCommand().getName().equals("excavate"),
            "exact NPC command id is available");
        check(NativeCommandText.resolve("Ellie, excavate 100 64 100 120 74 120")
                .equals("excavate 100 64 100 120 74 120"),
            "actual packaged local owner route recognizes explicit excavation");
        check(NativeCommandText.resolve("Ellie, get string 64")
                .equals("get string 64"),"existing resource route remains intact");
        System.out.println("NativeExcavateBindingsTest "+checks+
            " real Minecraft 1.21.1 signatures and package binding checks; no world execution");
    }
}
