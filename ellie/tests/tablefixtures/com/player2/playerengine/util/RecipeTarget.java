package com.player2.playerengine.util;
import net.minecraft.class_1792;
public record RecipeTarget(class_1792 item,int count){public int getTargetCount(){return count;}public class_1792 getOutputItem(){return item;}}
