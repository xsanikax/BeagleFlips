package com.flippingcopilot.ui; // Ensure this package matches your project structure

// PHASE 1: Removed CopilotLoginController import as we are bypassing its direct use here for now
// import com.flippingcopilot.controller.CopilotLoginController;
import com.flippingcopilot.model.LoginResponseManager; // Still needed if copilotPanel relies on it, but we'll force the view
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

import static com.flippingcopilot.ui.UIUtilities.buildButton; // Assuming UIUtilities is in this package or imported

@Singleton
public class MainPanel extends PluginPanel {

    // dependencies
    public final LoginPanel loginPanel; // Keep for now, but it won't be shown by default
    public final CopilotPanel copilotPanel;
    private final LoginResponseManager loginResponseManager;
    // PHASE 1: Comment out CopilotLoginController as we are not using its logout directly from here for now
    // private final CopilotLoginController copilotLoginController;

    // PHASE 1: We'll manage the view state directly for now to always show CopilotPanel
    // private Boolean isLoggedInView;

    @Inject
    public MainPanel(CopilotPanel copilotPanel,
                     LoginPanel loginPanel,
                     LoginResponseManager loginResponseManager
                     // PHASE 1: Remove CopilotLoginController from constructor
            /* CopilotLoginController copilotLoginController */) {
        super(false); // false means it's not wrapped in a scroll pane by default, which is common for main plugin panels
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        this.loginResponseManager = loginResponseManager;
        this.copilotPanel = copilotPanel;
        this.loginPanel = loginPanel;
        // this.copilotLoginController = copilotLoginController; // PHASE 1

        // PHASE 1: Directly render the main copilot view on initialization
        renderLoggedInView(); // Or a more direct way to add copilotPanel
    }

    public void refresh() {
        if(!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        // PHASE 1: Always ensure the main content is visible.
        // The logic for switching views is removed for now.
        // We'll just ensure copilotPanel refreshes if it's the active one.
        if (copilotPanel != null && copilotPanel.isShowing()) {
            copilotPanel.refresh();
        } else if (loginPanel != null && loginPanel.isShowing()){
            // This case should ideally not happen if we always show CopilotPanel
            loginPanel.refresh();
        } else {
            // Fallback or initial setup if needed
            removeAll();
            add(constructTopBar(true), BorderLayout.NORTH); // Assume "logged in" for top bar
            add(copilotPanel, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }

    // This method might not be called if we always show copilotPanel.
    // Kept for structure, but refresh() is now simpler.
    public void renderLoggedOutView() {
        removeAll();
        add(constructTopBar(false), BorderLayout.NORTH);
        // loginPanel.showLoginErrorMessage(""); // loginPanel is not being shown by default
        // add(loginPanel, BorderLayout.CENTER); // PHASE 1: Don't add loginPanel
        // Instead, or for testing, show a placeholder or the CopilotPanel directly
        add(copilotPanel, BorderLayout.CENTER); // Forcing copilot panel
        revalidate();
        repaint();
        // isLoggedInView = false; // PHASE 1: isLoggedInView flag not used for switching now
    }

    public void renderLoggedInView() {
        removeAll();
        add(constructTopBar(true), BorderLayout.NORTH); // True for "logged in" top bar (e.g. show logout)
        add(copilotPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        // isLoggedInView = true; // PHASE 1: isLoggedInView flag not used for switching now
    }

    private JPanel constructTopBar(boolean isLoggedIn) {
        JPanel container = new JPanel();
        container.setBackground(ColorScheme.DARK_GRAY_COLOR);
        container.setLayout(new BorderLayout());
        JPanel topBar = new JPanel();
        topBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        // PHASE 1: Forcing top bar to 3 columns as if logged out, or simplify
        // int columns = isLoggedIn ? 4 : 3;
        int columns = 2; // Just Discord and Website for now
        topBar.setLayout(new GridLayout(1, columns, 5, 0)); // Added some hgap

        JLabel discord = buildTopBarUriButton(UIUtilities.discordIcon,
                "Flipping Copilot Discord",
                "https://discord.gg/UyQxA4QJAq");
        topBar.add(discord);

        JLabel website = buildTopBarUriButton(UIUtilities.internetIcon,
                "Flipping Copilot website",
                "https://flippingcopilot.com");
        topBar.add(website);

        // PHASE 1: Logout button is removed as there's no login session to your service
        /*
        if (isLoggedIn) {
            BufferedImage icon = ImageUtil.loadImageResource(getClass(), UIUtilities.logoutIcon);
            JLabel logout = buildButton(icon, "Log out", () -> {
                // copilotLoginController.onLogout(); // PHASE 1: This controller might be removed
                // renderLoggedOutView(); // PHASE 1: We always show main view
                // For now, a logout button in offline mode might clear local data or reset views
                JOptionPane.showMessageDialog(this, "Logout clicked (Offline Mode) - implement local data clearing if needed.");
            });
            topBar.add(logout);
        }
        */

        container.add(topBar, BorderLayout.CENTER); // Centering the buttons a bit more
        container.setBorder(new EmptyBorder(3, 0, 10, 0));
        return container;
    }

    private JLabel buildTopBarUriButton(String iconPath, String tooltip, String uriString) {
        // Ensure UIUtilities is accessible, or move this helper method if it's only used here.
        // For now, assuming UIUtilities.discordIcon etc. are static final strings for image paths.
        BufferedImage icon = ImageUtil.loadImageResource(UIUtilities.class, iconPath); // Load relative to UIUtilities
        return buildButton(icon, tooltip, () -> {
            LinkBrowser.browse(uriString);
        });
    }
}
