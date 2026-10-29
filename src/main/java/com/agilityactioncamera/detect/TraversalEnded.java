package com.agilityactioncamera.detect;

import lombok.Value;

/**
 * Posted on the EventBus when a traversal finishes, or is abandoned
 * ({@code aborted}) after a teleport or timeout.
 */
@Value
public class TraversalEnded
{
	Traversal traversal;
	boolean aborted;
}
