package com.gorsok.toolkitpanel;

import com.gorsok.toolkitexporter.OsrsToolkitExporterPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Author's dev launcher: the whole OSRS Toolkit suite (this Panel + OSRS Toolkit Exporter) in one client.
 * Only compiled when the Exporter project sits next to this one (see build.gradle); not part of the plugin.
 */
public class DevLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ToolkitPanelPlugin.class, OsrsToolkitExporterPlugin.class);
		RuneLite.main(args);
	}
}
