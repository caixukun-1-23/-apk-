package com.acooldog.toolbox.route.presentation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.acooldog.toolbox.R;
import com.acooldog.toolbox.route.domain.model.RouteDefinition;
import com.acooldog.toolbox.utils.GoUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LocalRouteActionAdapter extends BaseAdapter {
    public interface Actions {
        void onRun(RouteDefinition routeDefinition);

        void onEdit(RouteDefinition routeDefinition);

        void onShare(RouteDefinition routeDefinition);

        void onDelete(RouteDefinition routeDefinition);
    }

    private final LayoutInflater layoutInflater;
    private final List<RouteDefinition> routes;
    private final Actions actions;
    private boolean busy;

    public void setBusy(boolean busy) {
        if (this.busy != busy) {
            this.busy = busy;
            notifyDataSetChanged();
        }
    }

    public LocalRouteActionAdapter(Context context, Actions actions) {
        this.layoutInflater = LayoutInflater.from(context);
        this.routes = new ArrayList<>();
        this.actions = actions;
    }

    public void submit(List<RouteDefinition> newRoutes) {
        if (routes.equals(newRoutes)) return;
        routes.clear();
        if (newRoutes != null) {
            routes.addAll(newRoutes);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return routes.size();
    }

    @Override
    public RouteDefinition getItem(int position) {
        return routes.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = layoutInflater.inflate(R.layout.route_local_item, parent, false);
            view.setTag(new ViewHolder(view));
        }
        ViewHolder holder = (ViewHolder) view.getTag();
        RouteDefinition routeDefinition = getItem(position);
        TextView nameView = holder.name;
        TextView metaView = holder.meta;
        Button runButton = holder.run;
        Button editButton = holder.edit;
        Button shareButton = holder.share;
        Button deleteButton = holder.delete;
        runButton.setEnabled(!busy);
        shareButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy);

        nameView.setText(routeDefinition.getName());
        metaView.setText(buildMeta(routeDefinition));
        runButton.setOnClickListener(v -> actions.onRun(routeDefinition));
        boolean readOnlyPrivacyRoute = routeDefinition.isDownloadedFromShared() && routeDefinition.isPrivacyProtected();
        editButton.setEnabled(!busy && !readOnlyPrivacyRoute);
        editButton.setText(readOnlyPrivacyRoute ? R.string.route_item_read_only : R.string.route_item_edit);
        if (readOnlyPrivacyRoute) {
            editButton.setOnClickListener(null);
        } else {
            editButton.setOnClickListener(v -> actions.onEdit(routeDefinition));
        }
        shareButton.setOnClickListener(v -> actions.onShare(routeDefinition));
        deleteButton.setOnClickListener(v -> actions.onDelete(routeDefinition));
        return view;
    }

    private static final class ViewHolder {
        final TextView name;
        final TextView meta;
        final Button run;
        final Button edit;
        final Button share;
        final Button delete;

        ViewHolder(View view) {
            name = view.findViewById(R.id.route_item_name);
            meta = view.findViewById(R.id.route_item_meta);
            run = view.findViewById(R.id.btn_route_item_run);
            edit = view.findViewById(R.id.btn_route_item_edit);
            share = view.findViewById(R.id.btn_route_item_share);
            delete = view.findViewById(R.id.btn_route_item_delete);
        }
    }

    private String buildMeta(RouteDefinition routeDefinition) {
        String shareTag;
        if (routeDefinition.shouldMaskMapForSimulation()) {
            shareTag = "[隐私路线] ";
        } else if (routeDefinition.isDownloadedFromShared()) {
            shareTag = "[共享下载] ";
        } else if (routeDefinition.isSharedRoute()) {
            shareTag = "[已共享] ";
        } else {
            shareTag = "[本地路线] ";
        }
        return String.format(
                Locale.getDefault(),
                "%s%d 点 · %s",
                shareTag,
                routeDefinition.getPoints().size(),
                GoUtils.timeStamp2Date(Long.toString(routeDefinition.getUpdatedAt() / 1000L))
        );
    }
}
