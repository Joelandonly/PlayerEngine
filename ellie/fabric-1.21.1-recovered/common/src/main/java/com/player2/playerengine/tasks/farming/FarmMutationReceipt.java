package com.player2.playerengine.tasks.farming;

interface FarmMutationReceipt {
   boolean hasIssuedMutation();

   boolean settlePendingTransition();

   void prepareForTransientDetach();

   boolean isSuccessful();

   FarmTaskReason reason();
}
