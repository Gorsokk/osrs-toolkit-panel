package com.gorsok.toolkitpanel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Reads the OSRS Toolkit companion app running on this computer (http://127.0.0.1:&lt;port&gt;).
 * GET requests only, all asynchronous (OkHttp enqueue): never blocks the client thread or RuneLite's executor.
 */
@Slf4j
class ToolkitClient
{
	private final OkHttpClient http;
	private final Gson gson;
	private volatile int port = 8765;

	ToolkitClient(OkHttpClient base, Gson gson)
	{
		this.http = base.newBuilder()
			.connectTimeout(1, TimeUnit.SECONDS)
			.readTimeout(4, TimeUnit.SECONDS)
			.proxy(java.net.Proxy.NO_PROXY)   // it's on this computer: never go through a system proxy
			.build();
		this.gson = gson;
	}

	String dashboardUrl()
	{
		return "http://127.0.0.1:" + port + "/";
	}

	/** Calls {@code done} (on an OkHttp thread) with a snapshot; connected=false when the app isn't running. */
	void fetch(int configuredPort, Consumer<ToolkitSnapshot> done)
	{
		port = configuredPort;
		ToolkitSnapshot s = new ToolkitSnapshot();
		get("/api/ping", ping ->
		{
			JsonObject p = json(ping);
			if (p == null || !"osrs-ge-toolkit".equals(str(p, "app")))
			{
				done.accept(s);   // not running (or something else on that port)
				return;
			}
			s.connected = true;
			s.port = configuredPort;
			s.version = str(p, "version");

			AtomicInteger remaining = new AtomicInteger(3);
			Runnable finish = () ->
			{
				if (remaining.decrementAndGet() == 0)
				{
					done.accept(s);
				}
			};
			get("/api/stream/state", body ->
			{
				JsonObject st = json(body);
				if (st != null && st.has("bond") && st.get("bond").isJsonObject())
				{
					JsonObject b = st.getAsJsonObject("bond");
					synchronized (s)
					{
						s.character = str(b, "character");
						s.bondPrice = lng(b, "price");
						s.cash = lng(b, "cash");
						s.bondPct = dbl(b, "pct");
					}
				}
				finish.run();
			});
			get("/data/market.json", body ->
			{
				JsonObject m = json(body);
				if (m != null)
				{
					synchronized (s)
					{
						s.scanTime = str(m, "generated_at");
						s.flips = rows(m, "flips_best_gp_per_hour", 8);
						s.alchs = rows(m, "alch", 4);
					}
				}
				finish.run();
			});
			get("/data/alerts.jsonl", body ->
			{
				List<ToolkitSnapshot.Alert> alerts = parseAlerts(body);
				synchronized (s)
				{
					s.alerts = alerts;
				}
				finish.run();
			});
		});
	}

	/** Async GET; {@code onBody} receives the body, or null on any error / 404. */
	private void get(String path, Consumer<String> onBody)
	{
		Request req = new Request.Builder().url("http://127.0.0.1:" + port + path).build();
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Toolkit {}: {}", path, e.getMessage());
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
					log.debug("Toolkit {}: {}", path, e.getMessage());
				}
				onBody.accept(body);
			}
		});
	}

	private List<ToolkitSnapshot.Alert> parseAlerts(String text)
	{
		List<ToolkitSnapshot.Alert> out = new ArrayList<>();
		if (text == null)
		{
			return out;
		}
		String[] lines = text.split("\n");
		for (int i = lines.length - 1; i >= 0 && out.size() < 25; i--)
		{
			String line = lines[i].trim();
			if (line.isEmpty())
			{
				continue;
			}
			try
			{
				JsonObject a = gson.fromJson(line, JsonObject.class);
				ToolkitSnapshot.Alert al = new ToolkitSnapshot.Alert();
				al.ts = str(a, "ts");
				al.category = str(a, "category");
				al.title = str(a, "title");
				al.body = str(a, "body");
				al.important = a.has("important") && a.get("important").isJsonPrimitive() && a.get("important").getAsBoolean();
				out.add(al);   // newest first
			}
			catch (RuntimeException ignored)
			{
				// half-written line: skip it
			}
		}
		return out;
	}

	private JsonObject json(String t)
	{
		if (t == null)
		{
			return null;
		}
		try
		{
			JsonElement e = gson.fromJson(t, JsonElement.class);
			return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static List<ToolkitSnapshot.Row> rows(JsonObject m, String key, int max)
	{
		List<ToolkitSnapshot.Row> out = new ArrayList<>();
		if (!m.has(key) || !m.get(key).isJsonArray())
		{
			return out;
		}
		JsonArray arr = m.getAsJsonArray(key);
		for (int i = 0; i < arr.size() && out.size() < max; i++)
		{
			if (!arr.get(i).isJsonObject())
			{
				continue;
			}
			JsonObject o = arr.get(i).getAsJsonObject();
			ToolkitSnapshot.Row r = new ToolkitSnapshot.Row();
			Long id = lng(o, "id");
			r.id = id == null ? 0 : id.intValue();
			r.name = str(o, "name");
			r.buy = lng(o, "buy_at");
			r.sell = lng(o, "sell_at");
			r.margin = lng(o, "net_margin");
			r.roi = dbl(o, "roi_pct");
			r.gpPerHour = lng(o, "gp_per_hour");
			r.qty = lng(o, "qty_per_cycle");
			r.alchValue = lng(o, "alch_value");
			r.profitPerCast = lng(o, "profit_per_cast");
			r.volatile_ = o.has("volatile") && o.get("volatile").isJsonPrimitive() && o.get("volatile").getAsBoolean();
			out.add(r);
		}
		return out;
	}

	static String str(JsonObject o, String k)
	{
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
	}

	static Long lng(JsonObject o, String k)
	{
		try
		{
			return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsLong() : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	static Double dbl(JsonObject o, String k)
	{
		try
		{
			return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
