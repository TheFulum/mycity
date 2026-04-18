package com.app.mycity.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.mycity.data.model.Issue;
import com.app.mycity.data.model.UserProfile;
import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.FragmentAdminStatsBinding;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Date;
import java.util.List;

public class AdminStatsFragment extends Fragment {

    private FragmentAdminStatsBinding b;
    private final IssueRepository issueRepo = new IssueRepository();
    private final UserRepository userRepo = new UserRepository();
    private ListenerRegistration issueListener;
    private ListenerRegistration userListener;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentAdminStatsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        setLabel(b.statTotal.getRoot(), "Всего");
        setLabel(b.statActive.getRoot(), "Активные");
        setLabel(b.statResolved.getRoot(), "Выполнены");
        setLabel(b.statUsers.getRoot(), "Всего");
        setLabel(b.statAdmins.getRoot(), "Админы");
        setLabel(b.statWeekNew.getRoot(), "Новых заявок");
        setLabel(b.statWeekResolved.getRoot(), "Закрыто");

        issueListener = issueRepo.listen(IssueRepository.SortField.DATE, false,
                IssueRepository.StatusFilter.ALL, (list, err) -> {
                    if (b == null) return;
                    bindIssueStats(list);
                });
        userListener = userRepo.listenAll((list, err) -> {
            if (b == null) return;
            bindUserStats(list);
        });
    }

    private void bindIssueStats(List<Issue> list) {
        int total = list.size();
        int active = 0, resolved = 0, weekNew = 0, weekResolved = 0;
        long weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        for (Issue i : list) {
            boolean isResolved = i.isResolved();
            if (isResolved) resolved++; else active++;
            Date created = i.getCreatedAt();
            if (created != null && created.getTime() >= weekAgo) weekNew++;
            Date res = i.getResolvedAt();
            if (res != null && res.getTime() >= weekAgo) weekResolved++;
        }
        setValue(b.statTotal.getRoot(), total);
        setValue(b.statActive.getRoot(), active);
        setValue(b.statResolved.getRoot(), resolved);
        setValue(b.statWeekNew.getRoot(), weekNew);
        setValue(b.statWeekResolved.getRoot(), weekResolved);
    }

    private void bindUserStats(List<UserProfile> list) {
        int admins = 0;
        for (UserProfile u : list) if (u.isAdmin()) admins++;
        setValue(b.statUsers.getRoot(), list.size());
        setValue(b.statAdmins.getRoot(), admins);
    }

    private void setValue(View cell, int value) {
        android.widget.TextView tv = cell.findViewById(com.app.mycity.R.id.tv_value);
        if (tv != null) tv.setText(String.valueOf(value));
    }

    private void setLabel(View cell, String label) {
        android.widget.TextView tv = cell.findViewById(com.app.mycity.R.id.tv_label);
        if (tv != null) tv.setText(label);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (issueListener != null) { issueListener.remove(); issueListener = null; }
        if (userListener != null) { userListener.remove(); userListener = null; }
        b = null;
    }
}
