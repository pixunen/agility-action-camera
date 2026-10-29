package com.agilityactioncamera.rig;

import lombok.Value;
import lombok.With;

/**
 * An orbit-style camera pose, which is what the client's free camera accepts:
 * a focal point plus yaw/pitch around it and a zoom level.
 * <p>
 * {@code x}/{@code y} are horizontal local coordinates (y is north), and
 * {@code height} uses the client's height convention, where more negative is
 * higher. The client maps these to focal X, Z and Y respectively.
 */
@Value
@With
public class CameraPose
{
	double x;
	double y;
	double height;
	/**
	 * Radians, see {@link Angles}.
	 */
	double yaw;
	/**
	 * Radians, positive looks down.
	 */
	double pitch;
	/**
	 * Normalised zoom in [0, 1]: 0 is the widest the client allows, 1 the closest.
	 */
	double zoom;
}
