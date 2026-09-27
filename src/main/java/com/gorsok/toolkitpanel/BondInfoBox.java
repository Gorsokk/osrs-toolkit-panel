package com.gorsok.toolkitpanel;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

class BondInfoBox extends InfoBox
{
	private volatile ToolkitSnapshot snap;

	BondInfoBox(BufferedImage image, Plugin plugin)
	{
		super(image, plugin);
	}

	void setSnapshot(ToolkitSnapshot s)
	{
		snap = s;
	}

	@Override
	public String getText()
	{
		ToolkitSnapshot s = snap;
		return s == null || s.bondPct == null ? "?" : Math.round(s.bondPct) + "%";
	}

	@Override
	public Color getTextColor()
	{
		ToolkitSnapshot s = snap;
		return s != null && s.bondPct != null && s.bondPct >= 100 ? new Color(0x4CAF7D) : Color.WHITE;
	}

	@Override
	public String getTooltip()
	{
		ToolkitSnapshot s = snap;
		if (s == null || s.bondPct == null)
		{
			return "OSRS Toolkit";
		}
		return Text.t("bond") + ": " + Text.pct(s.bondPct) + " %</br>" + Text.t("bond_line", Text.gp(s.cash), Text.gp(s.bondPrice));
	}
}
