package com.agilityactioncamera.director;

import com.agilityactioncamera.rig.CameraPose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AutoFramerTest
{
	@Test
	public void leavesComposedShotsAloneInsideTheDeadZone()
	{
		AutoFramer framer = new AutoFramer();
		for (int i = 0; i < 60; i++)
		{
			framer.update(0.15, 0.05, 1 / 60.0);
		}
		assertEquals(0, framer.getHeightOffset(), 1e-9);
		assertEquals(0, framer.getLateralOffset(), 1e-9);
	}

	@Test
	public void playerAboveFrameRaisesTheFocalPoint()
	{
		// e.g. hanging from a zipline well above the path
		AutoFramer framer = new AutoFramer();
		for (int i = 0; i < 30; i++)
		{
			framer.update(0, -0.4, 1 / 60.0);
		}
		// Negative is up
		assertTrue(framer.getHeightOffset() < -100);
	}

	@Test
	public void lateralCorrectionFollowsTheCameraRightVector()
	{
		AutoFramer framer = new AutoFramer();
		for (int i = 0; i < 30; i++)
		{
			framer.update(0.45, 0, 1 / 60.0);
		}
		// Camera looking north: screen-right is east (+x)
		CameraPose p = framer.apply(new CameraPose(0, 0, 0, 0, 0.5, 0.5));
		assertTrue(p.getX() > 50);
		assertEquals(0, p.getY(), 1e-6);
	}

	@Test
	public void correctionsAreBoundedAndDecay()
	{
		AutoFramer framer = new AutoFramer();
		for (int i = 0; i < 600; i++)
		{
			framer.update(0, 0.5, 1 / 60.0);
		}
		assertEquals(AutoFramer.MAX_HEIGHT, framer.getHeightOffset(), 1e-9);

		for (int i = 0; i < 600; i++)
		{
			framer.update(0, 0, 1 / 60.0);
		}
		assertTrue(framer.getHeightOffset() < AutoFramer.MAX_HEIGHT * 0.1);
	}
}
