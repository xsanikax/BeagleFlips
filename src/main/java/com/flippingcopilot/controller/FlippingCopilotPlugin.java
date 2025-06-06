package com.flippingcopilot.controller; // Ensure this package matches your project structure

import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.*;
import com.flippingcopilot.ui.graph.PriceGraphController; // Assuming this is correct based on your structure
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
		name = "Flipping Copilot (Offline)", // Changed name slightly for clarity during dev
		description = "Your AI assistant for trading - Standalone"
)
public class FlippingCopilotPlugin extends Plugin {

	@Inject
	private FlippingCopilotConfig config;
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ScheduledExecutorService executorService;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private Gson gson;
	@Inject
	private GrandExchange grandExchange;
	@Inject
	private GrandExchangeCollectHandler grandExchangeCollectHandler;
	@Inject
	private GrandExchangeOfferEventHandler offerEventHandler;
	// @Inject // PHASE 1: Comment out ApiRequestHandler as we are removing direct backend calls
	// private ApiRequestHandler apiRequestHandler;
	@Inject
	private AccountStatusManager accountStatusManager;
	@Inject
	private SuggestionController suggestionController;
	@Inject
	private SuggestionManager suggestionManager;
	@Inject
	private WebHookController webHookController; // Keep for now, might be repurposed or removed later
	@Inject
	private KeybindHandler keybindHandler;
	// @Inject // PHASE 1: Comment out CopilotLoginController
	// private CopilotLoginController copilotLoginController;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private LoginResponseManager loginResponseManager; // Keep for now, MainPanel might still check it. We'll force MainPanel to show logged-in view.
	@Inject
	private HighlightController highlightController;
	@Inject
	private GameUiChangesHandler gameUiChangesHandler;
	@Inject
	private OsrsLoginManager osrsLoginManager;
	@Inject
	private FlipManager flipManager;
	@Inject
	private SessionManager sessionManager;
	@Inject
	private GrandExchangeUncollectedManager grandExchangeUncollectedManager;
	@Inject
	private TransactionManger transactionManger;
	@Inject
	private OfferManager offerManager;
	@Inject
	private PriceGraphOpener priceGraphOpener;

	private MainPanel mainPanel;
	private StatsPanelV2 statsPanel; // Assuming this is the correct name from your files
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception {
		Persistance.setUp(gson); // Local persistence setup, this is good.

		mainPanel = injector.getInstance(MainPanel.class);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon-small.png"); // Ensure icon-small.png is in resources
		navButton = NavigationButton.builder()
				.tooltip("Flipping Copilot (Offline)")
				.icon(icon)
				.priority(3)
				.panel(mainPanel)
				.build();
		clientToolbar.addNavigation(navButton);

		// PHASE 1: Comment out direct controller setup that depends on login state or specific UI panels that might not exist without login
		// copilotLoginController.setLoginPanel(mainPanel.loginPanel);
		// copilotLoginController.setMainPanel(mainPanel);
		suggestionController.setCopilotPanel(mainPanel.copilotPanel); // Assumes mainPanel.copilotPanel exists
		suggestionController.setMainPanel(mainPanel);
		// suggestionController.setLoginPanel(mainPanel.loginPanel); // loginPanel might be removed/changed in MainPanel
		suggestionController.setSuggestionPanel(mainPanel.copilotPanel.suggestionPanel); // Assumes these exist
		grandExchangeCollectHandler.setSuggestionPanel(mainPanel.copilotPanel.suggestionPanel); // Assumes these exist
		if (mainPanel.copilotPanel != null) { // Defensive check
			statsPanel = mainPanel.copilotPanel.statsPanel;
		}


		mainPanel.refresh(); // We will modify MainPanel to always show the main content view for Phase 1

		// PHASE 1: Comment out attempts to load flips or sync data based on Copilot login
       /*
       if(loginResponseManager.isLoggedIn()) {
          flipManager.loadFlipsAsync();
       }
       */

		// This part is for OSRS login, which is fine
		if(osrsLoginManager.getInvalidStateDisplayMessage() == null) {
			flipManager.setIntervalDisplayName(osrsLoginManager.getPlayerDisplayName());
			if (sessionManager.getCachedSessionData() != null) { // Add null check for safety
				flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
			}
		}

		// PHASE 1: Comment out the scheduled task that updates session stats and might trigger API calls
       /*
       executorService.scheduleAtFixedRate(() ->
          clientThread.invoke(() -> {
             boolean loginValid = osrsLoginManager.isValidLoginState();
             if (loginValid) {
                AccountStatus accStatus = accountStatusManager.getAccountStatus();
                boolean isFlipping = accStatus != null && accStatus.currentlyFlipping();
                long cashStack = accStatus == null ? 0 : accStatus.currentCashStack();
                if(sessionManager.updateSessionStats(isFlipping, cashStack)) {
                   // The refresh might eventually try to get new suggestions.
                   // For phase 1, ensure SuggestionController doesn't make API calls.
                   if (mainPanel.copilotPanel != null && mainPanel.copilotPanel.statsPanel != null) {
                       mainPanel.copilotPanel.statsPanel.refresh(false, loginResponseManager.isLoggedIn() && osrsLoginManager.isValidLoginState());
                   }
                }
             }
          })
       , 2000, 1000, TimeUnit.MILLISECONDS);
       */
		log.info("Flipping Copilot (Offline) started!");
	}

	@Override
	protected void shutDown() throws Exception {
		offerManager.saveAll(); // Local saving, fine
		highlightController.removeAll(); // UI, fine
		clientToolbar.removeNavigation(navButton); // UI, fine

		// PHASE 1: Comment out webhook sending based on Copilot login
       /*
       if(loginResponseManager.isLoggedIn()) {
          String displayName = osrsLoginManager.getLastDisplayName();
          if (sessionManager.getCachedSessionData() != null && displayName != null) { // Add null checks
            webHookController.sendMessage(flipManager.calculateStats(sessionManager.getCachedSessionData().startTime, displayName), sessionManager.getCachedSessionData(), displayName, false);
          }
       }
       */
		keybindHandler.unregister(); // Local, fine
		log.info("Flipping Copilot (Offline) stopped!");
	}

	@Provides
	public FlippingCopilotConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(FlippingCopilotConfig.class);
	}

	//---------------------------- Event Handlers ----------------------------//
	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
		offerEventHandler.onGrandExchangeOfferChanged(event);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		if (event.getContainerId() == InventoryID.INVENTORY.getId() && grandExchange.isOpen()) {
			suggestionManager.setSuggestionNeeded(true); // This is fine, local suggestion engine will handle it later
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		suggestionController.onGameTick(); // Will need to modify SuggestionController to not call API
		offerEventHandler.onGameTick();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		int slot = grandExchange.getOpenSlot();
		grandExchangeCollectHandler.handleCollect(event, slot);
		gameUiChangesHandler.handleMenuOptionClicked(event);
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		priceGraphOpener.injectCopilotPriceGraphMenuEntry(event);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		gameUiChangesHandler.onWidgetLoaded(event);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event) {
		gameUiChangesHandler.onWidgetClosed(event);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		gameUiChangesHandler.onVarbitChanged(event);
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged event) {
		gameUiChangesHandler.onVarClientStrChanged(event);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
				sessionManager.reset();
				suggestionManager.reset(); // Fine, resets local state
				osrsLoginManager.reset();
				accountStatusManager.reset();
				grandExchangeUncollectedManager.reset();
				if (statsPanel != null) { // Add null check
					statsPanel.refresh(true, loginResponseManager.isLoggedIn() && osrsLoginManager.isValidLoginState());
				}
				mainPanel.refresh(); // Fine
				break;
			case LOGGING_IN:
			case HOPPING:
			case CONNECTION_LOST:
				osrsLoginManager.setLastLoginTick(client.getTickCount());
				break;
			case LOGGED_IN:
				clientThread.invokeLater(() -> {
					if (client.getGameState() != GameState.LOGGED_IN) {
						return true; // Return true to allow rescheduling if needed.
					}
					final String name = osrsLoginManager.getPlayerDisplayName();
					if(name == null) {
						return false; // Not ready yet, don't reschedule.
					}
					if (statsPanel != null) { // Add null check
						statsPanel.resetIntervalDropdownToSession();
					}
					flipManager.setIntervalDisplayName(name);
					if (sessionManager.getCachedSessionData() != null) { // Add null check
						flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
					}
					if (statsPanel != null) { // Add null check
						statsPanel.refresh(true, loginResponseManager.isLoggedIn()  && osrsLoginManager.isValidLoginState());
					}
					mainPanel.refresh(); // Fine

					// PHASE 1: Comment out transaction sync based on Copilot login
                /*
                if(loginResponseManager.isLoggedIn()) {
                   transactionManger.scheduleSyncIn(0, name);
                }
                */
					return true;
				});
		}
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event) {
		gameUiChangesHandler.onVarClientIntChanged(event);
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown clientShutdownEvent) {
		log.debug("client shutdown event received");
		offerManager.saveAll(); // Local, fine

		// PHASE 1: Comment out webhook sending based on Copilot login
       /*
       if(loginResponseManager.isLoggedIn()) {
          String displayName = osrsLoginManager.getLastDisplayName();
          if (sessionManager.getCachedSessionData() != null && displayName != null) { // Add null checks
            webHookController.sendMessage(flipManager.calculateStats(sessionManager.getCachedSessionData().startTime, displayName), sessionManager.getCachedSessionData(), displayName, false);
          }
       }
       */
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (event.getGroup().equals("flippingcopilot")) { // This should match your @ConfigGroup name
			log.debug("copilot config changed event received");
			if (event.getKey().equals("profitAmountColor") || event.getKey().equals("lossAmountColor")) {
				if (mainPanel.copilotPanel != null && mainPanel.copilotPanel.statsPanel != null) { // Add null checks
					mainPanel.copilotPanel.statsPanel.refresh(true, loginResponseManager.isLoggedIn() && osrsLoginManager.isValidLoginState());
				}
			}
			if (event.getKey().equals("suggestionHighlights")) {
				clientThread.invokeLater(highlightController::redraw);
			}
		}
	}
}
