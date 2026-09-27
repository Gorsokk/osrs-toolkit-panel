package com.gorsok.toolkitpanel;

import com.gorsok.positionexporter.PositionExporterPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Author's dev launcher: this plugin + Position Exporter in one client.
 * Only compiled when ../position-exporter exists (see build.gradle); not part of the plugin.
 */
public class DevLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ToolkitPanelPlugin.class, PositionExporterPlugin.class);
		RuneLite.main(args);
	}
}
