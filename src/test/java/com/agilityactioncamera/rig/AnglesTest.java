package com.agilityactioncamera.rig;

import net.runelite.api.Perspective;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AnglesTest
{
	@Test
	public void jauRoundTrip()
	{
		for (int jau = 0; jau < Angles.JAU_PER_TURN; jau += 97)
		{
			assertEquals(jau, Angles.toJau(Angles.toRadians(jau)));
		}
		assertEquals(Angles.JAU_PER_TURN - 1024, Angles.toJau(-Math.PI / 8));
	}

	@Test
	public void wrapKeepsShortestTurn()
	{
		assertEquals(-Math.PI / 2, Angles.wrap(3 * Math.PI / 2), 1e-9);
		assertEquals(0.1, Angles.wrap(0.1 + 4 * Math.PI), 1e-9);
	}

	/**
	 * A point straight along the camera's forward vector must project to the
	 * screen centre in front of the camera, using the client's own rotation
	 * (Perspective.localToCanvas, JAU14 tables).
	 */
	@Test
	public void forwardMatchesClientProjection()
	{
		for (int jau = 0; jau < Angles.JAU_PER_TURN; jau += 512)
		{
			double yaw = Angles.toRadians(jau);
			int x = (int) Math.round(Angles.forwardX(yaw) * 1000);
			int y = (int) Math.round(Angles.forwardY(yaw) * 1000);

			int sin = Perspective.SINE14[jau];
			int cos = Perspective.COSINE14[jau];
			int screenX = x * cos + y * sin >> 16;
			int depth = y * cos - x * sin >> 16;

			assertTrue("centred at jau " + jau, Math.abs(screenX) <= 1);
			assertTrue("in front at jau " + jau, depth > 0);
		}
	}

	@Test
	public void yawLookingAlongInvertsForward()
	{
		double yaw = Angles.yawLookingAlong(0.6, -0.8);
		assertEquals(0.6, Angles.forwardX(yaw), 1e-9);
		assertEquals(-0.8, Angles.forwardY(yaw), 1e-9);
		// North is yaw 0
		assertEquals(0, Angles.yawLookingAlong(0, 1), 1e-9);
	}
}
