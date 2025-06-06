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
import java.util.Random; // Import Random

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

        log.info("Offline: Triggering local suggestion logic.");

        // --- Basic Trading Algorithm (Offline Mode) ---
        // This is a placeholder algorithm based on the analysis of historical data.
        // It selects a profitable item and suggests a buy/sell price based on historical averages or typical spreads.
        // In a real-world scenario with live data, this would be more dynamic.

        // Hardcoded list of profitable items identified from historical data
        String[] profitableItems = {"Astral rune", "Chaos rune", "Death rune", "Nature rune", "Soul rune", "Adamant bolts", "Runite bolts", "Amethyst dart", "Cannonball", "Coal", "Gold bar", "Revenant ether"};

        Random random = new Random();
        String suggestedItemName = profitableItems[random.nextInt(profitableItems.length)];

        // Placeholder prices - In a real scenario, these would be based on analyzed historical data
        // or dynamically fetched live prices.
        int suggestedBuyPrice = 0;
        int suggestedSellPrice = 0;
        int suggestedQuantity = 1; // Placeholder quantity

        // Basic logic to assign placeholder prices based on item type (very simplified)
        switch (suggestedItemName) {
            case "Astral rune":
            case "Chaos rune":
            case "Death rune":
            case "Nature rune":
            case "Soul rune":
                suggestedBuyPrice = 100;
                suggestedSellPrice = 105;
                suggestedQuantity = 10000;
                break;
            case "Adamant bolts":
            case "Runite bolts":
            case "Amethyst dart":
                suggestedBuyPrice = 50;
                suggestedSellPrice = 55;
                suggestedQuantity = 5000;
                break;
            case "Cannonball":
                suggestedBuyPrice = 200;
                suggestedSellPrice = 205;
                suggestedQuantity = 1000;
                break;
            case "Coal":
                suggestedBuyPrice = 120;
                suggestedSellPrice = 125;
                suggestedQuantity = 5000;
                break;
            case "Gold bar":
                suggestedBuyPrice = 130;
                suggestedSellPrice = 135;
                suggestedQuantity = 2000;
                break;
            case "Revenant ether":
                suggestedBuyPrice = 180;
                suggestedSellPrice = 185;
                suggestedQuantity = 1000;
                break;
            default:
                // Fallback for any other item added to the list
                suggestedBuyPrice = 1000;
                suggestedSellPrice = 1100;
                suggestedQuantity = 100;
                break;
        }


        // Create a new suggestion based on the algorithm's output
        Suggestion generatedSuggestion = new Suggestion(
                "flip", // type - indicating a flip suggestion
                0,      // boxId - placeholder
                0,      // itemId - placeholder, will need mapping later
                suggestedBuyPrice, // suggested buy price
                suggestedQuantity, // suggested quantity
                suggestedItemName, // item name
                -1,     // id (command_id) - placeholder
                "Try flipping " + suggestedItemName + "! Buy at " + suggestedBuyPrice + ", sell at " + suggestedSellPrice + ".", // informative message
                null    // graphData - placeholder
        );

        // --- End of Basic Trading Algorithm ---


        // Simulate async behavior if the local engine were complex
        // For now, direct update.
        clientThread.invokeLater(() -> {
            suggestionManager.setSuggestion(generatedSuggestion); // Set the generated suggestion
            suggestionManager.setSuggestionError(null);
            suggestionManager.setSuggestionRequestInProgress(false); // Reset flag
            // suggestionManager.setGraphDataReadingInProgress(false); // Reset flag

            if (suggestionPanel != null) {
                suggestionPanel.refresh();
            }
            // showNotifications(oldSuggestion, generatedSuggestion, accountStatus); // Notifications can be added back later
            highlightController.removeAll(); // Clear old highlights
            // highlightController.redraw(); // Redraw based on the new suggestion (will need itemId mapping)
        });
    }


    void showNotifications(Suggestion oldSuggestion, Suggestion newSuggestion, AccountStatus accountStatus) {
        if (shouldNotify(newSuggestion, oldSuggestion)) {
            if (config.enableTrayNotifications()) {
                notifier.notify(newSuggestion.toMessage());
            }
            // Ensure copilotPanel is not null before checking isShowing()\n            if (copopilotPanel != null && !copilotPanel.isShowing() && config.enableChatNotifications()) {
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