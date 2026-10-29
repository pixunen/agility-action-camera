package com.agilityactioncamera.director;

/**
 * Ground height lookup used to keep cameras out of hills and walls.
 */
@FunctionalInterface
public interface Terrain
{
	/**
	 * Ground height at a local position (negative is up), or {@link Double#NaN}
	 * outside the loaded scene.
	 */
	double heightAt(double x, double y);
}
