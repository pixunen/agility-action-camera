package com.agilityactioncamera.detect;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Remembers how many game ticks each obstacle took during this session. The
 * server moves the player in whole ticks, so an obstacle takes the same
 * number every time and the latest run is the best guess. In memory only;
 * nothing camera-related is stored.
 */
public final class DurationLearner
{
	private final Map<Integer, Double> ticksByObstacle = new HashMap<>();

	public void record(int obstacleKey, int ticks)
	{
		if (ticks > 0)
		{
			ticksByObstacle.put(obstacleKey, (double) ticks);
		}
	}

	public OptionalDouble expectedTicks(int obstacleKey)
	{
		Double t = ticksByObstacle.get(obstacleKey);
		return t == null ? OptionalDouble.empty() : OptionalDouble.of(t);
	}

	public void clear()
	{
		ticksByObstacle.clear();
	}
}
