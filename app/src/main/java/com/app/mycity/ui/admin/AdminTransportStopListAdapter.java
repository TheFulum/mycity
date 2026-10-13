package com.app.mycity.ui.admin;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.data.model.TransportStop;
import com.app.mycity.databinding.ItemAdminStopBinding;
import com.app.mycity.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

public class AdminTransportStopListAdapter
        extends RecyclerView.Adapter<AdminTransportStopListAdapter.VH> {

    public interface Actions {
        void onOpen(TransportStop stop);
        void onDelete(TransportStop stop);
    }

    private List<TransportStop> items = new ArrayList<>();
    private final Actions actions;

    public AdminTransportStopListAdapter(Actions actions) {
        this.actions = actions;
    }

    public void submit(List<TransportStop> newList) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return newList.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                String oid = items.get(o).getId();
                String nid = newList.get(n).getId();
                return oid != null && oid.equals(nid);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                TransportStop a = items.get(o), b = newList.get(n);
                return equals(a.getName(), b.getName())
                        && a.getLat() == b.getLat()
                        && a.getLng() == b.getLng();
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
        ItemAdminStopBinding b = ItemAdminStopBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position), actions);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemAdminStopBinding b;

        VH(ItemAdminStopBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(TransportStop stop, Actions actions) {
            b.tvName.setText(stop.getName() != null ? stop.getName() : "");
            b.tvCoords.setText(GeoUtils.formatCoords(stop.getLat(), stop.getLng()));
            b.getRoot().setOnClickListener(v -> actions.onOpen(stop));
            b.btnDelete.setOnClickListener(v -> actions.onDelete(stop));
        }
    }
}
