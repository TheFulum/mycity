package com.app.mycity.ui.news;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.R;
import com.app.mycity.data.model.NewsItem;
import com.app.mycity.data.repository.NewsRepository;
import com.app.mycity.data.repository.NewsRepository.CategoryFilter;
import com.app.mycity.databinding.FragmentNewsFeedBinding;
import com.app.mycity.ui.main.MainActivity;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class NewsFeedFragment extends Fragment {

    private FragmentNewsFeedBinding b;
    private final NewsRepository repo = new NewsRepository();
    private NewsCardAdapter adapter;
    private ListenerRegistration registration;
    private CategoryFilter currentFilter = CategoryFilter.ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentNewsFeedBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new NewsCardAdapter(item -> {
            if (item == null || item.getId() == null) return;
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openNewsDetail(item.getId());
            }
        });

        b.rvNews.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvNews.setItemAnimator(null);
        b.rvNews.setAdapter(adapter);

        b.chipNewsAll.setOnClickListener(v -> selectFilter(CategoryFilter.ALL));
        b.chipNewsImportant.setOnClickListener(v -> selectFilter(CategoryFilter.IMPORTANT));
        b.chipNewsDangerous.setOnClickListener(v -> selectFilter(CategoryFilter.DANGEROUS));
        b.chipNewsInteresting.setOnClickListener(v -> selectFilter(CategoryFilter.INTERESTING));

        b.swipeRefreshNews.setOnRefreshListener(this::subscribe);

        applyChipSelection();
        subscribe();
    }

    private void selectFilter(CategoryFilter filter) {
        if (currentFilter == filter) return;
        currentFilter = filter;
        applyChipSelection();
        subscribe();
    }

    private void applyChipSelection() {
        if (b == null) return;
        b.chipNewsAll.setSelected(currentFilter == CategoryFilter.ALL);
        b.chipNewsImportant.setSelected(currentFilter == CategoryFilter.IMPORTANT);
        b.chipNewsDangerous.setSelected(currentFilter == CategoryFilter.DANGEROUS);
        b.chipNewsInteresting.setSelected(currentFilter == CategoryFilter.INTERESTING);
    }

    private void subscribe() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        registration = repo.listen(currentFilter, (list, error) -> {
            if (b == null) return;
            b.swipeRefreshNews.setRefreshing(false);
            if (error != null) {
                Toast.makeText(requireContext(), R.string.news_load_error, Toast.LENGTH_SHORT).show();
            }
            List<NewsItem> safe = list != null ? list : new ArrayList<>();
            adapter.submit(safe);
            updateEmptyState(safe.isEmpty());
        });
    }

    private void updateEmptyState(boolean empty) {
        if (b == null) return;
        b.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.rvNews.setVisibility(empty ? View.GONE : View.VISIBLE);
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
