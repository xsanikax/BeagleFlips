package com.flippingcopilot.controller; // Ensure this package matches your project structure

import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.*;
import com.flippingcopilot.ui.graph.PriceGraphController;
import com.flippingcopilot.ui.graph.model.Data; // Assuming this is used, keep if needed
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.RuneLite;
import net.runelite.client.Notifier;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Random; // Import Random
import java.util.List;
import java.util.Arrays;
import java.io.IOException;
import okhttp3.Request;

@Slf4j
@Getter
@Setter
@Singleton
public class SuggestionController {

    // dependencies
    private final PausedManager pausedManager;
 @NonNull private final Client client;
    private final OsrsLoginManager osrsLoginManager;
    private final HighlightController highlightController;
    @NonNull private final GrandExchange grandExchange;
    private final Notifier notifier;
    private final OfferManager offerManager;
    private final LoginResponseManager loginResponseManager; // May become less relevant
    private final ClientThread clientThread;
    private final FlippingCopilotConfig config;
    private final SuggestionManager suggestionManager;
    private final AccountStatusManager accountStatusManager;
    private final GrandExchangeUncollectedManager uncollectedManager;
    // private final ScheduledExecutorService executorService; // Removed as per compiler warnings if not used elsewhere
    // private final Gson gson; // Removed as per compiler warnings if not used directly in this class
    // private final JsonObject jsonObject; // Removed as per compiler warnings if not used
    private final PriceGraphController graphPriceGraphController;
    private final FlipManager flipManager; // Inject FlipManager to access local flip data
    private final OkHttpClient okHttpClient; // Inject OkHttpClient for HTTP requests
    private final Gson gson; // Inject Gson for JSON parsing


 @Inject
 public SuggestionController(PausedManager pausedManager, @NonNull Client client, OsrsLoginManager osrsLoginManager, HighlightController highlightController, @NonNull GrandExchange grandExchange, Notifier notifier, OfferManager offerManager, LoginResponseManager loginResponseManager, ClientThread clientThread, FlippingCopilotConfig config, SuggestionManager suggestionManager, AccountStatusManager accountStatusManager, GrandExchangeUncollectedManager uncollectedManager, PriceGraphController graphPriceGraphController, FlipManager flipManager, OkHttpClient okHttpClient, Gson gson) {
 this.pausedManager = pausedManager;
 this.client = client;
 this.osrsLoginManager = osrsLoginManager;
 this.highlightController = highlightController;
 this.grandExchange = grandExchange;
 this.notifier = notifier;
 this.offerManager = offerManager;
 this.loginResponseManager = loginResponseManager;
 this.clientThread = clientThread;
 this.config = config;
 this.suggestionManager = suggestionManager;
 this.accountStatusManager = accountStatusManager;
 this.uncollectedManager = uncollectedManager;
 this.graphPriceGraphController = graphPriceGraphController;
 this.flipManager = flipManager;
        this.okHttpClient = okHttpClient;
        this.gson = gson;
    }

    private MainPanel mainPanel;
    // private LoginPanel loginPanel; // Removed as per compiler warnings and previous analysis
    private CopilotPanel copilotPanel;
    private SuggestionPanel suggestionPanel;

    // Hardcoded list of profitable items identified from historical data
    private static final List<String> PROFITABLE_ITEMS = Arrays.asList(
            "Astral rune", "Chaos rune", "Death rune", "Nature rune", "Soul rune", "Adamant bolts", "Runite bolts", "Amethyst dart", "Cannonball", "Coal", "Gold bar", "Revenant ether",
            "Bandos godsword ornament kit", "Malediction ward", "Blue moon tassets", "Ahrim's armour set", "Karil's armour set",
            "Dharok's armour set", "Toxic blowpipe (empty)", "Dragon pickaxe",
            "Master wand"
            // Add other profitable items from your data here
    );

    private final Random random = new Random();

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

    /**
     * Generates a trading suggestion based on the local trading algorithm.
     * This replaces the previous backend API call for suggestions.
     */
    public void generateLocalSuggestion() {
        suggestionManager.setSuggestionNeeded(false);

        // PHASE 1: Simplified checks. No API login needed. OSRS login is still relevant.

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
        // Now integrating OSRS Wiki prices
        // This is a placeholder algorithm based on the analysis of historical data.
        // It selects a profitable item and suggests a buy/sell price based on historical averages or typical spreads.
        // In a real-world scenario with live data, this would be more dynamic.

        // Hardcoded list of profitable items identified from historical data
        String[] profitableItems = {"Astral rune", "Chaos rune", "Death rune", "Nature rune", "Soul rune", "Adamant bolts", "Runite bolts", "Amethyst dart", "Cannonball", "Coal", "Gold bar", "Revenant ether"};

        Random random = new Random();
        String suggestedItemName = profitableItems[random.nextInt(profitableItems.length)];

        // To get the item ID, we would ideally use a mapping from item name to ID.
        // For now, we'll use a placeholder and assume we have a way to get the ID.
        // In a real scenario, you'd likely load this mapping from data or the RuneLite API itself.
        int placeholderItemId = 0; // Placeholder for item ID
        // You need a way to get the item ID from the item name.
        // This is a crucial step for fetching prices from the Wiki API.
        // For this example, we'll just use a placeholder and fetch a price for a known item ID (e.g., 561 for Nature rune)
        int natureRuneItemId = 561; // Example: Nature rune ID

        // Fetch prices asynchronously
        fetchOsrsWikiPrice(natureRuneItemId); // Use a known item ID for now

        // The rest of the suggestion generation will happen in the callback of fetchOsrsWikiPrice
    }

    /**
     * Fetches the latest buy and sell prices for a given item ID from the OSRS Wiki API.
     * Implemented asynchronously to avoid blocking the game thread.
     *
     * @param itemId The ID of the item to fetch prices for.
     */
    private void fetchOsrsWikiPrice(int itemId) {
        String url = "https://prices.runescape.wiki/api/v1/osrs/latest?id=" + itemId;
        Request request = new Request.Builder().url(url).build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                log.error("Failed to fetch OSRS Wiki price for item ID {}: {}", itemId, e.getMessage());
                clientThread.invokeLater(() -> {
                    suggestionManager.setSuggestionError("Failed to fetch item price from OSRS Wiki.");
                    suggestionManager.setSuggestionRequestInProgress(false);
                    if (suggestionPanel != null) suggestionPanel.refresh();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull okhttp3.Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        log.error("OSRS Wiki price request failed for item ID {}: {}", itemId, response.code());
                        clientThread.invokeLater(() -> {
                            suggestionManager.setSuggestionError("Failed to fetch item price from OSRS Wiki (HTTP Error).");
                            suggestionManager.setSuggestionRequestInProgress(false);
                            if (suggestionPanel != null) suggestionPanel.refresh();
                        });
                        return;
                    }

                    String responseBody = response.body().string();
                    JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                    JsonObject data = jsonObject.getAsJsonObject("data");

                    if (data == null || !data.has(String.valueOf(itemId))) {
                        log.warn("OSRS Wiki data not found for item ID {}.", itemId);
                        clientThread.invokeLater(() -> {
                            suggestionManager.setSuggestionError("Item price not found on OSRS Wiki.");
                            suggestionManager.setSuggestionRequestInProgress(false);
                            if (suggestionPanel != null) suggestionPanel.refresh();
                        });
                        return;
                    }

                    JsonObject itemPriceData = data.getAsJsonObject(String.valueOf(itemId));
                    int highPrice = itemPriceData.has("high") ? itemPriceData.get("high").getAsInt() : -1;
                    int lowPrice = itemPriceData.has("low") ? itemPriceData.get("low").getAsInt() : -1;

                    // Now that we have the prices, generate the suggestion on the client thread
                    clientThread.invokeLater(() -> {
                        // --- Generate Suggestion using fetched prices ---
                        // In a real algorithm, you'd use these prices (high and low)
                        // to calculate a potential profit and determine the best buy/sell prices.
                        // For this basic implementation, we'll use the low price as buy and high price as sell.

                        int suggestedBuyPrice = lowPrice;
                        int suggestedSellPrice = highPrice;
                        int suggestedQuantity = 100; // Placeholder quantity - could be based on item liquidity later
                        int placeholderBoxId = 0; // Placeholder boxId

                        // You still need a way to map item ID back to item name if needed for the message
                        String suggestedItemName = "Item ID: " + itemId; // Placeholder item name

                        Suggestion generatedSuggestion = new Suggestion(
                                "flip", // type - indicating a flip suggestion
                                placeholderBoxId,
                                itemId, // Use the actual item ID
                                suggestedBuyPrice, // suggested buy price from Wiki
                                suggestedQuantity, // suggested quantity
                                suggestedItemName, // item name
                                -1,     // id (command_id) - placeholder
                                "Try flipping " + suggestedItemName + "! Buy at " + suggestedBuyPrice + ", sell at " + suggestedSellPrice + ".", // informative message
                                null    // graphData - placeholder
                        );
                        // --- End of Suggestion Generation ---

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

                } catch (IOException e) {
                    log.error("Error processing OSRS Wiki price response for item ID {}: {}", itemId, e.getMessage());
                    clientThread.invokeLater(() -> {
                        suggestionManager.setSuggestionError("Error processing OSRS Wiki price data.");
                        suggestionManager.setSuggestionRequestInProgress(false);
                        if (suggestionPanel != null) suggestionPanel.refresh();
                    });
                }
            }
        });


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