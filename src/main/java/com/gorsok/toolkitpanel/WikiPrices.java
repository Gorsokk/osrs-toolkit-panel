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
 */
@Slf4j
class WikiPrices
{
	static final String API = "https://prices.runescape.wiki/api/v1/osrs";
	static final String USER_AGENT = "OSRS Toolkit Panel RuneLite plugin - github.com/Gorsokk/osrs-toolkit-panel";
	private static final int NATURE_RUNE = 561;

	static class Price
	{
		Integer instantBuy, instantSell;   // last real instant-buy / instant-sell trades ("high" / "low")
		Long buyTime, sellTime;            // when they happened (unix seconds)
		Integer avgBuy1h, avgSell1h;
		Integer volume1h;
		Integer limit;
		Integer highAlch;
		Integer natureRune;
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
			Integer ht = num(li, "highTime"), lt = num(li, "lowTime");
			p.buyTime = ht == null ? null : (long) ht;
			p.sellTime = lt == null ? null : (long) lt;
		}
		JsonObject hi = h == null ? null : obj(h, String.valueOf(itemId));
		if (hi != null)
		{
			p.avgBuy1h = num(hi, "avgHighPrice");
			p.avgSell1h = num(hi, "avgLowPrice");
			Integer a = num(hi, "highPriceVolume"), b = num(hi, "lowPriceVolume");
			p.volume1h = (a == null ? 0 : a) + (b == null ? 0 : b);
		}
		p.limit = limits.get(itemId);
		p.highAlch = alchs.get(itemId);
		JsonObject nat = obj(l, String.valueOf(NATURE_RUNE));
		p.natureRune = nat == null ? null : num(nat, "high");
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
							Integer id = num(o, "id"), l = num(o, "limit"), alch = num(o, "highalch");
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

	private static Integer num(JsonObject o, String k)
	{
		try
		{
			return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : null;
		}
		catch (NumberFormatException e)
		{
			return null;
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
