package com.agilityactioncamera.detect;

import lombok.Value;

/**
 * Posted on the EventBus when the player starts a long obstacle traversal.
 */
@Value
public class TraversalStarted
{
	Traversal traversal;
}
