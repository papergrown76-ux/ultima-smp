package com.ultimasmp.anticheat.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests für den Rate-Limiter des Team-Kanals (Spam-/Missbrauchsschutz). */
class RateLimiterTest {

	@Test
	void erlaubtErstesEreignisImmer() {
		RateLimiter limiter = new RateLimiter(1000, 0, 0);
		assertTrue(limiter.tryAcquire("a", 0));
	}

	@Test
	void blockiertUnterhalbDesMindestabstands() {
		RateLimiter limiter = new RateLimiter(1000, 0, 0);
		assertTrue(limiter.tryAcquire("a", 0));
		assertFalse(limiter.tryAcquire("a", 999));
		assertTrue(limiter.tryAcquire("a", 1000));
	}

	@Test
	void schluesselSindUnabhaengig() {
		RateLimiter limiter = new RateLimiter(1000, 0, 0);
		assertTrue(limiter.tryAcquire("a", 0));
		assertTrue(limiter.tryAcquire("b", 10));
		assertFalse(limiter.tryAcquire("a", 500));
	}

	@Test
	void fensterLimitGreift() {
		RateLimiter limiter = new RateLimiter(0, 60_000, 3);
		assertTrue(limiter.tryAcquire("a", 0));
		assertTrue(limiter.tryAcquire("a", 1));
		assertTrue(limiter.tryAcquire("a", 2));
		assertFalse(limiter.tryAcquire("a", 3)); // 4. Ereignis im Fenster
	}

	@Test
	void fensterGibtNachAblaufWiederFrei() {
		RateLimiter limiter = new RateLimiter(0, 60_000, 2);
		assertTrue(limiter.tryAcquire("a", 0));
		assertTrue(limiter.tryAcquire("a", 1));
		assertFalse(limiter.tryAcquire("a", 2));
		// Nach Ablauf des Fensters sind die alten Ereignisse vergessen
		assertTrue(limiter.tryAcquire("a", 60_002));
	}

	@Test
	void blockierteEreignisseVerbrauchenKeinBudget() {
		RateLimiter limiter = new RateLimiter(1000, 60_000, 2);
		assertTrue(limiter.tryAcquire("a", 0));
		// Wird durch den Mindestabstand geblockt und darf NICHT ins Fenster zählen
		assertFalse(limiter.tryAcquire("a", 1));
		assertTrue(limiter.tryAcquire("a", 1000));
		assertFalse(limiter.tryAcquire("a", 2000)); // Fenster-Limit (2) erreicht
	}

	@Test
	void beideRegelnKombiniert() {
		RateLimiter limiter = new RateLimiter(2000, 60_000, 20);
		long now = 0;
		int allowed = 0;
		for (int i = 0; i < 100; i++) {
			if (limiter.tryAcquire("spammer", now)) {
				allowed++;
			}
			now += 500; // Angreifer feuert alle 500 ms
		}
		// 100 Versuche über 50 s, alle 2 s einer erlaubt -> maximal ~25, Fenster kappt bei 20
		assertTrue(allowed <= 25, "zu viele Ereignisse erlaubt: " + allowed);
	}
}
