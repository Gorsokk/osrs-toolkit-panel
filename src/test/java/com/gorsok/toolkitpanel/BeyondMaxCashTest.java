package com.gorsok.toolkitpanel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Prices above 2,147,483,647 gp (Beyond Max Cash): nothing may wrap around, be clamped or throw.
 * Plain unit tests: no RuneLite client and no network needed.
 */
public class BeyondMaxCashTest
{
	private static final long G = 1_000_000_000L;

	@Before
	public void english()
	{
		Text.fr = false;   // static: don't depend on the order the tests run in
	}

	private static JsonObject json(String s)
	{
		return new Gson().fromJson(s, JsonObject.class);
	}

	private static PriceHistory.Point point(long ts, Long high, Long low)
	{
		return new PriceHistory.Point(ts, high, low);
	}

	// ---------------------------------------------------------------- JSON numbers
	@Test
	public void wikiPricesAboveTheIntRangeAreNotTruncated()
	{
		JsonObject o = json("{\"high\":3500000000,\"low\":null,\"text\":\"abc\"}");
		assertEquals(Long.valueOf(3_500_000_000L), WikiPrices.num(o, "high"));
		assertNull(WikiPrices.num(o, "low"));
		assertNull(WikiPrices.num(o, "text"));
		assertNull(WikiPrices.num(o, "missing"));
		assertNull(WikiPrices.num(null, "high"));
	}

	@Test
	public void smallValuesRefuseToWrapAround()
	{
		JsonObject o = json("{\"id\":13190,\"big\":3500000000}");
		assertEquals(Integer.valueOf(13190), WikiPrices.intNum(o, "id"));
		assertNull("must be null, not a wrapped-around negative number", WikiPrices.intNum(o, "big"));
	}

	// ---------------------------------------------------------------- history points
	@Test
	public void midOfTwoPricesAbove1_07BillionDoesNotOverflow()
	{
		// Integer 1.5B + 1.5B wraps around to a negative number: this was a bug before Beyond Max Cash too.
		assertEquals(1.5 * G, point(0, 1_500_000_000L, 1_500_000_000L).mid().doubleValue(), 0.0001);
		assertEquals(3.45 * G, point(0, 3_500_000_000L, 3_400_000_000L).mid().doubleValue(), 0.0001);
	}

	@Test
	public void midWithOnlyOneSideOrNoTradeAtAll()
	{
		assertEquals(5_000.0, point(0, 5_000L, null).mid().doubleValue(), 0.0001);
		assertEquals(4_000.0, point(0, null, 4_000L).mid().doubleValue(), 0.0001);
		// a 5-minute step without any trade: used to throw a NullPointerException (unboxed null)
		assertNull(point(0, null, null).mid());
	}

	private static List<PriceHistory.Point> sixPointsBetween3And4Billion()
	{
		List<PriceHistory.Point> pts = new ArrayList<>();
		long[] mids = {3_000_000_000L, 3_200_000_000L, 3_400_000_000L, 3_600_000_000L, 3_800_000_000L, 4_000_000_000L};
		for (int i = 0; i < mids.length; i++)
		{
			pts.add(point(i, mids[i], mids[i]));
		}
		return pts;
	}

	@Test
	public void percentileOfValuesAboveTheIntRange()
	{
		List<Long> sorted = Arrays.asList(1L, 3_000_000_000L);
		assertEquals(1.0, PriceHistory.percentile(sorted, 0), 1e-6);
		assertEquals(1_500_000_000.5, PriceHistory.percentile(sorted, 50), 1e-6);
		assertEquals(3.0 * G, PriceHistory.percentile(sorted, 100), 1e-6);
		assertEquals(0.0, PriceHistory.percentile(new ArrayList<Long>(), 50), 0.0);
	}

	@Test
	public void gaugePositionWithPricesAbove2_147Billion()
	{
		// 5th percentile = 3.05B, 95th = 3.95B: 3.5B is exactly in the middle
		assertEquals(0.5, PriceHistory.position(sixPointsBetween3And4Billion(), 3.5 * G), 1e-9);
		assertEquals(0.0, PriceHistory.position(sixPointsBetween3And4Billion(), 1.0 * G), 1e-9);
		assertEquals(1.0, PriceHistory.position(sixPointsBetween3And4Billion(), 9.0 * G), 1e-9);
	}

	@Test
	public void gaugeNeedsEnoughPoints()
	{
		List<PriceHistory.Point> few = sixPointsBetween3And4Billion().subList(0, 4);
		assertNull(PriceHistory.position(few, 3.5 * G));
	}

	@Test
	public void averageOfValuesAboveTheIntRange()
	{
		assertEquals(3.5 * G, PriceHistory.average(sixPointsBetween3And4Billion()), 1e-3);
		assertNull(PriceHistory.average(new ArrayList<PriceHistory.Point>()));
	}

	// ---------------------------------------------------------------- tax and steps
	@Test
	public void geTaxIsCappedAtFiveMillionPerItem()
	{
		assertEquals(49, WikiPrices.netSell(49));                 // no tax under 50 gp
		assertEquals(98, WikiPrices.netSell(100));                // 2 %
		assertEquals(245_000_000L, WikiPrices.netSell(250_000_000L));   // the cap starts here
		assertEquals(245_000_000L, WikiPrices.netSell(249_999_999L));
		assertEquals(2_995_000_000L, WikiPrices.netSell(3_000_000_000L));
		assertEquals(2_148_995_000_000L, WikiPrices.netSell(2_149_000_000_000L));   // the new maximum: tax is still only 5M
	}

	@Test
	public void stepAtHugePrices()
	{
		assertEquals(1, WikiPrices.step(100));
		assertEquals(7_000_000L, WikiPrices.step(3_500_000_000L));
	}

	@Test
	public void saturatingMultiply()
	{
		assertEquals(12L, WikiPrices.saturatingMultiply(3, 4));
		assertEquals(Long.MAX_VALUE, WikiPrices.saturatingMultiply(Long.MAX_VALUE, 2));
		assertEquals(Long.MIN_VALUE, WikiPrices.saturatingMultiply(-Long.MAX_VALUE, 2));
		assertEquals(0L, WikiPrices.saturatingMultiply(0, Long.MAX_VALUE));
	}

	// ---------------------------------------------------------------- the typed price of a new offer
	@Test
	public void typedPriceIsTrustedOnlyWhenTheItemFitsIn32Bits()
	{
		WikiPrices.Price cheap = new WikiPrices.Price();
		cheap.instantBuy = 1_000_000L;
		cheap.avgSell1h = 900_000L;
		assertTrue(cheap.canReadTypedPrice(950_000L));
		assertTrue(cheap.canReadTypedPrice(0L));
		assertFalse("a negative value is garbage", cheap.canReadTypedPrice(-5L));
		assertFalse("-1 = the game variable no longer exists (Varbit 4398 does not exist)", cheap.canReadTypedPrice(-1L));

		WikiPrices.Price huge = new WikiPrices.Price();
		huge.instantSell = 3_500_000_000L;
		assertEquals(3_500_000_000L, huge.highestKnown());
		assertFalse(huge.canReadTypedPrice(2_000_000L));

		WikiPrices.Price onlyAverageIsHuge = new WikiPrices.Price();
		onlyAverageIsHuge.avgBuy1h = 2_200_000_000L;
		assertFalse(onlyAverageIsHuge.canReadTypedPrice(1L));

		assertEquals(0L, new WikiPrices.Price().highestKnown());
		assertTrue(new WikiPrices.Price().canReadTypedPrice(10L));
	}

	// ---------------------------------------------------------------- display
	@Test
	public void gpTextKeepsWorkingUpToTheNewMaximum()
	{
		assertEquals("950", Text.gp(950L));
		assertEquals("1.5M", Text.gp(1_500_000L));
		assertEquals("1.5B", Text.gp(1_500_000_000L));
		assertEquals("3.5B", Text.gp(3_500_000_000L));
		assertEquals("2.15T", Text.gp(2_149_000_000_000L));
		assertEquals("-3.5B", Text.gp(-3_500_000_000L));
		assertEquals("?", Text.gp((Long) null));
	}
}
