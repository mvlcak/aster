package dev.mvlcak.aster.tui;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class AppState {

    private static final int NO_PENDING_SLOT = -1;

    private ScreenMode screenMode = ScreenMode.CHAT;
    private final String workingDirectory = System.getProperty("user.dir");
    private final List<ChatTranscriptEntry> messages = new ArrayList<>();
    private boolean streaming;
    private int pendingAssistantSlot = NO_PENDING_SLOT;
    private long thinkingStartNanos;
    private long lastSpentTokens;
    private long totalSpentTokens;
    private long lastInputTokens;
    private long totalInputTokens;
    private long lastCompletionTokens;
    private long totalCompletionTokens;

    public synchronized long getLastInputTokens() {
        return lastInputTokens;
    }

    public synchronized long getTotalInputTokens() {
        return totalInputTokens;
    }

    public synchronized long getLastCompletionTokens() {
        return lastCompletionTokens;
    }

    public synchronized long getTotalCompletionTokens() {
        return totalCompletionTokens;
    }

    public synchronized long getLastSpentTokens(){
        return lastSpentTokens;
    }

    public synchronized long getTotalSpentTokens(){
        return totalSpentTokens;
    }

    public synchronized long thinkingElapsedSeconds() {
        return streaming ? (System.nanoTime() - thinkingStartNanos) / 1_000_000_000 : 0;
    }

    public synchronized ScreenMode currentScreen() {
        return screenMode;
    }

    public synchronized void switchScreen(ScreenMode screen) {
        this.screenMode = screen;
    }

    public String workingDirectory() {
        return workingDirectory;
    }

    public synchronized List<ChatTranscriptEntry> messages() {
        return List.copyOf(messages);
    }

    public synchronized boolean isStreaming() {
        return streaming;
    }

    public synchronized void appendUserMessage(String text) {
        messages.add(new ChatTranscriptEntry(ChatRole.USER, text));
    }

    public synchronized void appendSystemMessage(String text) {
        messages.add(new ChatTranscriptEntry(ChatRole.SYSTEM, text));
    }

    public synchronized void startAssistantResponse() {
        pendingAssistantSlot = messages.size();
        messages.add(new ChatTranscriptEntry(ChatRole.ASSISTANT, ""));
        streaming = true;
        thinkingStartNanos = System.nanoTime();
    }

    public synchronized void completeAssistantResponse(AtomicReference<ChatResponse> chatResponse) {
        String fallbackIfEmpty = extractText(chatResponse.get());
        Usage usage = chatResponse.get().getMetadata().getUsage();
        lastInputTokens = usage.getPromptTokens();
        totalInputTokens = totalInputTokens + lastInputTokens;
        lastCompletionTokens = usage.getCompletionTokens();
        totalCompletionTokens = totalCompletionTokens + lastCompletionTokens;
        lastSpentTokens = usage.getTotalTokens();
        totalSpentTokens = totalSpentTokens + lastSpentTokens;
        if (pendingAssistantSlot >= 0) {
            ChatTranscriptEntry pending = messages.get(pendingAssistantSlot);
            boolean isEmpty = pending.text() == null || pending.text().isBlank();
            boolean hasFallback = fallbackIfEmpty != null && !fallbackIfEmpty.isBlank();
            if (isEmpty && hasFallback) {
                messages.set(pendingAssistantSlot,
                        new ChatTranscriptEntry(ChatRole.ASSISTANT, fallbackIfEmpty));
            }
        }
        pendingAssistantSlot = NO_PENDING_SLOT;
        streaming = false;
    }

    public synchronized void completeAssistantResponseText(String text) {
        if (pendingAssistantSlot >= 0) {
            messages.set(pendingAssistantSlot, new ChatTranscriptEntry(ChatRole.ASSISTANT, text));
        }
        pendingAssistantSlot = NO_PENDING_SLOT;
        streaming = false;
    }

    public synchronized void abortAssistantResponse(String reason) {
        if (pendingAssistantSlot >= 0) {
            messages.remove(pendingAssistantSlot);
            pendingAssistantSlot = NO_PENDING_SLOT;
        }
        streaming = false;
        appendSystemMessage(reason);
    }

    private String extractText(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getText();
    }
}
