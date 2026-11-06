package com.player2.playerengine.player2api;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Narrow exception to ConversationManager's normal 64-block conversational range.
 * Only explicit, authenticated commands addressed to this companion are eligible.
 * The original conversation router still owns delivery; AgentConversationData.onEvent
 * still calls SetupDirectRoute, which rechecks ownership and command availability.
 *
 * No new queue, scheduler, login mechanism, name-based authorization or global chat radius.
 */
public final class RemoteOwnerCommandGate {
    private RemoteOwnerCommandGate() {}

    public static boolean include(boolean inNormalRange, Event.UserMessage message,
                                  AgentConversationData target) {
        if (inNormalRange) return true;
        return eligible(message,target);
    }

    public static boolean eligible(Event.UserMessage message,AgentConversationData target) {
        if(message==null||target==null||message.message()==null)return false;
        UUID authenticated=message.authenticatedUserUuid();
        if(authenticated==null||!target.isOwner(authenticated)||target.getMod()==null)return false;
        String name=target.getName();
        if(name==null||name.isBlank()||name.length()>64)return false;
        // A command must address exactly the companion's name. "Ellie setup" is
        // valid; "Tell Ellie..." or a bare "setup" is not a remote command.
        String prefix="(?i)^\\s*"+Pattern.quote(name)+"(?:\\s*[,!:]\\s*|\\s+)(.+)$";
        if(!Pattern.compile(prefix).matcher(message.message()).matches())return false;
        // Exact/known aliases only. Ordinary conversation must stay range-limited.
        return SetupDirectRoute.resolve(message.message())!=null;
    }
}
