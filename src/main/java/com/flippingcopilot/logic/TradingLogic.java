package com.flippingcopilot.logic;

import com.flippingcopilot.model.Suggestion;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;

@Slf4j
@Singleton
public class TradingLogic {

    public Suggestion generateSuggestion() {
        log.info("generateSuggestion() called — returning dummy suggestion.");
        return new Suggestion("Placeholder Item", 12345, "buy", 100, 120);
    }

    public void executeBuyOffer(Suggestion suggestion) {
        log.info("Executing buy offer for: {}", suggestion.getName());
        // Add logic here if needed to simulate a buy
    }

    public void checkTradeStatus() {
        log.info("Checking trade status (placeholder method).");
        // Add logic here if needed to simulate checking trade
    }
}
