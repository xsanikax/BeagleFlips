package com.flippingcopilot.controller; // Ensure this package matches your project structure

import com.flippingcopilot.model.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.VarClientStr;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Objects;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;

@Slf4j
@Getter
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class OfferHandler {

    private static final int GE_OFFER_INIT_STATE_CHILD_ID = 20;

    // dependencies
    private final Client client;
    private final SuggestionManager suggestionManager;
    // PHASE 1: Comment out ApiRequestHandler
    // private final ApiRequestHandler apiRequestHandler;
    private final OsrsLoginManager osrsLoginManager;
    private final OfferManager offerManager;
    private final HighlightController highlightController;
    private final LoginResponseManager loginResponseManager; // Keep for now, its isLoggedIn might be checked

    // state
    private String viewedSlotPriceErrorText = null;

    public void fetchSlotItemPrice(boolean isViewingSlot) {
        if (isViewingSlot) {
            var currentItemId = client.getVarpValue(CURRENT_GE_ITEM);
            offerManager.setViewedSlotItemId(currentItemId);
            if (currentItemId == -1 || currentItemId == 0) {
                offerManager.setViewedSlotItemPrice(-1); // Ensure price is reset if no item
                viewedSlotPriceErrorText = null;
                highlightController.redraw();
                return;
            }

            var suggestion = suggestionManager.getSuggestion();
            if (suggestion != null && suggestion.getItemId() == currentItemId &&
                    ((Objects.equals(suggestion.getType(), "sell") && isSelling()) ||
                            (Objects.equals(suggestion.getType(), "buy") && isBuying()))) {
                // If the current item matches the suggestion, use the suggestion's price
                offerManager.setViewedSlotItemPrice(suggestion.getPrice());
                offerManager.setLastViewedSlotItemPrice(suggestion.getPrice()); // Keep this for consistency
                offerManager.setLastViewedSlotPriceTime((int) Instant.now().getEpochSecond()); // Keep this
                viewedSlotPriceErrorText = null; // Clear any previous error
                highlightController.redraw();
                return;
            }

            // PHASE 1: Do not call the backend for prices.
            // Set a placeholder message or leave price as -1.
            // Later, this is where OSRS Wiki API calls would go.
            log.info("Offline Mode: Skipping backend price fetch for item {}. Price lookup for manually selected items will be implemented later.", currentItemId);
            offerManager.setViewedSlotItemPrice(-1); // Indicate price is not available
            viewedSlotPriceErrorText = "Offline: Price lookup unavailable";

            // It's important that OfferEditor (in the UI package) handles the case where
            // viewedSlotItemPrice is -1 and viewedSlotPriceErrorText is set.

            // Example of how it was done (commented out):
            /*
            if (!loginResponseManager.isLoggedIn()) { // This check might still be relevant if some local features depend on a "session"
                viewedSlotPriceErrorText = "Login to copilot to see item price.";
                offerManager.setViewedSlotItemPrice(-1);
                highlightController.redraw();
                return;
            }

            // PHASE 1: Original API call commented out
            // var fetchedPrice = apiRequestHandler.getItemPrice(currentItemId, osrsLoginManager.getPlayerDisplayName());
            // For now, simulate no price found or an error.
            ItemPrice fetchedPrice = new ItemPrice(0, 0, "Offline: Price lookup unavailable", null);


            if (fetchedPrice == null) {
                viewedSlotPriceErrorText = "Unknown error fetching price (offline)";
                offerManager.setViewedSlotItemPrice(-1);
            } else {
                if (fetchedPrice.getMessage() != null && !fetchedPrice.getMessage().isEmpty()) {
                    viewedSlotPriceErrorText = fetchedPrice.getMessage();
                    offerManager.setViewedSlotItemPrice(-1);
                } else {
                    viewedSlotPriceErrorText = null;
                    offerManager.setViewedSlotItemPrice(isSelling() ? fetchedPrice.getSellPrice() : fetchedPrice.getBuyPrice());
                }
            }
            // These were for tracking the source of the price, might be less relevant or adapted for local sources
            offerManager.setLastViewedSlotItemId(offerManager.getViewedSlotItemId());
            offerManager.setLastViewedSlotItemPrice(offerManager.getViewedSlotItemPrice());
            offerManager.setLastViewedSlotPriceTime((int) Instant.now().getEpochSecond());

            log.debug("Offline: Set item {} price: {}", offerManager.getViewedSlotItemId(),  offerManager.getViewedSlotItemPrice());
            */

        } else {
            offerManager.setViewedSlotItemPrice(-1);
            offerManager.setViewedSlotItemId(-1);
            viewedSlotPriceErrorText = null;
        }
        highlightController.redraw();
    }

    public boolean isSettingQuantity() {
        var chatboxTitleWidget = getChatboxTitleWidget();
        if (chatboxTitleWidget == null) return false;
        String chatInputText = chatboxTitleWidget.getText();
        return "How many do you wish to buy?".equals(chatInputText) || "How many do you wish to sell?".equals(chatInputText);
    }

    public boolean isSettingPrice() {
        var chatboxTitleWidget = getChatboxTitleWidget();
        if (chatboxTitleWidget == null) return false;
        String chatInputText = chatboxTitleWidget.getText();

        var offerTextWidget = getOfferTextWidget();
        if (offerTextWidget == null) return false;
        String offerText = offerTextWidget.getText();
        return "Set a price for each item:".equals(chatInputText) && ("Buy offer".equals(offerText) || "Sell offer".equals(offerText));
    }


    private Widget getChatboxTitleWidget() {
        return client.getWidget(ComponentID.CHATBOX_TITLE);
    }

    private Widget getOfferTextWidget() {
        // This used to be ComponentID.GRAND_EXCHANGE_OFFER_DESCRIPTION
        // Verify the correct widget ID for the "Buy Offer" / "Sell Offer" text in the chatbox title area.
        // It might be part of ComponentID.CHATBOX_GE_OFFER_FRAME or similar if not directly the title.
        // For now, let's assume it's within the broader offer container and check its children.
        Widget offerContainer = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER); // Main GE offer window
        if (offerContainer == null) {
            offerContainer = client.getWidget(ComponentID.CHATBOX_CONTAINER); // Try chatbox if main isn't it
        }
        if (offerContainer == null) return null;

        // Check common child indices for the "Buy Offer" / "Sell Offer" text.
        // This might need adjustment based on actual widget hierarchy.
        // The GE_OFFER_INIT_STATE_CHILD_ID = 20 was from your code.
        if (offerContainer.getChild(GE_OFFER_INIT_STATE_CHILD_ID) != null) {
            return offerContainer.getChild(GE_OFFER_INIT_STATE_CHILD_ID);
        }
        // Fallback or further inspection needed if the above is not correct.
        // log.warn("Offer text widget not found at expected child ID, returning null.");
        return null;
    }

    public boolean isSelling() {
        return client.getVarbitValue(Varbits.GE_OFFER_CREATION_TYPE) == 1;
    }

    public boolean isBuying() {
        return client.getVarbitValue(Varbits.GE_OFFER_CREATION_TYPE) == 0;
    }

    public String getOfferType() {
        if (isBuying()) {
            return "buy";
        } else if (isSelling()) {
            return "sell";
        } else {
            return null;
        }
    }

    public void setSuggestedAction(Suggestion suggestion) {
        var currentItemId = client.getVarpValue(CURRENT_GE_ITEM);

        if (isSettingQuantity()) {
            if (suggestion == null || currentItemId != suggestion.getItemId()) {
                return;
            }
            setChatboxValue(suggestion.getQuantity());
        } else if (isSettingPrice()) {
            int price = -1;
            // If suggestion is null, or item/type doesn't match, try using viewedSlotItemPrice (which is now local/placeholder)
            if (suggestion == null || currentItemId != suggestion.getItemId()
                    || !Objects.equals(suggestion.getType(), getOfferType())) { // Added Objects.equals for suggestion.getType()
                if (offerManager.getViewedSlotItemId() != currentItemId) {
                    return; // Don't set if the viewed item doesn't even match current GE item
                }
                price = offerManager.getViewedSlotItemPrice();
            } else {
                // Use suggestion price
                price = suggestion.getPrice();
            }

            if (price <= 0) { // Changed from == -1 to <= 0 as 0 is also an invalid price
                log.debug("Not setting price as it's invalid: {}", price);
                // Optionally show an error message in chatbox if price is invalid
                // setErrorTextInChatbox("Cannot set invalid price."); // Implement this if needed
                return;
            }

            setChatboxValue(price);
        }
    }

    public void setChatboxValue(int value) {
        var chatboxInputWidget = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
        if (chatboxInputWidget == null) {
            log.warn("Chatbox input widget is null, cannot set value.");
            return;
        }
        // The game client often expects a '*' to confirm the input via enter.
        // Ensure this behavior is consistent with how it works manually.
        chatboxInputWidget.setText(value + "*"); // Append '*' to allow enter confirmation
        client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(value)); // Set the underlying varc string
    }
}
