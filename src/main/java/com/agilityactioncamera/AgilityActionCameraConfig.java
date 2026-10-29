package com.agilityactioncamera;

import com.agilityactioncamera.director.ShotStyle;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(AgilityActionCameraConfig.GROUP)
public interface AgilityActionCameraConfig extends Config
{
	String GROUP = "agilityactioncamera";

	@ConfigSection(name = "Triggers", description = "Which obstacles get a cinematic", position = 0)
	String triggerSection = "triggers";

	@ConfigSection(name = "Shots", description = "Which shots the director may use", position = 1)
	String shotSection = "shots";

	@ConfigSection(name = "Feel", description = "Camera motion and presentation", position = 2)
	String feelSection = "feel";

	@ConfigSection(name = "Interruptions", description = "When to hand the camera back early", position = 3)
	String safetySection = "safety";

	@ConfigSection(name = "Debug", description = "Tools for tuning", position = 4, closedByDefault = true)
	String debugSection = "debug";

	@Range(min = 3, max = 20)
	@ConfigItem(
		keyName = "minTiles",
		name = "Minimum distance",
		description = "Only film obstacles that move you at least this many tiles",
		section = triggerSection,
		position = 0
	)
	@Units(" tiles")
	default int minTiles()
	{
		return 4;
	}

	@Range(max = 100)
	@ConfigItem(
		keyName = "chance",
		name = "Chance",
		description = "Chance that an eligible obstacle gets a cinematic",
		section = triggerSection,
		position = 1
	)
	@Units(Units.PERCENT)
	default int chance()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "genericDetection",
		name = "Any agility obstacle",
		description = "Also film unknown objects with agility actions (shortcuts, new courses), not just known course obstacles",
		section = triggerSection,
		position = 2
	)
	default boolean genericDetection()
	{
		return true;
	}

	@ConfigItem(
		keyName = "walkStyle",
		name = "Walk-style obstacles",
		description = "Film obstacles you walk across with a special pose, like tightropes and log balances",
		section = triggerSection,
		position = 3
	)
	default boolean walkStyle()
	{
		return true;
	}

	@Range(min = 1, max = 20)
	@ConfigItem(
		keyName = "minTicks",
		name = "Minimum duration",
		description = "Once an obstacle has been timed, only film it if it takes at least this long. Skips quick hops like stepping up onto a platform",
		section = triggerSection,
		position = 1
	)
	@Units(Units.TICKS)
	default int minTicks()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "clickFree",
		name = "Chained obstacles",
		description = "Detect agility movements from their animations alone, without a click. Needed for courses where one click chains several movements",
		section = triggerSection,
		position = 5
	)
	default boolean clickFree()
	{
		return true;
	}

	@ConfigItem(
		keyName = "filmClimbs",
		name = "Ladders and climbs",
		description = "Also film short vertical obstacles such as ladders and wall climbs",
		section = triggerSection,
		position = 4
	)
	default boolean filmClimbs()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shotStyle",
		name = "Shot style",
		description = "Action: close, low, handheld. Scenic: wide shots that show off the course",
		section = shotSection,
		position = -1
	)
	default ShotStyle shotStyle()
	{
		return ShotStyle.ACTION;
	}

	@ConfigItem(keyName = "shotTracking", name = "Tracking", description = "Side-on dolly alongside the player", section = shotSection, position = 0)
	default boolean shotTracking()
	{
		return true;
	}

	@ConfigItem(keyName = "shotChase", name = "Chase", description = "Over-the-shoulder chase", section = shotSection, position = 1)
	default boolean shotChase()
	{
		return true;
	}

	@ConfigItem(keyName = "shotFlyby", name = "Flyby", description = "Camera ahead of the player that whips round as they pass", section = shotSection, position = 2)
	default boolean shotFlyby()
	{
		return true;
	}

	@ConfigItem(keyName = "shotCrane", name = "Crane", description = "Rising or falling crane move on drops and climbs", section = shotSection, position = 3)
	default boolean shotCrane()
	{
		return true;
	}

	@ConfigItem(keyName = "shotOrbit", name = "Orbit", description = "Slow orbit around the player", section = shotSection, position = 4)
	default boolean shotOrbit()
	{
		return true;
	}

	@ConfigItem(keyName = "shotHero", name = "Hero", description = "Low, tight angle looking up at the player", section = shotSection, position = 5)
	default boolean shotHero()
	{
		return true;
	}

	@ConfigItem(keyName = "shotEstablishing", name = "Establishing", description = "Wide view of the whole obstacle", section = shotSection, position = 6)
	default boolean shotEstablishing()
	{
		return true;
	}

	@Range(max = 30)
	@ConfigItem(
		keyName = "cutMinTiles",
		name = "Cut on long obstacles",
		description = "Cut to a second shot halfway through obstacles at least this long. 0 disables cuts",
		section = shotSection,
		position = 7
	)
	@Units(" tiles")
	default int cutMinTiles()
	{
		return 8;
	}

	@Range(min = 1, max = 20)
	@ConfigItem(
		keyName = "stiffness",
		name = "Responsiveness",
		description = "How tightly the camera follows its shot. Lower is floatier",
		section = feelSection,
		position = 0
	)
	default int stiffness()
	{
		return 5;
	}

	@Range(max = 3000)
	@ConfigItem(
		keyName = "holdMs",
		name = "Hold after landing",
		description = "How long to stay on the shot after the player lands",
		section = feelSection,
		position = 1
	)
	@Units(Units.MILLISECONDS)
	default int holdMs()
	{
		return 150;
	}

	@Range(max = 5)
	@ConfigItem(
		keyName = "releaseEarlyTicks",
		name = "Hand back early",
		description = "Start returning the camera this many game ticks before you land, so you can click the next obstacle right away. Needs one run of an obstacle to time it. 0 waits for the landing",
		section = feelSection,
		position = 1
	)
	@Units(Units.TICKS)
	default int releaseEarlyTicks()
	{
		return 2;
	}

	@Range(min = 200, max = 4000)
	@ConfigItem(
		keyName = "returnMs",
		name = "Return time",
		description = "Maximum time to glide back to your own camera",
		section = feelSection,
		position = 2
	)
	@Units(Units.MILLISECONDS)
	default int returnMs()
	{
		return 500;
	}

	@ConfigItem(
		keyName = "autoFrame",
		name = "Auto-framing",
		description = "Watch where your character actually is on screen and keep them in frame",
		section = feelSection,
		position = 3
	)
	default boolean autoFrame()
	{
		return true;
	}

	@ConfigItem(keyName = "handheld", name = "Handheld shake", description = "Subtle camera-operator wobble in the Action style", section = feelSection, position = 3)
	default boolean handheld()
	{
		return true;
	}

	@ConfigItem(keyName = "letterbox", name = "Letterbox", description = "Cinematic black bars during shots", section = feelSection, position = 4)
	default boolean letterbox()
	{
		return true;
	}

	@Range(min = 2, max = 25)
	@ConfigItem(keyName = "letterboxSize", name = "Letterbox size", description = "Height of each bar", section = feelSection, position = 5)
	@Units(Units.PERCENT)
	default int letterboxSize()
	{
		return 10;
	}

	@ConfigItem(keyName = "vignette", name = "Vignette", description = "Darken the edges of the screen during shots", section = feelSection, position = 6)
	default boolean vignette()
	{
		return true;
	}

	@ConfigItem(keyName = "shotName", name = "Show shot name", description = "Show the current shot's name in the letterbox", section = feelSection, position = 7)
	default boolean shotName()
	{
		return false;
	}

	@ConfigItem(
		keyName = "abortOnInput",
		name = "Your input ends shot",
		description = "Hand the camera back when you click the game world, scroll, middle-drag or press arrow keys. World clicks are never sent during a shot",
		section = safetySection,
		position = 0
	)
	default boolean abortOnInput()
	{
		return true;
	}

	@ConfigItem(keyName = "abortOnDamage", name = "Damage ends shot", description = "Hand the camera back when you take damage", section = safetySection, position = 1)
	default boolean abortOnDamage()
	{
		return true;
	}

	@ConfigItem(keyName = "previewKey", name = "Preview hotkey", description = "Play a test orbit around your player", section = debugSection, position = 0)
	default Keybind previewKey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(keyName = "debugOverlay", name = "Debug overlay", description = "Draw detected paths and the director's choice", section = debugSection, position = 1)
	default boolean debugOverlay()
	{
		return false;
	}

	@ConfigItem(keyName = "logCamera", name = "Log camera values", description = "Log raw camera values every few seconds (developer client) to calibrate units", section = debugSection, position = 2)
	default boolean logCamera()
	{
		return false;
	}

	@ConfigItem(
		keyName = "logDetection",
		name = "Log detection",
		description = "Log obstacle clicks and the detector's per-tick view (developer client), to see why an obstacle did or didn't trigger",
		section = debugSection,
		position = 3
	)
	default boolean logDetection()
	{
		return false;
	}
}
