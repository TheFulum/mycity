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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.app.mycity.R;
import com.app.mycity.data.model.ChatThread;
import com.app.mycity.data.repository.ChatRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.ActivityMainBinding;
import com.app.mycity.data.repository.NotificationRepository;
import com.app.mycity.ui.admin.AdminModerationFragment;
import com.app.mycity.ui.admin.AdminStatsFragment;
import com.app.mycity.ui.admin.AdminTransportFragment;
import com.app.mycity.ui.admin.AdminUsersFragment;
import com.app.mycity.ui.chat.ChatDetailFragment;
import com.app.mycity.ui.chat.ChatListFragment;
import com.app.mycity.ui.chat.CreateChatFragment;
import com.app.mycity.ui.citypost.CityPostDetailFragment;
import com.app.mycity.ui.citypost.CitySectionFragment;
import com.app.mycity.ui.citypost.CreateCityPostFragment;
import com.app.mycity.ui.create.CreateIssueFragment;
import com.app.mycity.ui.feed.EditIssueFragment;
import com.app.mycity.ui.feed.IssueDetailFragment;
import com.app.mycity.ui.news.NewsDetailFragment;
import com.app.mycity.ui.news.NewsSectionFragment;
import com.app.mycity.ui.profile.ProfileFragment;
import com.app.mycity.ui.profile.UserProfileViewFragment;
import com.app.mycity.ui.transport.TransportRouteDetailFragment;
import com.app.mycity.ui.transport.TransportSectionFragment;
import com.app.mycity.util.SessionManager;
import com.app.mycity.util.ThemeManager;
import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_GUEST = "is_guest";
    private static final String HOST_TAG = "host_fragment";
    private static final String SECTION_TAG_PREFIX = "section_";

    private ActivityMainBinding binding;
    private boolean fabExpanded = false;
    private SessionManager session;
    private boolean isAdmin;
    private ListenerRegistration roleListener;
    private ListenerRegistration bellListener;
    private ListenerRegistration messagesUnreadListener;
    private int currentNavId = -1;

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

        setupBottomNav();
        setupToolbar();
        setupFabMenu();
        watchRole();
        watchBell();
        watchMessages();

        getSupportFragmentManager().addOnBackStackChangedListener(this::syncHostVisibility);

        showSection(R.id.nav_problems);
        binding.bottomNav.setSelectedItemId(R.id.nav_problems);
    }

    private void watchRole() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { isAdmin = false; return; }
        roleListener = new UserRepository().listen(u.getUid(), (profile, err) -> {
            if (profile != null && profile.isPermanentlyBlocked()) {
                String reason = profile.getBlockedReason() != null ? profile.getBlockedReason() : "без причины";
                FirebaseAuth.getInstance().signOut();
                session.clear();
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Аккаунт заблокирован")
                        .setMessage("Ваш аккаунт заблокирован по причине: " + reason)
                        .setCancelable(false)
                        .setPositiveButton("Понятно", (d, w) -> {
                            Intent i = new Intent(this, com.app.mycity.ui.auth.SplashActivity.class);
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                            finish();
                        })
                        .show();
                return;
            }
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

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            showSection(item.getItemId());
            return true;
        });
        binding.bottomNav.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.nav_problems) {
                Fragment fragment = getSupportFragmentManager().findFragmentByTag(SECTION_TAG_PREFIX + item.getItemId());
                if (fragment instanceof ProblemsSectionFragment) {
                    ((ProblemsSectionFragment) fragment).resetToFeed();
                }
            }
        });
    }

    private void showSection(int navItemId) {
        if (navItemId == currentNavId) return;
        FragmentManager fm = getSupportFragmentManager();
        androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction();

        if (currentNavId != 0) {
            Fragment previous = fm.findFragmentByTag(SECTION_TAG_PREFIX + currentNavId);
            if (previous != null) tx.hide(previous);
        }

        String tag = SECTION_TAG_PREFIX + navItemId;
        Fragment next = fm.findFragmentByTag(tag);
        if (next == null) {
            next = createSectionFragment(navItemId);
            if (next == null) return;
            tx.add(R.id.nav_host, next, tag);
        } else {
            tx.show(next);
        }
        tx.commit();
        currentNavId = navItemId;
    }

    private Fragment createSectionFragment(int navItemId) {
        if (navItemId == R.id.nav_problems) return new ProblemsSectionFragment();
        if (navItemId == R.id.nav_city) return new CitySectionFragment();
        if (navItemId == R.id.nav_news) return new NewsSectionFragment();
        if (navItemId == R.id.nav_transport) return new TransportSectionFragment();
        return null;
    }

    private void setupToolbar() {
        binding.ivAvatar.setOnClickListener(v -> openHostFragment(new ProfileFragment(), "profile"));
        binding.btnTheme.setOnClickListener(v -> showThemePicker());
        binding.tvTitle.setClickable(true);
        binding.tvTitle.setFocusable(true);
        binding.tvTitle.setOnClickListener(v -> {
            popHostToRoot();
            if (currentNavId != R.id.nav_problems) {
                binding.bottomNav.setSelectedItemId(R.id.nav_problems);
            }
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(SECTION_TAG_PREFIX + R.id.nav_problems);
            if (fragment instanceof ProblemsSectionFragment) {
                ((ProblemsSectionFragment) fragment).resetToFeed();
            }
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
            binding.btnMessagesContainer.setVisibility(View.GONE);
            binding.tvMessagesBadge.setVisibility(View.GONE);
        } else {
            binding.btnBell.setOnClickListener(v -> openHostFragment(new NotificationsFragment(), "notifications"));
            binding.btnMessagesContainer.setVisibility(View.VISIBLE);
            binding.btnMessages.setOnClickListener(v -> openHostFragment(new ChatListFragment(), "chat_list"));
        }
    }

    private void showThemePicker() {
        String[] labels = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };
        int[] modes = {ThemeManager.MODE_SYSTEM, ThemeManager.MODE_LIGHT, ThemeManager.MODE_DARK};
        int currentMode = ThemeManager.getSavedMode(this);
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == currentMode) {
                checked = i;
                break;
            }
        }

        final int[] selected = {checked};
        new MaterialAlertDialogBuilder(this, R.style.DialogTheme)
                .setTitle(R.string.theme_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> selected[0] = which)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Применить", (dialog, which) ->
                        ThemeManager.saveAndApply(this, modes[selected[0]]))
                .show();
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

    private void watchMessages() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null || session.isGuest()) return;
        messagesUnreadListener = new ChatRepository().listenUserChats(u.getUid(), (chats, err) -> {
            if (chats == null) return;
            int total = 0;
            for (ChatThread t : chats) {
                Integer c = t.getUnreadCounts() != null ? t.getUnreadCounts().get(u.getUid()) : null;
                if (c != null) total += c;
            }
            if (total > 0) {
                binding.tvMessagesBadge.setVisibility(View.VISIBLE);
                binding.tvMessagesBadge.setText(total > 9 ? "9+" : String.valueOf(total));
            } else {
                binding.tvMessagesBadge.setVisibility(View.GONE);
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
            if (currentNavId != R.id.nav_problems) {
                binding.bottomNav.setSelectedItemId(R.id.nav_problems);
            }
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(SECTION_TAG_PREFIX + R.id.nav_problems);
            if (fragment instanceof ProblemsSectionFragment) {
                ((ProblemsSectionFragment) fragment).resetToFeed();
            }
        });
        binding.fabAdd.setOnClickListener(v -> {
            collapseFab();
            if (currentNavId == R.id.nav_city) {
                openHostFragment(new CreateCityPostFragment(), "create_city");
            } else {
                openHostFragment(new CreateIssueFragment(), "create");
            }
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
        binding.fabAdminTransport.setOnClickListener(v -> {
            collapseFab();
            openHostFragment(new AdminTransportFragment(), "admin_transport");
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
            animateMiniFab(binding.fabAdminTransport, true, 300);
        }
        binding.fabTrigger.animate().rotation(180).setDuration(250).start();
    }

    private void refreshActiveFab() {
        Fragment host = getSupportFragmentManager().findFragmentById(R.id.fragment_host);
        FloatingActionButton active;
        if (host instanceof CreateIssueFragment) {
            active = binding.fabAdd;
        } else if (host instanceof CreateCityPostFragment) {
            active = binding.fabAdd;
        } else if (host instanceof AdminModerationFragment) {
            active = binding.fabAdminModerate;
        } else if (host instanceof AdminUsersFragment) {
            active = binding.fabAdminUsers;
        } else if (host instanceof AdminStatsFragment) {
            active = binding.fabAdminStats;
        } else if (host instanceof AdminTransportFragment) {
            active = binding.fabAdminTransport;
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
        applyFabTint(binding.fabAdminTransport, active == binding.fabAdminTransport, R.color.accent_red);
    }

    private void applyFabTint(FloatingActionButton fab, boolean active, @ColorRes int activeColor) {
        @ColorRes int colorRes = active ? activeColor : R.color.bg_secondary;
        fab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, colorRes)));

        @ColorRes int iconRes = active ? R.color.on_accent : R.color.text_primary;
        fab.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, iconRes)));
    }

    private void collapseFab() {
        if (!fabExpanded) return;
        fabExpanded = false;
        if (isAdmin) {
            animateMiniFab(binding.fabAdminTransport, false, 0);
            animateMiniFab(binding.fabAdminStats, false, 40);
            animateMiniFab(binding.fabAdminUsers, false, 80);
            animateMiniFab(binding.fabAdminModerate, false, 120);
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
        binding.navHost.setVisibility(View.GONE);
        binding.bottomNav.setVisibility(View.GONE);
        binding.fabTrigger.setVisibility(View.GONE);
    }

    public void openIssueDetail(String issueId) {
        openHostFragment(IssueDetailFragment.newInstance(issueId), "detail_" + issueId);
    }

    public void openCityPostDetail(String postId) {
        openHostFragment(CityPostDetailFragment.newInstance(postId), "city_detail_" + postId);
    }

    public void openNewsDetail(String newsId) {
        openHostFragment(NewsDetailFragment.newInstance(newsId), "news_detail_" + newsId);
    }

    public void openTransportRouteDetail(String routeId) {
        openHostFragment(TransportRouteDetailFragment.newInstance(routeId), "transport_route_" + routeId);
    }

    public void openCreateCityPost() {
        openHostFragment(new CreateCityPostFragment(), "create_city");
    }

    public void openCreateChat() {
        openHostFragment(new CreateChatFragment(), "create_chat");
    }

    public void openChatList() {
        openHostFragment(new ChatListFragment(), "chat_list");
    }

    public void openChatDetail(String chatId) {
        openHostFragment(ChatDetailFragment.newInstance(chatId), "chat_" + chatId);
    }

    public void openAdminTransport() {
        openHostFragment(new AdminTransportFragment(), "admin_transport");
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
        binding.navHost.setVisibility(hasHost ? View.GONE : View.VISIBLE);
        binding.bottomNav.setVisibility(hasHost ? View.GONE : View.VISIBLE);
        binding.fabTrigger.setVisibility(hasHost ? View.GONE : View.VISIBLE);
        if (hasHost && fabExpanded) collapseFab();
        refreshActiveFab();
    }

    @Override
    protected void onDestroy() {
        if (roleListener != null) { roleListener.remove(); roleListener = null; }
        if (bellListener != null) { bellListener.remove(); bellListener = null; }
        if (messagesUnreadListener != null) { messagesUnreadListener.remove(); messagesUnreadListener = null; }
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
