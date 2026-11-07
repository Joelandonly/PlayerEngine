package net.minecraft;
/** Block-item identity boundary. */
public class class_1747 {public final String id;public class_1747(String id){this.id=id;}public Stack method_7854(){return new Stack(this,1,64);}public record Stack(class_1747 item,int count,int max){public boolean method_7960(){return count==0;}public Object method_7909(){return item;}public int method_7947(){return count;}public int method_7914(){return max;}}}
