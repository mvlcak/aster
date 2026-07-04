package dev.mvlcak.aster.event;

import org.springframework.ai.chat.model.ChatResponse;

import java.util.concurrent.atomic.AtomicReference;

public sealed interface AppEvent permits
        AppEvent.UserInput, AppEvent.AssistantFail,
        AppEvent.SystemMessage, AppEvent.AssistantComplete {

    record UserInput(String text) implements AppEvent {}
    record AssistantComplete(AtomicReference<ChatResponse> chatResponse) implements AppEvent {}
    record AssistantFail(String error) implements AppEvent {}
    record SystemMessage(String text) implements AppEvent {}
}
