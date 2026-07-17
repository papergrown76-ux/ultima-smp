package com.ultimasmp.anticheat.client.track;

/**
 * Momentaufnahme des Zustands eines beobachteten Spielers in einem Client-Tick.
 * Wird vom {@link PlayerTracker} erhoben; die Detection-Module lesen daraus.
 */
public class TickSnapshot {
	public long tick;

	public double x, y, z;
	public float yaw, pitch;

	/** Positions-/Rotationsänderung gegenüber dem vorherigen Tick. */
	public double deltaXZ, deltaY;
	public float deltaYaw, deltaPitch;
	/** true, wenn der vorherige Snapshot genau einen Tick zurückliegt. */
	public boolean consecutive;

	public boolean grounded;
	public boolean touchingWater;
	public boolean submerged;
	public boolean swimming;
	public boolean usingItem;
	public boolean gliding;
	public boolean riding;
	public boolean sneaking;
	public boolean climbing;
	/** Befindet sich ein solider Block direkt unter dem Spieler? */
	public boolean blockBelowSolid;

	public int hurtTime;
}
