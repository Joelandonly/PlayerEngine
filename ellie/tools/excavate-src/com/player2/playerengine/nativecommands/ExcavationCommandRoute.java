package com.player2.playerengine.nativecommands;

import java.util.Locale;

/** Explicit owner grammar. No natural-language guess or model-generated coordinates. */
public final class ExcavationCommandRoute {
    private ExcavationCommandRoute(){}
    /**
     * A null return hands control back to the unchanged native command
     * resolver. Exactly six signed integer coordinates are accepted.
     */
    public static String exact(String input){
        if(input==null)return null;
        String text=input.strip().replaceFirst("(?i)^ellie\\s*[,!:]?\\s*", "")
            .replaceFirst("^@","").strip();
        String[] tokens=text.split("\\s+");
        if(tokens.length!=7||!tokens[0].toLowerCase(Locale.ROOT).equals("excavate"))return null;
        StringBuilder result=new StringBuilder("excavate");
        for(int i=1;i<7;i++){
            if(!tokens[i].matches("-?[0-9]{1,9}"))return null;
            try{result.append(' ').append(Integer.parseInt(tokens[i]));}
            catch(NumberFormatException ignored){return null;}
        }
        return result.toString();
    }
}
