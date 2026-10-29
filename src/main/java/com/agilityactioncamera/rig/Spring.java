package com.agilityactioncamera.rig;

/**
 * Critically damped spring. Frame-rate independent, never overshoots, and
 * carries velocity across target changes so moving targets look smooth.
 */
public final class Spring
{
	private double value;
	private double velocity;

	public Spring(double value)
	{
		this.value = value;
	}

	/**
	 * Advances the spring towards {@code target}.
	 *
	 * @param omega stiffness (angular frequency); higher is snappier
	 * @param dt    seconds since the last update
	 */
	public double update(double target, double omega, double dt)
	{
		// Exact solution of the critically damped oscillator over dt
		double x = value - target;
		double exp = Math.exp(-omega * dt);
		double temp = (velocity + omega * x) * dt;
		velocity = (velocity - omega * temp) * exp;
		value = target + (x + temp) * exp;
		return value;
	}

	public void snap(double v)
	{
		value = v;
		velocity = 0;
	}

	public double get()
	{
		return value;
	}

	public double getVelocity()
	{
		return velocity;
	}
}
