package com.agilityactioncamera.detect;

import com.agilityactioncamera.detect.TraversalDetector.Transition;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ObjectID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class TraversalDetectorTest
{
	private static final int IDLE = 808;
	private static final int WALK = 819;
	private static final int RUN = 824;
	private static final int BALANCE_POSE = 762;
	private static final int JUMP = 2586;

	private TraversalDetector detector;
	private int tick;

	@Before
	public void before()
	{
		detector = new TraversalDetector();
		tick = 100;
	}

	@Test
	public void deathslideIsDetectedAndEnds()
	{
		detector.onObjectClicked(ObjectID.WEREWOLF_SLIDE_CENTER, "Teeth-grip", tick);

		// Walking up to the slide: render lags the server by a tile or two
		assertEquals(Transition.NONE, detector.onTick(sample(0, 0, 1, 0, -1, RUN)));
		assertEquals(Transition.NONE, detector.onTick(sample(1, 0, 2, 0, -1, RUN)));

		// Forced movement: server jumps 12 tiles ahead, model still at the start
		assertEquals(Transition.STARTED, detector.onTick(sample(2, 0, 14, 0, AnimationID.ZIPLINE_SLIDE, IDLE)));
		Traversal t = detector.getCurrent();
		assertEquals(Traversal.Trigger.LEAP, t.getTrigger());
		assertEquals(12, t.lengthTiles(), 1e-9);

		// Sliding along
		assertEquals(Transition.NONE, detector.onTick(sample(6, 0, 14, 0, AnimationID.ZIPLINE_SLIDE, IDLE)));
		assertEquals(Transition.NONE, detector.onTick(sample(11, 0, 14, 0, AnimationID.ZIPLINE_SLIDE, IDLE)));
		assertEquals(0.75, t.progress(11 * 128, 0), 1e-9);

		// Landed; it only counts once the player has stood still for a moment
		assertEquals(Transition.NONE, detector.onTick(sample(14, 0, 14, 0, -1, IDLE)));
		assertEquals(Transition.NONE, detector.onTick(sample(14, 0, 14, 0, -1, IDLE)));
		assertEquals(Transition.ENDED, detector.onTick(sample(14, 0, 14, 0, -1, IDLE)));
		assertFalse(detector.isLastEndAborted());
		assertFalse(detector.isTraversing());
	}

	@Test
	public void ordinaryRunningIsIgnored()
	{
		detector.onObjectClicked(ObjectID.WEREWOLF_SLIDE_CENTER, "Teeth-grip", tick);
		for (int i = 0; i < 10; i++)
		{
			assertEquals(Transition.NONE, detector.onTick(sample(i * 2, 0, i * 2 + 2, 0, -1, RUN)));
		}
	}

	@Test
	public void unknownObjectsNeedAnAgilityVerb()
	{
		detector.onObjectClicked(1, "Open", tick);
		assertEquals(Transition.NONE, detector.onTick(sample(0, 0, 10, 0, JUMP, IDLE)));

		detector.onObjectClicked(1, "Jump", tick);
		assertEquals(Transition.STARTED, detector.onTick(sample(0, 0, 10, 0, JUMP, IDLE)));
	}

	@Test
	public void genericDetectionCanBeDisabled()
	{
		detector.setGenericDetection(false);
		detector.onObjectClicked(1, "Jump", tick);
		assertEquals(Transition.NONE, detector.onTick(sample(0, 0, 10, 0, JUMP, IDLE)));
	}

	@Test
	public void armingExpires()
	{
		detector.onObjectClicked(ObjectID.WEREWOLF_SLIDE_CENTER, "Teeth-grip", tick);
		tick += TraversalDetector.ARM_TIMEOUT_TICKS + 1;
		assertEquals(Transition.NONE, detector.onTick(sample(0, 0, 12, 0, AnimationID.ZIPLINE_SLIDE, IDLE)));
	}

	@Test
	public void walkingAwayDisarms()
	{
		detector.onObjectClicked(ObjectID.WEREWOLF_SLIDE_CENTER, "Teeth-grip", tick);
		detector.onOtherAction();
		// A generic animation with a big gap is only an obstacle if the player asked for one
		assertEquals(Transition.NONE, detector.onTick(sample(0, 0, 12, 0, JUMP, IDLE)));
	}

	@Test
	public void walkStyleObstacleGrowsItsPath()
	{
		detector.onObjectClicked(ObjectID.GNOME_LOG_BALANCE1, "Walk-across", tick);
		assertEquals(Transition.NONE, detector.onTick(sample(0, 0, 1, 0, -1, BALANCE_POSE)));
		assertEquals(Transition.STARTED, detector.onTick(sample(0.5, 0, 1.5, 0, -1, BALANCE_POSE)));
		assertEquals(Traversal.Trigger.WALK_STYLE, detector.getCurrent().getTrigger());

		for (int x = 2; x <= 7; x++)
		{
			assertEquals(Transition.EXTENDED, detector.onTick(sample(x - 1, 0, x, 0, -1, BALANCE_POSE)));
		}
		assertEquals(Transition.NONE, detector.onTick(sample(7, 0, 7, 0, -1, IDLE)));
		assertEquals(Transition.NONE, detector.onTick(sample(7, 0, 7, 0, -1, IDLE)));
		assertEquals(Transition.ENDED, detector.onTick(sample(7, 0, 7, 0, -1, IDLE)));
		assertEquals(7, detector.getCurrent().getEnd().getX() / 128, 1e-9);
	}

	/**
	 * What the Colossal Wyrm zipline looked like in-game: it starts as a short
	 * walk onto the platform, then the server tile leaps to the far end.
	 */
	@Test
	public void ziplineThatStartsWithAWalkOnRevealsItsLeap()
	{
		int zipline = ObjectID.VARLAMORE_WYRM_AGILITY_END_ZIPLINE_TRIGGER;
		detector.onObjectClicked(zipline, "Grab", tick);
		detector.onTick(sample(0, 0, 1, 0, -1, AnimationID.WYRM_AGILITY_ZIPLINE_WALK));
		assertEquals(Transition.STARTED, detector.onTick(sample(0.3, 0, 1.7, 0, -1, AnimationID.WYRM_AGILITY_ZIPLINE_WALK)));
		Traversal t = detector.getCurrent();
		assertEquals(ObstacleKind.ZIPLINE, t.getKind());
		assertFalse(t.isLeapRevealed());

		assertEquals(Transition.EXTENDED, detector.onTick(sample(1.6, 0, 16, 0, AnimationID.WYRM_AGILITY_ZIPLINE_ON, IDLE)));
		assertTrue(t.isLeapRevealed());
		assertEquals(15.7, t.lengthTiles(), 0.01);
	}

	@Test
	public void unknownObstacleKindComesFromTheAnimation()
	{
		detector.onObjectClicked(1, "Swing-on", tick);
		detector.onTick(sample(0, 0, 8, 0, AnimationID.HUMAN_ROPESWING_LONG, IDLE));
		assertEquals(ObstacleKind.SWING, detector.getCurrent().getKind());
	}

	@Test
	public void profileSetsTheStartingKindAndMovementRefinesIt()
	{
		detector.onObjectClicked(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_JUMP_1_TRIGGER, "Jump", tick);
		detector.onTick(sample(0, 0, 5, 0, 2586, IDLE));
		Traversal t = detector.getCurrent();
		assertEquals(ObstacleKind.JUMP, t.getKind());

		// Reaching for a ladder doesn't override a profile
		detector.onTick(sample(1, 0, 5, 0, AnimationID.HUMAN_REACHFORLADDER, IDLE));
		assertEquals(ObstacleKind.JUMP, t.getKind());

		// A chained balance without a new click does
		detector.onTick(sample(2, 0, 5, 0, AnimationID.WYRM_AGILITY_TIGHTROPE_WOBBLE, IDLE));
		assertEquals(ObstacleKind.BALANCE, t.getKind());
	}

	/**
	 * Courses are run without stopping: running on to the next obstacle ends
	 * the traversal and doesn't stretch its path.
	 */
	@Test
	public void runningOnEndsTheTraversal()
	{
		detector.onObjectClicked(ObjectID.WEREWOLF_SLIDE_CENTER, "Teeth-grip", tick);
		detector.onTick(sample(0, 0, 12, 0, AnimationID.ZIPLINE_SLIDE, IDLE));
		Traversal t = detector.getCurrent();
		detector.onTick(sample(12, 0, 12, 0, AnimationID.ZIPLINE_SLIDE, IDLE));
		// Landed and running straight on, render a couple of tiles behind the server
		assertNotEquals(Transition.ENDED, detector.onTick(sample(12, 0, 14, 0, -1, RUN)));
		assertNotEquals(Transition.ENDED, detector.onTick(sample(14, 0, 16, 0, -1, RUN)));
		assertEquals(Transition.ENDED, detector.onTick(sample(16, 0, 18, 0, -1, RUN)));
		assertEquals("running isn't part of the path", 12, t.lengthTiles(), 1e-9);
	}

	@Test
	public void wyrmMonkeybarsAreKnown()
	{
		assertTrue(ObstacleCatalog.isCourseObstacle(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_MONKEYBARS_1_TRIGGER));
		assertTrue(ObstacleCatalog.isCourseObstacle(ObjectID.VARLAMORE_WYRM_AGILITY_MULTIJUMP_TERMITES));
	}

	/**
	 * Replays the Colossal Wyrm advanced tightrope from a real detection log.
	 * The obstacle swaps the player's walk animation to the balance walk, so
	 * the pose "matches the walk" the whole way across; only comparing against
	 * the player's own remembered poses reveals it. It ends in a ledge drop,
	 * which must stay part of the same traversal.
	 */
	@Test
	public void wyrmTightropeFromRealLog()
	{
		int balanceWalk = 762;
		int balanceIdle = 763;
		int wobble = AnimationID.WYRM_AGILITY_TIGHTROPE_WOBBLE;

		// Standing around normally first, so the detector learns the player's own poses
		for (int i = 0; i < 6; i++)
		{
			detector.onTick(pose(43, 27, 43, 27, -1, IDLE, WALK));
		}
		detector.onObjectClicked(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_BALANCE_1_TRIGGER, "Cross", tick);

		// Walking to the rope
		assertEquals(Transition.NONE, detector.onTick(pose(43, 27, 42, 28, -1, WALK, WALK)));
		assertEquals(Transition.NONE, detector.onTick(pose(42, 28, 41, 28, -1, WALK, WALK)));
		// On the rope: walk animation swapped to the balance walk
		assertEquals(Transition.NONE, detector.onTick(pose(41, 28, 40, 29, -1, balanceWalk, balanceWalk)));
		assertEquals(Transition.STARTED, detector.onTick(pose(40, 29, 39, 30, -1, balanceWalk, balanceWalk)));
		Traversal t = detector.getCurrent();
		assertEquals(ObstacleKind.BALANCE, t.getKind());

		detector.onTick(pose(39, 30, 38, 31, -1, balanceWalk, balanceWalk));
		// Wobbling in place for a few ticks must not end it
		for (int i = 0; i < 4; i++)
		{
			assertNotEquals(Transition.ENDED, detector.onTick(pose(38, 31, 38, 31, wobble, balanceIdle, balanceWalk)));
		}
		detector.onTick(pose(38, 31, 37, 32, -1, balanceIdle, balanceWalk));
		detector.onTick(pose(37, 32, 36, 33, -1, balanceWalk, balanceWalk));
		detector.onTick(pose(36, 33, 35, 34, -1, balanceWalk, balanceWalk));
		// Off the rope, a one-tick pause, then the ledge drop
		assertNotEquals(Transition.ENDED, detector.onTick(pose(35, 34, 35, 34, -1, WALK, WALK)));
		assertNotEquals(Transition.ENDED, detector.onTick(pose(35, 34, 35, 34, -1, IDLE, WALK)));
		assertNotEquals(Transition.ENDED, detector.onTick(pose(35, 34, 34, 36, 1995, 823, WALK)));
		detector.onTick(pose(34, 35, 34, 36, 1995, 823, WALK));
		detector.onTick(pose(34, 36, 34, 36, -1, IDLE, WALK));
		detector.onTick(pose(34, 36, 34, 36, -1, IDLE, WALK));
		assertEquals(Transition.ENDED, detector.onTick(pose(34, 36, 34, 36, -1, IDLE, WALK)));

		assertTrue("one traversal covering rope and ledge: " + t.lengthTiles(), t.lengthTiles() > 8);
	}

	/**
	 * One click, several movements: the tightrope after a chained jump must be
	 * picked up from its animation alone.
	 */
	@Test
	public void chainedObstacleNeedsNoClick()
	{
		for (int i = 0; i < 6; i++)
		{
			detector.onTick(pose(0, 0, 0, 0, -1, IDLE, WALK));
		}
		assertEquals(Transition.NONE, detector.onTick(pose(0, 0, 1, 0, -1, 762, 762)));
		assertEquals(Transition.STARTED, detector.onTick(pose(0.5, 0, 2, 0, -1, 762, 762)));
		assertEquals(-1, detector.getCurrent().getObjectId());
		assertEquals(ObstacleKind.BALANCE, detector.getCurrent().getKind());
	}

	@Test
	public void clickFreeDetectionCanBeDisabled()
	{
		detector.setClickFree(false);
		for (int i = 0; i < 6; i++)
		{
			detector.onTick(pose(0, 0, 0, 0, -1, IDLE, WALK));
		}
		detector.onTick(pose(0, 0, 1, 0, -1, 762, 762));
		assertEquals(Transition.NONE, detector.onTick(pose(0.5, 0, 2, 0, -1, 762, 762)));
	}

	@Test
	public void turningOnTheSpotIsOrdinary()
	{
		for (int i = 0; i < 6; i++)
		{
			detector.onTick(pose(0, 0, 0, 0, -1, IDLE, WALK));
		}
		assertTrue(detector.isOrdinary(pose(0, 0, 1, 0, -1, AnimationID.HUMAN_TURNONSPOT, WALK)));
		assertTrue(detector.isOrdinary(pose(0, 0, 1, 0, -1, AnimationID.HUMAN_WALK_L, WALK)));
	}

	@Test
	public void obstacleWalkIsNotLearnedAsTheNormalWalk()
	{
		for (int i = 0; i < 6; i++)
		{
			detector.onTick(pose(0, 0, 0, 0, -1, IDLE, WALK));
		}
		// Right after an obstacle the game can still have the balance walk active
		detector.onTick(pose(0, 0, 0, 0, -1, 762, 762));
		PlayerSample balancing = pose(0, 0, 1, 0, -1, 762, 762);
		assertFalse(detector.isOrdinary(balancing));
	}

	@Test
	public void teleportAborts()
	{
		detector.onObjectClicked(ObjectID.WEREWOLF_SLIDE_CENTER, "Teeth-grip", tick);
		detector.onTick(sample(0, 0, 12, 0, AnimationID.ZIPLINE_SLIDE, IDLE));
		assertTrue(detector.isTraversing());
		assertEquals(Transition.ENDED, detector.onTick(sample(60, 60, 60, 60, -1, IDLE)));
		assertTrue(detector.isLastEndAborted());
	}

	@Test
	public void timesOut()
	{
		detector.onObjectClicked(ObjectID.WEREWOLF_SLIDE_CENTER, "Teeth-grip", tick);
		detector.onTick(sample(0, 0, 12, 0, AnimationID.ZIPLINE_SLIDE, IDLE));
		tick += TraversalDetector.TRAVERSAL_TIMEOUT_TICKS + 1;
		assertEquals(Transition.ENDED, detector.onTick(sample(1, 0, 12, 0, AnimationID.ZIPLINE_SLIDE, IDLE)));
		assertTrue(detector.isLastEndAborted());
	}

	/**
	 * Like {@link #sample} but with the current walk animation, which obstacles swap out.
	 */
	private PlayerSample pose(double renderX, double renderY, double serverX, double serverY, int animation, int pose, int walk)
	{
		return PlayerSample.builder()
			.tick(tick++)
			.renderX((int) Math.round(renderX * 128))
			.renderY((int) Math.round(renderY * 128))
			.serverX((int) Math.round(serverX * 128))
			.serverY((int) Math.round(serverY * 128))
			.animation(animation)
			.poseAnimation(pose)
			.idlePose(walk == WALK ? IDLE : 763)
			.walkPose(walk)
			.runPose(walk == WALK ? RUN : walk)
			.build();
	}

	/**
	 * Positions in tiles; advances the tick.
	 */
	private PlayerSample sample(double renderX, double renderY, double serverX, double serverY, int animation, int pose)
	{
		return PlayerSample.builder()
			.tick(tick++)
			.renderX((int) Math.round(renderX * 128))
			.renderY((int) Math.round(renderY * 128))
			.serverX((int) Math.round(serverX * 128))
			.serverY((int) Math.round(serverY * 128))
			.animation(animation)
			.poseAnimation(pose)
			.idlePose(IDLE)
			.walkPose(WALK)
			.runPose(RUN)
			.build();
	}
}
