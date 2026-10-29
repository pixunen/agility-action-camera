package com.agilityactioncamera.rig;

/**
 * Angle helpers for the client camera.
 * <p>
 * Since rev 239 the client measures camera angles in JAU14: 16384 units per
 * full turn. Yaw 0 looks north (+y in local coordinates) and the camera's
 * forward vector for yaw {@code a} is {@code (-sin a, cos a)}, as derived from
 * {@code Perspective.localToCanvas}. Positive pitch looks down.
 */
public final class Angles
{
	public static final int JAU_PER_TURN = 16384;
	public static final double TAU = Math.PI * 2;

	/**
	 * Pitch limits the client enforces without the pitch relaxer (128..383 in the old 2048-unit scale).
	 */
	public static final double MIN_PITCH = toRadians(128 * 8);
	public static final double MAX_PITCH = toRadians(383 * 8);

	private Angles()
	{
	}

	public static double toRadians(int jau)
	{
		return jau * TAU / JAU_PER_TURN;
	}

	public static int toJau(double radians)
	{
		int jau = (int) Math.round(radians * JAU_PER_TURN / TAU) % JAU_PER_TURN;
		return jau < 0 ? jau + JAU_PER_TURN : jau;
	}

	/**
	 * Wraps an angle into [-pi, pi).
	 */
	public static double wrap(double radians)
	{
		double a = (radians + Math.PI) % TAU;
		if (a < 0)
		{
			a += TAU;
		}
		return a - Math.PI;
	}

	/**
	 * Yaw that makes the camera look along the horizontal direction (dx, dy).
	 */
	public static double yawLookingAlong(double dx, double dy)
	{
		return Math.atan2(-dx, dy);
	}

	/**
	 * Horizontal unit vector the camera looks along for the given yaw.
	 */
	public static double forwardX(double yaw)
	{
		return -Math.sin(yaw);
	}

	public static double forwardY(double yaw)
	{
		return Math.cos(yaw);
	}

	public static double clampPitch(double pitch)
	{
		return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
	}
}
