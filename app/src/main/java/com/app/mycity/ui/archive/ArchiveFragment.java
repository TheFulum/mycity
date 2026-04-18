package com.app.mycity.ui.archive;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.databinding.FragmentArchiveBinding;
import com.app.mycity.ui.feed.IssueCardAdapter;
import com.app.mycity.ui.main.MainActivity;
import com.google.firebase.firestore.ListenerRegistration;

public class ArchiveFragment extends Fragment {

    private FragmentArchiveBinding b;
    private IssueCardAdapter adapter;
    private ListenerRegistration listener;
    private final IssueRepository repo = new IssueRepository();

    private IssueRepository.SortField sortField = IssueRepository.SortField.DATE;
    private boolean sortAsc = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentArchiveBinding.inflate(inflater, container, false);
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
        b.chipDate.setOnClickListener(v -> toggle(IssueRepository.SortField.DATE));
        b.chipComments.setOnClickListener(v -> toggle(IssueRepository.SortField.COMMENTS));

        applyChips();
        subscribe();
    }

    private void toggle(IssueRepository.SortField field) {
        if (sortField == field) sortAsc = !sortAsc;
        else { sortField = field; sortAsc = false; }
        applyChips();
        subscribe();
    }

    private void applyChips() {
        b.chipDate.setSelected(sortField == IssueRepository.SortField.DATE);
        b.chipComments.setSelected(sortField == IssueRepository.SortField.COMMENTS);
        b.chipDate.setText(sortField == IssueRepository.SortField.DATE
                ? "Дата " + (sortAsc ? "↑" : "↓") : "Дата");
        b.chipComments.setText(sortField == IssueRepository.SortField.COMMENTS
                ? "Комментарии " + (sortAsc ? "↑" : "↓") : "Комментарии");
    }

    private void subscribe() {
        if (listener != null) listener.remove();
        listener = repo.listen(sortField, sortAsc,
                IssueRepository.StatusFilter.RESOLVED, (list, err) -> {
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
