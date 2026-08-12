package dev.mvlcak.aster.event;

public sealed interface AppEvent permits
        AppEvent.UserInput, AppEvent.AssistantFail,
        AppEvent.ClearSession,
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

    record AssistantFail(String error) implements AppEvent {

        /**
         * Renders the most specific cause of a failure as a single line for the transcript.
         */
        public static AssistantFail of(Throwable failure) {
            Throwable root = failure;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String message = root.getMessage();
            String detail = message == null || message.isBlank()
                    ? root.getClass().getSimpleName()
                    : root.getClass().getSimpleName() + ": " + message.lines().findFirst().orElse("");
            return new AssistantFail("Request failed — " + detail);
        }
    }

    record ClearSession() implements AppEvent {
    }
}
