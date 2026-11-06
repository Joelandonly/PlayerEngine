package com.player2.playerengine.player2api;
import com.player2.playerengine.PlayerEngineController;import java.util.UUID;
public final class AgentConversationData{final PlayerEngineController controller;final UUID owner;public AgentConversationData(PlayerEngineController c,UUID o){controller=c;owner=o;}public boolean isOwner(UUID id){return owner.equals(id);}public PlayerEngineController getMod(){return controller;}public String getName(){return "Ellie";}}
