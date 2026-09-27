package com.gorsok.toolkitpanel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** Small price box shown while you set up a GE offer. Alt-drag to move it. */
class GeHelperOverlay extends OverlayPanel
{
	// Raw ids (stable for years) so the plugin doesn't depend on renamed API constants.
	private static final int GE_GROUP = 465;            // Grand Exchange interface
	private static final int VARP_GE_ITEM = 1151;       // item being set up (-1 = none)
	private static final int VARBIT_QUANTITY = 4396;
	private static final int VARBIT_TYPE = 4397;        // 0 = buy, 1 = sell
	private static final int VARBIT_PRICE = 4398;
	private static final int VARBIT_SELECTED_SLOT = 4439;  // 1-8 while looking at an existing offer, 0 otherwise

	private static final Color GOLD = new Color(0xE0B64B);
	private static final Color GOOD = new Color(0x4CAF7D);
	private static final Color BAD = new Color(0xFF6B6B);

	private final Client client;
	private final ToolkitPanelPlugin plugin;
	private final ToolkitPanelConfig config;
	private final PriceChartComponent chart = new PriceChartComponent();

	@Inject
	GeHelperOverlay(Client client, ToolkitPanelPlugin plugin, ToolkitPanelConfig config)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(230, 0));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.geOverlay())
		{
			return null;
		}
		Widget ge = client.getWidget(GE_GROUP, 0);
		if (ge == null || ge.isHidden())
		{
			return null;
		}
		// 1) setting up a new offer
		int itemId = client.getVarpValue(VARP_GE_ITEM);
		boolean buy = client.getVarbitValue(VARBIT_TYPE) == 0;
		int price = client.getVarbitValue(VARBIT_PRICE);
		int qty = client.getVarbitValue(VARBIT_QUANTITY);
		// 2) looking at an offer already placed ("Offer status")
		int slot = client.getVarbitValue(VARBIT_SELECTED_SLOT);
		if (itemId <= 0 && slot >= 1 && slot <= 8)
		{
			GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
			GrandExchangeOffer o = offers != null && offers.length >= slot ? offers[slot - 1] : null;
			if (o != null && o.getItemId() > 0 && o.getState() != GrandExchangeOfferState.EMPTY)
			{
				itemId = o.getItemId();
				GrandExchangeOfferState st = o.getState();
				buy = st == GrandExchangeOfferState.BUYING || st == GrandExchangeOfferState.BOUGHT
					|| st == GrandExchangeOfferState.CANCELLED_BUY;
				price = o.getPrice();
				qty = Math.max(1, o.getTotalQuantity() - o.getQuantitySold());
			}
		}
		if (itemId <= 0)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder().text(Text.t("ge_title")).color(GOLD).build());
		WikiPrices.Price p = plugin.getWikiPrices().get(itemId);
		if (p == null)
		{
			line(Text.t("ge_loading"), "", Color.LIGHT_GRAY);
			return super.render(g);
		}
		// The last REAL trades: what a seller just accepted (= what the top buyers pay) and what a buyer just paid.
		line(Text.t("ge_last_sell"), Text.t("ge_ago", Text.gp(p.instantSell), Text.ago(p.sellTime)), Color.WHITE);
		line(Text.t("ge_last_buy"), Text.t("ge_ago", Text.gp(p.instantBuy), Text.ago(p.buyTime)), Color.WHITE);
		if (p.avgBuy1h != null || p.avgSell1h != null)
		{
			line(Text.t("ge_avg1h"), Text.gp(p.avgSell1h) + " / " + Text.gp(p.avgBuy1h), Color.LIGHT_GRAY);
		}
		if (p.limit != null)
		{
			line(Text.t("ge_limit"), Text.gp(p.limit) + " / 4h", Color.LIGHT_GRAY);
		}
		if (p.volume1h != null)
		{
			line(Text.t("ge_vol"), Text.gp(p.volume1h), Color.LIGHT_GRAY);
		}
		Long oldest = p.sellTime == null || p.buyTime == null ? null : Math.min(p.sellTime, p.buyTime);
		if (oldest != null && System.currentTimeMillis() / 1000 - oldest > 15 * 60)
		{
			warn(Text.t("ge_stale", Text.ago(oldest)), GOLD);
		}

		if (p.instantBuy != null && p.instantSell != null)
		{
			long low = p.instantSell, high = p.instantBuy;
			if (buy)
			{
				// Resale price, prudent: never above the last instant buy nor the 1h average of instant buys.
				long resale = p.avgBuy1h != null ? Math.min(high, p.avgBuy1h) : high;
				long net = WikiPrices.netSell(resale);
				long maxBid = (long) Math.floor(net / (1 + config.minMarginPct() / 100.0));
				long bid = low + WikiPrices.step(low);   // just above the last accepted price: first in line
				if (bid <= maxBid)
				{
					line(Text.t("ge_bid"), Text.gp(bid) + "  (+" + Text.gp(net - bid) + "/u)", GOOD);
					line(Text.t("ge_bid_max"), Text.gp(maxBid), GOOD);
				}
				else
				{
					warn(Text.t("ge_no_margin"), BAD);
					if (maxBid > 0)
					{
						line(Text.t("ge_bid_max"), Text.gp(maxBid), BAD);
					}
				}
				if (price > 0)
				{
					if (price > high)
					{
						warn(Text.t("ge_warn_over", Text.gp((long) (price - high) * Math.max(qty, 1))), BAD);
					}
					else if (price > maxBid)
					{
						warn(Text.t("ge_over_max"), BAD);
					}
					else if (price < bid && bid <= maxBid)
					{
						// Behind the queue while there is still margin: this is where bidding higher pays.
						warn(Text.t("ge_bid_room", Text.gp(maxBid - price)), GOLD);
					}
				}
			}
			else
			{
				long ask = Math.max(low + 1, high - WikiPrices.step(high));   // just under the last instant buy
				line(Text.t("ge_ask"), Text.gp(ask) + "  (" + Text.gp(WikiPrices.netSell(ask)) + " net)", GOOD);
				if (price > 0 && price < low)
				{
					warn(Text.t("ge_warn_under", Text.gp((long) (low - price) * Math.max(qty, 1))), BAD);
				}
				else if (price > high)
				{
					warn(Text.t("ge_ask_high"), GOLD);
				}
			}
		}

		if (config.geChart())
		{
			ChartRange range = config.chartRange();
			chart.range = range;
			chart.points = plugin.getPriceHistory().get(itemId, range);
			chart.lastBuy = p.instantBuy;
			chart.lastSell = p.instantSell;
			chart.offer = price > 0 ? price : null;
			chart.buying = buy;
			chart.alchFloor = p.highAlch != null && p.natureRune != null && p.highAlch - p.natureRune > 0
				? (long) (p.highAlch - p.natureRune) : null;
			panelComponent.getChildren().add(chart);
		}

		final int shownId = itemId;
		ToolkitSnapshot s = plugin.getSnapshot();
		if (s != null && s.flips.stream().anyMatch(r -> r.id == shownId))
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("★ " + Text.t("ge_flip")).color(GOOD).build());
		}
		return super.render(g);
	}

	private void line(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(rightColor).build());
	}

	private void warn(String text, Color color)
	{
		panelComponent.getChildren().add(TitleComponent.builder().text("⚠ " + text).color(color).build());
	}
}
