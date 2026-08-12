package dev.mvlcak.aster.tui;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.ArrayList;
import java.util.List;

public class AppState {

    private static final int NO_PENDING_SLOT = -1;

    private ScreenMode screenMode = ScreenMode.CHAT;
    private final String workingDirectory = System.getProperty("user.dir");
    private final List<ChatTranscriptEntry> messages = new ArrayList<>();
    private boolean streaming;
    private String activityStatus = "";
    private int pendingAssistantSlot = NO_PENDING_SLOT;
    private long thinkingStartNanos;
    private long lastSpentTokens;
    private long totalSpentTokens;
    private long lastInputTokens;
    private long totalInputTokens;
    private long lastCompletionTokens;
    private long totalCompletionTokens;

    public synchronized void clearSession() {
        this.messages.clear();
    }

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
        streaming = true;
        activityStatus = "";
        thinkingStartNanos = System.nanoTime();
    }

    public synchronized void appendAssistantDelta(String delta) {
        if (pendingAssistantSlot < 0 || delta == null || delta.isEmpty()) {
            return;
        }
        ChatTranscriptEntry current = messages.get(pendingAssistantSlot);
        messages.set(pendingAssistantSlot,
                new ChatTranscriptEntry(ChatRole.ASSISTANT, current.text() + delta));
    }

    public synchronized void setActivityStatus(String status) {
        this.activityStatus = status == null ? "" : status;
    }

    public synchronized String currentActivityStatus() {
        return activityStatus;
    }

    public void countTokens(ChatResponse chatResponse) {
        Usage usage = chatResponse.getMetadata().getUsage();
        lastInputTokens = usage.getPromptTokens();
        totalInputTokens = totalInputTokens + lastInputTokens;
        lastCompletionTokens = usage.getCompletionTokens();
        totalCompletionTokens = totalCompletionTokens + lastCompletionTokens;
        lastSpentTokens = usage.getTotalTokens();
        totalSpentTokens = totalSpentTokens + lastSpentTokens;
    }

    public synchronized void completeAssistantResponseText(String text) {
        if (pendingAssistantSlot >= 0) {
            messages.add(new ChatTranscriptEntry(ChatRole.ASSISTANT, text));
        }
        pendingAssistantSlot = NO_PENDING_SLOT;
        streaming = false;
        activityStatus = "";
    }

    public synchronized void completeAssistantSummary(String text) {
        messages.add(new ChatTranscriptEntry(ChatRole.ASSISTANT, text));
        streaming = true;
    }

    public synchronized void abortAssistantResponse(String reason) {
        if (pendingAssistantSlot >= 0 && pendingAssistantSlot < messages.size()) {
            messages.remove(pendingAssistantSlot);
        }
        pendingAssistantSlot = NO_PENDING_SLOT;
        streaming = false;
        activityStatus = "";
        appendSystemMessage(reason);
    }

}
