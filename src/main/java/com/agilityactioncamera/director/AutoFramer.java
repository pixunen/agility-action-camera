package com.agilityactioncamera.director;

import com.agilityactioncamera.rig.CameraPose;
import lombok.Getter;

/**
 * Keeps the player in frame by watching where their model actually lands on
 * screen and nudging the focal point towards it.
 * <p>
 * Shots aim at where the path says the player should be. That is wrong
 * whenever an animation carries the model away from its tile (ziplines hang
 * the player metres above the ground) or the path is still being discovered.
 * This closes the loop: an integrating controller with a dead zone, so the
 * composition each shot intends is kept and only real drift is corrected.
 */
public final class AutoFramer
{
	/**
	 * Fraction of the viewport the player may drift from centre before we correct.
	 */
	static final double DEAD_ZONE_VERTICAL = 0.08;
	static final double DEAD_ZONE_HORIZONTAL = 0.22;
	/**
	 * Local units per second of correction for a full-viewport error.
	 */
	static final double GAIN_HEIGHT = 2400;
	static final double GAIN_LATERAL = 1800;
	static final double MAX_HEIGHT = 1600;
	static final double MAX_LATERAL = 900;
	/**
	 * How fast corrections bleed away once the player is back in the comfortable zone.
	 */
	static final double DECAY_PER_SECOND = 0.35;

	@Getter
	private double heightOffset;
	@Getter
	private double lateralOffset;

	public void reset()
	{
		heightOffset = 0;
		lateralOffset = 0;
	}

	/**
	 * @param errorX player's horizontal screen offset from centre, as a fraction of viewport width (+ is right)
	 * @param errorY vertical offset as a fraction of viewport height (+ is below centre)
	 */
	public void update(double errorX, double errorY, double dt)
	{
		double ey = outside(errorY, DEAD_ZONE_VERTICAL);
		double ex = outside(errorX, DEAD_ZONE_HORIZONTAL);

		// Player below centre: move the focal point down, which is a larger height value
		heightOffset = clamp(ey == 0 ? decay(heightOffset, dt) : heightOffset + ey * GAIN_HEIGHT * dt, MAX_HEIGHT);
		lateralOffset = clamp(ex == 0 ? decay(lateralOffset, dt) : lateralOffset + ex * GAIN_LATERAL * dt, MAX_LATERAL);
	}

	/**
	 * Applies the correction to a pose. Lateral moves along the camera's right vector.
	 */
	public CameraPose apply(CameraPose pose)
	{
		double rightX = Math.cos(pose.getYaw());
		double rightY = Math.sin(pose.getYaw());
		return pose
			.withX(pose.getX() + rightX * lateralOffset)
			.withY(pose.getY() + rightY * lateralOffset)
			.withHeight(pose.getHeight() + heightOffset);
	}

	private static double outside(double error, double deadZone)
	{
		if (Math.abs(error) <= deadZone)
		{
			return 0;
		}
		return error - Math.copySign(deadZone, error);
	}

	private static double decay(double value, double dt)
	{
		return value * Math.exp(-DECAY_PER_SECOND * dt);
	}

	private static double clamp(double v, double max)
	{
		return Math.max(-max, Math.min(max, v));
	}
}
