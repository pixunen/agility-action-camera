package com.agilityactioncamera.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class DurationLearnerTest
{
	@Test
	public void latestRunWins()
	{
		DurationLearner learner = new DurationLearner();
		learner.record(55180, 36);
		learner.record(55180, 35);
		assertEquals(35, learner.expectedTicks(55180).getAsDouble(), 0);
	}

	@Test
	public void ignoresEmptyRuns()
	{
		DurationLearner learner = new DurationLearner();
		learner.record(55192, 0);
		assertFalse(learner.expectedTicks(55192).isPresent());
	}
}
