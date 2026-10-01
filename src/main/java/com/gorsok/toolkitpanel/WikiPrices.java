package com.gorsok.toolkitpanel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Prices for the GE helper, from the OSRS Wiki real-time prices API (third-party: only used when the user enables it).
 * Refreshed only while the GE is open, asynchronously; cached (latest 60 s, 1h averages 5 min, item data 24 h).
 *
 * Prices are {@code Long}: since Beyond Max Cash, GE prices can be above 2,147,483,647 gp.
 */
@Slf4j
class WikiPrices
{
	static final String API = "https://prices.runescape.wiki/api/v1/osrs";
	static final String USER_AGENT = "OSRS Toolkit Panel RuneLite plugin - github.com/Gorsokk/osrs-toolkit-panel";
	private static final int NATURE_RUNE = 561;

	static class Price
	{
		Long instantBuy, instantSell;   // last real instant-buy / instant-sell trades ("high" / "low")
		Long buyTime, sellTime;         // when they happened (unix seconds)
		Long avgBuy1h, avgSell1h;
		Long volume1h;
		Integer limit;
		Integer highAlch;
		Integer natureRune;

		/** Highest price known for this item, 0 when none. */
		long highestKnown()
		{
			long max = 0;
			for (Long v : new Long[]{instantBuy, instantSell, avgBuy1h, avgSell1h})
			{
				if (v != null)
				{
					max = Math.max(max, v);
				}
			}
			return max;
		}

		/**
		 * Can a price typed into a NEW GE offer be trusted? It comes from a game variable that can't hold prices above
		 * 2,147,483,647, so it can't when this item trades above that range, nor when the game gave no value (negative).
		 */
		boolean canReadTypedPrice(long typed)
		{
			return typed >= 0 && highestKnown() <= Integer.MAX_VALUE;
		}
	}

	private final OkHttpClient http;
	private final Gson gson;

	private volatile JsonObject latest, hour;
	private volatile Map<Integer, Integer> limits = new HashMap<>(), alchs = new HashMap<>();
	private volatile long latestAt, hourAt, mappingAt;
	private final AtomicBoolean latestBusy = new AtomicBoolean(), hourBusy = new AtomicBoolean(), mappingBusy = new AtomicBoolean();

	WikiPrices(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	/** Non-blocking: what is cached (maybe null), and starts a background refresh when stale. */
	Price get(int itemId)
	{
		refreshIfStale();
		JsonObject l = latest, h = hour;
		if (l == null)
		{
			return null;
		}
		Price p = new Price();
		JsonObject li = obj(l, String.valueOf(itemId));
		if (li != null)
		{
			p.instantBuy = num(li, "high");
			p.instantSell = num(li, "low");
			p.buyTime = num(li, "highTime");
			p.sellTime = num(li, "lowTime");
		}
		JsonObject hi = h == null ? null : obj(h, String.valueOf(itemId));
		if (hi != null)
		{
			p.avgBuy1h = num(hi, "avgHighPrice");
			p.avgSell1h = num(hi, "avgLowPrice");
			Long a = num(hi, "highPriceVolume"), b = num(hi, "lowPriceVolume");
			p.volume1h = (a == null ? 0L : a) + (b == null ? 0L : b);
		}
		p.limit = limits.get(itemId);
		p.highAlch = alchs.get(itemId);
		JsonObject nat = obj(l, String.valueOf(NATURE_RUNE));
		p.natureRune = nat == null ? null : intNum(nat, "high");
		return p;
	}

	private void refreshIfStale()
	{
		long now = System.currentTimeMillis();
		if (now - latestAt > 60_000 && latestBusy.compareAndSet(false, true))
		{
			fetch("/latest", raw ->
			{
				JsonObject d = data(raw);
				if (d != null)
				{
					latest = d;
				}
				latestAt = System.currentTimeMillis() - (d == null ? 30_000 : 0);   // failed: retry in ~30 s
				latestBusy.set(false);
			});
		}
		if (now - hourAt > 300_000 && hourBusy.compareAndSet(false, true))
		{
			fetch("/1h", raw ->
			{
				JsonObject d = data(raw);
				if (d != null)
				{
					hour = d;
				}
				hourAt = System.currentTimeMillis() - (d == null ? 270_000 : 0);
				hourBusy.set(false);
			});
		}
		if (now - mappingAt > 86_400_000 && mappingBusy.compareAndSet(false, true))
		{
			fetch("/mapping", raw ->
			{
				boolean ok = false;
				try
				{
					if (raw != null)
					{
						Map<Integer, Integer> lim = new HashMap<>(), ha = new HashMap<>();
						for (JsonElement e : gson.fromJson(raw, JsonArray.class))
						{
							JsonObject o = e.getAsJsonObject();
							Integer id = intNum(o, "id"), l = intNum(o, "limit"), alch = intNum(o, "highalch");
							if (id != null && l != null)
							{
								lim.put(id, l);
							}
							if (id != null && alch != null)
							{
								ha.put(id, alch);
							}
						}
						limits = lim;
						alchs = ha;
						ok = true;
					}
				}
				catch (RuntimeException e)
				{
					log.debug("Wiki mapping: {}", e.getMessage());
				}
				mappingAt = System.currentTimeMillis() - (ok ? 0 : 86_400_000 - 60_000);
				mappingBusy.set(false);
			});
		}
	}

	private void fetch(String path, Consumer<String> onBody)
	{
		Request req = new Request.Builder().url(API + path).header("User-Agent", USER_AGENT).build();
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Wiki prices {}: {}", path, e.getMessage());
				onBody.accept(null);
			}

			@Override
			public void onResponse(Call call, Response r)
			{
				String body = null;
				try (ResponseBody b = r.body())
				{
					if (r.isSuccessful() && b != null)
					{
						body = b.string();
					}
				}
				catch (IOException e)
				{
					log.debug("Wiki prices {}: {}", path, e.getMessage());
				}
				onBody.accept(body);
			}
		});
	}

	private JsonObject data(String raw)
	{
		try
		{
			return raw == null ? null : obj(gson.fromJson(raw, JsonObject.class), "data");
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static JsonObject obj(JsonObject o, String k)
	{
		return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
	}

	/** A JSON number as a Long (never narrowed to 32 bits), or null when missing, null or not a number. */
	static Long num(JsonObject o, String k)
	{
		try
		{
			return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsLong() : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** For small values only (item ids, buy limits, alch values): null instead of a wrapped-around number. */
	static Integer intNum(JsonObject o, String k)
	{
		Long v = num(o, k);
		return v == null || v > Integer.MAX_VALUE || v < Integer.MIN_VALUE ? null : Integer.valueOf(v.intValue());
	}

	/** a * b, stuck at Long.MAX_VALUE / MIN_VALUE instead of wrapping around. */
	static long saturatingMultiply(long a, long b)
	{
		try
		{
			return Math.multiplyExact(a, b);
		}
		catch (ArithmeticException e)
		{
			return (a < 0) == (b < 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
		}
	}

	/** Smallest step that still puts you in front of the other offers at this price level. */
	static long step(long price)
	{
		return Math.max(1, Math.round(price * 0.002));
	}

	/** GE tax: 2 % (rounded down), none under 50 gp, capped at 5M. */
	static long netSell(long price)
	{
		if (price < 50)
		{
			return price;
		}
		return price - Math.min((long) (price * 0.02), 5_000_000L);
	}
}
