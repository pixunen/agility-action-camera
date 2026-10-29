package com.agilityactioncamera.rig;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarClientID;

/**
 * Owns the client camera while a cinematic plays. Must only be used on the client thread.
 * <p>
 * Lifecycle: {@link #take()} snapshots the player's camera and switches to
 * free camera mode; {@link #drive} applies smoothed poses each frame;
 * {@link #release()} glides back to the player's own camera and then
 * restores the snapshot; {@link #restoreNow()} restores immediately.
 */
@Slf4j
@Singleton
public class CameraController
{
	private static final int FREE_CAMERA = 1;
	private static final int FALLBACK_ZOOM_MIN = 128;
	private static final int FALLBACK_ZOOM_MAX = 1400;
	/**
	 * Close enough to the player's camera to switch back without a visible pop.
	 */
	private static final double RETURN_EPSILON = 6;

	public enum State
	{
		IDLE,
		ACTIVE,
		RETURNING,
	}

	private final Client client;
	private final CameraRig rig = new CameraRig();

	@Getter
	private State state = State.IDLE;
	private Snapshot snapshot;
	private int lastZoomApplied = Integer.MIN_VALUE;
	private long returnStartedNanos;
	private boolean unitsChecked;

	@Inject
	CameraController(Client client)
	{
		this.client = client;
	}

	/**
	 * Whether the camera is currently detached from the player. While true, world interaction must be blocked.
	 */
	public boolean isDetached()
	{
		return state == State.ACTIVE;
	}

	/**
	 * Whether the plugin is moving the camera at all, including the glide back.
	 */
	public boolean isControlling()
	{
		return state != State.IDLE;
	}

	/**
	 * Whether the player is already using the free camera themselves, in which case we don't interfere.
	 */
	public boolean isUserInFreeCamera()
	{
		return state == State.IDLE && client.getCameraMode() == FREE_CAMERA;
	}

	public boolean take()
	{
		if (state == State.ACTIVE)
		{
			return true;
		}
		if (state == State.IDLE)
		{
			if (client.getCameraMode() == FREE_CAMERA)
			{
				return false;
			}
			snapshot = Snapshot.capture(client);
			checkUnits();
			rig.snap(currentPose());
			lastZoomApplied = Integer.MIN_VALUE;
		}
		// Taking over again while returning keeps the original snapshot and the rig's momentum
		state = State.ACTIVE;
		client.setCameraMode(FREE_CAMERA);
		return true;
	}

	/**
	 * Applies one frame of camera motion.
	 *
	 * @param desired the pose the shot wants
	 * @param omega   spring stiffness
	 * @param cut     hard cut straight to {@code desired}
	 */
	public void drive(CameraPose desired, double omega, double dt, boolean cut)
	{
		if (state != State.ACTIVE)
		{
			return;
		}
		if (cut)
		{
			rig.snap(desired);
		}
		apply(cut ? desired : rig.update(desired, omega, dt));
	}

	/**
	 * Hands the camera back: it reattaches to the player at once, so world
	 * clicks work again straight away, and only the angle and zoom glide back
	 * (see {@link #driveReturn}).
	 */
	public void release(long nowNanos)
	{
		if (state == State.ACTIVE)
		{
			state = State.RETURNING;
			returnStartedNanos = nowNanos;
			client.setCameraMode(snapshot.mode);
		}
	}

	/**
	 * Per-frame update while returning; restores the snapshot once the glide completes or times out.
	 */
	public void driveReturn(double playerX, double playerY, double playerHeight, double omega, double dt, long nowNanos, long maxReturnNanos)
	{
		if (state != State.RETURNING)
		{
			return;
		}
		CameraPose home = new CameraPose(playerX, playerY, playerHeight,
			Angles.toRadians(snapshot.yawTarget), Angles.toRadians(snapshot.pitchTarget), normalisedZoom(snapshot.activeZoom()));
		applyAngles(rig.update(home, omega, dt));

		if (rig.distanceTo(home) < RETURN_EPSILON || nowNanos - returnStartedNanos >= maxReturnNanos)
		{
			restoreNow();
		}
	}

	/**
	 * Gives the camera back immediately, e.g. on logout or plugin shutdown.
	 */
	public void restoreNow()
	{
		if (state == State.IDLE)
		{
			return;
		}
		state = State.IDLE;
		client.setCameraMode(snapshot.mode);
		client.setCameraYawTarget(snapshot.yawTarget);
		client.setCameraPitchTarget(snapshot.pitchTarget);
		client.runScript(ScriptID.CAMERA_DO_ZOOM, snapshot.zoomSmall, snapshot.zoomBig);
		snapshot = null;
	}

	public CameraPose currentRigPose()
	{
		return rig.current();
	}

	/**
	 * The yaw target's unit isn't documented; we assume JAU14 like the actual
	 * yaw. Compare against the radian yaw once so a wrong assumption shows up in the log.
	 */
	private void checkUnits()
	{
		if (unitsChecked)
		{
			return;
		}
		unitsChecked = true;
		double target = Angles.toRadians(client.getCameraYawTarget());
		double actual = client.getCameraFpYaw();
		if (Math.abs(Angles.wrap(target - actual)) > 0.3)
		{
			log.warn("Camera yaw target {} doesn't match actual yaw {} rad; angle units may have changed", client.getCameraYawTarget(), actual);
		}
	}

	private CameraPose currentPose()
	{
		return new CameraPose(
			client.getCameraFocalPointX(),
			client.getCameraFocalPointZ(),
			client.getCameraFocalPointY(),
			Angles.toRadians(client.getCameraYawTarget()),
			Angles.toRadians(client.getCameraPitchTarget()),
			normalisedZoom(snapshot.activeZoom()));
	}

	private void apply(CameraPose pose)
	{
		// The client keeps x/z as the horizontal plane and y as height
		client.setCameraFocalPointX((float) pose.getX());
		client.setCameraFocalPointZ((float) pose.getY());
		client.setCameraFocalPointY((float) pose.getHeight());
		applyAngles(pose);
	}

	/**
	 * Angle and zoom only: what the player's own camera controls set, so it is fine on an attached camera.
	 */
	private void applyAngles(CameraPose pose)
	{
		client.setCameraYawTarget(Angles.toJau(pose.getYaw()));
		client.setCameraPitchTarget(Angles.toJau(Angles.clampPitch(pose.getPitch())));

		int zoom = denormalisedZoom(pose.getZoom());
		if (Math.abs(zoom - lastZoomApplied) >= 2)
		{
			lastZoomApplied = zoom;
			if (client.isResized())
			{
				client.runScript(ScriptID.CAMERA_DO_ZOOM, snapshot.zoomSmall, zoom);
			}
			else
			{
				client.runScript(ScriptID.CAMERA_DO_ZOOM, zoom, snapshot.zoomBig);
			}
		}
	}

	private int[] zoomLimits()
	{
		boolean resized = client.isResized();
		int min = client.getVarcIntValue(resized ? VarClientID.CAMERA_ZOOM_BIG_MIN : VarClientID.CAMERA_ZOOM_SMALL_MIN);
		int max = client.getVarcIntValue(resized ? VarClientID.CAMERA_ZOOM_BIG_MAX : VarClientID.CAMERA_ZOOM_SMALL_MAX);
		if (max <= min)
		{
			return new int[]{FALLBACK_ZOOM_MIN, FALLBACK_ZOOM_MAX};
		}
		return new int[]{min, max};
	}

	private double normalisedZoom(int zoom)
	{
		int[] limits = zoomLimits();
		return Easing.clamp01((zoom - limits[0]) / (double) (limits[1] - limits[0]));
	}

	private int denormalisedZoom(double zoom)
	{
		int[] limits = zoomLimits();
		return (int) Math.round(Easing.lerp(limits[0], limits[1], Easing.clamp01(zoom)));
	}

	private static final class Snapshot
	{
		private final boolean resized;
		private int mode;
		private int yawTarget;
		private int pitchTarget;
		private int zoomSmall;
		private int zoomBig;

		private Snapshot(boolean resized)
		{
			this.resized = resized;
		}

		static Snapshot capture(Client client)
		{
			Snapshot s = new Snapshot(client.isResized());
			s.mode = client.getCameraMode();
			s.yawTarget = client.getCameraYawTarget();
			s.pitchTarget = client.getCameraPitchTarget();
			s.zoomSmall = client.getVarcIntValue(VarClientID.CAMERA_ZOOM_SMALL);
			s.zoomBig = client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG);
			log.debug("Camera snapshot: mode={} yaw={} pitch={} zoom={}/{}", s.mode, s.yawTarget, s.pitchTarget, s.zoomSmall, s.zoomBig);
			return s;
		}

		int activeZoom()
		{
			return resized ? zoomBig : zoomSmall;
		}
	}
}
