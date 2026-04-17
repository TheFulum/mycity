package com.app.mycity.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.R;
import com.app.mycity.data.model.UserProfile;
import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.FragmentProfileBinding;
import com.app.mycity.ui.auth.SplashActivity;
import com.app.mycity.ui.feed.IssueCardAdapter;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.SessionManager;
import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

public class ProfileFragment extends Fragment implements EditProfileBottomSheet.Listener {

    private FragmentProfileBinding b;
    private final UserRepository userRepo = new UserRepository();
    private final IssueRepository issueRepo = new IssueRepository();
    private ListenerRegistration userListener;
    private ListenerRegistration myListener;
    private IssueCardAdapter adapter;

    private UserProfile currentProfile;
    private boolean showResolved = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentProfileBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        SessionManager session = new SessionManager(requireContext());

        if (user == null || session.isGuest()) {
            b.header.setVisibility(View.GONE);
            b.myIssuesBlock.setVisibility(View.GONE);
            b.guestBlock.setVisibility(View.VISIBLE);
            b.btnSignIn.setOnClickListener(v -> {
                session.clear();
                startActivity(new Intent(requireActivity(), SplashActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                requireActivity().finish();
            });
            return;
        }

        b.header.setVisibility(View.VISIBLE);
        b.myIssuesBlock.setVisibility(View.VISIBLE);
        b.guestBlock.setVisibility(View.GONE);

        adapter = new IssueCardAdapter(issue -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openIssueDetail(issue.getId());
            }
        });
        b.rvMy.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvMy.setAdapter(adapter);

        b.tabs.addTab(b.tabs.newTab().setText(R.string.profile_my_active));
        b.tabs.addTab(b.tabs.newTab().setText(R.string.profile_my_resolved));
        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                showResolved = tab.getPosition() == 1;
                subscribeMyIssues();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        b.btnEdit.setOnClickListener(v -> {
            EditProfileBottomSheet.newInstance(
                    currentProfile != null ? currentProfile.getDisplayName() : "",
                    currentProfile != null ? currentProfile.getAvatarUrl() : null)
                    .show(getChildFragmentManager(), "edit_profile");
        });

        b.btnSignOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            new SessionManager(requireContext()).clear();
            startActivity(new Intent(requireActivity(), SplashActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            requireActivity().finish();
        });

        userRepo.upsert(user.getUid(), null, user.getEmail(),
                user.getPhoneNumber());

        subscribeUser(user.getUid());
        subscribeMyIssues();
    }

    private void subscribeUser(String uid) {
        userListener = userRepo.listen(uid, (profile, err) -> {
            if (b == null) return;
            currentProfile = profile;
            String name = profile.getDisplayName();
            if (name == null || name.isEmpty()) {
                FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                name = u != null && u.getEmail() != null ? u.getEmail() : "Пользователь";
            }
            b.tvName.setText(name);
            b.tvEmail.setText(profile.getEmail() != null ? profile.getEmail() : "");
            b.tvPhone.setText(profile.getPhone() != null ? profile.getPhone() : "");
            if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                Glide.with(b.ivAvatar).load(profile.getAvatarUrl())
                        .placeholder(R.drawable.ic_avatar_placeholder).into(b.ivAvatar);
            }
            int count = profile.getIssueCount();
            if (count > 0) {
                b.tvBadge.setVisibility(View.VISIBLE);
                b.tvBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            } else {
                b.tvBadge.setVisibility(View.GONE);
            }
        });
    }

    private void subscribeMyIssues() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        if (myListener != null) myListener.remove();
        myListener = issueRepo.listenByAuthor(user.getUid(), showResolved, (list, err) -> {
            if (b == null || adapter == null) return;
            adapter.submit(list);
        });
    }

    @Override
    public void onProfileUpdated() {
        // Realtime listener подхватит изменения автоматически
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) { userListener.remove(); userListener = null; }
        if (myListener != null) { myListener.remove(); myListener = null; }
        b = null;
    }
}
