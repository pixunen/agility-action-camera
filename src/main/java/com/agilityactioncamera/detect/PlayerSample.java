package com.agilityactioncamera.detect;

import lombok.Builder;
import lombok.Value;

/**
 * One game tick's view of the local player, decoupled from the client so the
 * detector can be driven by tests.
 * <p>
 * "Render" is where the player model is drawn ({@code getLocalLocation}),
 * which slides along forced movement. "Server" is the authoritative tile
 * ({@code getWorldLocation}), which jumps straight to the destination.
 * All positions are local coordinates (128 units per tile).
 */
@Value
@Builder
public class PlayerSample
{
	int tick;
	int renderX;
	int renderY;
	int renderHeight;
	int serverX;
	int serverY;
	int serverHeight;
	int plane;
	int animation;
	int poseAnimation;
	int idlePose;
	int walkPose;
	int runPose;

	public double divergenceTiles()
	{
		return Math.hypot(serverX - renderX, serverY - renderY) / 128.0;
	}

	public boolean isOrdinaryPose()
	{
		return poseAnimation == -1 || poseAnimation == idlePose || poseAnimation == walkPose || poseAnimation == runPose;
	}
}
