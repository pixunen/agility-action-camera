package com.agilityactioncamera.rig;

public final class Easing
{
	private Easing()
	{
	}

	public static double clamp01(double t)
	{
		return Math.max(0, Math.min(1, t));
	}

	public static double smoothstep(double t)
	{
		t = clamp01(t);
		return t * t * (3 - 2 * t);
	}

	public static double inOutCubic(double t)
	{
		t = clamp01(t);
		return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
	}

	public static double outCubic(double t)
	{
		t = clamp01(t);
		return 1 - Math.pow(1 - t, 3);
	}

	public static double lerp(double a, double b, double t)
	{
		return a + (b - a) * t;
	}
}
