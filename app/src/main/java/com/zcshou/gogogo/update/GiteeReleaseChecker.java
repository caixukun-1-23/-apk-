package com.acooldog.toolbox.update;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class GiteeReleaseChecker {
    private static final String GITHUB_RELEASE_API = "https://api.github.com/repos/caixukun-1-23/-apk-/releases/latest";
    private static final String FALLBACK_MANIFEST_URL = "https://raw.githubusercontent.com/caixukun-1-23/-apk-/main/update.json";
    private static final String GITHUB_RELEASES_PAGE = "https://github.com/caixukun-1-23/-apk-/releases";
    private static final String TRUSTED_DOWNLOAD_PREFIX = GITHUB_RELEASES_PAGE + "/";

    private final OkHttpClient okHttpClient;

    public GiteeReleaseChecker(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    @Nullable
    public GiteeReleaseInfo fetchLatestRelease() throws Exception {
        Exception apiFailure = null;
        try {
            GiteeReleaseInfo release = parseGitHubRelease(fetchJson(
                    GITHUB_RELEASE_API,
                    "application/vnd.github+json"
            ));
            if (release != null) {
                return release;
            }
        } catch (Exception exception) {
            apiFailure = exception;
        }

        try {
            return parseFallbackManifest(fetchJson(FALLBACK_MANIFEST_URL, "application/json"));
        } catch (Exception fallbackFailure) {
            if (apiFailure != null) {
                fallbackFailure.addSuppressed(apiFailure);
            }
            throw fallbackFailure;
        }
    }

    private String fetchJson(String url, String accept) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", accept)
                .header("User-Agent", "PaDouBuPao")
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code());
            }
            return body.string();
        }
    }

    @Nullable
    static GiteeReleaseInfo parseGitHubRelease(String content) throws Exception {
        JSONObject root = new JSONObject(content);
        String tagName = root.optString("tag_name", "").trim();
        if (tagName.isEmpty()) {
            return null;
        }
        String releaseName = root.optString("name", tagName);
        String changelog = root.optString("body", "");

        String downloadUrl = "";
        JSONArray assets = root.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                String candidate = asset.optString("browser_download_url", "");
                if (name.endsWith(".apk") && isTrustedDownloadUrl(candidate)) {
                    downloadUrl = candidate;
                    break;
                }
            }
        }
        if (downloadUrl.isEmpty()) {
            downloadUrl = GITHUB_RELEASES_PAGE;
        }
        return new GiteeReleaseInfo(tagName, releaseName, changelog, downloadUrl);
    }

    @Nullable
    static GiteeReleaseInfo parseFallbackManifest(String content) throws Exception {
        JSONObject root = new JSONObject(content);
        String version = root.optString("version", "").trim();
        String downloadUrl = root.optString("download_url", "").trim();
        if (version.isEmpty() || !isTrustedDownloadUrl(downloadUrl)) {
            return null;
        }
        return new GiteeReleaseInfo(
                version,
                root.optString("name", version),
                root.optString("body", ""),
                downloadUrl
        );
    }

    private static boolean isTrustedDownloadUrl(String url) {
        return url != null && url.startsWith(TRUSTED_DOWNLOAD_PREFIX) && url.endsWith(".apk");
    }

    public boolean isNewerThan(String latestVersion, String currentVersion) {
        int[] latest = parseVersion(latestVersion);
        int[] current = parseVersion(currentVersion);
        for (int i = 0; i < 3; i++) {
            if (latest[i] > current[i]) {
                return true;
            }
            if (latest[i] < current[i]) {
                return false;
            }
        }
        return false;
    }

    private int[] parseVersion(String version) {
        String normalized = version == null ? "" : version.trim().toLowerCase().replace("v", "");
        String[] parts = normalized.split("\\.");
        int[] result = new int[]{0, 0, 0};
        for (int i = 0; i < result.length && i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {
                result[i] = 0;
            }
        }
        return result;
    }
}
