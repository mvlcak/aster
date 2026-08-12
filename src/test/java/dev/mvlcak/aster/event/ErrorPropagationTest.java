package dev.mvlcak.aster.event;

import dev.mvlcak.aster.ai.routing.RoutingWorkflow;
import dev.mvlcak.aster.chat.ChatService;
import dev.mvlcak.aster.tui.AppState;
import dev.mvlcak.aster.tui.ChatRole;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ErrorPropagationTest {

    @Test
    void workflowFailureBecomesSystemMessageAndStopsSpinner() {
        AppEventBus bus = new AppEventBus();
        AppState appState = new AppState();
        RoutingWorkflow routingWorkflow = Mockito.mock(RoutingWorkflow.class);
        Mockito.doThrow(new IllegalStateException("boom",
                        new TimeoutException("Did not observe any item within 30000ms")))
                .when(routingWorkflow).route(Mockito.anyString());

        new AppEventLoop(bus, appState, new ChatService(routingWorkflow, bus)).start();

        bus.dispatch(new AppEvent.UserInput("upgrade my dependencies"));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(appState.isStreaming()).isFalse();
            assertThat(appState.messages()).last().satisfies(entry -> {
                assertThat(entry.role()).isEqualTo(ChatRole.SYSTEM);
                assertThat(entry.text())
                        .isEqualTo("Request failed — TimeoutException: Did not observe any item within 30000ms");
            });
        });
    }
}