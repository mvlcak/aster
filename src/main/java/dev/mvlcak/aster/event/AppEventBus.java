package dev.mvlcak.aster.event;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AppEventBus {

    private final BlockingQueue<AppEvent> queue = new LinkedBlockingQueue<>();

    public void dispatch(AppEvent event) {
        queue.offer(event);
    }

    AppEvent take() throws InterruptedException {
        return queue.take();
    }
}
