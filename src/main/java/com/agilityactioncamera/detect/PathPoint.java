package com.agilityactioncamera.detect;

import lombok.Value;

/**
 * A point on a traversal path in local coordinates, with tile height
 * (negative is up).
 */
@Value
public class PathPoint
{
	double x;
	double y;
	double height;

	public double distanceTo(PathPoint o)
	{
		return Math.hypot(o.x - x, o.y - y);
	}
}
