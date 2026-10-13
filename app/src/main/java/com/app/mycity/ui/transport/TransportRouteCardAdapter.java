package com.app.mycity.ui.transport;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.app.mycity.data.model.TransportRoute;
import com.app.mycity.databinding.ItemTransportRouteBinding;

import java.util.ArrayList;
import java.util.List;

public class TransportRouteCardAdapter extends RecyclerView.Adapter<TransportRouteCardAdapter.VH> {

    public interface OnRouteClickListener {
        void onClick(TransportRoute route);
    }

    private List<TransportRoute> items = new ArrayList<>();
    private final OnRouteClickListener onRouteClick;

    public TransportRouteCardAdapter(OnRouteClickListener onRouteClick) {
        this.onRouteClick = onRouteClick;
    }

    public void submit(List<TransportRoute> newList) {
        final List<TransportRoute> safeNew = newList != null ? newList : new ArrayList<>();
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return safeNew.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                String oid = items.get(o).getId();
                String nid = safeNew.get(n).getId();
                return oid != null && oid.equals(nid);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                TransportRoute a = items.get(o), b = safeNew.get(n);
                return strEq(a.getRouteNumber(), b.getRouteNumber())
                        && strEq(a.getType(), b.getType())
                        && strEq(a.getName(), b.getName())
                        && strEq(a.getColor(), b.getColor())
                        && stopCount(a) == stopCount(b)
                        && a.isActive() == b.isActive();
            }
            private boolean strEq(String a, String b) {
                return a == null ? b == null : a.equals(b);
            }
            private int stopCount(TransportRoute r) {
                return r.getStopIds() != null ? r.getStopIds().size() : 0;
            }
        });
        items = new ArrayList<>(safeNew);
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTransportRouteBinding binding = ItemTransportRouteBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position), onRouteClick);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemTransportRouteBinding b;

        VH(ItemTransportRouteBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(TransportRoute route, OnRouteClickListener listener) {
            String number = route.getRouteNumber();
            b.tvRouteNumber.setText(number != null ? number : "");

            int color = parseRouteColor(route.getColor());
            b.tvRouteNumber.setBackgroundTintList(ColorStateList.valueOf(color));

            b.tvRouteType.setText(typeLabel(route.getType()));

            String name = route.getName();
            b.tvRouteName.setText(name != null ? name : "");

            int count = route.getStopIds() != null ? route.getStopIds().size() : 0;
            b.tvStopsCount.setText(b.getRoot().getContext().getString(
                    R.string.transport_stops_count_format, count));

            b.tvActiveBadge.setVisibility(route.isActive() ? View.GONE : View.VISIBLE);

            b.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onClick(route);
            });
        }

        private int parseRouteColor(String hex) {
            int fallback = ContextCompat.getColor(b.getRoot().getContext(), R.color.accent_blue);
            if (hex == null || hex.trim().isEmpty()) return fallback;
            try {
                return Color.parseColor(hex.trim());
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }

        private String typeLabel(String type) {
            if (type == null) return "";
            switch (type) {
                case TransportRoute.TYPE_BUS:
                    return b.getRoot().getContext().getString(R.string.transport_type_bus);
                case TransportRoute.TYPE_TRAM:
                    return b.getRoot().getContext().getString(R.string.transport_type_tram);
                case TransportRoute.TYPE_TROLLEYBUS:
                    return b.getRoot().getContext().getString(R.string.transport_type_trolleybus);
                case TransportRoute.TYPE_MARSHRUTKA:
                    return b.getRoot().getContext().getString(R.string.transport_type_marshrutka);
                default:
                    return "";
            }
        }
    }
}
