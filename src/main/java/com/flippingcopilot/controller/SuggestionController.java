package com.flippingcopilot.controller; // Ensure this package matches your project structure

import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.*;
import com.flippingcopilot.ui.graph.PriceGraphController;
import com.flippingcopilot.ui.graph.model.Data; // Assuming this is used, keep if needed
import com.google.gson.Gson;
import com.google.gson.JsonObject; // Keep if toJson is used with it
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

@Slf4j
@Getter
@Setter
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SuggestionController {

    // dependencies
    private final PausedManager pausedManager;
    private final Client client;
    private final Gson gson;
    private final OsrsLoginManager osrsLoginManager;
    private final HighlightController highlightController;
    private final GrandExchange grandExchange;
    private final ScheduledExecutorService executorService; // Keep if other timed tasks are local
    // PHASE 1: Comment out ApiRequestHandler
    // private final ApiRequestHandler apiRequestHandler;
    private final Notifier notifier;
    private final OfferManager offerManager;
    private final LoginResponseManager loginResponseManager; // May become less relevant
    private final ClientThread clientThread;
    private final FlippingCopilotConfig config;
    private final SuggestionManager suggestionManager;
    private final AccountStatusManager accountStatusManager;
    private final GrandExchangeUncollectedManager uncollectedManager;
    private final PriceGraphController graphPriceGraphController;

    private MainPanel mainPanel;
    private LoginPanel loginPanel; // May be removed if MainPanel no longer uses it
    private CopilotPanel copilotPanel;
    private SuggestionPanel suggestionPanel;

    public void togglePause() {
        if (pausedManager.isPaused()) {
            pausedManager.setPaused(false);
            suggestionManager.setSuggestionNeeded(true); // This will trigger a local suggestion attempt
            if (suggestionPanel != null) suggestionPanel.refresh();
        } else {
            pausedManager.setPaused(true);
            highlightController.removeAll();
            if (suggestionPanel != null) suggestionPanel.refresh();
        }
    }

    void onGameTick() {
        if (suggestionManager.isSuggestionRequestInProgress() || suggestionManager.isGraphDataReadingInProgress()) {
            // These flags will be controlled by the local suggestion engine later
            return;
        }
        if (isUncollectedOutOfSync()) {
            log.warn("uncollected is out of sync, it thinks there are items to collect but the GE is open and the Collect button not visible");
            uncollectedManager.clearAllUncollected(osrsLoginManager.getAccountHash());
            suggestionManager.setSuggestionNeeded(true);
        }
        if (osrsLoginManager.hasJustLoggedIn()) {
            return;
        }
        if ((suggestionManager.isSuggestionNeeded() || suggestionManager.suggestionOutOfDate()) && !(grandExchange.isSlotOpen() && !accountStatusManager.isSuggestionSkipped())) {
            // PHASE 1: Instead of calling the API, we'll set a placeholder or trigger a local engine (later)
            getOfflineSuggestion();
        }
    }

    private boolean isUncollectedOutOfSync() {
        if (client.getTickCount() <= uncollectedManager.getLastUncollectedAddedTick() + 2) {
            return false;
        }
        if (!grandExchange.isHomeScreenOpen() || grandExchange.isCollectButtonVisible()) {
            return false;
        }
        if (uncollectedManager.HasUncollected(osrsLoginManager.getAccountHash())) {
            return true;
        }
        return suggestionPanel != null && suggestionPanel.isCollectItemsSuggested();
    }

    // PHASE 1: Renamed from getSuggestionAsync to getOfflineSuggestion
    public void getOfflineSuggestion() {
        suggestionManager.setSuggestionNeeded(false);

        // PHASE 1: Simplified checks. No API login needed. OSRS login is still relevant.
        if (!osrsLoginManager.isValidLoginState()) {
            // SuggestionPanel will handle displaying "log in to game" message
            if (suggestionPanel != null) suggestionPanel.refresh();
            return;
        }

        if (suggestionManager.isSuggestionRequestInProgress()) {
            // This flag will be set by the local engine if it's busy
            return;
        }

        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if (accountStatus == null) {
            log.warn("AccountStatus is null, cannot generate offline suggestion.");
            if (suggestionPanel != null) suggestionPanel.refresh(); // Show appropriate message
            return;
        }

        suggestionManager.setSuggestionRequestInProgress(true);
        // For graph data, we'll need a local/wiki source later.
        // suggestionManager.setGraphDataReadingInProgress(true);

        log.info("Offline: Triggering local suggestion logic (placeholder for now)");

        // Simulate a local suggestion process (replace with actual local engine later)
        // For Phase 1, let's just set a "wait" or "offline mode" suggestion.
        Suggestion placeholderSuggestion = new Suggestion(
                "wait", // type
                0,      // boxId
                0,      // itemId
                0,      // price
                0,      // quantity
                "Offline Mode", // name
                -1,     // id (command_id)
                "Suggestions are local.", // message
                null    // graphData
        );

        // Simulate async behavior if the local engine were complex
        // For now, direct update.
        clientThread.invokeLater(() -> {
            suggestionManager.setSuggestion(placeholderSuggestion);
            suggestionManager.setSuggestionError(null);
            suggestionManager.setSuggestionRequestInProgress(false); // Reset flag
            // suggestionManager.setGraphDataReadingInProgress(false); // Reset flag

            if (suggestionPanel != null) {
                suggestionPanel.refresh();
            }
            // showNotifications(oldSuggestion, placeholderSuggestion, accountStatus); // Notifications can be added back later
            highlightController.removeAll(); // Clear old highlights
            highlightController.redraw(); // Redraw based on new placeholder suggestion (will likely do nothing for "wait")
        });
    }


    void showNotifications(Suggestion oldSuggestion, Suggestion newSuggestion, AccountStatus accountStatus) {
        if (shouldNotify(newSuggestion, oldSuggestion)) {
            if (config.enableTrayNotifications()) {
                notifier.notify(newSuggestion.toMessage());
            }
            // Ensure copilotPanel is not null before checking isShowing()
            if (copilotPanel != null && !copilotPanel.isShowing() && config.enableChatNotifications()) {
                showChatNotifications(newSuggestion, accountStatus);
            }
        }
    }

    static boolean shouldNotify(Suggestion newSuggestion, Suggestion oldSuggestion) {
        if (newSuggestion.getType().equals("wait")) {
            return false;
        }
        // PHASE 1: Temporarily allow notifications even if suggestions are same, for testing offline mode.
        // Can revert this to: if (oldSuggestion != null && newSuggestion.equals(oldSuggestion))
        if (oldSuggestion != null && newSuggestion.getType().equals(oldSuggestion.getType()) && newSuggestion.getItemId() == oldSuggestion.getItemId()){
            return false; // Only avoid if type and item are identical to prevent spam of same item suggestions
        }
        return true;
    }

    private void showChatNotifications(Suggestion newSuggestion, AccountStatus accountStatus) {
        if (accountStatus.isCollectNeeded(newSuggestion)) {
            clientThread.invokeLater(() -> showChatNotification("Flipping Copilot: Collect items"));
        }
        clientThread.invokeLater(() -> showChatNotification(newSuggestion.toMessage()));
    }

    private void showChatNotification(String message) {
        String chatMessage = new ChatMessageBuilder()
                .append(config.chatTextColor(), message)
                .build();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", chatMessage, "");
    }
}
