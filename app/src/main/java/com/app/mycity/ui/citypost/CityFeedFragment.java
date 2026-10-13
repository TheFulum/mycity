package com.app.mycity.ui.citypost;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.R;
import com.app.mycity.data.model.CityPost;
import com.app.mycity.data.repository.CityPostRepository;
import com.app.mycity.data.repository.CityPostRepository.CategoryFilter;
import com.app.mycity.data.repository.CityPostRepository.SortField;
import com.app.mycity.databinding.FragmentCityFeedBinding;
import com.app.mycity.ui.main.MainActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class CityFeedFragment extends Fragment {

    private FragmentCityFeedBinding b;
    private final CityPostRepository repo = new CityPostRepository();
    private CityPostCardAdapter adapter;
    private ListenerRegistration registration;

    private SortField sortField = SortField.DATE;
    private boolean sortAsc = false;
    private CategoryFilter categoryFilter = CategoryFilter.ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentCityFeedBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new CityPostCardAdapter(post -> {
            if (post == null || post.getId() == null) return;
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openCityPostDetail(post.getId());
            }
        });
        adapter.setOnAuthorClick(uid -> {
            if (uid == null || uid.isEmpty()) return;
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openUserProfile(uid);
            }
        });

        b.rvPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvPosts.setItemAnimator(null);
        b.rvPosts.setAdapter(adapter);

        b.chipAll.setOnClickListener(v -> selectCategory(CategoryFilter.ALL));
        b.chipNature.setOnClickListener(v -> selectCategory(CategoryFilter.NATURE));
        b.chipPlace.setOnClickListener(v -> selectCategory(CategoryFilter.PLACE));
        b.chipVenue.setOnClickListener(v -> selectCategory(CategoryFilter.VENUE));
        b.chipHistory.setOnClickListener(v -> selectCategory(CategoryFilter.HISTORY));

        b.btnSort.setOnClickListener(v -> showSortDialog());
        b.btnSortReverse.setOnClickListener(v -> {
            sortAsc = !sortAsc;
            applySortLabel();
            subscribe();
        });

        b.swipeRefresh.setOnRefreshListener(this::subscribe);

        applyChipSelection();
        applySortLabel();
        subscribe();
    }

    private void selectCategory(CategoryFilter filter) {
        if (categoryFilter == filter) return;
        categoryFilter = filter;
        applyChipSelection();
        subscribe();
    }

    private void applyChipSelection() {
        if (b == null) return;
        b.chipAll.setSelected(categoryFilter == CategoryFilter.ALL);
        b.chipNature.setSelected(categoryFilter == CategoryFilter.NATURE);
        b.chipPlace.setSelected(categoryFilter == CategoryFilter.PLACE);
        b.chipVenue.setSelected(categoryFilter == CategoryFilter.VENUE);
        b.chipHistory.setSelected(categoryFilter == CategoryFilter.HISTORY);
    }

    private void showSortDialog() {
        String[] options = new String[]{
                getString(R.string.city_sort_date),
                getString(R.string.city_sort_likes),
                getString(R.string.city_sort_comments)
        };
        int checked = sortField == SortField.DATE ? 0 : sortField == SortField.LIKES ? 1 : 2;
        new MaterialAlertDialogBuilder(requireContext(), R.style.DialogTheme)
                .setTitle(R.string.city_sort_title)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    SortField next;
                    switch (which) {
                        case 1: next = SortField.LIKES; break;
                        case 2: next = SortField.COMMENTS; break;
                        case 0:
                        default: next = SortField.DATE; break;
                    }
                    if (next != sortField) {
                        sortField = next;
                        applySortLabel();
                        subscribe();
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void applySortLabel() {
        if (b == null) return;
        int labelRes;
        switch (sortField) {
            case LIKES: labelRes = R.string.city_sort_likes; break;
            case COMMENTS: labelRes = R.string.city_sort_comments; break;
            case DATE:
            default: labelRes = R.string.city_sort_date; break;
        }
        b.btnSort.setText(getString(labelRes) + " " + (sortAsc ? "↑" : "↓"));
        b.btnSortReverse.setRotation(sortAsc ? 180f : 0f);
    }

    private void subscribe() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        registration = repo.listen(sortField, sortAsc, categoryFilter, (list, error) -> {
            if (b == null) return;
            b.swipeRefresh.setRefreshing(false);
            if (error != null) {
                Toast.makeText(requireContext(), R.string.city_load_error, Toast.LENGTH_SHORT).show();
            }
            List<CityPost> safe = list != null ? list : new ArrayList<>();
            adapter.submit(safe);
            updateEmptyState(safe.isEmpty());
        });
    }

    private void updateEmptyState(boolean empty) {
        if (b == null) return;
        TextView tv = b.tvEmpty;
        tv.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.rvPosts.setVisibility(empty ? View.GONE : View.VISIBLE);
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
