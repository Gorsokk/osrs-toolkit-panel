package com.gorsok.toolkitpanel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Price history for the GE chart (OSRS Wiki /timeseries). Fetched on demand, cached 5 minutes per item and range.
 * Prices are {@code Long}: they can be above 2,147,483,647 gp, so no sum or average may be done on 32-bit numbers.
 */
@Slf4j
class PriceHistory
{
	static class Point
	{
		final long ts;
		final Long high, low;   // average instant-buy / instant-sell price in that step (may be null)

		Point(long ts, Long high, Long low)
		{
			this.ts = ts;
			this.high = high;
			this.low = low;
		}

		Double mid()
		{
			if (high != null && low != null)
			{
				return ((double) high + low) / 2.0;   // never an int addition: two 1.5B prices would wrap around
			}
			if (high != null)
			{
				return (double) high;
			}
			if (low != null)
			{
				return (double) low;
			}
			return null;   // a step without any trade (written out: a nested ?: would unbox this null and throw)
		}
	}

	private static final String API = "https://prices.runescape.wiki/api/v1/osrs/timeseries";
	private static final long TTL_MS = 5 * 60_000L;

	private static class Entry
	{
		volatile List<Point> points;
		volatile long fetchedAt;
		volatile boolean loading;
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final Map<String, Entry> cache = new ConcurrentHashMap<>();

	PriceHistory(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	/** Non-blocking: cached points for the range (oldest first), or null while the first download runs. */
	List<Point> get(int itemId, ChartRange range)
	{
		String key = itemId + ":" + range.timestep;
		Entry e = cache.computeIfAbsent(key, k -> new Entry());
		long now = System.currentTimeMillis();
		if (!e.loading && now - e.fetchedAt > TTL_MS)
		{
			e.loading = true;
			download(itemId, range, pts ->
			{
				if (pts != null)
				{
					e.points = pts;
				}
				e.fetchedAt = System.currentTimeMillis();   // also after a failure: retry in 5 min, not every frame
				e.loading = false;
			});
		}
		List<Point> pts = e.points;
		if (pts == null)
		{
			return null;
		}
		long from = now / 1000 - range.seconds;
		List<Point> out = new ArrayList<>();
		for (Point p : pts)
		{
			if (p.ts >= from)
			{
				out.add(p);
			}
		}
		return out;
	}

	private void download(int itemId, ChartRange range, java.util.function.Consumer<List<Point>> done)
	{
		Request req = new Request.Builder().url(API + "?timestep=" + range.timestep + "&id=" + itemId)
			.header("User-Agent", WikiPrices.USER_AGENT).build();
		http.newCall(req).enqueue(new okhttp3.Callback()
		{
			@Override
			public void onFailure(okhttp3.Call call, java.io.IOException ex)
			{
				log.debug("price history {}: {}", itemId, ex.getMessage());
				done.accept(null);
			}

			@Override
			public void onResponse(okhttp3.Call call, Response r)
			{
				List<Point> out = null;
				try (ResponseBody b = r.body())
				{
					if (r.isSuccessful() && b != null)
					{
						JsonObject o = gson.fromJson(b.string(), JsonObject.class);
						JsonArray arr = o.has("data") && o.get("data").isJsonArray() ? o.getAsJsonArray("data") : new JsonArray();
						out = new ArrayList<>(arr.size());
						for (JsonElement el : arr)
						{
							JsonObject p = el.getAsJsonObject();
							out.add(new Point(p.get("timestamp").getAsLong(),
								WikiPrices.num(p, "avgHighPrice"), WikiPrices.num(p, "avgLowPrice")));
						}
					}
				}
				catch (java.io.IOException | RuntimeException ex)
				{
					log.debug("price history {}: {}", itemId, ex.getMessage());
				}
				done.accept(out);
			}
		});
	}

	/** Where the current price sits in the range's usual prices (5th-95th percentile): 0 = bottom, 1 = top. */
	static Double position(List<Point> pts, double current)
	{
		List<Long> mids = new ArrayList<>();
		for (Point p : pts)
		{
			Double m = p.mid();
			if (m != null)
			{
				mids.add(Math.round(m));
			}
		}
		if (mids.size() < 5)
		{
			return null;
		}
		Collections.sort(mids);
		double lo = percentile(mids, 5), hi = percentile(mids, 95);
		if (hi - lo < 1e-9)
		{
			return null;
		}
		return Math.max(0, Math.min(1, (current - lo) / (hi - lo)));
	}

	/** Percentile of an already sorted list. */
	static double percentile(List<Long> sorted, double pct)
	{
		if (sorted.isEmpty())
		{
			return 0;
		}
		double k = (sorted.size() - 1) * pct / 100.0;
		int f = (int) Math.floor(k), c = Math.min(sorted.size() - 1, f + 1);
		double a = sorted.get(f), b = sorted.get(c);
		return a + (b - a) * (k - f);
	}

	static Double average(List<Point> pts)
	{
		double sum = 0;
		int n = 0;
		for (Point p : pts)
		{
			Double m = p.mid();
			if (m != null)
			{
				sum += m;
				n++;
			}
		}
		return n == 0 ? null : sum / n;
	}
}
