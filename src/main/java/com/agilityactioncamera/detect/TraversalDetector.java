package com.agilityactioncamera.detect;

import lombok.Getter;
import lombok.Setter;

/**
 * Detects long obstacle traversals from per-tick player samples.
 * <p>
 * The RuneLite API exposes no forced-movement data, so this relies on the
 * fact that during forced movement the server tile jumps to the destination
 * while the rendered model slides there over several ticks:
 * <ol>
 * <li><b>ARMED</b>: the player clicked an obstacle (catalogued, or any object
 * with an agility verb when generic detection is on).</li>
 * <li><b>TRAVERSING</b>: while armed, the server tile pulls ahead of the model
 * by at least {@code minTiles}, or a known travel animation plays, or (for
 * walk-style obstacles like tightropes) an unusual movement pose persists.</li>
 * <li>Ends when the model catches up with the server tile and the server tile
 * stops moving, or on timeout or teleport.</li>
 * </ol>
 */
public final class TraversalDetector
{
	public enum Transition
	{
		NONE,
		STARTED,
		/**
		 * The path grew or the obstacle kind became clearer.
		 */
		EXTENDED,
		ENDED,
	}

	private enum State
	{
		IDLE,
		ARMED,
		TRAVERSING,
	}

	/**
	 * Ticks the player may take to walk to a clicked obstacle.
	 */
	static final int ARM_TIMEOUT_TICKS = 30;
	static final int TRAVERSAL_TIMEOUT_TICKS = 60;
	static final double TELEPORT_TILES = 20;
	static final double ARRIVED_TILES = 0.25;
	static final double NEW_WAYPOINT_TILES = 0.5;
	static final int POSE_START_TICKS = 2;
	/**
	 * Ticks the player must stand still in an ordinary pose before a traversal
	 * counts as over. Multi-part obstacles (a tightrope that ends in a ledge
	 * drop) pause briefly between parts; this keeps them one traversal.
	 */
	public static final int SETTLE_TICKS = 3;

	@Setter
	private int minTiles = 4;
	@Setter
	private boolean genericDetection = true;
	@Setter
	private boolean walkStyleObstacles = true;
	/**
	 * Start traversals from distinctive agility animations alone. Courses like
	 * Colossal Wyrm chain several movements off a single click.
	 */
	@Setter
	private boolean clickFree = true;

	private State state = State.IDLE;
	private int armedObjectId = -1;
	private int armedUntilTick;
	private int unusualPoseTicks;
	private int nextId = 1;
	private int lastServerX;
	private int lastServerY;
	private int settledTicks;
	/**
	 * The player's own idle/walk/run poses, remembered while they're not on an
	 * obstacle. Obstacles replace the player's walk animation (a tightrope
	 * makes the balance walk "the" walk), so comparing against the current
	 * poses would make every obstacle look ordinary.
	 */
	private int baseIdle = -1;
	private int baseWalk = -1;
	private int baseRun = -1;
	private int lastEndTick = -RELEARN_AFTER_TICKS;
	static final int RELEARN_AFTER_TICKS = 5;

	@Getter
	private Traversal current;
	@Getter
	private boolean lastEndAborted;

	/**
	 * Called when the player clicks a game object.
	 */
	public void onObjectClicked(int objectId, String option, int tick)
	{
		if (state == State.TRAVERSING)
		{
			return;
		}
		if (ObstacleCatalog.isCourseObstacle(objectId) || (genericDetection && ObstacleCatalog.isAgilityVerb(option)))
		{
			state = State.ARMED;
			armedObjectId = objectId;
			armedUntilTick = tick + ARM_TIMEOUT_TICKS;
			unusualPoseTicks = 0;
		}
		else
		{
			disarm();
		}
	}

	/**
	 * Called when the player does anything else that cancels the approach to an obstacle (walking, other clicks).
	 */
	public void onOtherAction()
	{
		if (state == State.ARMED)
		{
			disarm();
		}
	}

	public Transition onTick(PlayerSample s)
	{
		switch (state)
		{
			case IDLE:
				rememberBasePoses(s);
				if (clickFree && looksLikeAgility(s))
				{
					// No click: the kind comes from the animation, never from an earlier click
					state = State.ARMED;
					armedObjectId = -1;
					armedUntilTick = s.getTick() + ARM_TIMEOUT_TICKS;
					unusualPoseTicks = 0;
					return tickArmed(s);
				}
				return Transition.NONE;
			case ARMED:
				return tickArmed(s);
			case TRAVERSING:
				return tickTraversing(s);
			default:
				return Transition.NONE;
		}
	}

	/**
	 * Whether the player is moving the way they normally do: standing, walking
	 * or running with their own poses, with no obstacle animation swapped in.
	 */
	public boolean isOrdinary(PlayerSample s)
	{
		if (baseWalk == -1)
		{
			return s.isOrdinaryPose() || ObstacleCatalog.TURN_POSES.contains(s.getPoseAnimation());
		}
		int pose = s.getPoseAnimation();
		boolean ownPose = pose == -1 || pose == baseIdle || pose == baseWalk || pose == baseRun || ObstacleCatalog.TURN_POSES.contains(pose);
		boolean ownWalk = s.getWalkPose() == baseWalk;
		return ownPose && ownWalk;
	}

	private boolean looksLikeAgility(PlayerSample s)
	{
		// A tick's grace so a landing animation doesn't immediately start a new traversal
		boolean quiet = s.getTick() - lastEndTick >= 2;
		return quiet && (ObstacleCatalog.TRAVEL_ANIMATIONS.contains(s.getAnimation())
			|| ObstacleKind.isAgilityAnimation(s.getAnimation())
			|| ObstacleKind.isAgilityAnimation(s.getPoseAnimation()));
	}

	private void rememberBasePoses(PlayerSample s)
	{
		// Only learn from a player who looks normal by their own standards, well
		// after any obstacle, and never from a walk an obstacle swapped in
		boolean quiet = s.getTick() - lastEndTick >= RELEARN_AFTER_TICKS;
		boolean agilityWalk = ObstacleKind.forAnimation(s.getWalkPose()) != ObstacleKind.UNKNOWN;
		if (quiet && !agilityWalk && s.isOrdinaryPose() && s.getAnimation() == -1 && s.getWalkPose() != -1)
		{
			baseIdle = s.getIdlePose();
			baseWalk = s.getWalkPose();
			baseRun = s.getRunPose();
		}
	}

	/**
	 * Abandons any traversal, e.g. on logout or region change.
	 */
	public void reset()
	{
		disarm();
		current = null;
		// A logout can restart the tick counter; don't let an old end tick block detection
		lastEndTick = -RELEARN_AFTER_TICKS;
	}

	public boolean isTraversing()
	{
		return state == State.TRAVERSING;
	}

	private Transition tickArmed(PlayerSample s)
	{
		if (s.getTick() > armedUntilTick)
		{
			disarm();
			return Transition.NONE;
		}

		double divergence = s.divergenceTiles();
		boolean travelAnim = ObstacleCatalog.TRAVEL_ANIMATIONS.contains(s.getAnimation());
		boolean ordinary = isOrdinary(s);
		boolean unusualMotion = s.getAnimation() != -1 || !ordinary;
		unusualPoseTicks = !ordinary ? unusualPoseTicks + 1 : 0;

		boolean leap = divergence >= minTiles && unusualMotion;
		boolean knownTravel = travelAnim && divergence >= 1;
		boolean walkStyle = walkStyleObstacles && unusualPoseTicks >= POSE_START_TICKS && divergence >= 0.5;

		if (!leap && !knownTravel && !walkStyle)
		{
			return Transition.NONE;
		}

		Traversal.Trigger trigger = leap ? Traversal.Trigger.LEAP : knownTravel ? Traversal.Trigger.TRAVEL_ANIMATION : Traversal.Trigger.WALK_STYLE;
		PathPoint start = new PathPoint(s.getRenderX(), s.getRenderY(), s.getRenderHeight());
		PathPoint end = new PathPoint(s.getServerX(), s.getServerY(), s.getServerHeight());
		current = new Traversal(nextId++, trigger, armedObjectId, s.getTick(), s.getAnimation(), start, end);
		current.setProfileKind(ObstacleKind.forObject(armedObjectId));
		current.refineKind(observedKind(s));
		lastServerX = s.getServerX();
		lastServerY = s.getServerY();
		state = State.TRAVERSING;
		settledTicks = 0;
		lastEndAborted = false;
		return Transition.STARTED;
	}

	private Transition tickTraversing(PlayerSample s)
	{
		double serverMoved = Math.hypot(s.getServerX() - lastServerX, s.getServerY() - lastServerY) / 128.0;
		if (serverMoved >= TELEPORT_TILES || s.getTick() - current.getStartTick() > TRAVERSAL_TIMEOUT_TICKS)
		{
			return end(true, s.getTick());
		}

		Transition result = current.refineKind(observedKind(s)) ? Transition.EXTENDED : Transition.NONE;
		boolean normal = isMovingNormally(s);
		if (serverMoved >= NEW_WAYPOINT_TILES)
		{
			// Plain running to the next obstacle isn't part of this one
			if (!normal)
			{
				current.extend(new PathPoint(s.getServerX(), s.getServerY(), s.getServerHeight()));
				result = Transition.EXTENDED;
			}
			lastServerX = s.getServerX();
			lastServerY = s.getServerY();
		}
		// Over once the player is back to plain standing, walking or running for a
		// moment; they don't have to stop, since courses are run without stopping
		settledTicks = normal ? settledTicks + 1 : 0;
		if (settledTicks >= SETTLE_TICKS)
		{
			return end(false, s.getTick());
		}
		return result;
	}

	private static ObstacleKind observedKind(PlayerSample s)
	{
		ObstacleKind kind = ObstacleKind.forAnimation(s.getAnimation());
		return kind != ObstacleKind.UNKNOWN ? kind : ObstacleKind.forAnimation(s.getPoseAnimation());
	}

	/**
	 * No obstacle animation and the player's own poses: they've landed and are
	 * standing, walking or running as usual.
	 */
	public boolean isMovingNormally(PlayerSample s)
	{
		return s.getAnimation() == -1 && isOrdinary(s);
	}

	public boolean isArmed()
	{
		return state == State.ARMED;
	}

	private Transition end(boolean aborted, int tick)
	{
		lastEndAborted = aborted;
		lastEndTick = tick;
		state = State.IDLE;
		armedObjectId = -1;
		return Transition.ENDED;
	}

	private void disarm()
	{
		state = State.IDLE;
		armedObjectId = -1;
		unusualPoseTicks = 0;
	}
}
