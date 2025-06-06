package com.flippingcopilot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WikiItemStats {
    private int itemId;
    private String name;
    private int highAlch;
    private int buyPrice;
    private int sellPrice;
    private int overallAverage;
    private int highPriceVolume;
    private int lowPriceVolume;
}
