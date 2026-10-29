package com.agilityactioncamera.ui;

import com.agilityactioncamera.AgilityActionCameraConfig;
import com.agilityactioncamera.AgilityActionCameraPlugin;
import com.agilityactioncamera.rig.Easing;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Animated letterbox bars and vignette over the 3D viewport. Drawn under
 * widgets so the interface stays usable.
 */
public class LetterboxOverlay extends Overlay
{
	private static final double IN_SECONDS = 0.45;
	private static final double OUT_SECONDS = 0.6;

	private final Client client;
	private final AgilityActionCameraPlugin plugin;
	private final AgilityActionCameraConfig config;

	private double amount;
	private long lastNanos;
	private BufferedImage vignette;

	@Inject
	LetterboxOverlay(Client client, AgilityActionCameraPlugin plugin, AgilityActionCameraConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		long now = System.nanoTime();
		double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;

		boolean on = plugin.isCinematicPlaying();
		amount = on ? Math.min(1, amount + dt / IN_SECONDS) : Math.max(0, amount - dt / OUT_SECONDS);
		if (amount <= 0)
		{
			return null;
		}

		int x = client.getViewportXOffset();
		int y = client.getViewportYOffset();
		int w = client.getViewportWidth();
		int h = client.getViewportHeight();
		double e = Easing.inOutCubic(amount);

		if (config.vignette())
		{
			drawVignette(g, x, y, w, h, (float) e);
		}

		if (config.letterbox())
		{
			int bar = (int) Math.round(h * config.letterboxSize() / 100.0 * e);
			g.setColor(Color.BLACK);
			g.fillRect(x, y, w, bar);
			g.fillRect(x, y + h - bar, w, bar);

			String name = plugin.getCurrentShotName();
			if (config.shotName() && name != null && bar > 14)
			{
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				Font font = FontManager.getRunescapeSmallFont();
				g.setFont(font);
				FontMetrics fm = g.getFontMetrics();
				g.setColor(new Color(255, 255, 255, (int) (160 * e)));
				g.drawString(name.toUpperCase(), x + w - fm.stringWidth(name.toUpperCase()) - 12, y + h - (bar - fm.getAscent()) / 2 - 2);
			}
		}
		return null;
	}

	private void drawVignette(Graphics2D g, int x, int y, int w, int h, float alpha)
	{
		if (vignette == null || vignette.getWidth() != w || vignette.getHeight() != h)
		{
			vignette = buildVignette(w, h);
		}
		Composite old = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		g.drawImage(vignette, x, y, null);
		g.setComposite(old);
	}

	private static BufferedImage buildVignette(int w, int h)
	{
		BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		float radius = (float) Math.hypot(w, h) / 2f;
		g.setPaint(new RadialGradientPaint(
			new Point2D.Float(w / 2f, h / 2f), radius,
			new float[]{0f, 0.55f, 1f},
			new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)},
			MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fillRect(0, 0, w, h);
		g.dispose();
		return img;
	}
}
