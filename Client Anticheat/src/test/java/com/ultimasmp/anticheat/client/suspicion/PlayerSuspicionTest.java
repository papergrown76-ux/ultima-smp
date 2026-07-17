package com.ultimasmp.anticheat.client.suspicion;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests für den Verdachtszustand eines Spielers (reine Java-Logik). */
class PlayerSuspicionTest {
	private final UUID uuid = UUID.randomUUID();
	private final UUID reporterA = UUID.randomUUID();
	private final UUID reporterB = UUID.randomUUID();

	@Test
	void maxScoreUndWorstModule() {
		PlayerSuspicion s = new PlayerSuspicion(uuid, "Steve");
		s.scores.put("killaura", 40.0);
		s.scores.put("reach", 65.0);
		assertEquals(65.0, s.maxScore(), 1.0e-9);
		assertEquals("reach", s.worstModuleId());
	}

	@Test
	void leererZustand() {
		PlayerSuspicion s = new PlayerSuspicion(uuid, "Steve");
		assertEquals(0.0, s.maxScore(), 1.0e-9);
		assertEquals("", s.worstModuleId());
		assertEquals(0, s.confirmationCount(System.currentTimeMillis()));
	}

	@Test
	void bestaetigungenZaehlenNurUnterschiedlicheMelder() {
		PlayerSuspicion s = new PlayerSuspicion(uuid, "Steve");
		long now = 1_000_000;
		s.remoteReports.put(reporterA, new PlayerSuspicion.RemoteReport("A", "killaura", 60, now));
		// Zweite Meldung desselben Melders überschreibt die erste
		s.remoteReports.put(reporterA, new PlayerSuspicion.RemoteReport("A", "reach", 70, now + 10));
		s.remoteReports.put(reporterB, new PlayerSuspicion.RemoteReport("B", "killaura", 55, now + 20));
		assertEquals(2, s.confirmationCount(now + 30));
	}

	@Test
	void abgelaufeneBestaetigungenZaehlenNicht() {
		PlayerSuspicion s = new PlayerSuspicion(uuid, "Steve");
		long now = 1_000_000;
		s.remoteReports.put(reporterA, new PlayerSuspicion.RemoteReport("A", "killaura", 60, now));
		assertEquals(1, s.confirmationCount(now + PlayerSuspicion.REMOTE_REPORT_TTL_MS));
		assertEquals(0, s.confirmationCount(now + PlayerSuspicion.REMOTE_REPORT_TTL_MS + 1));
	}

	@Test
	void staleErstNachAbklingenUndTtl() {
		PlayerSuspicion s = new PlayerSuspicion(uuid, "Steve");
		long now = 1_000_000;
		s.scores.put("speed", 10.0);
		s.lastFlagMs = now;
		assertFalse(s.isStale(now), "frischer Verdacht darf nicht stale sein");

		// Score abgeklungen, aber Flag noch innerhalb der TTL
		s.scores.clear();
		assertFalse(s.isStale(now + 1000));

		// Nach Ablauf der TTL ohne Scores und ohne Bestätigungen -> stale
		assertTrue(s.isStale(now + PlayerSuspicion.REMOTE_REPORT_TTL_MS + 1));
	}
}
