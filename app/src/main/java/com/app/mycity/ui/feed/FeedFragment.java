package com.app.mycity.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.databinding.FragmentFeedBinding;
import com.app.mycity.ui.main.MainActivity;
import com.google.firebase.firestore.ListenerRegistration;

public class FeedFragment extends Fragment {

    private FragmentFeedBinding b;
    private IssueCardAdapter adapter;
    private ListenerRegistration listener;
    private final IssueRepository repo = new IssueRepository();

    private IssueRepository.SortField sortField = IssueRepository.SortField.DATE;
    private boolean sortAsc = false;
    private IssueRepository.StatusFilter statusFilter = IssueRepository.StatusFilter.ALL;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentFeedBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new IssueCardAdapter(issue -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openIssueDetail(issue.getId());
            }
        });
        adapter.setOnAuthorClick(uid -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openUserProfile(uid);
            }
        });
        b.rvIssues.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvIssues.setItemAnimator(null);
        b.rvIssues.setAdapter(adapter);

        b.swipeRefresh.setOnRefreshListener(this::subscribe);

        b.chipDate.setOnClickListener(v -> { toggleSort(IssueRepository.SortField.DATE); });
        b.chipComments.setOnClickListener(v -> { toggleSort(IssueRepository.SortField.COMMENTS); });
        b.chipStatus.setOnClickListener(v -> {
            statusFilter = next(statusFilter);
            applyChipStatus();
            subscribe();
        });

        applyChipSort();
        applyChipStatus();
        subscribe();
    }

    protected IssueRepository.StatusFilter initialStatus() { return IssueRepository.StatusFilter.ALL; }

    private void toggleSort(IssueRepository.SortField field) {
        if (sortField == field) sortAsc = !sortAsc;
        else { sortField = field; sortAsc = false; }
        applyChipSort();
        subscribe();
    }

    private void applyChipSort() {
        b.chipDate.setSelected(sortField == IssueRepository.SortField.DATE);
        b.chipComments.setSelected(sortField == IssueRepository.SortField.COMMENTS);
        b.chipDate.setText(sortField == IssueRepository.SortField.DATE
                ? "Дата " + (sortAsc ? "↑" : "↓") : "Дата");
        b.chipComments.setText(sortField == IssueRepository.SortField.COMMENTS
                ? "Комментарии " + (sortAsc ? "↑" : "↓") : "Комментарии");
    }

    private void applyChipStatus() {
        b.chipStatus.setSelected(statusFilter != IssueRepository.StatusFilter.ALL);
        switch (statusFilter) {
            case ACTIVE:   b.chipStatus.setText("Активные");   break;
            case RESOLVED: b.chipStatus.setText("Выполненные"); break;
            default:       b.chipStatus.setText("Все");
        }
    }

    private IssueRepository.StatusFilter next(IssueRepository.StatusFilter f) {
        switch (f) {
            case ALL: return IssueRepository.StatusFilter.ACTIVE;
            case ACTIVE: return IssueRepository.StatusFilter.RESOLVED;
            default: return IssueRepository.StatusFilter.ALL;
        }
    }

    private void subscribe() {
        if (listener != null) listener.remove();
        listener = repo.listen(sortField, sortAsc, statusFilter, (list, err) -> {
            if (b == null) return;
            b.swipeRefresh.setRefreshing(false);
            adapter.submit(list);
            b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) { listener.remove(); listener = null; }
        b = null;
    }
}
