package com.agilityactioncamera.director;

import com.agilityactioncamera.rig.Angles;
import com.agilityactioncamera.rig.CameraPose;
import static com.agilityactioncamera.rig.Easing.inOutCubic;
import static com.agilityactioncamera.rig.Easing.lerp;
import static com.agilityactioncamera.rig.Easing.smoothstep;

/**
 * The shot library. Each shot is a pure function from the moment of the
 * traversal to an orbit pose (focal point, yaw, pitch, zoom). Everything is
 * computed from live path geometry; nothing is a stored camera position.
 * <p>
 * Yaw offsets are relative to the travel direction: 0 looks along the
 * direction of travel (camera behind the player), 180 looks back at the
 * player from ahead, and +/-90 is a side-on view.
 */
public enum ShotType
{
	/**
	 * Side-on dolly that runs alongside the player with a slight lead.
	 */
	TRACKING("Tracking")
		{
			@Override
			public CameraPose frame(ShotContext c)
			{
				double lead = 128;
				double drift = Math.toRadians(14) * (c.getU() - 0.5);
				return pose(c, c.getSubjectX() + c.getDirX() * lead, c.getSubjectY() + c.getDirY() * lead, c.getSubjectHeight(),
					c.travelYaw() + c.getSide() * (Math.toRadians(90) - drift), Math.toRadians(26), 0.45);
			}

			@Override
			double fit(ShotContext c)
			{
				return 0.4;
			}
		},

	/**
	 * Low over-the-shoulder chase looking down the line of travel.
	 */
	CHASE("Chase")
		{
			@Override
			public CameraPose frame(ShotContext c)
			{
				double lead = 256;
				return pose(c, c.getSubjectX() + c.getDirX() * lead, c.getSubjectY() + c.getDirY() * lead, c.getSubjectHeight(),
					c.travelYaw() + c.getSide() * Math.toRadians(14), Math.toRadians(30), 0.6);
			}

			@Override
			double fit(ShotContext c)
			{
				return c.getLengthTiles() >= 5 ? 0.35 : 0;
			}
		},

	/**
	 * Starts ahead looking back at the oncoming player, then whips round as
	 * they pass, ending behind them. The signature GTA shot.
	 */
	FLYBY("Flyby")
		{
			@Override
			public CameraPose frame(ShotContext c)
			{
				double e = inOutCubic(c.getU());
				double offset = Math.toRadians(lerp(150, 35, e));
				return pose(c, c.getSubjectX(), c.getSubjectY(), c.getSubjectHeight(),
					c.travelYaw() + c.getSide() * offset, Math.toRadians(24), lerp(0.7, 0.4, e));
			}

			@Override
			double fit(ShotContext c)
			{
				return c.getLengthTiles() >= 6 ? 0.55 : 0;
			}
		},

	/**
	 * Crane move: top-down to low for descents, low to top-down for climbs.
	 */
	CRANE("Crane")
		{
			@Override
			public CameraPose frame(ShotContext c)
			{
				double e = smoothstep(c.getU());
				boolean descending = c.getDrop() >= 0;
				double pitch = descending ? lerp(62, 25, e) : lerp(25, 62, e);
				return pose(c, c.getSubjectX(), c.getSubjectY(), c.getSubjectHeight(),
					c.travelYaw() + c.getSide() * Math.toRadians(70), Math.toRadians(pitch), lerp(0.3, 0.55, e));
			}

			@Override
			double fit(ShotContext c)
			{
				return Math.abs(c.getDrop()) > 150 ? 0.6 : 0.1;
			}
		},

	/**
	 * Slow orbit around the player. Good for short or vertical obstacles where the path itself isn't interesting.
	 */
	ORBIT("Orbit")
		{
			@Override
			public CameraPose frame(ShotContext c)
			{
				double yaw = c.travelYaw() + c.getSide() * (Math.toRadians(60) + c.getT() * Math.toRadians(28));
				return pose(c, c.getSubjectX(), c.getSubjectY(), c.getSubjectHeight(), yaw, Math.toRadians(32), 0.55);
			}

			@Override
			double fit(ShotContext c)
			{
				return c.getLengthTiles() < 6 ? 0.5 : 0.1;
			}
		},

	/**
	 * Low, tight front three-quarter angle looking up at the player.
	 */
	HERO("Hero")
		{
			@Override
			public CameraPose frame(ShotContext c)
			{
				return pose(c, c.getSubjectX(), c.getSubjectY(), c.getSubjectHeight() - 60,
					c.travelYaw() + c.getSide() * Math.toRadians(150), Angles.MIN_PITCH, 0.75);
			}

			@Override
			double fit(ShotContext c)
			{
				return 0.25;
			}
		},

	/**
	 * Wide side view of the whole obstacle; the player crosses the frame.
	 * Best for long ziplines and deathslides.
	 */
	ESTABLISHING("Establishing")
		{
			@Override
			public CameraPose frame(ShotContext c)
			{
				double midX = (c.getStart().getX() + c.getEnd().getX()) / 2;
				double midY = (c.getStart().getY() + c.getEnd().getY()) / 2;
				double midH = (c.getStart().getHeight() + c.getEnd().getHeight()) / 2 - 90;
				// Mostly locked off, drifting a little towards the subject so they stay framed
				double follow = 0.3;
				return pose(c,
					lerp(midX, c.getSubjectX(), follow),
					lerp(midY, c.getSubjectY(), follow),
					lerp(midH, c.getSubjectHeight(), follow),
					c.travelYaw() + c.getSide() * Math.toRadians(90), Math.toRadians(38), 0.15);
			}

			@Override
			double fit(ShotContext c)
			{
				return c.getLengthTiles() >= 8 ? 0.6 : -0.5;
			}
		};

	private final String displayName;

	ShotType(String displayName)
	{
		this.displayName = displayName;
	}

	/**
	 * Desired camera pose for this moment of the traversal.
	 */
	public abstract CameraPose frame(ShotContext c);

	/**
	 * How well this shot suits the traversal's shape, before occlusion and variety are considered.
	 */
	abstract double fit(ShotContext c);

	@Override
	public String toString()
	{
		return displayName;
	}

	private static CameraPose pose(ShotContext c, double x, double y, double height, double yaw, double pitch, double zoom)
	{
		return new CameraPose(x, y, height, Angles.wrap(yaw), Angles.clampPitch(pitch), zoom);
	}
}
