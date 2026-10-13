package com.app.mycity.ui.news;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.app.mycity.R;
import com.app.mycity.data.model.NewsItem;
import com.app.mycity.data.repository.NewsRepository;
import com.app.mycity.databinding.FragmentNewsDetailBinding;
import com.app.mycity.ui.main.MainActivity;
import com.bumptech.glide.Glide;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Date;

public class NewsDetailFragment extends Fragment {

    private static final String ARG_ID = "news_id";

    public static NewsDetailFragment newInstance(String newsId) {
        NewsDetailFragment f = new NewsDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ID, newsId);
        f.setArguments(args);
        return f;
    }

    private FragmentNewsDetailBinding b;
    private final NewsRepository repo = new NewsRepository();
    private ListenerRegistration registration;
    private String newsId;
    private NewsItem currentItem;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentNewsDetailBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        newsId = requireArguments().getString(ARG_ID);

        b.btnBack.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).popHost();
            }
        });

        b.btnOpenOriginal.setOnClickListener(v -> openOriginal());

        b.chipLocation.setOnClickListener(v -> openLocation());

        if (newsId == null || newsId.isEmpty()) return;
        subscribe();
    }

    private void subscribe() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        registration = repo.listenOne(newsId, (item, error) -> {
            if (b == null) return;
            if (error != null) {
                Toast.makeText(requireContext(), R.string.news_load_error, Toast.LENGTH_SHORT).show();
                return;
            }
            if (item == null) return;
            currentItem = item;
            render(item);
        });
    }

    private void render(NewsItem item) {
        String imageUrl = item.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            b.ivHeaderImage.setVisibility(View.VISIBLE);
            Glide.with(b.ivHeaderImage.getContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(b.ivHeaderImage);
        } else {
            b.ivHeaderImage.setVisibility(View.GONE);
        }

        b.tvCategoryBadge.setText(categoryLabel(item.getCategory()));
        int badgeColor = ContextCompat.getColor(requireContext(), categoryColor(item.getCategory()));
        b.tvCategoryBadge.setBackgroundTintList(ColorStateList.valueOf(badgeColor));

        b.tvTitle.setText(item.getTitle() != null ? item.getTitle() : "");
        b.tvBody.setText(item.getBody() != null ? item.getBody() : "");
        b.tvSourceAndDate.setText(formatSourceAndDate(item));

        boolean hasLocation = item.getLat() != null && item.getLng() != null;
        b.chipLocation.setVisibility(hasLocation ? View.VISIBLE : View.GONE);

        boolean hasUrl = item.getSourceUrl() != null && !item.getSourceUrl().isEmpty();
        b.btnOpenOriginal.setVisibility(hasUrl ? View.VISIBLE : View.GONE);
    }

    private void openOriginal() {
        if (currentItem == null) return;
        String url = currentItem.getSourceUrl();
        if (url == null || url.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.news_load_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void openLocation() {
        if (currentItem == null) return;
        if (currentItem.getLat() == null || currentItem.getLng() == null) return;
        if (getActivity() instanceof MainActivity) {
            String title = currentItem.getTitle() != null ? currentItem.getTitle() : "";
            ((MainActivity) getActivity()).openMapFullscreen(currentItem.getLat(), currentItem.getLng(), title);
        }
    }

    private String categoryLabel(String category) {
        if (category == null) return getString(R.string.news_category_unknown);
        switch (category) {
            case NewsItem.CATEGORY_IMPORTANT:
                return getString(R.string.news_category_important);
            case NewsItem.CATEGORY_DANGEROUS:
                return getString(R.string.news_category_dangerous);
            case NewsItem.CATEGORY_INTERESTING:
                return getString(R.string.news_category_interesting);
            default:
                return getString(R.string.news_category_unknown);
        }
    }

    @ColorRes
    private int categoryColor(String category) {
        if (category == null) return R.color.text_secondary;
        switch (category) {
            case NewsItem.CATEGORY_DANGEROUS:
                return R.color.accent_red;
            case NewsItem.CATEGORY_IMPORTANT:
                return R.color.accent_yellow;
            case NewsItem.CATEGORY_INTERESTING:
                return R.color.accent_blue;
            default:
                return R.color.text_secondary;
        }
    }

    private String formatSourceAndDate(NewsItem item) {
        String source = item.getSourceName() != null ? item.getSourceName() : "";
        String relative = "";
        Date published = item.getPublishedAt();
        if (published != null) {
            long now = System.currentTimeMillis();
            long time = published.getTime();
            CharSequence rel = DateUtils.getRelativeTimeSpanString(
                    time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
            relative = rel != null ? rel.toString() : "";
        }
        if (source.isEmpty() && relative.isEmpty()) return "";
        if (source.isEmpty()) return relative;
        if (relative.isEmpty()) return source;
        return getString(R.string.news_source_and_date_format, source, relative);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        b = null;
    }
}
