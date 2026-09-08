package com.acooldog.toolbox.update;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GiteeReleaseCheckerTest {
    @Test
    public void comparesPublishedSemanticVersions() {
        GiteeReleaseChecker checker = new GiteeReleaseChecker(null);

        assertTrue(checker.isNewerThan("v2.4.1", "v2.4.0"));
        assertFalse(checker.isNewerThan("v2.4.1", "v2.4.1"));
        assertFalse(checker.isNewerThan("v2.3.9", "v2.4.0"));
    }

    @Test
    public void parsesFallbackManifest() throws Exception {
        GiteeReleaseInfo release = GiteeReleaseChecker.parseFallbackManifest(
                "{\"version\":\"v2.4.1\",\"name\":\"release\",\"body\":\"notes\"," +
                        "\"download_url\":\"https://github.com/caixukun-1-23/-apk-/releases/latest/download/app.apk\"}"
        );

        assertEquals("v2.4.1", release.getTagName());
        assertEquals("notes", release.getChangelog());
    }

    @Test
    public void rejectsUntrustedFallbackDownload() throws Exception {
        assertNull(GiteeReleaseChecker.parseFallbackManifest(
                "{\"version\":\"v9.9.9\",\"download_url\":\"https://example.com/app.apk\"}"
        ));
    }

    @Test
    public void ignoresUntrustedApiAsset() throws Exception {
        GiteeReleaseInfo release = GiteeReleaseChecker.parseGitHubRelease(
                "{\"tag_name\":\"v2.4.1\",\"assets\":[{" +
                        "\"name\":\"app.apk\",\"browser_download_url\":\"https://example.com/app.apk\"}]}"
        );

        assertEquals("https://github.com/caixukun-1-23/-apk-/releases", release.getDownloadUrl());
    }
}
