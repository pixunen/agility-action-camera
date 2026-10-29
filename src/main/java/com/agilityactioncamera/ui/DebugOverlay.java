package com.agilityactioncamera.ui;

import com.agilityactioncamera.AgilityActionCameraConfig;
import com.agilityactioncamera.AgilityActionCameraPlugin;
import com.agilityactioncamera.detect.PathPoint;
import com.agilityactioncamera.detect.Traversal;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Developer aid: draws the detected path and the director's state.
 */
public class DebugOverlay extends Overlay
{
	private final Client client;
	private final AgilityActionCameraPlugin plugin;
	private final AgilityActionCameraConfig config;

	@Inject
	DebugOverlay(Client client, AgilityActionCameraPlugin plugin, AgilityActionCameraConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.debugOverlay())
		{
			return null;
		}

		Traversal traversal = plugin.getLastTraversal();
		if (traversal != null)
		{
			List<PathPoint> points = traversal.getPoints();
			g.setStroke(new BasicStroke(2));
			Point prev = null;
			for (int i = 0; i < points.size(); i++)
			{
				PathPoint p = points.get(i);
				Point canvas = Perspective.localToCanvas(client, new LocalPoint((int) p.getX(), (int) p.getY(), client.getTopLevelWorldView()),
					client.getTopLevelWorldView().getPlane(), 0);
				if (canvas == null)
				{
					prev = null;
					continue;
				}
				g.setColor(i == 0 ? Color.GREEN : i == points.size() - 1 ? Color.RED : Color.YELLOW);
				g.fillOval(canvas.getX() - 4, canvas.getY() - 4, 8, 8);
				if (prev != null)
				{
					g.setColor(Color.CYAN);
					g.drawLine(prev.getX(), prev.getY(), canvas.getX(), canvas.getY());
				}
				prev = canvas;
			}
		}

		g.setColor(Color.WHITE);
		int x = client.getViewportXOffset() + 8;
		int y = client.getViewportYOffset() + 20;
		g.drawString("detector: " + plugin.getDetectorStatus(), x, y);
		g.drawString("camera: " + plugin.getCameraStatus(), x, y + 14);
		return null;
	}
}
