package com.acooldog.toolbox;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.acooldog.toolbox.update.GiteeReleaseChecker;
import com.acooldog.toolbox.update.GiteeReleaseInfo;
import com.acooldog.toolbox.utils.GoUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;
import okhttp3.OkHttpClient;

public class HomeActivity extends BaseActivity {
    private static final String PREF_IGNORED_RELEASE = "pref_ignored_gitee_release";
    private static final String PREF_LAST_AUTO_CHECK_VERSION = "pref_last_auto_check_version";
    private static final int SDK_PERMISSION_REQUEST = 127;

    private ExecutorService ioExecutor;
    private SharedPreferences sharedPreferences;
    private OkHttpClient okHttpClient;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        ioExecutor = Executors.newSingleThreadExecutor();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        okHttpClient = new OkHttpClient();

        findViewById(R.id.card_route_create).setOnClickListener(v -> open(RouteCreateActivity.class));
        findViewById(R.id.card_route_run).setOnClickListener(v -> open(RouteRunActivity.class));
        findViewById(R.id.card_route_library).setOnClickListener(v -> open(RouteActivity.class));
        findViewById(R.id.btn_check_update).setOnClickListener(v -> checkGiteeReleaseUpdate(true));
        findViewById(R.id.btn_settings).setOnClickListener(v -> open(SettingsActivity.class));
        findViewById(R.id.card_mock_status).setOnClickListener(v -> openMockLocationSettings());

        requestMissingPermissions();
        checkGiteeReleaseUpdate(false);
        refreshMockStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMockStatus();
    }

    @Override
    protected void onDestroy() {
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SDK_PERMISSION_REQUEST && !allPermissionsGranted(grantResults)) {
            GoUtils.DisplayToast(this, getString(R.string.app_error_permission));
        }
    }

    private void open(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    private void refreshMockStatus() {
        boolean ready = GoUtils.isAllowMockLocation(this);
        View card = findViewById(R.id.card_mock_status);
        TextView title = findViewById(R.id.tv_mock_status_title);
        TextView detail = findViewById(R.id.tv_mock_status_detail);
        TextView action = findViewById(R.id.tv_mock_status_action);
        if (card == null || title == null || detail == null || action == null) {
            return;
        }
        if (ready) {
            card.setBackgroundResource(R.drawable.bg_status_ok);
            title.setText(R.string.home_mock_on_title);
            title.setTextColor(getResources().getColor(R.color.saberCyan, getTheme()));
            detail.setText(R.string.home_mock_on_detail);
            action.setText("");
        } else {
            card.setBackgroundResource(R.drawable.bg_status_warn);
            title.setText(R.string.home_mock_off_title);
            title.setTextColor(getResources().getColor(R.color.saberGold, getTheme()));
            detail.setText(R.string.home_mock_off_detail);
            action.setText(R.string.home_mock_action);
        }
    }

    private void openMockLocationSettings() {
        if (GoUtils.isAllowMockLocation(this)) {
            return;
        }
        GoUtils.showEnableMockLocationDialog(this);
    }

    private void requestMissingPermissions() {
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(missing, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(missing, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), SDK_PERMISSION_REQUEST);
        }
    }

    private void addIfMissing(List<String> missing, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    private boolean allPermissionsGranted(@NonNull int[] grantResults) {
        if (grantResults.length == 0) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void checkGiteeReleaseUpdate(boolean manual) {
        String currentVersion = GoUtils.getVersionName(this);
        if (currentVersion == null) {
            return;
        }
        if (!manual) {
            String checkedVersion = sharedPreferences.getString(PREF_LAST_AUTO_CHECK_VERSION, "");
            if (currentVersion.equals(checkedVersion)) {
                return;
            }
        }
        if (!GoUtils.isNetworkAvailable(this)) {
            if (manual) {
                GoUtils.DisplayToast(this, getString(R.string.app_error_network));
            }
            return;
        }
        if (manual) {
            GoUtils.DisplayToast(this, getString(R.string.update_checking));
        }
        ioExecutor.execute(() -> {
            try {
                GiteeReleaseChecker checker = new GiteeReleaseChecker(okHttpClient);
                GiteeReleaseInfo releaseInfo = checker.fetchLatestRelease();
                sharedPreferences.edit().putString(PREF_LAST_AUTO_CHECK_VERSION, currentVersion).apply();
                if (releaseInfo == null) {
                    if (manual) {
                        runOnUiThread(() -> GoUtils.DisplayToast(this, getString(R.string.update_check_failed)));
                    }
                    return;
                }

                String ignoredTag = sharedPreferences.getString(PREF_IGNORED_RELEASE, "");
                boolean newer = checker.isNewerThan(releaseInfo.getTagName(), currentVersion);
                if (!newer) {
                    if (manual) {
                        runOnUiThread(() -> GoUtils.DisplayToast(this, getString(R.string.update_last)));
                    }
                    return;
                }
                if (!manual && releaseInfo.getTagName().equals(ignoredTag)) {
                    return;
                }

                runOnUiThread(() -> showReleaseUpdateDialog(releaseInfo));
            } catch (Exception exception) {
                if (manual) {
                    runOnUiThread(() -> GoUtils.DisplayToast(this, buildDetailedToast(R.string.update_check_failed, exception)));
                }
            }
        });
    }

    private void showReleaseUpdateDialog(GiteeReleaseInfo releaseInfo) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_release_update, null);
        android.widget.TextView versionView = dialogView.findViewById(R.id.update_release_version);
        android.widget.TextView changelogView = dialogView.findViewById(R.id.update_release_changelog);

        versionView.setText(getString(R.string.update_dialog_version, releaseInfo.getTagName()));
        Markwon.create(this).setMarkdown(
                changelogView,
                releaseInfo.getChangelog().isEmpty() ? getString(R.string.update_dialog_empty_log) : releaseInfo.getChangelog()
        );

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_dialog_title)
                .setView(dialogView)
                .setPositiveButton(R.string.update_dialog_download, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.getDownloadUrl()));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.update_dialog_acknowledged, (dialog, which) ->
                        sharedPreferences.edit().putString(PREF_IGNORED_RELEASE, releaseInfo.getTagName()).apply())
                .show();
    }

    private String buildDetailedToast(int prefixResId, Exception exception) {
        String detail = exception == null || exception.getMessage() == null ? "" : exception.getMessage().trim();
        if (detail.isEmpty()) {
            return getString(prefixResId);
        }
        return getString(prefixResId) + " " + detail;
    }
}
