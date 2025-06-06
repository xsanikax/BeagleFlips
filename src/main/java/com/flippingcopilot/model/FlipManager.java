package com.flippingcopilot.model; // Ensure this package matches your project structure

// PHASE 1: ApiRequestHandler import might be removed if all its uses are commented out
// import com.flippingcopilot.controller.ApiRequestHandler;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient; // Keep if used by other non-backend tasks, or remove if not.

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class is essentially a cache of user flips that facilitates efficient access to the flips and statistics for
 * any time range and rs account(s) combination. Since after several years a (very) active user could have hundreds of
 * thousands of flips, it would be too slow to filter and re-calculate flips/statistics from scratch every time.
 * A bucketed aggregation strategy is used where we keep pre-computed weekly buckets of statistics and flips. For any
 * time range we can efficiently combine the weekly buckets and only have to re-calculate statistics for the partial
 * weeks on the boundaries of the time range. Have tested the UI experience with >100k flips.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FlipManager {

    private static final int WEEK_SECS = 7 * 24 * 60 * 60;

    // dependencies
    // PHASE 1: Comment out ApiRequestHandler
    // private final ApiRequestHandler api;
    private final ScheduledExecutorService executorService;
    private final OkHttpClient okHttpClient; // PHASE 1: Evaluate if still needed for other tasks. If not, remove.

    @Setter
    private Runnable flipsChangedCallback = () -> {};

    // state
    private String intervalDisplayName;
    private int intervalStartTime;
    private Stats intervalStats = new Stats();

    final Map<String, Integer> displayNameToAccountId = new HashMap<>();
    final Map<Integer, Map<Integer, FlipV2>> lastOpenFLipByItemId = new HashMap<>();
    final Map<UUID, Integer> existingCloseTimes = new HashMap<>();
    final List<WeekAggregate> weeks = new ArrayList<>(365*5);

    private int resetSeq = 0;
    public volatile boolean flipsLoaded = false; // PHASE 1: This will become true after local load or if no local data.

    public synchronized String getIntervalDisplayName() {
        return intervalDisplayName;
    }

    public synchronized List<String> getDisplayNameOptions() {
        // PHASE 1: This will be populated from local data or stay empty.
        return displayNameToAccountId.keySet().stream().sorted().collect(Collectors.toList());
    }

    public synchronized long estimateTransactionProfit(String displayName, Transaction t) {
        Integer accountId = displayNameToAccountId.get(displayName);
        if (accountId != null && lastOpenFLipByItemId.containsKey(accountId)) {
            FlipV2 flip = lastOpenFLipByItemId.get(accountId).get(t.getItemId());
            if(flip != null) {
                return flip.calculateProfit(t);
            }
        }
        return 0;
    }

    public synchronized void mergeFlips(List<FlipV2> flips, String displayName) {
        if(!flips.isEmpty() && displayName != null) {
            // PHASE 1: Account ID will need to be managed locally if we support multiple local "profiles"
            displayNameToAccountId.put(displayName, flips.get(0).getAccountId());
        }
        flips.forEach(this::mergeFlip_);
        flipsChangedCallback.run();
    }

    public synchronized Stats getIntervalStats() {
        return intervalStats.copy();
    }

    public synchronized Stats calculateStats(int startTime, String displayName) {
        if(displayName == null) {
            return calculateStatsAllAccounts(startTime);
        } else {
            return calculateStatsForAccount(startTime, displayNameToAccountId.getOrDefault(displayName, -1));
        }
    }

    public synchronized void setIntervalDisplayName(String displayName) {
        if (Objects.equals(displayName, intervalDisplayName)) {
            return;
        }
        if (displayName != null && !displayNameToAccountId.containsKey(displayName)) {
            // PHASE 1: For an offline mode, how account IDs are assigned/discovered might change.
            // For now, we can keep this, assuming a single user profile or a way to get a local accountId.
            displayNameToAccountId.put(displayName, -1); // Or a locally generated ID
        }
        intervalDisplayName = displayName;
        recalculateIntervalStats();
    }

    public synchronized void setIntervalStartTime(int startTime) {
        log.debug("time interval start set to: {}", Instant.ofEpochSecond(startTime));
        if (startTime == intervalStartTime) {
            return;
        }
        intervalStartTime = startTime;
        recalculateIntervalStats();
    }

    private void recalculateIntervalStats() {
        if(intervalDisplayName == null) {
            intervalStats = calculateStatsAllAccounts(intervalStartTime);
        } else {
            intervalStats = calculateStatsForAccount(intervalStartTime, displayNameToAccountId.getOrDefault(intervalDisplayName, -1));
        }
        log.debug("interval flips updated to {}, interval profit updated to {}", intervalStats.flipsMade, intervalStats.profit);
        flipsChangedCallback.run();
    }

    private Stats calculateStatsAllAccounts(int startTime) {
        Stats stats = new Stats();
        WeekAggregate w = getOrInitWeek(startTime);
        for (FlipV2 f : w.flipsAfter(startTime, false)) {
            stats.addFlip(f);
        }
        for(int i=w.pos+1; i < weeks.size(); i++) {
            stats.add(weeks.get(i).allStats);
        }
        return stats;
    }

    private Stats calculateStatsForAccount(int startTime, int accountId) {
        Stats stats = new Stats();
        WeekAggregate w = getOrInitWeek(startTime);
        for (FlipV2 f : w.flipsAfterForAccount(startTime, accountId)) {
            stats.addFlip(f);
        }
        for(int i=w.pos+1; i < weeks.size(); i++) {
            Stats accountStats = weeks.get(i).accountIdToStats.get(accountId);
            if (accountStats != null) { // Add null check
                stats.add(accountStats);
            }
        }
        return stats;
    }

    public synchronized List<FlipV2> getPageFlips(int page, int pageSize) {
        Integer accountId = intervalDisplayName == null ? null : displayNameToAccountId.getOrDefault(intervalDisplayName, -1);
        if (Objects.equals(accountId,-1) && intervalDisplayName != null) { // Check if it's specifically -1 and not just null intervalDisplayName
            return new ArrayList<>();
        }

        int toSkip = (page -1) * pageSize;
        WeekAggregate intervalWeek = getOrInitWeek(intervalStartTime);
        List<FlipV2> resultFlips = new ArrayList<>(pageSize);
        for(int i=weeks.size()-1; i >= intervalWeek.pos; i--) {
            if (weeks.get(i).weekEnd <= intervalStartTime || resultFlips.size() == pageSize) {
                break;
            }
            WeekAggregate w = weeks.get(i);
            List<FlipV2> weekFlips = accountId == null ? w.flipsAfter(intervalStartTime, true) : w.flipsAfterForAccount(intervalStartTime, accountId);
            int n = weekFlips.size();
            if (n > toSkip) {
                int end = n - toSkip;
                int start = Math.max(0, end - (pageSize - resultFlips.size()));
                for(int ii=end-1; ii >= start; ii--) {
                    resultFlips.add(weekFlips.get(ii));
                }
                toSkip = 0;
            } else {
                toSkip -= n;
            }
        }
        return resultFlips;
    }

    public void loadFlipsAsync() {
        // PHASE 1: Don't call the backend.
        // Later, this will load flips from local storage.
        // For now, we can just set flipsLoaded to true and call the callback.
        log.info("Offline Mode: Skipping backend call for loadFlipsAsync. Will load locally later.");
        executorService.execute(() -> { // Still use executor for consistency if callback expects it.
            synchronized (this) {
                // Simulate loading success for now.
                // In a real local version, you'd read from a file here.
                // For example: List<FlipV2> localFlips = Persistance.loadLocalFlips(osrsLoginManager.getPlayerDisplayName());
                // mergeFlips(localFlips, osrsLoginManager.getPlayerDisplayName());
                flipsLoaded = true; // Mark as loaded
            }
            flipsChangedCallback.run(); // Notify UI to refresh (will show empty if no local data)
        });
    }

    // PHASE 1: The private loadFlips(int seq) method that contained API calls is entirely removed.
    // If it's called from somewhere else (it shouldn't be, as loadFlipsAsync is the public entry),
    // those calls would need to be removed too.

    public synchronized void reset() {
        intervalDisplayName = null;
        intervalStartTime = 0;
        intervalStats = new Stats();
        displayNameToAccountId.clear();
        lastOpenFLipByItemId.clear();
        existingCloseTimes.clear();
        weeks.clear();
        flipsLoaded = false; // Reset this, will be set by local loading
        resetSeq += 1;
        log.info("FlipManager reset for offline mode.");
        // Potentially trigger a load of (empty) local data here or let UI refresh handle it
        flipsChangedCallback.run();
    }

    private void mergeFlip_(FlipV2 flip) {
        Integer existingCloseTime = existingCloseTimes.get(flip.getId());
        Integer intervalAccountId = intervalDisplayName == null ? null : displayNameToAccountId.getOrDefault(intervalDisplayName, -1);

        if(existingCloseTime != null) {
            WeekAggregate wa = getOrInitWeek(existingCloseTime);
            FlipV2 removed = wa.removeFlip(flip.getId(), existingCloseTime, flip.getAccountId());
            if (removed != null) { // Add null check
                if(removed.getClosedTime() >= intervalStartTime && (intervalAccountId == null || removed.getAccountId() == intervalAccountId)) {
                    intervalStats.subtractFlip(removed);
                }
            }
        }
        WeekAggregate wa = getOrInitWeek(flip.getClosedTime());
        wa.addFlip(flip);
        if(flip.getClosedTime() >= intervalStartTime && (intervalAccountId == null || flip.getAccountId() == intervalAccountId)) {
            intervalStats.addFlip(flip);
        }
        if(flip.getClosedQuantity() < flip.getOpenedQuantity()) {
            lastOpenFLipByItemId.computeIfAbsent(flip.getAccountId(), (k) -> new HashMap<>()).put(flip.getItemId(), flip);
        } else if (flip.isClosed()) {
            lastOpenFLipByItemId.computeIfAbsent(flip.getAccountId(), (k) -> new HashMap<>()).remove(flip.getItemId());
        }
        existingCloseTimes.put(flip.getId(), flip.getClosedTime());
    }

    private WeekAggregate getOrInitWeek(int closeTime) {
        int ws = closeTime - (closeTime % WEEK_SECS);
        int i = bisect(weeks.size(), (a) ->  Integer.compare(weeks.get(a).weekStart, ws));
        if (i >= 0){
            WeekAggregate w = weeks.get(i);
            w.pos = i;
            return w;
        }
        WeekAggregate wf = new WeekAggregate();
        wf.weekStart = ws;
        wf.weekEnd = ws + WEEK_SECS;
        wf.pos = -i-1;
        weeks.add(wf.pos, wf);
        return wf;
    }

    class WeekAggregate {
        int pos;
        int weekStart;
        int weekEnd;
        Stats allStats = new Stats();
        Map<Integer, Stats> accountIdToStats = new HashMap<>(20);
        Map<Integer, List<FlipV2>> accountIdToFlips = new HashMap<>(20);

        void addFlip(FlipV2 flip) {
            int accountId = flip.getAccountId();
            allStats.addFlip(flip);
            accountIdToStats.computeIfAbsent(accountId, (k) -> new Stats()).addFlip(flip);
            List<FlipV2> flips = accountIdToFlips.computeIfAbsent(accountId, (k) -> new ArrayList<>());
            int i = bisect(flips.size(), closedTimeCmp(flips, flip.getId(), flip.getClosedTime()));
            flips.add(-i -1, flip);
        }

        FlipV2 removeFlip(UUID id, int closeTime, int accountId) {
            List<FlipV2> flips = accountIdToFlips.get(accountId); // Use get, not computeIfAbsent
            if (flips == null) return null; // If no flips for account, nothing to remove

            int i = bisect(flips.size(), closedTimeCmp(flips, id, closeTime));
            if (i < 0 || i >= flips.size() || !flips.get(i).getId().equals(id)) { // Ensure found flip matches ID
                return null; // Flip not found or ID mismatch
            }
            FlipV2 flip = flips.get(i);
            allStats.subtractFlip(flip);
            flips.remove(i);
            Stats accStats = accountIdToStats.get(accountId);
            if (accStats != null) accStats.subtractFlip(flip); // Check if stats exist before subtracting
            return flip;
        }

        public List<FlipV2> flipsAfterForAccount(int time, int accountId) {
            if (weekEnd <= time) {
                return Collections.emptyList();
            }
            List<FlipV2> flips = accountIdToFlips.getOrDefault(accountId, Collections.emptyList()); // Use getOrDefault
            if (time <= weekStart) {
                return flips;
            }
            int cut = -bisect(flips.size(), closedTimeCmp(flips, FlipV2.MAX_UUID, time)) - 1;
            if (cut < 0 || cut > flips.size()) return Collections.emptyList(); // Bounds check for subList
            return flips.subList(cut, flips.size());
        }

        public List<FlipV2> flipsAfter(int time, boolean requireSorted) {
            if (weekEnd <= time) {
                return Collections.emptyList();
            }
            List<FlipV2> combinedFlips = new ArrayList<>(allStats.flipsMade);
            accountIdToFlips.keySet().forEach(i -> combinedFlips.addAll(flipsAfterForAccount(time, i)));
            if (requireSorted) {
                combinedFlips.sort(Comparator.comparing(FlipV2::getClosedTime).thenComparing(FlipV2::getId));
            }
            return combinedFlips;
        }

        @Override
        public String toString() {
            return String.format("WeekAggregate[start=%s, flips=%d]", Instant.ofEpochSecond(weekStart), allStats.flipsMade);
        }
    }

    private Function<Integer, Integer> closedTimeCmp(List<FlipV2> flips, UUID id, int time) {
        return (a) -> {
            int c = Integer.compare(flips.get(a).getClosedTime(), time);
            return c != 0 ? c : id.compareTo(flips.get(a).getId());
        };
    }

    private int bisect(int size, Function<Integer, Integer> cmpFunc) {
        int high = size -1;
        int low = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = cmpFunc.apply(mid);
            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid;
        }
        return -(low + 1);
    }
}
