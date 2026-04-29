package com.app.mycity.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.FragmentUserProfileViewBinding;
import com.app.mycity.ui.feed.IssueCardAdapter;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.DateUtils;
import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.ListenerRegistration;

import com.app.mycity.R;

public class UserProfileViewFragment extends Fragment {

    private static final String ARG_UID = "uid";

    public static UserProfileViewFragment newInstance(String uid) {
        UserProfileViewFragment f = new UserProfileViewFragment();
        Bundle args = new Bundle();
        args.putString(ARG_UID, uid);
        f.setArguments(args);
        return f;
    }

    private FragmentUserProfileViewBinding b;
    private final UserRepository userRepo = new UserRepository();
    private final IssueRepository issueRepo = new IssueRepository();

    private ListenerRegistration userListener;
    private ListenerRegistration issuesListener;
    private IssueCardAdapter adapter;
    private boolean showResolved = false;
    private String uid;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentUserProfileViewBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        uid = requireArguments().getString(ARG_UID);

        adapter = new IssueCardAdapter(issue -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openIssueDetail(issue.getId());
            }
        });
        adapter.setOnAuthorClick(authorId -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openUserProfile(authorId);
            }
        });
        b.rvIssues.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvIssues.setItemAnimator(null);
        b.rvIssues.setAdapter(adapter);

        b.tabs.addTab(b.tabs.newTab().setText(R.string.profile_my_active));
        b.tabs.addTab(b.tabs.newTab().setText(R.string.profile_my_resolved));
        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                showResolved = tab.getPosition() == 1;
                subscribeIssues();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        userListener = userRepo.listen(uid, (profile, err) -> {
            if (b == null) return;
            String name = profile.getDisplayName();
            b.tvName.setText(name != null && !name.isEmpty() ? name : "Пользователь");

            String email = profile.getEmail();
            if (email != null && !email.isEmpty()) {
                b.tvEmail.setText(email);
                b.tvEmail.setVisibility(View.VISIBLE);
            } else {
                b.tvEmail.setVisibility(View.GONE);
            }

            String phone = profile.getPhone();
            if (phone != null && !phone.isEmpty()) {
                b.tvPhone.setText(phone);
                b.tvPhone.setVisibility(View.VISIBLE);
            } else {
                b.tvPhone.setVisibility(View.GONE);
            }

            b.tvJoined.setText(profile.getCreatedAt() != null
                    ? "Зарегистрирован: " + DateUtils.format(profile.getCreatedAt()) : "");
            b.tvIssueCount.setText("Заявок подано: " + profile.getIssueCount());

            int count = profile.getIssueCount();
            if (count > 0) {
                b.tvBadge.setVisibility(View.VISIBLE);
                b.tvBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            } else {
                b.tvBadge.setVisibility(View.GONE);
            }

            b.ivCrown.setVisibility(profile.isAdmin() ? View.VISIBLE : View.GONE);

            if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                Glide.with(b.ivAvatar).load(profile.getAvatarUrl())
                        .placeholder(R.drawable.ic_avatar_placeholder).into(b.ivAvatar);
            }
        });

        subscribeIssues();
    }

    private void subscribeIssues() {
        if (issuesListener != null) { issuesListener.remove(); issuesListener = null; }
        issuesListener = issueRepo.listenByAuthor(uid, showResolved, (list, err) -> {
            if (b == null || adapter == null) return;
            adapter.submit(list);
            b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) { userListener.remove(); userListener = null; }
        if (issuesListener != null) { issuesListener.remove(); issuesListener = null; }
        b = null;
    }
}
