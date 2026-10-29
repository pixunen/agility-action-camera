package com.agilityactioncamera;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class AgilityActionCameraPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AgilityActionCameraPlugin.class);
		RuneLite.main(args);
	}
}
