package com.player2.playerengine.player2api;
import java.util.UUID;
/** Message/authentication boundary, never packaged. */
public interface Event{record UserMessage(String message,UUID authenticatedUserUuid)implements Event{}}
