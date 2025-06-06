package com.flippingcopilot.model; // Assuming this is the correct package based on your structure

import com.flippingcopilot.controller.DoesNothingExecutorService; // This is one of your test utility classes
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlipPriceGraphControllerTest { // The class name in your error was FlipPriceGraphControllerTest

    private static final String DISPLAY_NAME_1 = "Acc 1";
    private static final String DISPLAY_NAME_2 = "Acc 2";
    private static final String DISPLAY_NAME_3 = "Acc 3";
    private final Map<String, Integer> displayNameToAccountId = Map.of(DISPLAY_NAME_1, 0, DISPLAY_NAME_2, 1, DISPLAY_NAME_3, 2);

    @Test
    public void testOneAccount() {

        // generate 6 months or random flips
        int now = (int) Instant.now().getEpochSecond();
        int sixMonthsAgo = (int) Instant.now().minus(365/ 2, ChronoUnit.DAYS).getEpochSecond();
        List<FlipV2> flips = generateFlipsBetween(sixMonthsAgo, now, 10_000, List.of(0));

        // create and populate the flip cache
        // MODIFIED CONSTRUCTOR CALL: Removed the first 'null' argument that was for ApiRequestHandler
        FlipManager flipManager = new FlipManager(new DoesNothingExecutorService(), new OkHttpClient.Builder().build());
        flipManager.setFlipsChangedCallback(() -> {});
        flipManager.displayNameToAccountId.putAll(displayNameToAccountId);
        flipManager.mergeFlips(flips, null); // Assuming this is still the intended way to populate for test
        flipManager.setIntervalDisplayName(DISPLAY_NAME_1);


        verifyflipManagerStoredOrder(flipManager); // Renamed from verifyFlipManagerStoredOrder

        flips.sort(Comparator.comparing(FlipV2::getClosedTime).reversed().thenComparing(FlipV2::getId));

        // create list of test interval start times
        List<Integer> testTimes = Stream.generate(()-> randomIntBetween(sixMonthsAgo, now)).limit(100).collect(Collectors.toList());
        testTimes.add(0, 0); // add 0 which is ALL

        for (int time : testTimes) {
            flipManager.setIntervalStartTime(time);

            // check stats equal
            Stats stats1 = flipManager.getIntervalStats();
            Stats stats2 = expectedStats(flips, time, DISPLAY_NAME_1);
            Assert.assertEquals(stats1, stats2);

            // check all flips equal
            List<FlipV2> allFlips1 = flipManager.getPageFlips(1, flips.size());
            List<FlipV2> allFlips2 = expectedPage(flips, time, 1, flips.size(), DISPLAY_NAME_1);
            assertFlipListsEqual(allFlips1, allFlips2);

            // check paginated flips equal
            for (int pageSize : Arrays.asList(3, 20, 33, 50, 100)) {
                int numPages = (stats1.flipsMade + pageSize -1) / pageSize; // Corrected numPages calculation
                if (stats1.flipsMade == 0 && pageSize > 0) numPages = 1; // Handle case with 0 flips
                for(int page=1; page <= numPages; page++) {
                    List<FlipV2> pageFlips2 = expectedPage(flips, time, page, pageSize, DISPLAY_NAME_1);
                    List<FlipV2> pageFlips1 = flipManager.getPageFlips(page, pageSize);
                    assertFlipListsEqual(pageFlips1, pageFlips2);
                }
            }
        }
    }

    @Test
    public void testMultipleAccounts() {

        // generate 6 months or random flips
        int now = (int) Instant.now().getEpochSecond();
        int sixMonthsAgo = (int) Instant.now().minus(365/ 2, ChronoUnit.DAYS).getEpochSecond();
        List<FlipV2> flips = generateFlipsBetween(sixMonthsAgo, now, 5_000, List.of(0, 1, 2));

        // create and populate the flip cache
        // MODIFIED CONSTRUCTOR CALL: Removed the first 'null' argument
        FlipManager flipManager = new FlipManager(new DoesNothingExecutorService(), new OkHttpClient.Builder().build());
        flipManager.setFlipsChangedCallback(() -> {});
        flipManager.displayNameToAccountId.putAll(displayNameToAccountId);
        flipManager.mergeFlips(flips, null); // Assuming this is still the intended way to populate for test
        verifyflipManagerStoredOrder(flipManager); // Renamed


        flips.sort(Comparator.comparing(FlipV2::getClosedTime).reversed().thenComparing(Comparator.comparing(FlipV2::getId).reversed()));

        // create list of test interval start times
        List<Integer> testTimes = Stream.generate(()-> randomIntBetween(sixMonthsAgo, now)).limit(100).collect(Collectors.toList());
        testTimes.add(0, 0); // add 0 which is ALL

        flipManager.setIntervalDisplayName(null);
        List<FlipV2> flips1 = flipManager.getPageFlips(1, flips.size());
        assertFlipListsEqual(flips1, flips);

        for (String name : Arrays.asList(DISPLAY_NAME_1, DISPLAY_NAME_2, DISPLAY_NAME_3, null)) {
            flipManager.setIntervalDisplayName(name);
            for (int time : testTimes) {
                flipManager.setIntervalStartTime(time);

                // check stats equal
                Stats stats1 = flipManager.getIntervalStats();
                Stats stats2 = expectedStats(flips, time, name);
                Assert.assertEquals(stats1, stats2);

                // check all flips equal
                List<FlipV2> allFlips1 = flipManager.getPageFlips(1, flips.size());
                List<FlipV2> allFlips2 = expectedPage(flips, time, 1, flips.size(), name);
                assertFlipListsEqual(allFlips1, allFlips2);

                // check paginated flips equal
                for (int pageSize : Arrays.asList(3, 20, 33, 50, 100)) {
                    int numPages = (stats1.flipsMade + pageSize -1) / pageSize; // Corrected numPages calculation
                    if (stats1.flipsMade == 0 && pageSize > 0) numPages = 1; // Handle case with 0 flips
                    for (int page = 1; page <= numPages; page++) {
                        List<FlipV2> pageFlips1 = flipManager.getPageFlips(page, pageSize);
                        List<FlipV2> pageFlips2 = expectedPage(flips, time, page, pageSize, name);
                        assertFlipListsEqual(pageFlips1, pageFlips2);
                    }
                }
            }
        }
    }

    // Renamed method to match convention and avoid potential conflicts
    public void verifyflipManagerStoredOrder(FlipManager flipManager) {
        for (int i =0; i < flipManager.weeks.size(); i++) {
            // Ensure weeks are sorted by weekStart
            if (i > 0) { // Add check for i > 0 before accessing i-1
                Assert.assertTrue("Weeks should be sorted by start time", flipManager.weeks.get(i-1).weekStart <= flipManager.weeks.get(i).weekStart);
            }
            FlipManager.WeekAggregate w = flipManager.weeks.get(i);
            for (List<FlipV2> flipsInAccount : w.accountIdToFlips.values()) { // Renamed variable for clarity
                for (int ii =1; ii < flipsInAccount.size(); ii++) {
                    Assert.assertTrue("Flips within a week and account should be sorted by close time", flipsInAccount.get(ii-1).getClosedTime() <= flipsInAccount.get(ii).getClosedTime());
                }
            }
        }
    }

    private List<FlipV2> expectedPage(List<FlipV2> flips, int time, int pageNumber, int pageSize, String displayName) {
        Integer accountId = displayName == null ? null : displayNameToAccountId.getOrDefault(displayName, -1);
        int toSkip = (pageNumber - 1) * pageSize;
        List<FlipV2> page = new ArrayList<>();
        for(FlipV2 f : flips) {
            if(f.getClosedTime() > time && (accountId == null || Objects.equals(accountId, f.getAccountId()))) { // Use Objects.equals
                if(toSkip > 0) {
                    toSkip -= 1;
                } else {
                    page.add(f);
                    if(page.size() == pageSize) {
                        break;
                    }
                }
            }
        }
        return page;
    }

    private void assertFlipListsEqual(List<FlipV2> f1, List<FlipV2> f2) {
        if(f1.size() != f2.size()) {
            Assert.fail("flips lists not equal length. Expected " + f2.size() + " but got " + f1.size());
        }
        for (int i=0; i < f1.size(); i++) {
            FlipV2 flip1 = f1.get(i);
            FlipV2 flip2 = f2.get(i);
            if (!Objects.equals(flip1, flip2)) { // Use Objects.equals for potentially null or complex objects
                Assert.fail("flips don't match at index " + i + ". Expected: " + flip2 + ", Got: " + flip1);
            }
        }
    }

    private Stats expectedStats(List<FlipV2> flips, int time, String displayName) {
        Integer accountId = displayName == null ? null : displayNameToAccountId.getOrDefault(displayName, -1);
        Stats stats = new Stats(0,0,0,0);
        for(FlipV2 f : flips) {
            if(f.getClosedTime() > time && (accountId == null || Objects.equals(accountId, f.getAccountId()))) { // Use Objects.equals
                stats.flipsMade += 1;
                stats.gross += f.getSpent();
                stats.profit += f.getProfit();
                // Assuming taxPaid is correctly set in FlipV2 if you need to sum it here too
                // stats.taxPaid += f.getTaxPaid();
            }
        }
        return stats;
    }

    private List<FlipV2> generateFlipsBetween(int start, int end, int number, List<Integer> accountIds) {
        List<FlipV2> flips = new ArrayList<>();
        Random random = new Random(); // Create Random instance once
        for (int i =0; i< number; i++) {
            FlipV2 f = new FlipV2();
            f.setId(UUID.randomUUID());
            f.setAccountId(accountIds.get(random.nextInt(accountIds.size())));
            if(randomIntBetween(0, 1000, random) > 2) { // Pass random instance
                f.setClosedTime(randomIntBetween(start, end, random));
                f.setSpent(randomIntBetween(100, 1_000_000_000, random));
                f.setProfit(randomIntBetween(-2_000_000, 4_000_000, random));
                // itemID and itemName would also need to be set for a complete FlipV2 object
                f.setItemId(randomIntBetween(1, 20000, random)); // Example item ID
                f.setItemName("Test Item " + f.getItemId()); // Example item name
                f.setOpenedQuantity(randomIntBetween(1,1000,random));
                f.setClosedQuantity(f.getOpenedQuantity()); // Assuming fully closed for simplicity in test data
                f.setOpenedTime(f.getClosedTime() - randomIntBetween(60, 3600, random)); // Opened before closed
                f.setReceivedPostTax(f.getSpent() + f.getProfit()); // Simplified
                f.setTaxPaid(0); // Simplified for test data, or calculate if needed
                f.setClosed(true);
                flips.add(f);
            }
        }
        return flips;
    }

    // Overload randomIntBetween to accept a Random instance
    private int randomIntBetween(int min, int max, Random random) {
        if (min > max) { // Ensure min is not greater than max
            return min; // Or throw an IllegalArgumentException
        }
        return random.nextInt((max - min) + 1) + min;
    }
    // Keep the old one if it's used elsewhere without a Random instance explicitly
    private int randomIntBetween(int min, int max) {
        return randomIntBetween(min, max, new Random());
    }
}

