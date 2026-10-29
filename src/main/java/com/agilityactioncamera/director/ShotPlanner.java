package com.agilityactioncamera.director;

import com.agilityactioncamera.detect.ObstacleKind;
import com.agilityactioncamera.detect.PathPoint;
import com.agilityactioncamera.detect.Traversal;
import com.agilityactioncamera.rig.Angles;
import com.agilityactioncamera.rig.CameraPose;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Set;
import lombok.Setter;

/**
 * Picks shots for a traversal. Every enabled shot is tried on both sides of
 * the path and scored on:
 * <ul>
 * <li><b>fit</b>: how well the shot suits the path shape (long, descending, short)</li>
 * <li><b>clearance</b>: sampled line of sight from the estimated camera position to the
 * player, against terrain and the edge of the loaded scene</li>
 * <li><b>variety</b>: a penalty for repeating recent shots</li>
 * <li>a little randomness, so laps don't all look the same</li>
 * </ul>
 */
public final class ShotPlanner
{
	/**
	 * Chest height above the path, in local units (negative is up).
	 */
	public static final double SUBJECT_HEIGHT_OFFSET = -90;

	private static final double[] SAMPLE_US = {0.1, 0.5, 0.9};
	private static final int RAY_STEPS = 10;
	private static final int HISTORY = 3;

	private final Deque<ShotType> history = new ArrayDeque<>();
	private final Random random;
	@Setter
	private ShotStyle style = ShotStyle.ACTION;

	public ShotPlanner(Random random)
	{
		this.random = random;
	}

	/**
	 * @param cutMinTiles split into two shots when the path is at least this long; 0 disables cuts
	 */
	public ShotPlan plan(Traversal traversal, Terrain terrain, Set<ShotType> enabled, int cutMinTiles)
	{
		List<ShotPlan.Entry> entries = new ArrayList<>();
		if (enabled.isEmpty())
		{
			return new ShotPlan(entries);
		}

		boolean cut = cutMinTiles > 0 && traversal.lengthTiles() >= cutMinTiles && enabled.size() > 1;
		if (cut)
		{
			ShotPlan.Entry first = best(traversal, terrain, enabled, 0, 0.5, null);
			ShotPlan.Entry second = best(traversal, terrain, enabled, 0.5, 1, first.getType());
			entries.add(first);
			entries.add(second);
		}
		else
		{
			entries.add(best(traversal, terrain, enabled, 0, 1, null));
		}

		for (ShotPlan.Entry e : entries)
		{
			remember(e.getType());
		}
		return new ShotPlan(entries);
	}

	public void clearHistory()
	{
		history.clear();
	}

	private ShotPlan.Entry best(Traversal traversal, Terrain terrain, Set<ShotType> enabled, double fromU, double toU, ShotType exclude)
	{
		ShotPlan.Entry best = null;
		for (ShotType type : enabled)
		{
			if (type == exclude)
			{
				continue;
			}
			for (int side : new int[]{1, -1})
			{
				double score = score(type, side, traversal, terrain, fromU, toU);
				if (best == null || score > best.getScore())
				{
					best = new ShotPlan.Entry(type, side, fromU, score);
				}
			}
		}
		if (best == null)
		{
			// Only the excluded shot is enabled; reuse it from the other side
			best = new ShotPlan.Entry(exclude, -1, fromU, 0);
		}
		return best;
	}

	double score(ShotType type, int side, Traversal traversal, Terrain terrain, double fromU, double toU)
	{
		ShotContext base = context(traversal, side, 0, 0);
		double score = type.fit(base) + kindBonus(type, traversal.getKind()) + style.bias(type);

		double blocked = 0;
		for (double s : SAMPLE_US)
		{
			double u = fromU + (toU - fromU) * s;
			ShotContext c = context(traversal, side, u, s * 3);
			blocked += occlusion(type.frame(c), c, terrain);
		}
		score -= 1.5 * blocked / SAMPLE_US.length;

		int recency = 0;
		for (ShotType recent : history)
		{
			if (recent == type)
			{
				score -= 0.45 / (1 + recency);
			}
			recency++;
		}

		return score + random.nextDouble() * 0.3;
	}

	/**
	 * How much an obstacle kind favours a shot. Ziplines want to show the whole
	 * line, balances want to sit close behind or beside the player, and so on.
	 */
	static double kindBonus(ShotType type, ObstacleKind kind)
	{
		switch (kind)
		{
			case ZIPLINE:
				switch (type)
				{
					case ESTABLISHING:
						return 0.6;
					case FLYBY:
						return 0.45;
					case TRACKING:
						return 0.2;
					case HERO:
					case ORBIT:
						return -0.5;
					default:
						return 0;
				}
			case SWING:
				return type == ShotType.FLYBY ? 0.45 : type == ShotType.TRACKING ? 0.3 : 0;
			case JUMP:
				return type == ShotType.TRACKING ? 0.3 : type == ShotType.HERO || type == ShotType.CRANE ? 0.2 : 0;
			case BALANCE:
				return type == ShotType.CHASE || type == ShotType.TRACKING ? 0.3 : type == ShotType.HERO ? 0.2 : 0;
			case MONKEYBARS:
				return type == ShotType.TRACKING ? 0.4 : type == ShotType.ORBIT ? 0.2 : 0;
			case CLIMB:
				return type == ShotType.ORBIT || type == ShotType.CRANE ? 0.3 : 0;
			default:
				return 0;
		}
	}

	/**
	 * Builds a context for progress {@code u}, with the subject on the path.
	 */
	public static ShotContext context(Traversal traversal, int side, double u, double t)
	{
		PathPoint at = traversal.pointAt(u);
		return ShotContext.builder()
			.start(traversal.getStart())
			.end(traversal.getEnd())
			.dirX(traversal.dirX())
			.dirY(traversal.dirY())
			.lengthTiles(traversal.lengthTiles())
			.drop(traversal.drop())
			.side(side)
			.u(u)
			.t(t)
			.subjectX(at.getX())
			.subjectY(at.getY())
			.subjectHeight(at.getHeight() + SUBJECT_HEIGHT_OFFSET)
			.build();
	}

	/**
	 * Estimated distance from the focal point to the camera. The client
	 * derives the real one from pitch and zoom; this approximation is only
	 * used for scoring, never for positioning.
	 */
	static double estimatedDistance(double zoom)
	{
		return 1600 - 900 * zoom;
	}

	/**
	 * Fraction in [0, 1] of the line of sight that is under ground or outside the scene.
	 */
	static double occlusion(CameraPose pose, ShotContext c, Terrain terrain)
	{
		double dist = estimatedDistance(pose.getZoom());
		double horizontal = dist * Math.cos(pose.getPitch());
		double camX = pose.getX() - Angles.forwardX(pose.getYaw()) * horizontal;
		double camY = pose.getY() - Angles.forwardY(pose.getYaw()) * horizontal;
		double camH = pose.getHeight() - dist * Math.sin(pose.getPitch());

		int blocked = 0;
		for (int i = 0; i <= RAY_STEPS; i++)
		{
			double t = (double) i / RAY_STEPS;
			double x = camX + (c.getSubjectX() - camX) * t;
			double y = camY + (c.getSubjectY() - camY) * t;
			double h = camH + (c.getSubjectHeight() - camH) * t;
			double ground = terrain.heightAt(x, y);
			// Heights are negative-up: the ray is underground when it's below (greater than) the ground
			if (Double.isNaN(ground) || h > ground - 16)
			{
				blocked++;
			}
		}
		return (double) blocked / (RAY_STEPS + 1);
	}

	private void remember(ShotType type)
	{
		history.addFirst(type);
		while (history.size() > HISTORY)
		{
			history.removeLast();
		}
	}
}
