package com.player2.playerengine.player2api;

import java.util.Arrays;

public record Character(String id, String name, String shortName, String greetingInfo, String description, String skinURL, String[] voiceIds) {
   @Override
   public String toString() {
      return String.format(
         "Character{id='%s', name='%s', shortName='%s', greeting='%s', voiceIds=%s}",
         this.id,
         this.name,
         this.shortName,
         this.greetingInfo,
         Arrays.toString(this.voiceIds)
      );
   }
}
