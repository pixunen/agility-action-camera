package com.agilityactioncamera.rig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CameraRigTest
{
	@Test
	public void springConvergesWithoutOvershoot()
	{
		Spring s = new Spring(0);
		double prev = 0;
		for (int i = 0; i < 300; i++)
		{
			double v = s.update(100, 6, 1 / 60.0);
			assertTrue("monotonic", v >= prev - 1e-9);
			assertTrue("no overshoot", v <= 100 + 1e-9);
			prev = v;
		}
		assertEquals(100, prev, 0.01);
	}

	@Test
	public void springIsFrameRateIndependent()
	{
		Spring fast = new Spring(0);
		Spring slow = new Spring(0);
		for (int i = 0; i < 144; i++)
		{
			fast.update(50, 5, 1 / 144.0);
		}
		for (int i = 0; i < 30; i++)
		{
			slow.update(50, 5, 1 / 30.0);
		}
		assertEquals(fast.get(), slow.get(), 1e-6);
	}

	@Test
	public void yawTurnsTheShortWayAcrossNorth()
	{
		CameraRig rig = new CameraRig();
		double nearlyFull = Angles.toRadians(16000);
		double justPast = Angles.toRadians(300);
		rig.snap(new CameraPose(0, 0, 0, nearlyFull, 0.5, 0.5));

		CameraPose p = rig.update(new CameraPose(0, 0, 0, justPast, 0.5, 0.5), 8, 0.05);
		// Moving forward from 16000 through 0 means the unwrapped yaw increases
		assertTrue(p.getYaw() > nearlyFull);
		for (int i = 0; i < 200; i++)
		{
			p = rig.update(new CameraPose(0, 0, 0, justPast, 0.5, 0.5), 8, 0.05);
		}
		assertEquals(0, Angles.wrap(p.getYaw() - justPast), 1e-3);
	}
}
