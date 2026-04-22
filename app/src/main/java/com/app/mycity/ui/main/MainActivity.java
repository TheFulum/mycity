package com.app.mycity.ui.main;

import android.animation.AnimatorSet;
import android.content.Intent;
import android.animation.ObjectAnimator;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.app.mycity.R;
import com.app.mycity.data.model.UserProfile;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.ActivityMainBinding;
import com.app.mycity.data.repository.NotificationRepository;
import com.app.mycity.ui.admin.AdminModerationFragment;
import com.app.mycity.ui.admin.AdminStatsFragment;
import com.app.mycity.ui.admin.AdminUsersFragment;
import com.app.mycity.ui.create.CreateIssueFragment;
import com.app.mycity.ui.feed.FeedFragment;
import com.app.mycity.ui.feed.EditIssueFragment;
import com.app.mycity.ui.feed.IssueDetailFragment;
import com.app.mycity.ui.map.MapFragment;
import com.app.mycity.ui.profile.ProfileFragment;
import com.app.mycity.ui.profile.UserProfileViewFragment;
import com.app.mycity.util.SessionManager;
import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_GUEST = "is_guest";
    private static final String HOST_TAG = "host_fragment";

    private ActivityMainBinding binding;
    private boolean fabExpanded = false;
    private SessionManager session;
    private boolean isAdmin;
    private ListenerRegistration roleListener;
    private ListenerRegistration bellListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        session = new SessionManager(this);
        if (getIntent().getBooleanExtra(EXTRA_GUEST, false)) {
            session.setGuest(true);
        } else if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            session.setGuest(false);
        }

        setupPager();
        setupToolbar();
        setupFabMenu();
        watchRole();
        watchBell();

        getSupportFragmentManager().addOnBackStackChangedListener(this::syncHostVisibility);
    }

    private void watchRole() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { isAdmin = false; return; }
        roleListener = new UserRepository().listen(u.getUid(), (profile, err) -> {
            boolean prev = isAdmin;
            isAdmin = profile != null && profile.isAdmin();
            if (profile != null && profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                Glide.with(this).load(profile.getAvatarUrl())
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .into(binding.ivAvatar);
            }
            if (isAdmin != prev && fabExpanded) {
                collapseFab();
                expandFab();
            }
        });
    }

    private void setupPager() {
        binding.viewPager.setAdapter(new FragmentStateAdapter(this) {
            @Override @NonNull
            public Fragment createFragment(int position) {
                return position == 0 ? new FeedFragment() : new MapFragment();
            }
            @Override public int getItemCount() { return 2; }
        });
        binding.viewPager.setOffscreenPageLimit(1);
        binding.viewPager.registerOnPageChangeCallback(
                new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                    @Override public void onPageSelected(int position) {
                        binding.viewPager.setUserInputEnabled(position == 0);
                    }
                });
    }

    private void setupToolbar() {
        binding.ivAvatar.setOnClickListener(v -> openHostFragment(new ProfileFragment(), "profile"));
        binding.tvTitle.setClickable(true);
        binding.tvTitle.setFocusable(true);
        binding.tvTitle.setOnClickListener(v -> {
            popHostToRoot();
            binding.viewPager.setCurrentItem(0, true);
        });

        binding.btnGuestExit.setVisibility(View.GONE);

        if (session.isGuest()) {
            binding.btnBell.setImageResource(R.drawable.ic_home);
            binding.btnBell.setColorFilter(ContextCompat.getColor(this, R.color.accent_blue));
            binding.btnBell.setContentDescription(getString(R.string.app_title));
            binding.tvBellBadge.setVisibility(View.GONE);
            binding.btnBell.setOnClickListener(v -> {
                session.clear();
                Intent i = new Intent(this, com.app.mycity.ui.auth.SplashActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
            });
        } else {
            binding.btnBell.setOnClickListener(v -> openHostFragment(new NotificationsFragment(), "notifications"));
        }
    }

    private void watchBell() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null || session.isGuest()) return;
        bellListener = new NotificationRepository().listenUnreadCount(u.getUid(), count -> {
            if (count > 0) {
                binding.tvBellBadge.setVisibility(View.VISIBLE);
                binding.tvBellBadge.setText(count > 9 ? "9+" : String.valueOf(count));
            } else {
                binding.tvBellBadge.setVisibility(View.GONE);
            }
        });
    }

    private void setupFabMenu() {
        binding.fabTrigger.setOnClickListener(v -> {
            if (fabExpanded) collapseFab(); else expandFab();
        });
        binding.fabHome.setOnClickListener(v -> {
            collapseFab();
            popHostToRoot();
            binding.viewPager.setCurrentItem(0, true);
        });
        binding.fabAdd.setOnClickListener(v -> {
            collapseFab();
            openHostFragment(new CreateIssueFragment(), "create");
        });
        binding.fabAdminModerate.setOnClickListener(v -> {
            collapseFab();
            openHostFragment(new AdminModerationFragment(), "admin_moderate");
        });
        binding.fabAdminUsers.setOnClickListener(v -> {
            collapseFab();
            openHostFragment(new AdminUsersFragment(), "admin_users");
        });
        binding.fabAdminStats.setOnClickListener(v -> {
            collapseFab();
            openHostFragment(new AdminStatsFragment(), "admin_stats");
        });

        binding.getRoot().setOnTouchListener((v, ev) -> {
            if (fabExpanded && ev.getAction() == MotionEvent.ACTION_DOWN) {
                collapseFab();
            }
            return false;
        });
    }

    private void expandFab() {
        fabExpanded = true;
        refreshActiveFab();
        animateMiniFab(binding.fabHome, true, 0);
        animateMiniFab(binding.fabAdd, true, 50);
        if (isAdmin) {
            animateMiniFab(binding.fabAdminModerate, true, 150);
            animateMiniFab(binding.fabAdminUsers, true, 200);
            animateMiniFab(binding.fabAdminStats, true, 250);
        }
        binding.fabTrigger.animate().rotation(180).setDuration(250).start();
    }

    private void refreshActiveFab() {
        Fragment host = getSupportFragmentManager().findFragmentById(R.id.fragment_host);
        FloatingActionButton active;
        if (host instanceof CreateIssueFragment) {
            active = binding.fabAdd;
        } else if (host instanceof AdminModerationFragment) {
            active = binding.fabAdminModerate;
        } else if (host instanceof AdminUsersFragment) {
            active = binding.fabAdminUsers;
        } else if (host instanceof AdminStatsFragment) {
            active = binding.fabAdminStats;
        } else if (host == null) {
            active = binding.fabHome;
        } else {
            active = null;
        }
        applyFabTint(binding.fabHome, active == binding.fabHome, R.color.accent_blue);
        applyFabTint(binding.fabAdd, active == binding.fabAdd, R.color.accent_blue);
        applyFabTint(binding.fabAdminModerate, active == binding.fabAdminModerate, R.color.accent_red);
        applyFabTint(binding.fabAdminUsers, active == binding.fabAdminUsers, R.color.accent_red);
        applyFabTint(binding.fabAdminStats, active == binding.fabAdminStats, R.color.accent_red);
    }

    private void applyFabTint(FloatingActionButton fab, boolean active, @ColorRes int activeColor) {
        @ColorRes int colorRes = active ? activeColor : R.color.bg_secondary;
        fab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, colorRes)));
    }

    private void collapseFab() {
        if (!fabExpanded) return;
        fabExpanded = false;
        if (isAdmin) {
            animateMiniFab(binding.fabAdminStats, false, 0);
            animateMiniFab(binding.fabAdminUsers, false, 40);
            animateMiniFab(binding.fabAdminModerate, false, 80);
        }
        animateMiniFab(binding.fabAdd, false, 0);
        animateMiniFab(binding.fabHome, false, 50);
        binding.fabTrigger.animate().rotation(0).setDuration(250).start();
    }

    private void animateMiniFab(View view, boolean show, long delay) {
        view.setVisibility(View.VISIBLE);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(view, "alpha", show ? 0f : 1f, show ? 1f : 0f),
                ObjectAnimator.ofFloat(view, "scaleX", show ? 0.4f : 1f, show ? 1f : 0.4f),
                ObjectAnimator.ofFloat(view, "scaleY", show ? 0.4f : 1f, show ? 1f : 0.4f),
                ObjectAnimator.ofFloat(view, "translationY", show ? 30f : 0f, show ? 0f : 30f)
        );
        set.setDuration(250);
        set.setStartDelay(delay);
        set.setInterpolator(show ? new OvershootInterpolator(1.2f) : new DecelerateInterpolator());
        set.start();
        if (!show) view.postDelayed(() -> view.setVisibility(View.INVISIBLE), 250 + delay);
    }

    public void openHostFragment(Fragment fragment, String tag) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_host, fragment, HOST_TAG)
                .addToBackStack(tag)
                .commit();
        binding.fragmentHost.setVisibility(View.VISIBLE);
        binding.viewPager.setVisibility(View.GONE);
    }

    public void openIssueDetail(String issueId) {
        openHostFragment(IssueDetailFragment.newInstance(issueId), "detail_" + issueId);
    }

    public void openUserProfile(String uid) {
        openHostFragment(UserProfileViewFragment.newInstance(uid), "user_" + uid);
    }

    public void openEditIssue(String issueId) {
        openHostFragment(EditIssueFragment.newInstance(issueId), "edit_" + issueId);
    }

    public void openMapFullscreen(double lat, double lng, String title) {
        openHostFragment(com.app.mycity.ui.map.MapFullscreenFragment.newInstance(lat, lng, title), "map_full");
    }

    public void openMapPicker(double lat, double lng) {
        openHostFragment(com.app.mycity.ui.map.MapFullscreenFragment.newInstanceEditable(lat, lng, ""), "map_pick");
    }

    public void popHost() {
        getSupportFragmentManager().popBackStack();
    }

    public void popHostToRoot() {
        getSupportFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    private void syncHostVisibility() {
        boolean hasHost = getSupportFragmentManager().getBackStackEntryCount() > 0;
        binding.fragmentHost.setVisibility(hasHost ? View.VISIBLE : View.GONE);
        binding.viewPager.setVisibility(hasHost ? View.GONE : View.VISIBLE);
        refreshActiveFab();
    }

    @Override
    protected void onDestroy() {
        if (roleListener != null) { roleListener.remove(); roleListener = null; }
        if (bellListener != null) { bellListener.remove(); bellListener = null; }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (fabExpanded) { collapseFab(); return; }
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            super.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }
}
