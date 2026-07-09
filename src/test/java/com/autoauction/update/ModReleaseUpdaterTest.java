package com.autoauction.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ModReleaseUpdaterTest {
	@Test
	void buildsModReleaseUrlsFromApiBase() {
		assertEquals(URI.create("https://example.test/api/mod/releases"), ModReleaseUpdater.releaseManifestUri("https://example.test/"));
		assertEquals(URI.create("https://example.test/api/mod/releases/autoauction.jar/download"),
			ModReleaseUpdater.resolveDownloadUri("https://example.test/", "/api/mod/releases/autoauction.jar/download"));
	}

	@Test
	void choosesLatestReleaseByModName() {
		List<ModReleaseUpdater.Release> releases = List.of(
			new ModReleaseUpdater.Release("altmanager-1.0.0.jar", "Alt Manager", "", "", "b".repeat(64)),
			new ModReleaseUpdater.Release("autoauction-1.0.0.jar", "AutoAuction", "", "", "a".repeat(64))
		);

		Optional<ModReleaseUpdater.Release> release = ModReleaseUpdater.findRelease(releases, "AutoAuction");

		assertTrue(release.isPresent());
		assertEquals("autoauction-1.0.0.jar", release.get().filename());
	}

	@Test
	void onlyUpdatesWhenShaDiffersAndIsValid() {
		assertFalse(ModReleaseUpdater.shouldUpdate("a".repeat(64), "a".repeat(64)));
		assertTrue(ModReleaseUpdater.shouldUpdate("a".repeat(64), "b".repeat(64)));
		assertFalse(ModReleaseUpdater.shouldUpdate("a".repeat(64), "not-a-sha"));
		assertFalse(ModReleaseUpdater.shouldUpdate("", "b".repeat(64)));
	}

	@Test
	void temporaryDownloadsDoNotLookLikeLoadableJars() {
		assertFalse(ModReleaseUpdater.downloadTempSuffix().endsWith(".jar"));
	}
}
