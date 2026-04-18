package com.app.mycity.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.data.model.Issue;
import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.databinding.FragmentAdminModerationBinding;
import com.app.mycity.ui.main.MainActivity;
import com.google.firebase.firestore.ListenerRegistration;

public class AdminModerationFragment extends Fragment {

    private FragmentAdminModerationBinding b;
    private final IssueRepository issueRepo = new IssueRepository();
    private ListenerRegistration listener;
    private AdminIssueAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentAdminModerationBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new AdminIssueAdapter(new AdminIssueAdapter.Actions() {
            @Override public void onOpen(Issue issue) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openIssueDetail(issue.getId());
                }
            }
            @Override public void onToggleStatus(Issue issue) { toggleStatus(issue); }
            @Override public void onDelete(Issue issue) { confirmDelete(issue); }
        });
        b.rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rv.setItemAnimator(null);
        b.rv.setAdapter(adapter);

        listener = issueRepo.listen(IssueRepository.SortField.DATE, false,
                IssueRepository.StatusFilter.ALL, (list, err) -> {
                    if (b == null) return;
                    adapter.submit(list);
                    b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void toggleStatus(Issue issue) {
        if (issue.isResolved()) {
            issueRepo.setStatus(issue.getId(), Issue.STATUS_ACTIVE)
                    .addOnFailureListener(e -> toast("Ошибка: " + e.getMessage()));
        } else {
            AdminResolveBottomSheet bs = AdminResolveBottomSheet.newInstance(issue.getId());
            bs.show(getChildFragmentManager(), "admin_resolve");
        }
    }

    private void confirmDelete(Issue issue) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Удалить заявку?")
                .setMessage(issue.getTitle())
                .setPositiveButton("Удалить", (d, w) ->
                        issueRepo.delete(issue.getId())
                                .addOnSuccessListener(v -> toast("Удалено"))
                                .addOnFailureListener(e -> toast("Ошибка: " + e.getMessage())))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) { listener.remove(); listener = null; }
        b = null;
    }
}
