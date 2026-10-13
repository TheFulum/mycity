package com.app.mycity.ui.admin;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.data.model.TransportRoute;
import com.app.mycity.databinding.ItemAdminRouteBinding;

import java.util.ArrayList;
import java.util.List;

public class AdminTransportRouteListAdapter
        extends RecyclerView.Adapter<AdminTransportRouteListAdapter.VH> {

    public interface Actions {
        void onOpen(TransportRoute route);
        void onDelete(TransportRoute route);
    }

    private List<TransportRoute> items = new ArrayList<>();
    private final Actions actions;

    public AdminTransportRouteListAdapter(Actions actions) {
        this.actions = actions;
    }

    public void submit(List<TransportRoute> newList) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return newList.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                String oid = items.get(o).getId();
                String nid = newList.get(n).getId();
                return oid != null && oid.equals(nid);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                TransportRoute a = items.get(o), b = newList.get(n);
                return equals(a.getRouteNumber(), b.getRouteNumber())
                        && equals(a.getName(), b.getName())
                        && equals(a.getType(), b.getType())
                        && equals(a.getColor(), b.getColor());
            }
            private boolean equals(String a, String b) {
                return a == null ? b == null : a.equals(b);
            }
        });
        items = new ArrayList<>(newList);
        result.dispatchUpdatesTo(this);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAdminRouteBinding b = ItemAdminRouteBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position), actions);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static String typeLabel(String type) {
        if (type == null) return "—";
        switch (type) {
            case TransportRoute.TYPE_BUS: return "Автобус";
            case TransportRoute.TYPE_TRAM: return "Трамвай";
            case TransportRoute.TYPE_TROLLEYBUS: return "Троллейбус";
            case TransportRoute.TYPE_MARSHRUTKA: return "Маршрутка";
            default: return type;
        }
    }

    static int parseColor(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemAdminRouteBinding b;

        VH(ItemAdminRouteBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(TransportRoute route, Actions actions) {
            String num = route.getRouteNumber() != null ? route.getRouteNumber() : "?";
            b.tvNumber.setText(num);
            int fallback = androidx.core.content.ContextCompat.getColor(
                    b.getRoot().getContext(), com.app.mycity.R.color.accent_blue);
            int color = parseColor(route.getColor(), fallback);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(color);
            bg.setCornerRadius(8f * b.getRoot().getResources().getDisplayMetrics().density);
            b.tvNumber.setBackground(bg);
            b.tvName.setText(route.getName() != null ? route.getName() : "");
            b.tvType.setText(typeLabel(route.getType()));
            b.getRoot().setOnClickListener(v -> actions.onOpen(route));
            b.btnDelete.setOnClickListener(v -> actions.onDelete(route));
        }
    }
}
