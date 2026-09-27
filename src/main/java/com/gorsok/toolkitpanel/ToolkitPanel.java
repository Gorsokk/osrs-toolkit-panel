package com.gorsok.toolkitpanel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/** Side panel. Call {@link #update} on the Swing thread only. */
class ToolkitPanel extends PluginPanel
{
	private static final Color GOLD = new Color(0xE0B64B);
	private static final Color GOOD = new Color(0x4CAF7D);
	private static final Color BAD = new Color(0xE0616B);
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();

	private final JPanel content = new JPanel();
	private final Supplier<String> dashboardUrl;

	ToolkitPanel(Supplier<String> dashboardUrl)
	{
		super(false);
		this.dashboardUrl = dashboardUrl;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(10, 10, 10, 10));
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel north = new WidthTrackingPanel();
		north.setBackground(ColorScheme.DARK_GRAY_COLOR);
		north.add(content, BorderLayout.NORTH);
		javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(north);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
		showSnapshot(null);
	}

	void showSnapshot(ToolkitSnapshot s)
	{
		content.removeAll();

		JLabel title = new JLabel("OSRS Toolkit");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 20f));
		title.setForeground(GOLD);
		put(title, 0);

		if (s == null || !s.connected)
		{
			put(label(Text.t("offline"), BAD, true), 4);
			put(wrap(Text.t("offline_hint"), MUTED), 8);
			JButton get = new JButton(Text.t("get_toolkit"));
			get.setFocusable(false);
			get.addActionListener(e -> LinkBrowser.browse("https://github.com/Gorsokk/osrs-toolkit"));
			put(get, 10);
			content.revalidate();
			content.repaint();
			return;
		}

		String who = s.character != null ? " · " + s.character : "";
		put(label(Text.t("connected", s.version != null ? s.version : "") + who, GOOD, false), 2);

		// --- bond
		put(header(Text.t("bond")), 12);
		JPanel bond = card();
		if (s.bondPct != null && s.bondPrice != null)
		{
			JProgressBar bar = new JProgressBar(0, 1000);
			bar.setValue((int) Math.round(s.bondPct * 10));
			bar.setStringPainted(true);
			bar.setString(Text.pct(s.bondPct) + " %");
			bar.setForeground(GOLD);
			bar.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
			bar.setAlignmentX(Component.LEFT_ALIGNMENT);
			bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
			bond.add(bar);
			bond.add(Box.createVerticalStrut(4));
			bond.add(label(Text.t("bond_line", Text.gp(s.cash), Text.gp(s.bondPrice)), ColorScheme.LIGHT_GRAY_COLOR, false));
		}
		else
		{
			bond.add(label(Text.t("bond_none"), MUTED, false));
		}
		put(bond, 4);

		// --- flips
		put(header(Text.t("flips")), 12);
		if (s.flips.isEmpty())
		{
			put(wrap(Text.t("no_scan"), MUTED), 4);
		}
		for (ToolkitSnapshot.Row r : s.flips)
		{
			JPanel c = card();
			c.add(label(r.name + (r.volatile_ ? "  ⚠ " + Text.t("volatile") : ""), r.volatile_ ? GOLD : Color.WHITE, true));
			c.add(label(Text.t("flip_line", Text.gp(r.buy), Text.gp(r.sell), Text.pct(r.roi), Text.gp(r.gpPerHour)),
				ColorScheme.LIGHT_GRAY_COLOR, false));
			put(c, 3);
		}

		// --- alch
		if (!s.alchs.isEmpty())
		{
			put(header(Text.t("alchs")), 12);
			for (ToolkitSnapshot.Row r : s.alchs)
			{
				JPanel c = card();
				c.add(label(r.name, Color.WHITE, true));
				c.add(label(Text.t("alch_line", Text.gp(r.buy), Text.gp(r.profitPerCast)), ColorScheme.LIGHT_GRAY_COLOR, false));
				put(c, 3);
			}
		}
		if (s.scanTime != null)
		{
			put(label(Text.t("scan", s.scanTime.replace('T', ' ').replaceAll("[-+]\\d{4}$", "")), MUTED, false), 4);
		}

		// --- alerts
		put(header(Text.t("alerts")), 12);
		if (s.alerts.isEmpty())
		{
			put(label(Text.t("no_alerts"), MUTED, false), 4);
		}
		int shown = 0;
		for (ToolkitSnapshot.Alert a : s.alerts)
		{
			if (shown++ >= 10)
			{
				break;
			}
			JPanel c = card();
			String time = a.ts != null && a.ts.length() >= 16 ? a.ts.substring(11, 16) : "";
			c.add(label(time + "  " + (a.category != null ? a.category : ""), a.important ? GOLD : MUTED, false));
			c.add(wrap(a.title, a.important ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR));
			if (a.body != null && !a.body.isEmpty())
			{
				c.setToolTipText("<html><body style='width:220px'>" + escape(a.body) + "</body></html>");
			}
			put(c, 3);
		}

		JButton open = new JButton(Text.t("dashboard"));
		open.setAlignmentX(Component.LEFT_ALIGNMENT);
		open.setFocusable(false);
		open.addActionListener(e -> LinkBrowser.browse(dashboardUrl.get()));
		put(open, 14);

		content.revalidate();
		content.repaint();
	}

	// ------------------------------------------------------------------ helpers
	private void put(Component c, int gapBefore)
	{
		if (gapBefore > 0 && content.getComponentCount() > 0)
		{
			content.add(Box.createVerticalStrut(gapBefore));
		}
		if (c instanceof javax.swing.JComponent)
		{
			((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		content.add(c);
	}

	private static JLabel header(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(GOLD);
		return l;
	}

	private static JLabel label(String text, Color color, boolean bold)
	{
		JLabel l = new JLabel(text);
		l.setFont(bold ? FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD) : FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	/** Label that wraps to the panel width. */
	private static JLabel wrap(String text, Color color)
	{
		JLabel l = new JLabel("<html><body style='width:" + (PANEL_WIDTH - 70) + "px'>" + escape(text) + "</body></html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JPanel card()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return p;
	}

	/** Follows the scroll pane's width, so nothing is cut off on the right. */
	private static class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable
	{
		WidthTrackingPanel()
		{
			super(new BorderLayout());
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d)
		{
			return 64;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
