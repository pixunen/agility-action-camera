package com.agilityactioncamera.detect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * A single obstacle traversal in progress. The path starts where the player
 * model was when the movement began and gains a waypoint every time the
 * server tile moves on (multi-stage obstacles).
 */
public final class Traversal
{
	/**
	 * What made the detector decide this was a traversal.
	 */
	public enum Trigger
	{
		/**
		 * The server tile jumped well ahead of the model (forced movement).
		 */
		LEAP,
		/**
		 * A known travel animation (zipline, rope swing...) started.
		 */
		TRAVEL_ANIMATION,
		/**
		 * An unusual walking pose (tightrope, log) with the server tile moving a tile at a time.
		 * The path grows as the player goes, so progress has to come from time instead.
		 */
		WALK_STYLE,
		PREVIEW,
	}

	@Getter
	private final int id;
	@Getter
	private final Trigger trigger;
	@Getter
	private final int objectId;
	@Getter
	private final int startTick;
	@Getter
	private final int animation;
	private final List<PathPoint> points = new ArrayList<>();
	private double maxProgress;
	@Getter
	private ObstacleKind kind = ObstacleKind.UNKNOWN;
	private boolean kindFromProfile;
	/**
	 * True once the server tile has jumped several tiles in one go, i.e. the
	 * real destination is now known even if the traversal started walk-style.
	 */
	@Getter
	private boolean leapRevealed;

	/**
	 * Server tiles that count as a leap when the path grows by this much at once.
	 */
	static final double LEAP_TILES = 3;

	Traversal(int id, Trigger trigger, int objectId, int startTick, int animation, PathPoint start, PathPoint end)
	{
		this.id = id;
		this.trigger = trigger;
		this.objectId = objectId;
		this.startTick = startTick;
		this.animation = animation;
		points.add(start);
		if (end.distanceTo(start) > 0)
		{
			points.add(end);
		}
	}

	/**
	 * Key for remembering how long this obstacle takes: the clicked object, or
	 * for click-free traversals, the kind of movement.
	 */
	public int learnKey()
	{
		return objectId >= 0 ? objectId : -100 - kind.ordinal();
	}

	/**
	 * A zero-length traversal at the player, for previewing shots.
	 */
	public static Traversal preview(PathPoint at, int tick)
	{
		return new Traversal(-1, Trigger.PREVIEW, -1, tick, -1, at, at);
	}

	void extend(PathPoint p)
	{
		if (p.distanceTo(getEnd()) >= LEAP_TILES * 128)
		{
			leapRevealed = true;
		}
		points.add(p);
	}

	/**
	 * Sets the starting kind from a course profile. Chained obstacles mean the
	 * player can move on to something else without another click, so a later,
	 * clearly different agility animation still wins.
	 */
	void setProfileKind(ObstacleKind kind)
	{
		if (kind != ObstacleKind.UNKNOWN)
		{
			this.kind = kind;
			kindFromProfile = true;
		}
	}

	/**
	 * Refines the kind from what the player is doing.
	 *
	 * @return true if the kind changed
	 */
	boolean refineKind(ObstacleKind observed)
	{
		if (observed == ObstacleKind.UNKNOWN || observed == kind)
		{
			return false;
		}
		// A profile kind is only replaced by movement, not by a ladder reach
		if (kindFromProfile && observed == ObstacleKind.CLIMB)
		{
			return false;
		}
		kind = observed;
		return true;
	}

	public List<PathPoint> getPoints()
	{
		return Collections.unmodifiableList(points);
	}

	public PathPoint getStart()
	{
		return points.get(0);
	}

	public PathPoint getEnd()
	{
		return points.get(points.size() - 1);
	}

	/**
	 * Path length in local units.
	 */
	public double length()
	{
		double len = 0;
		for (int i = 1; i < points.size(); i++)
		{
			len += points.get(i - 1).distanceTo(points.get(i));
		}
		return len;
	}

	public double lengthTiles()
	{
		return length() / 128.0;
	}

	/**
	 * Unit direction from start to end, or north for a degenerate path.
	 */
	public double dirX()
	{
		double d = getStart().distanceTo(getEnd());
		return d < 1 ? 0 : (getEnd().getX() - getStart().getX()) / d;
	}

	public double dirY()
	{
		double d = getStart().distanceTo(getEnd());
		return d < 1 ? 1 : (getEnd().getY() - getStart().getY()) / d;
	}

	/**
	 * How far down the path is the end compared to the start, in local units (positive = descending).
	 */
	public double drop()
	{
		// Heights are negative-up, so a larger value is lower
		return getEnd().getHeight() - getStart().getHeight();
	}

	/**
	 * Progress of a rendered position along the path in [0, 1]. Monotonic:
	 * it never goes backwards, which keeps shots from jittering when the
	 * render position wobbles.
	 */
	public double progress(double x, double y)
	{
		double total = length();
		if (total < 1)
		{
			return maxProgress;
		}

		double best = Double.MAX_VALUE;
		double bestAlong = 0;
		double walked = 0;
		for (int i = 1; i < points.size(); i++)
		{
			PathPoint a = points.get(i - 1);
			PathPoint b = points.get(i);
			double sx = b.getX() - a.getX();
			double sy = b.getY() - a.getY();
			double segLen = Math.hypot(sx, sy);
			double t = segLen < 1 ? 0 : ((x - a.getX()) * sx + (y - a.getY()) * sy) / (segLen * segLen);
			t = Math.max(0, Math.min(1, t));
			double px = a.getX() + sx * t;
			double py = a.getY() + sy * t;
			double d = Math.hypot(x - px, y - py);
			if (d < best)
			{
				best = d;
				bestAlong = walked + segLen * t;
			}
			walked += segLen;
		}

		maxProgress = Math.max(maxProgress, bestAlong / total);
		return maxProgress;
	}

	/**
	 * Height on the path at a given progress, interpolated between path
	 * points. Used instead of the ground height, which is wrong under ziplines
	 * and between rooftops.
	 */
	public double heightAt(double u)
	{
		double total = length();
		if (total < 1)
		{
			return getStart().getHeight();
		}
		double target = Math.max(0, Math.min(1, u)) * total;
		double walked = 0;
		for (int i = 1; i < points.size(); i++)
		{
			PathPoint a = points.get(i - 1);
			PathPoint b = points.get(i);
			double segLen = a.distanceTo(b);
			if (walked + segLen >= target)
			{
				double t = segLen < 1 ? 1 : (target - walked) / segLen;
				return a.getHeight() + (b.getHeight() - a.getHeight()) * t;
			}
			walked += segLen;
		}
		return getEnd().getHeight();
	}

	/**
	 * Position on the path at a given progress.
	 */
	public PathPoint pointAt(double u)
	{
		double total = length();
		if (total < 1)
		{
			return getStart();
		}
		double target = Math.max(0, Math.min(1, u)) * total;
		double walked = 0;
		for (int i = 1; i < points.size(); i++)
		{
			PathPoint a = points.get(i - 1);
			PathPoint b = points.get(i);
			double segLen = a.distanceTo(b);
			if (walked + segLen >= target)
			{
				double t = segLen < 1 ? 1 : (target - walked) / segLen;
				return new PathPoint(
					a.getX() + (b.getX() - a.getX()) * t,
					a.getY() + (b.getY() - a.getY()) * t,
					a.getHeight() + (b.getHeight() - a.getHeight()) * t);
			}
			walked += segLen;
		}
		return getEnd();
	}
}
