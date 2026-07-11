package dev.mvlcak.aster.event;

public sealed interface AppEvent permits
        AppEvent.UserInput, AppEvent.AssistantFail,
        AppEvent.SystemMessage,
        AppEvent.AssistantCompleteText, AppEvent.AssistantDelta,
        AppEvent.AssistantStatus, AppEvent.AssistantSummary {

    record UserInput(String text) implements AppEvent {}

    record AssistantCompleteText(String text) implements AppEvent {
    }

    record AssistantDelta(String text) implements AppEvent {
    }

    record AssistantStatus(String text) implements AppEvent {
    }

    record AssistantSummary(String text) implements AppEvent {
    }
    record AssistantFail(String error) implements AppEvent {}
    record SystemMessage(String text) implements AppEvent {}
}
