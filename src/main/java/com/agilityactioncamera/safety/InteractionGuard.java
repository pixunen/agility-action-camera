package com.agilityactioncamera.safety;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import net.runelite.api.MenuAction;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseWheelListener;

/**
 * Keeps the plugin within Jagex's rules while the camera is detached: no
 * world interaction may happen from a camera that isn't the player's. World
 * clicks are consumed, and camera input from the player can be treated as
 * "give me my camera back".
 * <p>
 * Input listeners only observe; they never consume or inject events.
 */
@Singleton
public class InteractionGuard extends MouseAdapter implements KeyListener, MouseWheelListener
{
	private static final Set<MenuAction> WORLD_ACTIONS = EnumSet.of(
		MenuAction.WALK, MenuAction.SET_HEADING,
		MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION,
		MenuAction.GAME_OBJECT_FOURTH_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION,
		MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION, MenuAction.NPC_THIRD_OPTION,
		MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION,
		MenuAction.PLAYER_FIRST_OPTION, MenuAction.PLAYER_SECOND_OPTION, MenuAction.PLAYER_THIRD_OPTION,
		MenuAction.PLAYER_FOURTH_OPTION, MenuAction.PLAYER_FIFTH_OPTION, MenuAction.PLAYER_SIXTH_OPTION,
		MenuAction.PLAYER_SEVENTH_OPTION, MenuAction.PLAYER_EIGHTH_OPTION,
		MenuAction.GROUND_ITEM_FIRST_OPTION, MenuAction.GROUND_ITEM_SECOND_OPTION, MenuAction.GROUND_ITEM_THIRD_OPTION,
		MenuAction.GROUND_ITEM_FOURTH_OPTION, MenuAction.GROUND_ITEM_FIFTH_OPTION,
		MenuAction.WORLD_ENTITY_FIRST_OPTION, MenuAction.WORLD_ENTITY_SECOND_OPTION, MenuAction.WORLD_ENTITY_THIRD_OPTION,
		MenuAction.WORLD_ENTITY_FOURTH_OPTION, MenuAction.WORLD_ENTITY_FIFTH_OPTION,
		MenuAction.ITEM_USE_ON_GAME_OBJECT, MenuAction.ITEM_USE_ON_NPC, MenuAction.ITEM_USE_ON_PLAYER,
		MenuAction.ITEM_USE_ON_GROUND_ITEM,
		MenuAction.WIDGET_TARGET_ON_GAME_OBJECT, MenuAction.WIDGET_TARGET_ON_NPC, MenuAction.WIDGET_TARGET_ON_PLAYER,
		MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
		MenuAction.EXAMINE_OBJECT, MenuAction.EXAMINE_NPC, MenuAction.EXAMINE_ITEM_GROUND, MenuAction.EXAMINE_WORLD_ENTITY
	);

	private final ClientThread clientThread;

	/**
	 * Called on the client thread when the player reaches for the camera themselves.
	 */
	@Setter
	private Runnable onTakeover = () ->
	{
	};

	/**
	 * Only react to input while a cinematic owns the camera.
	 */
	@Setter
	private volatile boolean armed;

	@Inject
	InteractionGuard(ClientThread clientThread)
	{
		this.clientThread = clientThread;
	}

	public static boolean isWorldAction(MenuAction action)
	{
		return WORLD_ACTIONS.contains(action);
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (e.getButton() == MouseEvent.BUTTON2)
		{
			takeover();
		}
		return e;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
	{
		takeover();
		return e;
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		switch (e.getKeyCode())
		{
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_RIGHT:
			case KeyEvent.VK_UP:
			case KeyEvent.VK_DOWN:
				takeover();
				break;
			default:
				break;
		}
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
	}

	private void takeover()
	{
		if (armed)
		{
			clientThread.invoke(onTakeover);
		}
	}
}
