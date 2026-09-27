package com.gorsok.toolkitpanel;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(ToolkitPanelConfig.GROUP)
public interface ToolkitPanelConfig extends Config
{
	String GROUP = "osrstoolkitpanel";
	String WIKI_WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";

	enum Language
	{
		ENGLISH, FRANCAIS
	}

	@ConfigSection(name = "GE price helper", description = "Prices, chart and gauge in the Grand Exchange offer window", position = 10)
	String geSection = "ge";

	@ConfigSection(name = "OSRS Toolkit app", description = "Side panel fed by the optional OSRS Toolkit desktop app", position = 20)
	String toolkitSection = "toolkit";

	@ConfigItem(keyName = "language", name = "Language / Langue", description = "Plugin language", position = 0)
	default Language language()
	{
		return Language.ENGLISH;
	}

	// ------------------------------------------------------------------ GE helper (OSRS Wiki prices)
	@ConfigItem(keyName = "geOverlay", name = "Enable GE price helper",
		description = "In the GE offer window: last real trades, suggested / max profitable price, margin after tax, buy limit. "
			+ "Uses the OSRS Wiki real-time prices API. " + WIKI_WARNING,
		warning = WIKI_WARNING + ". Enable the GE price helper?",
		position = 1, section = geSection)
	default boolean geOverlay()
	{
		return false;
	}

	@ConfigItem(keyName = "geChart", name = "Price chart + gauge",
		description = "Price chart and dip / normal / peak gauge under the GE helper (needs the GE price helper)",
		position = 2, section = geSection)
	default boolean geChart()
	{
		return true;
	}

	@ConfigItem(keyName = "chartRange", name = "Chart range", description = "24 hours, 7 days or 30 days", position = 3, section = geSection)
	default ChartRange chartRange()
	{
		return ChartRange.WEEK;
	}

	@ConfigItem(keyName = "minMarginPct", name = "Min. margin %",
		description = "The highest buy price suggested still leaves at least this margin after tax", position = 4, section = geSection)
	@Range(min = 0, max = 50)
	default int minMarginPct()
	{
		return 2;
	}

	// ------------------------------------------------------------------ OSRS Toolkit app (on this computer)
	@ConfigItem(keyName = "port", name = "Toolkit port",
		description = "Port of the OSRS Toolkit app on this computer (127.0.0.1). Default 8765.", position = 1, section = toolkitSection)
	@Range(min = 1, max = 65535)
	default int port()
	{
		return 8765;
	}

	@ConfigItem(keyName = "refreshSeconds", name = "Refresh (seconds)", description = "How often the panel reads the Toolkit app",
		position = 2, section = toolkitSection)
	@Range(min = 5, max = 120)
	default int refreshSeconds()
	{
		return 10;
	}

	@ConfigItem(keyName = "bondInfobox", name = "Bond infobox", description = "Show your progress toward a bond as an infobox",
		position = 3, section = toolkitSection)
	default boolean bondInfobox()
	{
		return true;
	}

	@ConfigItem(keyName = "chatAlerts", name = "Alerts in chat", description = "Post new Toolkit alerts in the game chat",
		position = 4, section = toolkitSection)
	default boolean chatAlerts()
	{
		return true;
	}

	@ConfigItem(keyName = "chatImportantOnly", name = "Only important alerts", description = "In chat, only the alerts the Toolkit marks as important",
		position = 5, section = toolkitSection)
	default boolean chatImportantOnly()
	{
		return true;
	}
}
