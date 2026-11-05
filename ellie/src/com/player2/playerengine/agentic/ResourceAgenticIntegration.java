package com.player2.playerengine.agentic;

import com.player2.playerengine.PlayerEngineSettings;
import com.player2.playerengine.tasks.base.Task;
import java.util.*;
import java.util.regex.*;

/** Adds one bounded resource objective to existing validated Agentic plans.
 * The native TaskCatalogue and current supervisor still own execution and recovery.
 */
public final class ResourceAgenticIntegration {
 private ResourceAgenticIntegration() {}
 private static final String KIND="get_resource";
 private static final Pattern GET=Pattern.compile("(?i)^\\s*(?:get|obtain|acquire|collect|craft)\\s+((?:minecraft:)?[a-z][a-z0-9_]{1,55})(?:\\s+(?:x)?([0-9]{1,4}))?\\s*$");
 public record Parsed(String item,int count) {}
 public static Parsed parse(String text){
  if(text==null||text.length()>110)return null;
  text=text.trim();
  // The command parser can preserve outer quotes for RestOfLineArg; accept either form.
  if(text.length()>=2 && ((text.charAt(0)=='"' && text.charAt(text.length()-1)=='"') || (text.charAt(0)=='\'' && text.charAt(text.length()-1)=='\'')))text=text.substring(1,text.length()-1).trim();
  Matcher m=GET.matcher(text);if(!m.matches())return null;
  int n;try{n=m.group(2)==null?1:Integer.parseInt(m.group(2));}catch(NumberFormatException bad){return null;}
  if(n<1||n>256)return null;
  String item=m.group(1).toLowerCase(Locale.ROOT);if(item.startsWith("minecraft:"))item=item.substring(10);
  if(item.equals("air")||item.equals("bedrock")||item.equals("command_block"))return null;
  return new Parsed(item,n);
 }
 public static AgenticPlannerOutcome plan(PlayerEngineSettings settings,String text){
  Parsed parsed=parse(text);if(parsed==null)return null;
  AgenticPlan p=new AgenticPlan(1,"Acquire "+parsed.count+" "+parsed.item,
      List.of(new AgenticStepSpec("collect_resource","get_resource",Map.of("item",parsed.item,"count",Integer.toString(parsed.count)),"Use inventory, verified storage, then native collection")),
      "Local deterministic material acquisition; current executor owns action");
  AgenticPlanValidationResult result=AgenticPlanValidator.validate(p,settings);
  return new AgenticPlannerOutcome(result,result.valid()?"native_resource":"native_resource_invalid",List.of(),result.valid()?null:"Resource goal failed validation");
 }
 public static Set<String> allowResourceKind(Set<String> other){Set<String> all=new LinkedHashSet<>(other);all.add(KIND);return Set.copyOf(all);}
 public static List<List<String>> addResourceSequence(Object[] existing){
  List<List<String>> sequences=new ArrayList<>();for(Object o:existing)sequences.add((List<String>)o);
  sequences.add(List.of(KIND));return sequences;
 }
 public static void registerFactory(AgenticStepFactoryRegistry registry){registry.register(KIND,ResourceAgenticIntegration::task);}
 private static Optional<Task> task(AgenticStepSpec step,AgenticExecutionContext ctx){
  try{
   String item=step.args().get("item");int count=Integer.parseInt(step.args().getOrDefault("count","1"));
   if(item==null||!item.matches("[a-z0-9_]{2,56}")||count<1||count>256||!Boolean.TRUE.equals(com.player2.playerengine.nativecommands.NativeAccess.callStatic("com.player2.playerengine.TaskCatalogue","taskExists",item)))return Optional.empty();
   Task task=(Task)com.player2.playerengine.nativecommands.NativeAccess.callStatic("com.player2.playerengine.TaskCatalogue","getItemTask",item,count);return Optional.ofNullable(task);
  }catch(Exception | LinkageError failure){return Optional.empty();}
 }
}
