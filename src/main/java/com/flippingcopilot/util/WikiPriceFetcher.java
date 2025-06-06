package com.flippingcopilot.util;

import com.flippingcopilot.model.WikiItemStats;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
public class WikiPriceFetcher {

    private static final String API_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";

    public static Map<Integer, WikiItemStats> fetchPrices() {
        Map<Integer, WikiItemStats> itemStatsMap = new HashMap<>();

        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "FlippingCopilot/1.0");

            InputStream in = conn.getInputStream();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(in).get("data");

            for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                int itemId = Integer.parseInt(entry.getKey());
                JsonNode node = entry.getValue();

                WikiItemStats stats = new WikiItemStats();
                stats.setItemId(itemId);
                stats.setBuyPrice(node.get("high").asInt());
                stats.setSellPrice(node.get("low").asInt());
                stats.setHighPriceVolume(node.get("highTime").asInt());
                stats.setLowPriceVolume(node.get("lowTime").asInt());

                itemStatsMap.put(itemId, stats);
            }

        } catch (Exception e) {
            log.error("Error fetching prices from OSRS Wiki", e);
        }

        return itemStatsMap;
    }
}
