package com.ultimasmp.anticheat.client.util;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Kleine Mathe-Helfer für die Detection-Module. */
public final class MathUtil {
	private MathUtil() {
	}

	/** Blickrichtung als Einheitsvektor aus Yaw/Pitch (Vanilla-Formel). */
	public static Vec3d directionFromRotation(float yaw, float pitch) {
		float yawRad = -yaw * ((float) Math.PI / 180.0f);
		float pitchRad = -pitch * ((float) Math.PI / 180.0f);
		float cosYaw = MathHelper.cos(yawRad);
		float sinYaw = MathHelper.sin(yawRad);
		float cosPitch = MathHelper.cos(pitchRad);
		float sinPitch = MathHelper.sin(pitchRad);
		return new Vec3d(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
	}

	/** Winkel zwischen zwei Vektoren in Grad (0-180). */
	public static double angleBetweenDeg(Vec3d a, Vec3d b) {
		double lenProduct = a.length() * b.length();
		if (lenProduct < 1.0e-8) {
			return 0.0;
		}
		double cos = MathHelper.clamp(a.dotProduct(b) / lenProduct, -1.0, 1.0);
		return Math.toDegrees(Math.acos(cos));
	}

	/** Horizontale Distanz zwischen zwei Punkten. */
	public static double horizontalDistance(double x1, double z1, double x2, double z2) {
		double dx = x2 - x1;
		double dz = z2 - z1;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Standardabweichung einer Messreihe. */
	public static double stdDev(double[] values, int count) {
		if (count < 2) {
			return 0.0;
		}
		double mean = 0.0;
		for (int i = 0; i < count; i++) {
			mean += values[i];
		}
		mean /= count;
		double variance = 0.0;
		for (int i = 0; i < count; i++) {
			double d = values[i] - mean;
			variance += d * d;
		}
		return Math.sqrt(variance / count);
	}
}
