package com.agilityactioncamera.director;

import com.agilityactioncamera.detect.PathPoint;
import lombok.Builder;
import lombok.Value;

/**
 * Everything a shot needs to frame one moment of a traversal.
 */
@Value
@Builder(toBuilder = true)
public class ShotContext
{
	PathPoint start;
	PathPoint end;
	/**
	 * Unit travel direction, start to end.
	 */
	double dirX;
	double dirY;
	double lengthTiles;
	/**
	 * Positive when the path descends (local height units).
	 */
	double drop;
	/**
	 * Which side of the path the camera works from: +1 right of travel, -1 left.
	 */
	int side;
	/**
	 * Path progress in [0, 1].
	 */
	double u;
	/**
	 * Seconds since this shot started.
	 */
	double t;
	/**
	 * Where the camera should frame the player (chest height).
	 */
	double subjectX;
	double subjectY;
	double subjectHeight;

	/**
	 * Yaw that looks along the direction of travel.
	 */
	public double travelYaw()
	{
		return Math.atan2(-dirX, dirY);
	}
}
