package com.gorsok.toolkitpanel;

// OSRS Toolkit Panel: shows the local OSRS Toolkit app inside RuneLite.
//   - side panel: bond progress, top flips, High Alch picks, recent alerts
//   - infobox: % toward a bond
//   - game chat: new Toolkit alerts (important ones by default)
//   - GE offer window: instant prices, margin after tax, buy limit, warning when the price is off
// The side panel reads the optional OSRS Toolkit app on this computer (http://127.0.0.1:8765, GET only).
// The GE helper uses the OSRS Wiki prices API and is OFF by default (third-party server warning).
// Nothing about your account is ever sent anywhere.

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "OSRS Toolkit Panel",
	description = "GE price helper with price chart, plus bond progress, top flips and alerts from the OSRS Toolkit app",
	tags = {"grand exchange", "ge", "flipping", "bond", "prices", "toolkit"}
)
public class ToolkitPanelPlugin extends Plugin
{
	private static final int OLD_SCHOOL_BOND = 13190;

	@Inject private ToolkitPanelConfig config;
	@Inject private ClientToolbar clientToolbar;
	@Inject private OverlayManager overlayManager;
	@Inject private InfoBoxManager infoBoxManager;
	@Inject private ItemManager itemManager;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private ClientThread clientThread;
	@Inject private ScheduledExecutorService executor;
	@Inject private OkHttpClient okHttpClient;
	@Inject private Gson gson;
	@Inject private GeHelperOverlay geOverlay;

	private ToolkitClient toolkit;
	@Getter private WikiPrices wikiPrices;
	@Getter private PriceHistory priceHistory;
	@Getter private volatile ToolkitSnapshot snapshot;

	private ToolkitPanel panel;
	private NavigationButton navButton;
	private BondInfoBox bondBox;
	private ScheduledFuture<?> poller;
	private final Set<String> seenAlerts = new HashSet<>();
	private final java.util.concurrent.atomic.AtomicBoolean polling = new java.util.concurrent.atomic.AtomicBoolean();
	private boolean alertsSeeded;

	@Provides
	ToolkitPanelConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ToolkitPanelConfig.class);
	}

	@Override
	protected void startUp()
	{
		Text.fr = config.language() == ToolkitPanelConfig.Language.FRANCAIS;
		toolkit = new ToolkitClient(okHttpClient, gson);
		wikiPrices = new WikiPrices(okHttpClient, gson);
		priceHistory = new PriceHistory(okHttpClient, gson);
		snapshot = null;
		seenAlerts.clear();
		alertsSeeded = false;
		polling.set(false);

		panel = new ToolkitPanel(toolkit::dashboardUrl);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder().tooltip("OSRS Toolkit").icon(icon).priority(7).panel(panel).build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(geOverlay);
		schedule();
	}

	@Override
	protected void shutDown()
	{
		if (poller != null)
		{
			poller.cancel(false);
			poller = null;
		}
		overlayManager.remove(geOverlay);
		clientToolbar.removeNavigation(navButton);
		removeBondBox();
		panel = null;
		snapshot = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!ToolkitPanelConfig.GROUP.equals(e.getGroup()))
		{
			return;
		}
		Text.fr = config.language() == ToolkitPanelConfig.Language.FRANCAIS;
		schedule();   // new interval / port, and redraw right away
	}

	private void schedule()
	{
		if (poller != null)
		{
			poller.cancel(false);
		}
		poller = executor.scheduleWithFixedDelay(this::poll, 0, config.refreshSeconds(), TimeUnit.SECONDS);
	}

	/** RuneLite's executor: only starts the async requests, never waits on the network. */
	private void poll()
	{
		if (!polling.compareAndSet(false, true))
		{
			return;   // previous refresh still running
		}
		toolkit.fetch(config.port(), s ->
		{
			polling.set(false);
			snapshot = s;
			ToolkitPanel p = panel;
			if (p != null)
			{
				SwingUtilities.invokeLater(() -> p.showSnapshot(s));
			}
			clientThread.invokeLater(() -> updateBondBox(s));
			if (s.connected)
			{
				announceAlerts(s.alerts);
			}
		});
	}

	private void updateBondBox(ToolkitSnapshot s)
	{
		boolean want = config.bondInfobox() && s.connected && s.bondPct != null;
		if (!want)
		{
			removeBondBox();
			return;
		}
		if (bondBox == null)
		{
			bondBox = new BondInfoBox(itemManager.getImage(OLD_SCHOOL_BOND), this);
			infoBoxManager.addInfoBox(bondBox);
		}
		bondBox.setSnapshot(s);
	}

	private void removeBondBox()
	{
		if (bondBox != null)
		{
			infoBoxManager.removeInfoBox(bondBox);
			bondBox = null;
		}
	}

	/** Posts alerts that appeared since the last refresh. The first refresh only remembers what already exists. */
	private synchronized void announceAlerts(List<ToolkitSnapshot.Alert> newestFirst)
	{
		if (!alertsSeeded)
		{
			newestFirst.forEach(a -> seenAlerts.add(a.key()));
			alertsSeeded = true;
			return;
		}
		for (int i = newestFirst.size() - 1; i >= 0; i--)
		{
			ToolkitSnapshot.Alert a = newestFirst.get(i);
			if (!seenAlerts.add(a.key()))
			{
				continue;
			}
			if (!config.chatAlerts() || (config.chatImportantOnly() && !a.important) || a.title == null)
			{
				continue;
			}
			String msg = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT).append("[" + Text.t("chat_prefix") + "] ")
				.append(ChatColorType.NORMAL).append(a.title)
				.build();
			chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE).runeLiteFormattedMessage(msg).build());
		}
		if (seenAlerts.size() > 500)
		{
			seenAlerts.clear();
			newestFirst.forEach(a -> seenAlerts.add(a.key()));
		}
	}
}
