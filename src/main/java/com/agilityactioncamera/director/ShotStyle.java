package com.agilityactioncamera.director;

import com.agilityactioncamera.rig.Angles;
import com.agilityactioncamera.rig.CameraPose;
import com.agilityactioncamera.rig.Easing;

/**
 * Overall directing style: which shots are favoured and how tightly they frame.
 */
public enum ShotStyle
{
	/**
	 * Close, low and moving: Hero, Chase and Flyby, tight zoom, handheld feel.
	 */
	ACTION("Action", 0.22, 0.35, true),
	BALANCED("Balanced", 0.08, 0.15, false),
	/**
	 * Wide and composed: shows off the course.
	 */
	SCENIC("Scenic", 0, 0, false);

	private final String displayName;
	/**
	 * Added to every shot's zoom (closer).
	 */
	private final double zoomPush;
	/**
	 * How far every shot's pitch is pulled towards the lowest angle the client allows.
	 */
	private final double lowerPitch;
	private final boolean handheld;

	ShotStyle(String displayName, double zoomPush, double lowerPitch, boolean handheld)
	{
		this.displayName = displayName;
		this.zoomPush = zoomPush;
		this.lowerPitch = lowerPitch;
		this.handheld = handheld;
	}

	/**
	 * Score bias for a shot under this style.
	 */
	double bias(ShotType type)
	{
		switch (this)
		{
			case ACTION:
				switch (type)
				{
					case HERO:
						return 0.35;
					case CHASE:
						return 0.3;
					case FLYBY:
						return 0.2;
					case TRACKING:
						return 0.15;
					case ORBIT:
						return -0.2;
					case ESTABLISHING:
						// Only as a rare change of pace on very long lines
						return -1.1;
					default:
						return 0;
				}
			case SCENIC:
				return type == ShotType.ESTABLISHING ? 0.3 : type == ShotType.ORBIT || type == ShotType.CRANE ? 0.1 : 0;
			default:
				return type == ShotType.ESTABLISHING ? -0.2 : 0;
		}
	}

	/**
	 * Tightens a shot's framing for this style.
	 *
	 * @param t seconds into the shot, for the handheld motion
	 */
	public CameraPose apply(CameraPose pose, double t, boolean shake)
	{
		double pitch = Easing.lerp(pose.getPitch(), Angles.MIN_PITCH, lowerPitch);
		CameraPose styled = pose
			.withZoom(Easing.clamp01(pose.getZoom() + zoomPush))
			.withPitch(Angles.clampPitch(pitch));
		if (handheld && shake)
		{
			styled = styled
				.withYaw(styled.getYaw() + Math.toRadians(1.1) * wobble(t, 0.37, 0.71))
				.withPitch(Angles.clampPitch(styled.getPitch() + Math.toRadians(0.6) * wobble(t, 0.53, 0.97)));
		}
		return styled;
	}

	/**
	 * Smooth pseudo-random motion in [-1, 1]: incommensurate sines never visibly repeat.
	 */
	static double wobble(double t, double f1, double f2)
	{
		double tau = Angles.TAU;
		return 0.6 * Math.sin(tau * f1 * t) + 0.4 * Math.sin(tau * f2 * t + 1.3);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
