package com.agilityactioncamera.detect;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ObjectID;

/**
 * What sort of movement an obstacle is. The director uses it to prefer shots
 * that suit the movement; it is never a camera position.
 * <p>
 * Known obstacles get their kind from a per-course profile. Anything else is
 * classified from the animation the player plays, so new courses still get
 * sensible shots.
 */
public enum ObstacleKind
{
	ZIPLINE,
	SWING,
	JUMP,
	BALANCE,
	MONKEYBARS,
	CLIMB,
	UNKNOWN;

	/**
	 * Course profiles: object id to kind. Colossal Wyrm first; names come from the gameval constants.
	 */
	private static final Map<Integer, ObstacleKind> PROFILES = ImmutableMap.<Integer, ObstacleKind>builder()
		// Colossal Wyrm
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_START_LADDER_TRIGGER, CLIMB)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_LADDER_1_TRIGGER, CLIMB)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_LADDER_1_TRIGGER, CLIMB)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_BALANCE_1_TRIGGER, BALANCE)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_BALANCE_1_TERMITES, BALANCE)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_BALANCE_1_TRIGGER, BALANCE)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_BALANCE_1_TERMITES, BALANCE)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_BALANCE_1_TRIGGER, BALANCE)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_BALANCE_1_TERMITES, BALANCE)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_MULTIJUMP_TRIGGER, JUMP)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_MULTIJUMP_TERMITES, JUMP)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_JUMP_1_TRIGGER, JUMP)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_JUMP_1_TERMITES, JUMP)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_JUMP_1_TRIGGER, JUMP)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_JUMP_1_TERMITES, JUMP)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_MONKEYBARS_1_TRIGGER, MONKEYBARS)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_MONKEYBARS_1_TERMITES, MONKEYBARS)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_MONKEYBARS_1_TRIGGER, MONKEYBARS)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_MONKEYBARS_1_TERMITES, MONKEYBARS)
		.put(ObjectID.VARLAMORE_WYRM_AGILITY_END_ZIPLINE_TRIGGER, ZIPLINE)
		// Werewolf
		.put(ObjectID.WEREWOLF_SLIDE_CENTER, ZIPLINE)
		.put(ObjectID.WEREWOLF_SLIDE_SIDE, ZIPLINE)
		.put(ObjectID.WEREWOLF_SLIDE_SIDE_MIRROR, ZIPLINE)
		.build();

	private static final Map<Integer, ObstacleKind> ANIMATIONS = ImmutableMap.<Integer, ObstacleKind>builder()
		.put(AnimationID.ZIPLINE_BITE, ZIPLINE)
		.put(AnimationID.ZIPLINE_SLIDE, ZIPLINE)
		.put(AnimationID.WYRM_AGILITY_ZIPLINE_ON, ZIPLINE)
		.put(AnimationID.WYRM_AGILITY_ZIPLINE_WALK, ZIPLINE)
		.put(AnimationID.WYRM_AGILITY_ZIPLINE_JUMP, ZIPLINE)
		.put(AnimationID.WYRM_AGILITY_ZIPLINE_READY, ZIPLINE)
		.put(AnimationID.WYRM_AGILITY_ZIPLINE_OFF, ZIPLINE)
		.put(AnimationID.WYRM_AGILITY_ZIPLINE_CAPE_DRAG, ZIPLINE)
		.put(AnimationID.HUMAN_ROPESWING, SWING)
		.put(AnimationID.HUMAN_ROPESWING_LONG, SWING)
		.put(AnimationID.HUMAN_LONGJUMP, JUMP)
		.put(AnimationID.HUMAN_SPOT_JUMP, JUMP)
		.put(AnimationID.HUMAN_JUMP_HURDLE, JUMP)
		.put(AnimationID.HUMAN_JUMP_STONES, JUMP)
		.put(AnimationID.HUMAN_STEPPINGSTONEJUMP, JUMP)
		.put(AnimationID.ROOFTOPS_POLE_VAULT, JUMP)
		.put(AnimationID.WYRM_AGILITY_LONGJUMP, JUMP)
		.put(AnimationID.WYRM_AGILITY_SHORTJUMP, JUMP)
		.put(AnimationID.VARLAMORE_WYRM_AGILITY_LONGJUMP, JUMP)
		.put(AnimationID.HUMAN_MONKEYBARS_ON, MONKEYBARS)
		.put(AnimationID.HUMAN_MONKEYBARS_OFF, MONKEYBARS)
		.put(AnimationID.HUMAN_MONKEYBARS_WALK, MONKEYBARS)
		.put(AnimationID.HUMAN_MONKEYBARS_READY, MONKEYBARS)
		.put(AnimationID.HUMAN_WALK_LOGBALANCE, BALANCE)
		.put(AnimationID.HUMAN_WALK_LOGBALANCE_READY, BALANCE)
		.put(AnimationID.HUMAN_WALK_LOGBALANCE_STUMBLE, BALANCE)
		.put(AnimationID.HUMAN_WALK_LOGBALANCE_LOOP, BALANCE)
		.put(AnimationID.WYRM_AGILITY_LEDGE_ON, BALANCE)
		.put(AnimationID.WYRM_AGILITY_LEDGE_WALK, BALANCE)
		.put(AnimationID.WYRM_AGILITY_LEDGE_READY, BALANCE)
		.put(AnimationID.WYRM_AGILITY_LEDGE_OFF, BALANCE)
		.put(AnimationID.HUMAN_REACHFORLADDER, CLIMB)
		.put(AnimationID.WYRM_AGILITY_TIGHTROPE_WOBBLE, BALANCE)
		.build();

	/**
	 * Kind from a course profile, or {@link #UNKNOWN}.
	 */
	public static ObstacleKind forObject(int objectId)
	{
		return PROFILES.getOrDefault(objectId, UNKNOWN);
	}

	/**
	 * Kind implied by an animation or pose, or {@link #UNKNOWN}.
	 */
	public static ObstacleKind forAnimation(int animation)
	{
		return ANIMATIONS.getOrDefault(animation, UNKNOWN);
	}

	/**
	 * Whether an animation or pose is distinctive enough to mean "doing agility" without a click to confirm it.
	 */
	public static boolean isAgilityAnimation(int animation)
	{
		ObstacleKind kind = forAnimation(animation);
		return kind != UNKNOWN && kind != CLIMB;
	}
}
