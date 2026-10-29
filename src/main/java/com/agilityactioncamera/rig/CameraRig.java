package com.agilityactioncamera.rig;

/**
 * Smooths a stream of desired poses with one spring per channel. Yaw is
 * unwrapped so the camera always turns the short way round.
 */
public final class CameraRig
{
	private final Spring x = new Spring(0);
	private final Spring y = new Spring(0);
	private final Spring height = new Spring(0);
	private final Spring yaw = new Spring(0);
	private final Spring pitch = new Spring(0);
	private final Spring zoom = new Spring(0);

	public void snap(CameraPose pose)
	{
		x.snap(pose.getX());
		y.snap(pose.getY());
		height.snap(pose.getHeight());
		yaw.snap(pose.getYaw());
		pitch.snap(pose.getPitch());
		zoom.snap(pose.getZoom());
	}

	/**
	 * @param omega position stiffness; angles use slightly softer springs so
	 *              pans lag the subject a little, like a camera operator
	 */
	public CameraPose update(CameraPose target, double omega, double dt)
	{
		double angleOmega = omega * 0.8;
		// Pick the target yaw closest to the current (unwrapped) yaw
		double yawTarget = yaw.get() + Angles.wrap(target.getYaw() - yaw.get());

		return new CameraPose(
			x.update(target.getX(), omega, dt),
			y.update(target.getY(), omega, dt),
			height.update(target.getHeight(), omega, dt),
			yaw.update(yawTarget, angleOmega, dt),
			pitch.update(target.getPitch(), angleOmega, dt),
			zoom.update(target.getZoom(), angleOmega, dt));
	}

	public CameraPose current()
	{
		return new CameraPose(x.get(), y.get(), height.get(), yaw.get(), pitch.get(), zoom.get());
	}

	/**
	 * Rough distance from {@code target}, mixing positions (local units) and
	 * angles (scaled so one radian counts like ~2 tiles).
	 */
	public double distanceTo(CameraPose target)
	{
		double dx = x.get() - target.getX();
		double dy = y.get() - target.getY();
		double dh = height.get() - target.getHeight();
		double da = Angles.wrap(yaw.get() - target.getYaw()) * 256;
		double dp = (pitch.get() - target.getPitch()) * 256;
		double dz = (zoom.get() - target.getZoom()) * 256;
		return Math.sqrt(dx * dx + dy * dy + dh * dh + da * da + dp * dp + dz * dz);
	}
}
