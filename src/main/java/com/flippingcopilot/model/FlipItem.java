package com.flippingcopilot.model;

public class FlipItem {
    private final String itemName;
    private final int buyPrice;
    private final int sellPrice;
    private final int margin;

    public FlipItem(String itemName, int buyPrice, int sellPrice, int margin) {
        this.itemName = itemName;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.margin = margin;
    }

    public String getItemName() {
        return itemName;
    }

    public int getBuyPrice() {
        return buyPrice;
    }

    public int getSellPrice() {
        return sellPrice;
    }

    public int getMargin() {
        return margin;
    }
}
