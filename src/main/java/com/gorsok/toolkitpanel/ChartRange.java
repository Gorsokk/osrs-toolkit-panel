package com.gorsok.toolkitpanel;

/** Range of the GE price chart. Public top-level enum: RuneLite's config proxy must be able to see it. */
public enum ChartRange
{
	DAY("5m", 24 * 3600L, "24h", "24 h"),
	WEEK("1h", 7 * 86400L, "7d", "7 j"),
	MONTH("6h", 30 * 86400L, "30d", "30 j");

	final String timestep;
	final long seconds;
	private final String en, fr;

	ChartRange(String timestep, long seconds, String en, String fr)
	{
		this.timestep = timestep;
		this.seconds = seconds;
		this.en = en;
		this.fr = fr;
	}

	String label()
	{
		return Text.fr ? fr : en;
	}

	@Override
	public String toString()
	{
		return en;
	}
}
