package com.app.mycity.ui.feed;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.bumptech.glide.Glide;

import java.util.List;

public class PhotoSwipeAdapter extends RecyclerView.Adapter<PhotoSwipeAdapter.VH> {

    private final List<String> urls;

    public PhotoSwipeAdapter(List<String> urls) {
        this.urls = urls;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ZoomImageView iv = new ZoomImageView(parent.getContext());
        iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setBackgroundColor(0xFF000000);
        return new VH(iv);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.iv.reset();
        Glide.with(holder.iv.getContext())
                .load(urls.get(position))
                .placeholder(R.drawable.bg_image_placeholder)
                .into(holder.iv);
    }

    @Override
    public int getItemCount() { return urls.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ZoomImageView iv;
        VH(ZoomImageView iv) { super(iv); this.iv = iv; }
    }
}
