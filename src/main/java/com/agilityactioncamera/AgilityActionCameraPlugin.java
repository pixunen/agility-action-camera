package com.agilityactioncamera;

import com.agilityactioncamera.detect.DurationLearner;
import com.agilityactioncamera.detect.ObstacleCatalog;
import com.agilityactioncamera.detect.ObstacleKind;
import com.agilityactioncamera.detect.PathPoint;
import com.agilityactioncamera.detect.PlayerSample;
import com.agilityactioncamera.detect.Traversal;
import com.agilityactioncamera.detect.TraversalDetector;
import com.agilityactioncamera.detect.TraversalEnded;
import com.agilityactioncamera.detect.TraversalStarted;
import com.agilityactioncamera.director.AutoFramer;
import com.agilityactioncamera.director.CinematicSession;
import com.agilityactioncamera.director.ShotPlan;
import com.agilityactioncamera.director.ShotPlanner;
import com.agilityactioncamera.director.ShotType;
import com.agilityactioncamera.director.Terrain;
import com.agilityactioncamera.rig.CameraController;
import com.agilityactioncamera.rig.CameraPose;
import com.agilityactioncamera.safety.InteractionGuard;
import com.agilityactioncamera.ui.DebugOverlay;
import com.agilityactioncamera.ui.LetterboxOverlay;
import com.google.inject.Provides;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Collections;
import java.util.EnumSet;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Agility Action Camera",
	description = "Cinematic camera shots during long agility obstacles",
	tags = {"agility", "camera", "cinematic", "action", "zipline", "rooftop"}
)
public class AgilityActionCameraPlugin extends Plugin
{
	private static final long PREVIEW_NANOS = TimeUnit.SECONDS.toNanos(6);
	private static final long CAMERA_LOG_NANOS = TimeUnit.SECONDS.toNanos(3);
	private static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(600);
	private static final double DEFAULT_WALK_STYLE_TICKS = 8;
	/**
	 * Ticks the path must stop growing before position counts as progress.
	 * Hop-style jumps pause for a tick between hops.
	 */
	private static final int PATH_SETTLED_TICKS = 3;
	/**
	 * How far along an obstacle that hasn't been timed yet the camera is handed back.
	 */
	private static final double UNTIMED_RELEASE_PROGRESS = 0.85;
	/**
	 * Normal-movement ticks before the player counts as landed. Repeated hops
	 * (the Wyrm multi-jump) leave a single idle tick between hops.
	 */
	private static final int LANDED_TICKS = 2;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private AgilityActionCameraConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private LetterboxOverlay letterboxOverlay;

	@Inject
	private DebugOverlay debugOverlay;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private CameraController camera;

	@Inject
	private InteractionGuard guard;

	private final TraversalDetector detector = new TraversalDetector();
	private final DurationLearner durations = new DurationLearner();
	private final Random random = new Random();
	private final ShotPlanner planner = new ShotPlanner(random);
	private final AutoFramer framer = new AutoFramer();

	private CinematicSession session;
	private int chanceRejectedId = -1;
	private int userAbandonedId = -1;
	private int handedBackId = -1;
	private double handedBackLength;
	/**
	 * The last hand-back came before the traversal ended: that traversal is done with the camera.
	 */
	private boolean handedBackEarly;
	/**
	 * Tick the current traversal's path last grew; early hand-back waits until it stops.
	 */
	private int lastGrowthTick;
	private long traversalStartNanos;
	/**
	 * When the last game tick arrived, to tell how far into the current tick a frame is.
	 */
	private long lastTickNanos;
	private int normalTicks;
	private double lastGrowthLength;
	private long previewEndsNanos = -1;
	private long lastFrameNanos;
	private long lastCameraLogNanos;
	/**
	 * Difference between the client's focal height and the ground under the player, so the return glide lands where the client expects.
	 */
	private double homeHeightOffset;

	@Getter
	private Traversal lastTraversal;
	@Getter
	private String currentShotName;

	private final HotkeyListener previewHotkey = new HotkeyListener(() -> config.previewKey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(AgilityActionCameraPlugin.this::startPreview);
		}
	};

	@Override
	protected void startUp()
	{
		applyConfig();
		guard.setOnTakeover(() -> userTookCamera("camera input"));
		overlayManager.add(letterboxOverlay);
		overlayManager.add(debugOverlay);
		keyManager.registerKeyListener(previewHotkey);
		keyManager.registerKeyListener(guard);
		mouseManager.registerMouseListener(guard);
		mouseManager.registerMouseWheelListener(guard);
	}

	@Override
	protected void shutDown()
	{
		clientThread.invoke(() ->
		{
			camera.restoreNow();
			endSession();
		});
		detector.reset();
		durations.clear();
		planner.clearHistory();
		guard.setArmed(false);
		overlayManager.remove(letterboxOverlay);
		overlayManager.remove(debugOverlay);
		keyManager.unregisterKeyListener(previewHotkey);
		keyManager.unregisterKeyListener(guard);
		mouseManager.unregisterMouseListener(guard);
		mouseManager.unregisterMouseWheelListener(guard);
		lastTraversal = null;
	}

	@Provides
	AgilityActionCameraConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AgilityActionCameraConfig.class);
	}

	public boolean isCinematicPlaying()
	{
		return session != null && camera.getState() == CameraController.State.ACTIVE;
	}

	public String getDetectorStatus()
	{
		return detector.isTraversing() ? "traversing" : "idle";
	}

	public String getCameraStatus()
	{
		String shot = currentShotName == null ? "" : " (" + currentShotName + ")";
		return camera.getState().name().toLowerCase() + shot;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (AgilityActionCameraConfig.GROUP.equals(event.getGroup()))
		{
			applyConfig();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			// Scene reloads shift local coordinates, so any path in progress is meaningless now
			camera.restoreNow();
			endSession();
			detector.reset();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		if (camera.isDetached())
		{
			if (InteractionGuard.isWorldAction(action))
			{
				// Jagex rule: no world interaction from a detached camera. The click is
				// dropped, and the camera snaps straight back so the next click works
				event.consume();
				boolean finishing = session != null && session.isLanded();
				if (config.abortOnInput() || finishing)
				{
					log.debug("World click '{}' dropped while the camera was detached; restoring", Text.removeTags(event.getMenuOption()));
					camera.restoreNow();
					endSession();
				}
			}
			return;
		}

		switch (action)
		{
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				String option = Text.removeTags(event.getMenuOption());
				detector.onObjectClicked(event.getId(), option, client.getTickCount());
				if (config.logDetection())
				{
					log.debug("click object {} '{}' known={} kind={} -> {}", event.getId(), option,
						ObstacleCatalog.isCourseObstacle(event.getId()), ObstacleKind.forObject(event.getId()),
						detector.isArmed() ? "armed" : "ignored");
				}
				break;
			default:
				if (InteractionGuard.isWorldAction(action))
				{
					detector.onOtherAction();
				}
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		PlayerSample sample = sample();
		if (sample == null)
		{
			return;
		}

		boolean watching = detector.isArmed() || detector.isTraversing();
		TraversalDetector.Transition transition = detector.onTick(sample);
		boolean interesting = sample.getAnimation() != -1 || !detector.isOrdinary(sample);
		if (config.logDetection() && (watching || interesting || transition != TraversalDetector.Transition.NONE))
		{
			log.debug("tick {} {}: render=({}, {}) server=({}, {}) div={} anim={} pose={} walk={} {}", sample.getTick(), transition,
				sample.getRenderX() / 128, sample.getRenderY() / 128, sample.getServerX() / 128, sample.getServerY() / 128,
				String.format("%.2f", sample.divergenceTiles()), sample.getAnimation(), sample.getPoseAnimation(), sample.getWalkPose(),
				detector.isOrdinary(sample) ? "ordinary" : "special");
		}

		// Hand the camera back as soon as the player is moving normally again; if the
		// obstacle carries on (a chained part), a new shot picks it up
		lastTickNanos = System.nanoTime();
		normalTicks = detector.isTraversing() && detector.isMovingNormally(sample) ? normalTicks + 1 : 0;
		// A pause well before the obstacle's timed landing is a gap between chained parts: keep filming
		if (session != null && session.getTraversal() == detector.getCurrent() && normalTicks >= LANDED_TICKS
			&& !expectsMore(session.getTraversal(), 0))
		{
			session.landed(System.nanoTime());
		}

		switch (transition)
		{
			case STARTED:
				lastTraversal = detector.getCurrent();
				lastGrowthTick = client.getTickCount();
				lastGrowthLength = lastTraversal.length();
				traversalStartNanos = System.nanoTime();
				eventBus.post(new TraversalStarted(detector.getCurrent()));
				break;
			case EXTENDED:
				if (detector.getCurrent().length() > lastGrowthLength)
				{
					lastGrowthTick = client.getTickCount();
					lastGrowthLength = detector.getCurrent().length();
				}
				traversalChanged(detector.getCurrent());
				break;
			case ENDED:
				Traversal t = detector.getCurrent();
				if (!detector.isLastEndAborted())
				{
					// The detector ends a traversal only after the player has moved normally for a
					// while; the landing was the first of those ticks
					int landingTick = client.getTickCount() - normalTicks + 1;
					durations.record(t.learnKey(), landingTick - t.getStartTick());
				}
				eventBus.post(new TraversalEnded(t, detector.isLastEndAborted()));
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onTraversalStarted(TraversalStarted event)
	{
		Traversal traversal = event.getTraversal();
		log.debug("Traversal {} started: object {} {} kind {} anim {} {} tiles (expected {} ticks)", traversal.getId(), traversal.getObjectId(),
			traversal.getTrigger(), traversal.getKind(), traversal.getAnimation(), String.format("%.1f", traversal.lengthTiles()),
			durations.expectedTicks(traversal.learnKey()));

		// Roll the dice once per traversal, so a late start can't re-roll its way in
		chanceRejectedId = random.nextInt(100) >= config.chance() ? traversal.getId() : -1;
		tryStart(traversal);
	}

	/**
	 * The path grew or the obstacle kind became clear: plan again if the
	 * current shot no longer suits it, or start late if it wasn't filmed yet.
	 */
	private void traversalChanged(Traversal traversal)
	{
		if (session != null && session.getTraversal() == traversal)
		{
			if (session.isOutdated())
			{
				ShotPlan plan = planner.plan(traversal, terrain(), enabledShots(), config.cutMinTiles());
				if (!plan.getEntries().isEmpty())
				{
					log.debug("Traversal {} now {} tiles, kind {}: replanned {}", traversal.getId(),
						String.format("%.1f", traversal.lengthTiles()), traversal.getKind(), plan.getEntries());
					session.replan(plan);
				}
			}
		}
		else if (session == null && camera.getState() != CameraController.State.ACTIVE)
		{
			// Includes a camera that's still gliding back, so chained obstacles flow shot to shot
			tryStart(traversal);
		}
	}

	private void tryStart(Traversal traversal)
	{
		if (traversal.getId() == chanceRejectedId || traversal.getId() == userAbandonedId
			|| camera.isUserInFreeCamera() || !eligible(traversal))
		{
			return;
		}
		// Already filmed and handed back: never after an early release, otherwise
		// only for a substantial new stretch (a chained obstacle)
		if (traversal.getId() == handedBackId
			&& (handedBackEarly || traversal.length() < handedBackLength + config.minTiles() * Perspective.LOCAL_TILE_SIZE))
		{
			return;
		}

		ShotPlan plan = planner.plan(traversal, terrain(), enabledShots(), config.cutMinTiles());
		if (plan.getEntries().isEmpty())
		{
			return;
		}
		OptionalDouble measured = durations.expectedTicks(traversal.learnKey());
		long expectedTicks = Math.round(measured.orElse(DEFAULT_WALK_STYLE_TICKS));
		// Time from the traversal's start, not the shot's, so a late start still knows when the end comes
		long startNanos = traversal == lastTraversal ? traversalStartNanos : System.nanoTime();
		startSession(new CinematicSession(traversal, plan, startNanos, expectedTicks * TICK_NANOS, measured.isPresent()));
	}

	private boolean eligible(Traversal traversal)
	{
		if (traversal.getKind() == ObstacleKind.CLIMB && !config.filmClimbs())
		{
			return false;
		}
		// Once timed, the measured duration decides
		OptionalDouble expected = durations.expectedTicks(traversal.learnKey());
		if (expected.isPresent())
		{
			return expected.getAsDouble() >= config.minTicks();
		}
		if (traversal.lengthTiles() >= config.minTiles())
		{
			return true;
		}
		// First time, short so far: only movements that are long by nature
		switch (traversal.getKind())
		{
			case ZIPLINE:
			case SWING:
			case MONKEYBARS:
			case BALANCE:
				return true;
			default:
				return false;
		}
	}

	@Subscribe
	public void onTraversalEnded(TraversalEnded event)
	{
		if (session == null || session.getTraversal() != event.getTraversal())
		{
			return;
		}
		if (event.isAborted())
		{
			handBack("traversal aborted (teleport or timeout)");
		}
		else
		{
			session.landed(System.nanoTime());
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (config.abortOnDamage() && camera.isDetached() && event.getActor() == client.getLocalPlayer())
		{
			userTookCamera("damage");
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		long now = System.nanoTime();
		double dt = lastFrameNanos == 0 ? 0 : Math.min(0.1, (now - lastFrameNanos) / 1e9);
		lastFrameNanos = now;

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		LocalPoint render = player.getLocalLocation();

		if (session != null && camera.getState() == CameraController.State.ACTIVE)
		{
			CinematicSession.Frame frame = session.frame(now, render.getX(), render.getY());
			if (frame.isCut())
			{
				framer.reset();
			}
			if (config.autoFrame())
			{
				updateFraming(player, dt);
			}
			CameraPose pose = config.shotStyle().apply(frame.getPose(), session.shotSeconds(now), config.handheld());
			camera.drive(config.autoFrame() ? framer.apply(pose) : pose, config.stiffness(), dt, frame.isCut());
			currentShotName = frame.getShot().toString();

			boolean previewOver = previewEndsNanos >= 0 && now >= previewEndsNanos;
			// A measured duration says best when the end is near; otherwise position
			// does, but only once the path has stopped growing and the end is known
			boolean settled = client.getTickCount() - lastGrowthTick >= PATH_SETTLED_TICKS;
			// Once landed, the hold decides; a landing isn't an early release
			boolean nearEnd = config.releaseEarlyTicks() > 0 && !session.isLanded() && (isTimed(session.getTraversal()) ? !expectsMore(session.getTraversal(), now)
				: settled && session.isProgressReliable() && frame.getU() >= UNTIMED_RELEASE_PROGRESS);
			if (previewOver || nearEnd || session.isHoldOver(now, TimeUnit.MILLISECONDS.toNanos(config.holdMs())))
			{
				handBack(previewOver ? "preview over" : nearEnd ? "near end" : "landed");
			}
		}

		if (camera.getState() == CameraController.State.RETURNING)
		{
			double height = groundHeight(render) + homeHeightOffset;
			// Return briskly: the player wants their camera back to click the next obstacle
			camera.driveReturn(render.getX(), render.getY(), height, Math.max(9, config.stiffness() * 1.8), dt, now,
				TimeUnit.MILLISECONDS.toNanos(config.returnMs()));
		}

		// Also during the glide back, so the player's own camera input wins over it
		guard.setArmed(config.abortOnInput() && camera.isControlling());

		if (config.logCamera() && now - lastCameraLogNanos > CAMERA_LOG_NANOS)
		{
			lastCameraLogNanos = now;
			logCamera(render);
		}
	}

	private void startSession(CinematicSession newSession)
	{
		if (camera.getState() == CameraController.State.IDLE)
		{
			Player player = client.getLocalPlayer();
			homeHeightOffset = client.getCameraFocalPointY() - groundHeight(player.getLocalLocation());
		}
		if (!camera.take())
		{
			return;
		}
		session = newSession;
		previewEndsNanos = -1;
		framer.reset();
		log.debug("Shot plan: {}", newSession.getPlan().getEntries());
	}

	private void startPreview()
	{
		Player player = client.getLocalPlayer();
		if (player == null || client.getGameState() != GameState.LOGGED_IN || camera.isUserInFreeCamera())
		{
			return;
		}
		LocalPoint lp = player.getLocalLocation();
		Traversal preview = Traversal.preview(new PathPoint(lp.getX(), lp.getY(), groundHeight(lp)), client.getTickCount());
		ShotPlan plan = new ShotPlan(Collections.singletonList(new ShotPlan.Entry(ShotType.ORBIT, 1, 0, 0)));
		startSession(new CinematicSession(preview, plan, System.nanoTime(), PREVIEW_NANOS));
		previewEndsNanos = System.nanoTime() + PREVIEW_NANOS;
	}

	/**
	 * Ends the current shot and glides back to the player's own camera.
	 */
	private boolean isTimed(Traversal traversal)
	{
		return durations.expectedTicks(traversal.learnKey()).isPresent();
	}

	/**
	 * Whether a timed traversal is still more than the hand-back lead away from
	 * its landing tick. Counted in game ticks, since the server moves the player
	 * in whole ticks; {@code nowNanos} places a frame within the current tick
	 * (0 means "at the tick").
	 */
	private boolean expectsMore(Traversal traversal, long nowNanos)
	{
		OptionalDouble expected = durations.expectedTicks(traversal.learnKey());
		if (!expected.isPresent())
		{
			return false;
		}
		double intoTick = nowNanos == 0 ? 0 : Math.min(1, Math.max(0, (nowNanos - lastTickNanos) / (double) TICK_NANOS));
		double ticksIn = client.getTickCount() - traversal.getStartTick() + intoTick;
		return ticksIn < expected.getAsDouble() - config.releaseEarlyTicks();
	}

	private void handBack(String reason)
	{
		if (session != null)
		{
			log.debug("Traversal {}: handing camera back ({})", session.getTraversal().getId(), reason);
			handedBackId = session.getTraversal().getId();
			handedBackLength = session.getTraversal().length();
			handedBackEarly = "near end".equals(reason);
		}
		camera.release(System.nanoTime());
		endSession();
	}

	/**
	 * The player reached for the camera (or got hit): hand it back and don't
	 * take it again for the rest of this traversal.
	 */
	private void userTookCamera(String reason)
	{
		if (session != null)
		{
			userAbandonedId = session.getTraversal().getId();
		}
		handBack(reason);
	}

	private void endSession()
	{
		session = null;
		previewEndsNanos = -1;
		currentShotName = null;
	}

	/**
	 * Feeds the auto-framer with where the player model was drawn last frame.
	 */
	private void updateFraming(Player player, double dt)
	{
		Shape hull = player.getConvexHull();
		if (hull == null)
		{
			return;
		}
		Rectangle bounds = hull.getBounds();
		double w = client.getViewportWidth();
		double h = client.getViewportHeight();
		if (bounds.isEmpty() || w <= 0 || h <= 0)
		{
			return;
		}
		double errorX = (bounds.getCenterX() - client.getViewportXOffset() - w / 2) / w;
		double errorY = (bounds.getCenterY() - client.getViewportYOffset() - h / 2) / h;
		framer.update(errorX, errorY, dt);
	}

	private PlayerSample sample()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		WorldView wv = player.getWorldView();
		if (wv == null || !wv.isTopLevel())
		{
			// Players on boats move with their world entity; not handled
			return null;
		}
		LocalPoint render = player.getLocalLocation();
		WorldPoint wp = player.getWorldLocation();
		LocalPoint server = LocalPoint.fromWorld(wv, wp);
		if (render == null || server == null)
		{
			return null;
		}

		return PlayerSample.builder()
			.tick(client.getTickCount())
			.renderX(render.getX())
			.renderY(render.getY())
			.renderHeight(Perspective.getTileHeight(client, render, wv.getPlane()))
			.serverX(server.getX())
			.serverY(server.getY())
			.serverHeight(Perspective.getTileHeight(client, server, wp.getPlane()))
			.plane(wp.getPlane())
			.animation(player.getAnimation())
			.poseAnimation(player.getPoseAnimation())
			.idlePose(player.getIdlePoseAnimation())
			.walkPose(player.getWalkAnimation())
			.runPose(player.getRunAnimation())
			.build();
	}

	private Terrain terrain()
	{
		WorldView wv = client.getTopLevelWorldView();
		int plane = wv.getPlane();
		int maxX = wv.getSizeX() * Perspective.LOCAL_TILE_SIZE;
		int maxY = wv.getSizeY() * Perspective.LOCAL_TILE_SIZE;
		return (x, y) ->
		{
			if (x < 0 || y < 0 || x >= maxX || y >= maxY)
			{
				return Double.NaN;
			}
			return Perspective.getTileHeight(client, new LocalPoint((int) x, (int) y, wv), plane);
		};
	}

	private double groundHeight(LocalPoint lp)
	{
		return Perspective.getTileHeight(client, lp, client.getTopLevelWorldView().getPlane());
	}

	private Set<ShotType> enabledShots()
	{
		Set<ShotType> shots = EnumSet.noneOf(ShotType.class);
		if (config.shotTracking())
		{
			shots.add(ShotType.TRACKING);
		}
		if (config.shotChase())
		{
			shots.add(ShotType.CHASE);
		}
		if (config.shotFlyby())
		{
			shots.add(ShotType.FLYBY);
		}
		if (config.shotCrane())
		{
			shots.add(ShotType.CRANE);
		}
		if (config.shotOrbit())
		{
			shots.add(ShotType.ORBIT);
		}
		if (config.shotHero())
		{
			shots.add(ShotType.HERO);
		}
		if (config.shotEstablishing())
		{
			shots.add(ShotType.ESTABLISHING);
		}
		return shots;
	}

	private void applyConfig()
	{
		detector.setMinTiles(config.minTiles());
		detector.setGenericDetection(config.genericDetection());
		detector.setWalkStyleObstacles(config.walkStyle());
		detector.setClickFree(config.clickFree());
		planner.setStyle(config.shotStyle());
	}

	private void logCamera(LocalPoint render)
	{
		float dx = client.getCameraFpX() - client.getCameraFocalPointX();
		float dy = client.getCameraFpY() - client.getCameraFocalPointZ();
		float dz = client.getCameraFpZ() - client.getCameraFocalPointY();
		log.debug("camera mode={} focal=({}, {}, {}) cam=({}, {}, {}) dist={} fpYaw={} fpPitch={} yawT={} pitchT={} zoom={}/{} player=({}, {}) ground={}",
			client.getCameraMode(),
			client.getCameraFocalPointX(), client.getCameraFocalPointY(), client.getCameraFocalPointZ(),
			client.getCameraFpX(), client.getCameraFpY(), client.getCameraFpZ(),
			Math.sqrt(dx * dx + dy * dy + dz * dz),
			client.getCameraFpYaw(), client.getCameraFpPitch(),
			client.getCameraYawTarget(), client.getCameraPitchTarget(),
			client.getVarcIntValue(VarClientID.CAMERA_ZOOM_SMALL),
			client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG),
			render.getX(), render.getY(), groundHeight(render));
	}
}
