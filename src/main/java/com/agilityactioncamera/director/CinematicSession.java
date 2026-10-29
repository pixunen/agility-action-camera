package com.agilityactioncamera.director;

import com.agilityactioncamera.detect.ObstacleKind;
import com.agilityactioncamera.detect.Traversal;
import com.agilityactioncamera.rig.CameraPose;
import lombok.Getter;
import lombok.Value;

/**
 * One traversal's worth of camera direction: turns live player position into
 * the pose the current shot wants, and reports cuts between shots.
 */
public final class CinematicSession
{
	@Value
	public static class Frame
	{
		CameraPose pose;
		ShotType shot;
		double u;
		/**
		 * True on the first frame of a new shot, so the rig can hard-cut instead of easing.
		 */
		boolean cut;
	}

	@Getter
	private final Traversal traversal;
	@Getter
	private ShotPlan plan;
	/**
	 * Path length and kind the current plan was made for, to spot when the traversal outgrows it.
	 */
	private double plannedLength;
	private ObstacleKind plannedKind;
	/**
	 * Expected duration, used for walk-style traversals whose path is not known up front.
	 */
	private final long expectedNanos;
	private final long startNanos;
	private int currentIndex = -1;
	private long shotStartNanos;
	private long landedNanos = -1;
	private boolean replanned;

	/**
	 * Whether {@code expectedNanos} was measured, rather than a guess.
	 */
	private final boolean durationKnown;

	public CinematicSession(Traversal traversal, ShotPlan plan, long startNanos, long expectedNanos)
	{
		this(traversal, plan, startNanos, expectedNanos, false);
	}

	public CinematicSession(Traversal traversal, ShotPlan plan, long startNanos, long expectedNanos, boolean durationKnown)
	{
		this.durationKnown = durationKnown;
		this.traversal = traversal;
		this.plan = plan;
		this.plannedLength = traversal.length();
		this.plannedKind = traversal.getKind();
		this.startNanos = startNanos;
		this.expectedNanos = expectedNanos;
	}

	public Frame frame(long nowNanos, double renderX, double renderY)
	{
		double u;
		if (landedNanos >= 0)
		{
			u = 1;
		}
		else if ((traversal.getTrigger() == Traversal.Trigger.WALK_STYLE && !traversal.isLeapRevealed())
			|| traversal.getTrigger() == Traversal.Trigger.PREVIEW)
		{
			// Never reach 1 by time alone; landing does that
			u = Math.min(0.99, (nowNanos - startNanos) / (double) expectedNanos);
		}
		else
		{
			u = traversal.progress(renderX, renderY);
		}
		int index = plan.indexAt(u);
		boolean cut = index != currentIndex && !replanned;
		if (index != currentIndex || replanned)
		{
			currentIndex = index;
			shotStartNanos = nowNanos;
			replanned = false;
		}

		ShotPlan.Entry entry = plan.getEntries().get(index);
		double t = (nowNanos - shotStartNanos) / 1e9;
		ShotContext c = ShotPlanner.context(traversal, entry.getSide(), u, t).toBuilder()
			.subjectX(renderX)
			.subjectY(renderY)
			.build();

		// The first shot eases in from the player's own camera; only later shots hard-cut
		return new Frame(entry.getType().frame(c), entry.getType(), u, cut && currentIndex > 0);
	}

	/**
	 * Whether progress means "how close to the end": true when the destination
	 * is known, or the obstacle's duration has been measured before. Early
	 * hand-back relies on it.
	 */
	public boolean isProgressReliable()
	{
		boolean timeBased = traversal.getTrigger() == Traversal.Trigger.WALK_STYLE && !traversal.isLeapRevealed();
		return traversal.getTrigger() != Traversal.Trigger.PREVIEW && (!timeBased || durationKnown);
	}

	/**
	 * Seconds into the current shot.
	 */
	public double shotSeconds(long nowNanos)
	{
		return (nowNanos - shotStartNanos) / 1e9;
	}

	/**
	 * Whether the traversal has turned out different enough from what the plan
	 * assumed (much longer, or a different kind of obstacle) to plan again.
	 */
	public boolean isOutdated()
	{
		if (landedNanos >= 0)
		{
			return false;
		}
		boolean grew = traversal.length() > plannedLength * 1.5 + 2 * 128;
		return grew || traversal.getKind() != plannedKind;
	}

	/**
	 * Switches to a new plan. The camera eases into it rather than cutting, so
	 * the change reads as a deliberate camera move.
	 */
	public void replan(ShotPlan newPlan)
	{
		plan = newPlan;
		plannedLength = traversal.length();
		plannedKind = traversal.getKind();
		replanned = true;
	}

	/**
	 * The player has arrived; the current shot holds on them briefly before handing back.
	 */
	public void landed(long nowNanos)
	{
		if (landedNanos < 0)
		{
			landedNanos = nowNanos;
		}
	}

	public boolean isLanded()
	{
		return landedNanos >= 0;
	}

	public boolean isHoldOver(long nowNanos, long holdNanos)
	{
		return landedNanos >= 0 && nowNanos - landedNanos >= holdNanos;
	}
}
