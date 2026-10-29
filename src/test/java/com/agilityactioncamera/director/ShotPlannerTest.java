package com.agilityactioncamera.director;

import com.agilityactioncamera.detect.ObstacleKind;
import com.agilityactioncamera.detect.PlayerSample;
import com.agilityactioncamera.detect.Traversal;
import com.agilityactioncamera.detect.TraversalDetector;
import com.agilityactioncamera.rig.Angles;
import com.agilityactioncamera.rig.CameraPose;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import net.runelite.api.gameval.ObjectID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ShotPlannerTest
{
	private static final Terrain FLAT = (x, y) -> 0;
	private static final Set<ShotType> ALL = EnumSet.allOf(ShotType.class);

	@Test
	public void everyShotStaysWithinClientLimits()
	{
		Traversal t = traversal(12);
		for (ShotType type : ShotType.values())
		{
			for (int side : new int[]{1, -1})
			{
				for (double u = 0; u <= 1; u += 0.1)
				{
					CameraPose p = type.frame(ShotPlanner.context(t, side, u, u * 4));
					assertTrue(type + " pitch", p.getPitch() >= Angles.MIN_PITCH - 1e-9 && p.getPitch() <= Angles.MAX_PITCH + 1e-9);
					assertTrue(type + " zoom", p.getZoom() >= 0 && p.getZoom() <= 1);
					assertTrue(type + " yaw", p.getYaw() >= -Math.PI && p.getYaw() < Math.PI);
				}
			}
		}
	}

	@Test
	public void sideShotLooksAcrossThePath()
	{
		// Travelling east; camera on the right (south) side must look north
		Traversal t = traversal(12);
		CameraPose p = ShotType.TRACKING.frame(ShotPlanner.context(t, 1, 0.5, 0));
		assertTrue(Angles.forwardY(p.getYaw()) > 0.9);
	}

	@Test
	public void flybyStartsAheadAndEndsBehind()
	{
		Traversal t = traversal(12);
		CameraPose start = ShotType.FLYBY.frame(ShotPlanner.context(t, 1, 0, 0));
		CameraPose end = ShotType.FLYBY.frame(ShotPlanner.context(t, 1, 1, 0));
		// Looking back (west) at the start, forward (east) at the end
		assertTrue(Angles.forwardX(start.getYaw()) < -0.5);
		assertTrue(Angles.forwardX(end.getYaw()) > 0.5);
	}

	@Test
	public void occlusionPrefersTheOpenSide()
	{
		Traversal t = traversal(12);
		// A cliff along the south side of the path (heights are negative-up)
		Terrain cliff = (x, y) -> y < -128 ? -2000 : 0;
		ShotPlanner planner = new ShotPlanner(new Random(1));

		double right = planner.score(ShotType.TRACKING, 1, t, cliff, 0, 1);
		double left = planner.score(ShotType.TRACKING, -1, t, cliff, 0, 1);
		assertTrue("camera on the open north side should win: left " + left + " right " + right, left > right);
	}

	@Test
	public void sceneEdgeCountsAsBlocked()
	{
		Traversal t = traversal(12);
		CameraPose pose = ShotType.TRACKING.frame(ShotPlanner.context(t, 1, 0.5, 0));
		ShotContext c = ShotPlanner.context(t, 1, 0.5, 0);
		assertEquals(0, ShotPlanner.occlusion(pose, c, FLAT), 1e-9);
		assertEquals(1, ShotPlanner.occlusion(pose, c, (x, y) -> Double.NaN), 1e-9);
	}

	@Test
	public void longObstaclesCutToADifferentShot()
	{
		ShotPlanner planner = new ShotPlanner(new Random(7));
		ShotPlan plan = planner.plan(traversal(14), FLAT, ALL, 10);
		assertEquals(2, plan.getEntries().size());
		assertNotEquals(plan.getEntries().get(0).getType(), plan.getEntries().get(1).getType());
		assertEquals(0, plan.indexAt(0.3));
		assertEquals(1, plan.indexAt(0.7));

		assertEquals(1, planner.plan(traversal(6), FLAT, ALL, 10).getEntries().size());
		assertEquals(1, planner.plan(traversal(14), FLAT, ALL, 0).getEntries().size());
	}

	@Test
	public void varietyAvoidsRepeatingTheSameShot()
	{
		ShotPlanner planner = new ShotPlanner(new Random(3));
		int repeats = 0;
		ShotType last = null;
		for (int i = 0; i < 40; i++)
		{
			ShotType type = planner.plan(traversal(9), FLAT, ALL, 0).getEntries().get(0).getType();
			if (type == last)
			{
				repeats++;
			}
			last = type;
		}
		assertTrue("repeats " + repeats, repeats < 10);
	}

	@Test
	public void respectsEnabledShots()
	{
		ShotPlanner planner = new ShotPlanner(new Random(5));
		ShotPlan plan = planner.plan(traversal(14), FLAT, EnumSet.of(ShotType.ORBIT), 10);
		assertTrue(plan.getEntries().stream().allMatch(e -> e.getType() == ShotType.ORBIT));
		assertTrue(planner.plan(traversal(14), FLAT, EnumSet.noneOf(ShotType.class), 10).getEntries().isEmpty());
	}

	@Test
	public void sessionReportsCutsOnlyBetweenShots()
	{
		Traversal t = traversal(14);
		ShotPlan plan = new ShotPlanner(new Random(7)).plan(t, FLAT, ALL, 10);
		CinematicSession session = new CinematicSession(t, plan, 0, 1);

		assertFalse("first shot eases in", session.frame(0, 0, 0).isCut());
		assertFalse(session.frame(1, 3 * 128, 0).isCut());
		assertTrue("cut at halfway", session.frame(2, 8 * 128, 0).isCut());
		assertFalse(session.frame(3, 9 * 128, 0).isCut());

		session.landed(4);
		assertEquals(1, session.frame(5, 14 * 128, 0).getU(), 1e-9);
		assertTrue(session.isHoldOver(10, 5));
	}

	@Test
	public void scenicZiplinesFavourShotsThatShowTheWholeLine()
	{
		assertTrue(ShotPlanner.kindBonus(ShotType.ESTABLISHING, ObstacleKind.ZIPLINE) > ShotPlanner.kindBonus(ShotType.HERO, ObstacleKind.ZIPLINE));

		int wide = 0;
		ShotPlanner planner = new ShotPlanner(new Random(11));
		planner.setStyle(ShotStyle.SCENIC);
		for (int i = 0; i < 20; i++)
		{
			ShotType type = planner.plan(zipline(14), FLAT, ALL, 0).getEntries().get(0).getType();
			if (type == ShotType.ESTABLISHING || type == ShotType.FLYBY || type == ShotType.TRACKING)
			{
				wide++;
			}
		}
		assertTrue("wide shots " + wide, wide >= 18);
	}

	@Test
	public void actionStyleMostlyAvoidsWideShots()
	{
		ShotPlanner planner = new ShotPlanner(new Random(13));
		planner.setStyle(ShotStyle.ACTION);
		int establishing = 0;
		for (int i = 0; i < 40; i++)
		{
			for (ShotPlan.Entry e : planner.plan(traversal(12), FLAT, ALL, 8).getEntries())
			{
				if (e.getType() == ShotType.ESTABLISHING)
				{
					establishing++;
				}
			}
		}
		assertTrue("establishing " + establishing, establishing <= 4);
	}

	@Test
	public void actionStyleFramesTighterAndLower()
	{
		CameraPose base = new CameraPose(0, 0, 0, 0, Math.toRadians(40), 0.4);
		CameraPose action = ShotStyle.ACTION.apply(base, 0, false);
		assertTrue(action.getZoom() > base.getZoom());
		assertTrue(action.getPitch() < base.getPitch());
		assertEquals(base.getYaw(), action.getYaw(), 1e-9);

		// Handheld shake is small and smooth
		CameraPose a = ShotStyle.ACTION.apply(base, 1.0, true);
		CameraPose b = ShotStyle.ACTION.apply(base, 1.0 + 1 / 60.0, true);
		assertTrue(Math.abs(a.getYaw()) <= Math.toRadians(1.2));
		assertTrue(Math.abs(a.getYaw() - b.getYaw()) < Math.toRadians(0.1));
	}

	@Test
	public void sessionReplansWithoutCuttingWhenThePathGrows()
	{
		TraversalDetector detector = new TraversalDetector();
		detector.onObjectClicked(ObjectID.VARLAMORE_WYRM_AGILITY_END_ZIPLINE_TRIGGER, "Grab", 0);
		int walkOn = 11649;
		detector.onTick(PlayerSample.builder().tick(1).serverX(128).poseAnimation(walkOn).idlePose(808).animation(-1).build());
		detector.onTick(PlayerSample.builder().tick(2).renderX(40).serverX(220).poseAnimation(walkOn).idlePose(808).animation(-1).build());
		Traversal t = detector.getCurrent();
		ShotPlanner planner = new ShotPlanner(new Random(2));
		CinematicSession session = new CinematicSession(t, planner.plan(t, FLAT, ALL, 0), 0, 1);
		session.frame(0, 40, 0);
		assertFalse(session.isOutdated());

		detector.onTick(PlayerSample.builder().tick(3).renderX(200).serverX(16 * 128).poseAnimation(-1).idlePose(808).animation(11650).build());
		assertTrue(session.isOutdated());
		session.replan(planner.plan(t, FLAT, ALL, 0));
		assertFalse(session.isOutdated());
		assertFalse("replans ease, never cut", session.frame(1, 300, 0).isCut());
	}

	private static Traversal zipline(int tiles)
	{
		TraversalDetector detector = new TraversalDetector();
		detector.onObjectClicked(ObjectID.WEREWOLF_SLIDE_CENTER, "Teeth-grip", 0);
		detector.onTick(PlayerSample.builder()
			.tick(1)
			.serverX(tiles * 128)
			.animation(1602)
			.poseAnimation(-1)
			.idlePose(808)
			.build());
		return detector.getCurrent();
	}

	/**
	 * A straight eastward traversal of the given length in tiles, built through the detector.
	 */
	private static Traversal traversal(int tiles)
	{
		TraversalDetector detector = new TraversalDetector();
		detector.onObjectClicked(ObjectID.WEREWOLF_SLIDE_CENTER, "Teeth-grip", 0);
		detector.onTick(PlayerSample.builder()
			.tick(1)
			.serverX(tiles * 128)
			.animation(1)
			.poseAnimation(-1)
			.idlePose(808)
			.build());
		return detector.getCurrent();
	}
}
