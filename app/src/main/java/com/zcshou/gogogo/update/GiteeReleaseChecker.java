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
    private static final String GITHUB_RELEASES_PAGE = "https://github.com/caixukun-1-23/-apk-/releases";

    private final OkHttpClient okHttpClient;

    public GiteeReleaseChecker(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    @Nullable
    public GiteeReleaseInfo fetchLatestRelease() throws Exception {
        Request request = new Request.Builder()
                .url(GITHUB_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PaDouBuPao")
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code());
            }
            String content = body.string();
            JSONObject root = new JSONObject(content);
            String tagName = root.optString("tag_name", "");
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
                    String browserDownloadUrl = asset.optString("browser_download_url", "");
                    if (name.endsWith(".apk") && !browserDownloadUrl.isEmpty()) {
                        downloadUrl = browserDownloadUrl;
                        break;
                    }
                }
            }
            if (downloadUrl.isEmpty()) {
                downloadUrl = GITHUB_RELEASES_PAGE;
            }

            if (tagName.isEmpty()) {
                return null;
            }
            return new GiteeReleaseInfo(tagName, releaseName, changelog, downloadUrl);
        }
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
