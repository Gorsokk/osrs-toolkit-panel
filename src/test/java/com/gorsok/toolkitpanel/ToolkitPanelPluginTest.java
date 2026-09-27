package com.gorsok.toolkitpanel;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ToolkitPanelPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ToolkitPanelPlugin.class);
		RuneLite.main(args);
	}
}
