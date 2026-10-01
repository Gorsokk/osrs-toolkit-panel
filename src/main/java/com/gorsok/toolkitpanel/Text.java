package com.gorsok.toolkitpanel;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Tiny EN/FR dictionary + number formatting. */
final class Text
{
	private static final Map<String, String[]> T = new HashMap<>();

	static
	{
		put("connected", "Connected to OSRS Toolkit %s", "Connecté à OSRS Toolkit %s");
		put("offline", "OSRS Toolkit app not detected", "Appli OSRS Toolkit non détectée");
		put("offline_hint", "This panel shows the free OSRS Toolkit desktop app (bond goal, flip scanner, alerts) when it runs on this computer. "
			+ "The GE price helper and chart work without it: enable them in the plugin settings.",
			"Ce panneau affiche l'appli gratuite OSRS Toolkit (objectif bond, scanner de flips, alertes) quand elle tourne sur ce PC. "
			+ "L'aide GE et le graphique fonctionnent sans elle : active-les dans les réglages du plugin.");
		put("get_toolkit", "Get OSRS Toolkit", "Obtenir OSRS Toolkit");
		put("bond", "Bond goal", "Objectif bond");
		put("bond_line", "%s / %s gp", "%s / %s gp");
		put("bond_none", "Waiting for a bank export...", "En attente d'un export de banque...");
		put("flips", "Top flips (gp/h)", "Meilleurs flips (gp/h)");
		put("flip_line", "%s → %s  ·  %s%%  ·  %s/h", "%s → %s  ·  %s %%  ·  %s/h");
		put("alchs", "High Alch", "High Alch");
		put("alch_line", "buy ≤ %s  ·  +%s/cast", "achat ≤ %s  ·  +%s/cast");
		put("scan", "Scan: %s", "Scan : %s");
		put("no_scan", "No scan yet: the Toolkit scans every few minutes.", "Pas encore de scan : le Toolkit scanne toutes les quelques minutes.");
		put("alerts", "Recent alerts", "Alertes récentes");
		put("no_alerts", "No alerts yet.", "Aucune alerte pour l'instant.");
		put("dashboard", "Open the dashboard", "Ouvrir le tableau de bord");
		put("volatile", "volatile", "volatil");
		put("ge_title", "OSRS Toolkit", "OSRS Toolkit");
		put("ge_instant_buy", "Instant buy", "Achat instant.");
		put("ge_instant_sell", "Instant sell", "Vente instant.");
		put("ge_avg1h", "1h avg", "Moy. 1 h");
		put("ge_margin", "Margin (tax)", "Marge (taxe)");
		put("ge_limit", "Buy limit", "Limite");
		put("ge_vol", "Volume 1h", "Volume 1 h");
		put("ge_warn_over", "Above instant buy by %s", "Au-dessus de l'achat de %s");
		put("ge_warn_under", "Below instant sell by %s", "Sous la vente de %s");
		put("ge_loading", "Loading prices...", "Chargement des prix...");
		put("ge_flip", "In your top flips", "Dans tes meilleurs flips");
		put("ge_last_sell", "Last sale", "Dernière vente");
		put("ge_last_buy", "Last buy", "Dernier achat");
		put("ge_ago", "%s (%s ago)", "%s (il y a %s)");
		put("ge_bid", "Suggested bid", "Offre conseillée");
		put("ge_bid_max", "Max still profitable", "Max rentable");
		put("ge_ask", "Suggested sell", "Vente conseillée");
		put("ge_no_margin", "No margin left right now", "Plus de marge en ce moment");
		put("ge_stale", "Few trades: prices %s old", "Peu d'échanges : prix vieux de %s");
		put("ge_bid_room", "Room to bid higher: +%s", "Marge pour monter : +%s");
		put("ge_over_max", "Above the profitable max", "Au-dessus du max rentable");
		put("ge_price_unreadable", "Typed price unreadable", "Prix saisi illisible");
		put("ge_ask_high", "Above the last buy: may be slow", "Au-dessus du dernier achat : peut être lent");
		put("chart_loading", "Loading chart...", "Chargement du graphique...");
		put("chart_nodata", "No price history", "Pas d'historique de prix");
		put("chart_now", "now", "maint.");
		put("chart_avg", "avg", "moy.");
		put("chart_you", "you", "toi");
		put("gauge_low", "Dip", "Creux");
		put("gauge_mid", "Normal", "Normal");
		put("gauge_high", "Peak", "Pic");
		put("gauge_title", "Price: %s (%d%%) over %s", "Prix : %s (%d %%) sur %s");
		put("gauge_unknown", "Price position unknown", "Position du prix inconnue");
		put("gauge_hint_buy_high", "pricey to buy", "cher pour acheter");
		put("gauge_hint_sell_low", "low to sell", "bas pour vendre");
		put("chat_prefix", "Toolkit", "Toolkit");
	}

	private static void put(String k, String en, String fr)
	{
		T.put(k, new String[]{en, fr});
	}

	static boolean fr = false;

	static String t(String key, Object... args)
	{
		String[] v = T.get(key);
		String s = v == null ? key : v[fr ? 1 : 0];
		return args.length == 0 ? s : String.format(s, args);
	}

	/** 1234567 -> "1.23M", 12345 -> "12.3k", 950 -> "950". */
	static String gp(Long n)
	{
		if (n == null)
		{
			return "?";
		}
		long a = Math.abs(n);
		String s;
		if (a >= 1_000_000_000_000L)
		{
			s = trim(n / 1e12) + "T";
		}
		else if (a >= 1_000_000_000L)
		{
			s = trim(n / 1e9) + "B";
		}
		else if (a >= 1_000_000L)
		{
			s = trim(n / 1e6) + "M";
		}
		else if (a >= 10_000L)
		{
			s = trim(n / 1e3) + "k";
		}
		else
		{
			s = NumberFormat.getIntegerInstance(Locale.US).format(n);
			if (fr)
			{
				s = s.replace(',', ' ');   // plain space: the RuneScape font has no narrow no-break space
			}
		}
		return s;
	}

	static String gp(Integer n)
	{
		return n == null ? "?" : gp((long) n);
	}

	private static String trim(double v)
	{
		String s = String.format(Locale.US, Math.abs(v) >= 100 ? "%.0f" : Math.abs(v) >= 10 ? "%.1f" : "%.2f", v);
		if (s.contains("."))
		{
			s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return fr ? s.replace('.', ',') : s;
	}

	/** 75 -> "1m", 5000 -> "1h23m", 30 -> "30s". */
	static String ago(Long unixSeconds)
	{
		if (unixSeconds == null)
		{
			return "?";
		}
		long s = Math.max(0, System.currentTimeMillis() / 1000 - unixSeconds);
		if (s < 60)
		{
			return s + "s";
		}
		if (s < 3600)
		{
			return (s / 60) + "m";
		}
		return (s / 3600) + "h" + String.format("%02d", (s % 3600) / 60);
	}

	static String pct(Double d)
	{
		if (d == null)
		{
			return "?";
		}
		String s = String.format(Locale.US, "%.1f", d);
		return fr ? s.replace('.', ',') : s;
	}

	private Text()
	{
	}
}
