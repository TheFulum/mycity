package com.app.mycity.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.data.repository.NotificationRepository;
import com.app.mycity.databinding.FragmentNotificationsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding b;
    private final NotificationRepository repo = new NotificationRepository();
    private ListenerRegistration listener;
    private NotificationAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentNotificationsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();

        adapter = new NotificationAdapter();
        adapter.setOnClickListener(n -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openIssueDetail(n.getIssueId());
            }
        });
        adapter.setOnDeleteListener(n -> {
            if (n.getId() == null) return;
            new MaterialAlertDialogBuilder(requireContext(), com.app.mycity.R.style.DialogTheme)
                    .setTitle("Удалить уведомление?")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (d, w) -> repo.delete(n.getId()))
                    .show();
        });
        b.rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvNotifications.setItemAnimator(null);
        b.rvNotifications.setAdapter(adapter);

        b.btnMarkAllRead.setOnClickListener(v -> repo.markAllRead(uid));
        b.btnDeleteAll.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext(), com.app.mycity.R.style.DialogTheme)
                        .setTitle("Очистить все уведомления?")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Очистить", (d, w) -> repo.deleteAll(uid))
                        .show());

        listener = repo.listenAll(uid, (list, err) -> {
            if (b == null) return;
            adapter.submit(list);
            b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            b.rvNotifications.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        });

        repo.markAllRead(uid);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) { listener.remove(); listener = null; }
        b = null;
    }
}
