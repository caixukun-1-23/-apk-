package com.acooldog.toolbox;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;

import com.acooldog.toolbox.route.domain.model.RouteDefinition;
import com.acooldog.toolbox.route.presentation.LocalRouteActionAdapter;
import com.acooldog.toolbox.route.presentation.RouteListViewModel;
import com.acooldog.toolbox.utils.GoUtils;
import com.acooldog.toolbox.utils.SearchSortUtils;
import com.acooldog.toolbox.utils.ShareUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class RouteActivity extends BaseActivity {
    private RouteListViewModel viewModel;
    private LocalRouteActionAdapter adapter;
    private SearchView searchView;
    private String query = "";

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::handleImportedRoute);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route);
        MaterialToolbar toolbar = findViewById(R.id.toolbar_route);
        toolbar.setNavigationOnClickListener(v -> finish());
        viewModel = new ViewModelProvider(this).get(RouteListViewModel.class);
        adapter = new LocalRouteActionAdapter(this, new LocalActions());
        ListView list = findViewById(R.id.route_list_view);
        list.setAdapter(adapter);
        findViewById(R.id.fab_import).setOnClickListener(v ->
                importLauncher.launch(new String[]{"*/*"}));
        findViewById(R.id.route_create_button).setOnClickListener(v ->
                startActivity(new Intent(this, RouteCreateActivity.class)));
        findViewById(R.id.route_retry_button).setOnClickListener(v -> viewModel.refresh());
        searchView = findViewById(R.id.route_search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String value) {
                searchView.clearFocus();
                return true;
            }
            @Override public boolean onQueryTextChange(String value) {
                query = value == null ? "" : value.trim();
                render();
                return true;
            }
        });
        if (savedInstanceState != null) searchView.setQuery(savedInstanceState.getString("query", ""), false);
        viewModel.getRoutes().observe(this, ignored -> render());
        viewModel.isLoading().observe(this, ignored -> render());
        viewModel.getError().observe(this, ignored -> render());
        viewModel.getMessage().observe(this, message -> {
            if (message != null && message != 0) {
                GoUtils.DisplayToast(this, getString(message));
                viewModel.consumeMessage();
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("query", searchView.getQuery().toString());
        super.onSaveInstanceState(outState);
    }

    private void render() {
        List<RouteDefinition> all = viewModel.getRoutes().getValue();
        List<RouteDefinition> filtered = new ArrayList<>();
        if (all != null) {
            for (RouteDefinition route : all) {
                if (SearchSortUtils.matches(query, route.getName())) filtered.add(route);
            }
        }
        adapter.submit(filtered);
        boolean loading = Boolean.TRUE.equals(viewModel.isLoading().getValue());
        Integer error = viewModel.getError().getValue();
        boolean failed = error != null && error != 0;
        findViewById(R.id.route_loading).setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.fab_import).setEnabled(!loading);
        adapter.setBusy(loading);
        TextView errorView = findViewById(R.id.route_error);
        errorView.setVisibility(failed ? View.VISIBLE : View.GONE);
        if (failed) errorView.setText(error);
        findViewById(R.id.route_retry_button).setVisibility(failed ? View.VISIBLE : View.GONE);
        TextView count = findViewById(R.id.route_count);
        count.setText(getString(query.isEmpty() ? R.string.route_count_all : R.string.route_count_filtered,
                filtered.size()));
        boolean empty = filtered.isEmpty();
        TextView emptyView = findViewById(R.id.route_no_data);
        emptyView.setText(query.isEmpty() ? R.string.route_library_empty : R.string.route_library_search_empty);
        emptyView.setVisibility(empty && !loading && !failed ? View.VISIBLE : View.GONE);
        findViewById(R.id.route_list_view).setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void handleImportedRoute(Uri uri) {
        if (uri != null) viewModel.importRoute(uri);
    }

    private final class LocalActions implements LocalRouteActionAdapter.Actions {
        @Override public void onRun(RouteDefinition route) {
            startActivity(new Intent(RouteActivity.this, RouteRunActivity.class)
                    .putExtra(RouteRunActivity.EXTRA_ROUTE_ID, route.getId()));
        }
        @Override public void onEdit(RouteDefinition route) {
            startActivity(new Intent(RouteActivity.this, RouteCreateActivity.class)
                    .putExtra(RouteCreateActivity.EXTRA_EDIT_ROUTE_ID, route.getId()));
        }
        @Override public void onShare(RouteDefinition route) {
            try {
                ShareUtils.shareFile(RouteActivity.this, route.getFile(), route.getName());
            } catch (RuntimeException exception) {
                GoUtils.DisplayToast(RouteActivity.this, getString(R.string.route_export_failed));
            }
        }
        @Override public void onDelete(RouteDefinition route) {
            new MaterialAlertDialogBuilder(RouteActivity.this)
                    .setTitle(R.string.route_delete_title)
                    .setMessage(getString(R.string.route_delete_named, route.getName()))
                    .setPositiveButton(R.string.route_item_delete, (dialog, which) -> viewModel.deleteRoute(route))
                    .setNegativeButton(R.string.route_share_cancel, null)
                    .show();
        }
    }
}
