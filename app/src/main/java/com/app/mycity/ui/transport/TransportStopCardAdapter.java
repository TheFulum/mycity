package com.app.mycity.ui.transport;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.data.model.TransportStop;
import com.app.mycity.databinding.ItemTransportStopBinding;
import com.app.mycity.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

public class TransportStopCardAdapter extends RecyclerView.Adapter<TransportStopCardAdapter.VH> {

    public interface OnStopClickListener {
        void onClick(TransportStop stop);
    }

    private List<TransportStop> items = new ArrayList<>();
    private final OnStopClickListener onStopClick;

    public TransportStopCardAdapter(OnStopClickListener onStopClick) {
        this.onStopClick = onStopClick;
    }

    public void submit(List<TransportStop> newList) {
        items = new ArrayList<>(newList != null ? newList : new ArrayList<>());
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTransportStopBinding binding = ItemTransportStopBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position), position, onStopClick);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemTransportStopBinding b;

        VH(ItemTransportStopBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(TransportStop stop, int orderIndex, OnStopClickListener listener) {
            b.tvOrder.setText(String.valueOf(orderIndex + 1));

            String name = stop.getName();
            b.tvStopName.setText(name != null ? name : "");

            b.tvStopAddress.setText(GeoUtils.formatCoords(stop.getLat(), stop.getLng()));

            if (listener != null) {
                b.getRoot().setOnClickListener(v -> listener.onClick(stop));
                b.getRoot().setClickable(true);
                b.getRoot().setFocusable(true);
            } else {
                b.getRoot().setOnClickListener(null);
                b.getRoot().setClickable(false);
                b.getRoot().setFocusable(false);
            }
        }
    }
}
