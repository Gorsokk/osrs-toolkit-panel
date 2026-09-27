package com.gorsok.toolkitpanel;

import java.util.ArrayList;
import java.util.List;

/** What the panel shows, as read from the Toolkit. Immutable once handed to the UI. */
class ToolkitSnapshot
{
	boolean connected;
	int port;
	String version;
	String character;

	Long bondPrice;
	Long cash;
	Double bondPct;

	String scanTime;
	List<Row> flips = new ArrayList<>();
	List<Row> alchs = new ArrayList<>();
	List<Alert> alerts = new ArrayList<>();   // newest first

	static class Row
	{
		int id;
		String name;
		Long buy, sell, margin, gpPerHour, qty, alchValue, profitPerCast;
		Double roi;
		boolean volatile_;
	}

	static class Alert
	{
		String ts, category, title, body;
		boolean important;

		String key()
		{
			return ts + "|" + title;
		}
	}
}
