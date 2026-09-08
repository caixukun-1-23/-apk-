package com.acooldog.toolbox.route.presentation;

import android.app.Application;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.acooldog.toolbox.R;
import com.acooldog.toolbox.route.domain.model.RouteDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RouteListViewModel extends AndroidViewModel {
    private final MutableLiveData<List<RouteDefinition>> routes = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> error = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> message = new MutableLiveData<>(0);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final RouteModule routeModule;
    private volatile boolean cleared;
    private int pendingOperations;

    public RouteListViewModel(@NonNull Application application) {
        super(application);
        routeModule = RouteModule.from(application);
    }

    public LiveData<List<RouteDefinition>> getRoutes() { return routes; }
    public LiveData<Boolean> isLoading() { return loading; }
    public LiveData<Integer> getError() { return error; }
    public LiveData<Integer> getMessage() { return message; }
    public void consumeMessage() { message.setValue(0); }

    public void refresh() {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        execute(() -> {}, 0, R.string.route_load_failed);
    }

    public void deleteRoute(RouteDefinition route) {
        execute(() -> routeModule.deleteRouteUseCase().execute(route.getId()),
                R.string.route_delete_success, R.string.route_delete_failed);
    }

    public void importRoute(Uri uri) {
        execute(() -> {
            String displayName = "imported-route";
            try (Cursor cursor = getApplication().getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (column >= 0 && !cursor.isNull(column)) displayName = cursor.getString(column);
                }
            }
            try (InputStream input = getApplication().getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Unable to open route file");
                routeModule.importRouteUseCase().execute(displayName, input);
            }
        }, R.string.route_import_success, R.string.route_import_failed_detail);
    }

    private void execute(IoAction action, int successMessage, int failureMessage) {
        // Entry points and results run on the main thread; only disk work runs on the executor.
        if (cleared) return;
        pendingOperations++;
        loading.setValue(true);
        error.setValue(0);
        executor.execute(() -> {
            List<RouteDefinition> result = null;
            int failure = 0;
            int success = 0;
            try {
                action.run();
                success = successMessage;
                result = routeModule.getRoutesUseCase().execute();
            } catch (Exception exception) {
                failure = success != 0 ? R.string.route_load_failed : failureMessage;
            }
            List<RouteDefinition> finalResult = result;
            int finalFailure = failure;
            int finalSuccess = success;
            mainHandler.post(() -> {
                if (cleared) return;
                if (finalResult != null) routes.setValue(finalResult);
                error.setValue(finalFailure);
                loading.setValue(--pendingOperations > 0);
                if (finalSuccess != 0) message.setValue(finalSuccess);
            });
        });
    }

    @Override
    protected void onCleared() {
        cleared = true;
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private interface IoAction { void run() throws IOException; }
}
