package com.agilityactioncamera.director;

import java.util.List;
import lombok.Value;

/**
 * The shots chosen for one traversal, each covering a slice of path progress.
 */
@Value
public class ShotPlan
{
	@Value
	public static class Entry
	{
		ShotType type;
		int side;
		/**
		 * Path progress at which this shot cuts in.
		 */
		double fromU;
		double score;
	}

	List<Entry> entries;

	/**
	 * Index of the shot covering progress {@code u}.
	 */
	public int indexAt(double u)
	{
		int index = 0;
		for (int i = 0; i < entries.size(); i++)
		{
			if (u >= entries.get(i).getFromU())
			{
				index = i;
			}
		}
		return index;
	}
}
