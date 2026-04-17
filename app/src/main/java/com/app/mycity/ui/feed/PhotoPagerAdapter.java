package com.app.mycity.ui.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.VH> {

    private final List<String> urls = new ArrayList<>();

    public void submit(List<String> list) {
        urls.clear();
        if (list != null) urls.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_pager, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Glide.with(holder.iv.getContext())
                .load(urls.get(position))
                .placeholder(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(holder.iv);
    }

    @Override public int getItemCount() { return urls.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView iv;
        VH(@NonNull View v) { super(v); iv = v.findViewById(R.id.iv_photo); }
    }
}
