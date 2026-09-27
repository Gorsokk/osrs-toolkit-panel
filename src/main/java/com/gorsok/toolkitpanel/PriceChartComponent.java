package com.gorsok.toolkitpanel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.List;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * Price chart + "where is the price in its range" gauge, drawn inside the GE helper panel.
 * Band = spread between the average instant-sell (bottom) and instant-buy (top) prices of each step.
 */
class PriceChartComponent implements LayoutableRenderableEntity
{
	private static final int CHART_H = 96, GAUGE_H = 34, GAP = 6;
	private static final Color GRID = new Color(255, 255, 255, 28);
	private static final Color BAND = new Color(224, 182, 75, 60);
	private static final Color LINE = new Color(224, 182, 75);
	private static final Color AVG = new Color(190, 190, 190);
	private static final Color OFFER = new Color(96, 200, 255);
	private static final Color FLOOR = new Color(190, 120, 255);
	private static final Color LAST_BUY = new Color(255, 140, 90);
	private static final Color LAST_SELL = new Color(110, 220, 140);
	private static final Color LOW_ZONE = new Color(76, 175, 125);
	private static final Color MID_ZONE = new Color(140, 140, 140);
	private static final Color HIGH_ZONE = new Color(224, 97, 107);
	private static final Stroke DASH = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f);

	// inputs, set before each render
	List<PriceHistory.Point> points;
	ChartRange range;
	Integer lastBuy, lastSell;
	Integer offer;          // player's price, or null
	Long alchFloor;         // high alch value - nature rune, or null
	boolean buying;

	private final Point location = new Point();
	private int width = 200;
	private final Rectangle bounds = new Rectangle();

	@Override
	public Dimension render(Graphics2D g)
	{
		int x0 = location.x, y0 = location.y, w = width;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();

		if (points == null || points.size() < 2)
		{
			g.setColor(Color.LIGHT_GRAY);
			g.drawString(Text.t(points == null ? "chart_loading" : "chart_nodata"), x0, y0 + fm.getAscent());
			int h = fm.getHeight() + 2;
			bounds.setBounds(x0, y0, w, h);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			return new Dimension(w, h);
		}

		// ---------- scale
		// Robust scale: ignore the 3 % most extreme prices (one troll trade at 20 gp would flatten everything).
		java.util.List<Integer> all = new java.util.ArrayList<>();
		for (PriceHistory.Point p : points)
		{
			if (p.high != null) all.add(p.high);
			if (p.low != null) all.add(p.low);
		}
		java.util.Collections.sort(all);
		double lo = PriceHistory.percentile(all, 3), hi = PriceHistory.percentile(all, 97);
		for (Integer v : new Integer[]{lastBuy, lastSell})
		{
			if (v != null) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
		}
		double dataLo = lo, dataHi = hi;
		// markers stretch the scale only when close to the data, so one silly offer doesn't flatten the chart
		double span0 = Math.max(1, hi - lo);
		for (Number v : new Number[]{offer, alchFloor})
		{
			if (v != null && v.doubleValue() > dataLo - span0 * 0.6 && v.doubleValue() < dataHi + span0 * 0.6)
			{
				lo = Math.min(lo, v.doubleValue());
				hi = Math.max(hi, v.doubleValue());
			}
		}
		double pad = Math.max(1, (hi - lo) * 0.08);
		final double yLo = lo - pad, yHi = hi + pad;

		String topLabel = Text.gp((long) Math.round(dataHi)), botLabel = Text.gp((long) Math.round(dataLo));
		int labelW = Math.max(fm.stringWidth(topLabel), fm.stringWidth(botLabel)) + 4;
		final int cx = x0 + labelW, cw = w - labelW - 4, cy = y0 + 2, ch = CHART_H;
		long t0 = points.get(0).ts, t1 = Math.max(t0 + 1, System.currentTimeMillis() / 1000);

		// ---------- frame + labels
		g.setColor(new Color(0, 0, 0, 70));
		g.fillRect(cx, cy, cw, ch);
		g.setColor(GRID);
		g.drawRect(cx, cy, cw, ch);
		g.drawLine(cx, cy + ch / 2, cx + cw, cy + ch / 2);
		g.setColor(Color.LIGHT_GRAY);
		g.drawString(topLabel, x0, py(dataHi, yLo, yHi, cy, ch) + fm.getAscent() / 2);
		g.drawString(botLabel, x0, py(dataLo, yLo, yHi, cy, ch) + fm.getAscent() / 2);
		String left = "-" + range.label(), right = Text.t("chart_now");
		int ly = cy + ch + fm.getAscent() + 1;
		g.drawString(left, cx, ly);
		g.drawString(right, cx + cw - fm.stringWidth(right), ly);

		// ---------- band + mid line
		Path2D.Double top = new Path2D.Double(), mid = new Path2D.Double();
		Polygon band = new Polygon();
		int[] bottomX = new int[points.size()], bottomY = new int[points.size()];
		int nb = 0;
		boolean started = false;
		for (PriceHistory.Point p : points)
		{
			int px = cx + (int) Math.round((p.ts - t0) * (double) cw / (t1 - t0));
			if (p.high != null && p.low != null)
			{
				band.addPoint(px, py(p.high, yLo, yHi, cy, ch));
				bottomX[nb] = px;
				bottomY[nb++] = py(p.low, yLo, yHi, cy, ch);
			}
			Double m = p.mid();
			if (m != null)
			{
				double yy = py(m, yLo, yHi, cy, ch);
				if (!started) { mid.moveTo(px, yy); started = true; } else { mid.lineTo(px, yy); }
			}
		}
		for (int i = nb - 1; i >= 0; i--)
		{
			band.addPoint(bottomX[i], bottomY[i]);
		}
		java.awt.Shape oldClip = g.getClip();
		g.clipRect(cx, cy, cw + 1, ch + 1);
		g.setColor(BAND);
		g.fillPolygon(band);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(1.4f));
		g.draw(mid);

		// ---------- reference lines
		Double avg = PriceHistory.average(points);
		Stroke old = new BasicStroke(1f);
		g.setStroke(DASH);
		if (avg != null)
		{
			hline(g, avg, AVG, Text.t("chart_avg"), yLo, yHi, cx, cw, cy, ch, fm);
		}
		if (alchFloor != null && alchFloor > 0)
		{
			hline(g, alchFloor, FLOOR, "alch", yLo, yHi, cx, cw, cy, ch, fm);
		}
		if (offer != null && offer > 0)
		{
			hline(g, offer, OFFER, Text.t("chart_you"), yLo, yHi, cx, cw, cy, ch, fm);
		}
		g.setStroke(old);

		// ---------- last real trades at the right edge
		dot(g, lastBuy, LAST_BUY, yLo, yHi, cx + cw - 3, cy, ch);
		dot(g, lastSell, LAST_SELL, yLo, yHi, cx + cw - 3, cy, ch);
		g.setClip(oldClip);

		// ---------- gauge
		int gy = ly + GAP;
		Double pos = null;
		if (lastBuy != null && lastSell != null)
		{
			pos = PriceHistory.position(points, (lastBuy + lastSell) / 2.0);
		}
		int gh = drawGauge(g, pos, x0, gy, w, fm);

		int h = gy + gh - y0;
		bounds.setBounds(x0, y0, w, h);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		return new Dimension(w, h);
	}

	private int drawGauge(Graphics2D g, Double pos, int x, int y, int w, FontMetrics fm)
	{
		int barY = y + fm.getHeight() + 2, barH = 7;
		String verdict;
		Color vc;
		if (pos == null)
		{
			verdict = Text.t("gauge_unknown");
			vc = Color.LIGHT_GRAY;
		}
		else
		{
			String zone = pos < 1 / 3.0 ? "gauge_low" : pos > 2 / 3.0 ? "gauge_high" : "gauge_mid";
			vc = pos < 1 / 3.0 ? LOW_ZONE : pos > 2 / 3.0 ? HIGH_ZONE : Color.LIGHT_GRAY;
			String hint = "";
			if (buying && pos > 2 / 3.0)
			{
				hint = " · " + Text.t("gauge_hint_buy_high");
			}
			else if (!buying && pos < 1 / 3.0)
			{
				hint = " · " + Text.t("gauge_hint_sell_low");
			}
			verdict = Text.t("gauge_title", Text.t(zone), Math.round(pos * 100), range.label()) + hint;
		}
		g.setColor(vc);
		g.drawString(verdict, x, y + fm.getAscent());

		int third = w / 3;
		g.setColor(alpha(LOW_ZONE, 150));
		g.fillRect(x, barY, third, barH);
		g.setColor(alpha(MID_ZONE, 150));
		g.fillRect(x + third, barY, third, barH);
		g.setColor(alpha(HIGH_ZONE, 150));
		g.fillRect(x + 2 * third, barY, w - 2 * third, barH);
		if (pos != null)
		{
			int mx = x + (int) Math.round(pos * (w - 1));
			g.setColor(Color.WHITE);
			g.fillPolygon(new int[]{mx - 4, mx + 4, mx}, new int[]{barY - 4, barY - 4, barY + 1}, 3);
			g.fillRect(mx - 1, barY, 2, barH);
		}
		g.setColor(Color.GRAY);
		int ly = barY + barH + fm.getAscent() + 1;
		String a = Text.t("gauge_low"), b = Text.t("gauge_mid"), c = Text.t("gauge_high");
		g.drawString(a, x, ly);
		g.drawString(b, x + (w - fm.stringWidth(b)) / 2, ly);
		g.drawString(c, x + w - fm.stringWidth(c), ly);
		return ly - y + 2;
	}

	private static void hline(Graphics2D g, double v, Color c, String label, double yLo, double yHi,
		int cx, int cw, int cy, int ch, FontMetrics fm)
	{
		int y = py(v, yLo, yHi, cy, ch);
		boolean above = y < cy, below = y > cy + ch;
		y = Math.max(cy + 1, Math.min(cy + ch - 1, y));
		g.setColor(c);
		g.drawLine(cx, y, cx + cw, y);
		String s = label + (above ? " ↑" : below ? " ↓" : "");
		int tw = fm.stringWidth(s);
		int ty = y - 2 < cy + fm.getAscent() ? y + fm.getAscent() + 1 : y - 2;
		g.setColor(new Color(0, 0, 0, 150));
		g.fillRect(cx + 2, ty - fm.getAscent() + 1, tw + 2, fm.getAscent());
		g.setColor(c);
		g.drawString(s, cx + 3, ty);
	}

	private static void dot(Graphics2D g, Integer v, Color c, double yLo, double yHi, int x, int cy, int ch)
	{
		if (v == null)
		{
			return;
		}
		int y = Math.max(cy + 2, Math.min(cy + ch - 2, py(v, yLo, yHi, cy, ch)));
		g.setColor(Color.BLACK);
		g.fillOval(x - 4, y - 4, 8, 8);
		g.setColor(c);
		g.fillOval(x - 3, y - 3, 6, 6);
	}

	private static int py(double v, double yLo, double yHi, int cy, int ch)
	{
		return cy + (int) Math.round((yHi - v) / (yHi - yLo) * ch);
	}

	private static Color alpha(Color c, int a)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point p)
	{
		location.setLocation(p);
	}

	@Override
	public void setPreferredSize(Dimension d)
	{
		if (d != null && d.width > 0)
		{
			width = d.width;
		}
	}
}
