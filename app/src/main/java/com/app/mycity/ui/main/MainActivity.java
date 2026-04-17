package com.app.mycity.ui.main;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.app.mycity.R;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.ActivityMainBinding;
import com.app.mycity.ui.archive.ArchiveFragment;
import com.app.mycity.ui.create.CreateIssueFragment;
import com.app.mycity.ui.feed.FeedFragment;
import com.app.mycity.ui.feed.IssueDetailFragment;
import com.app.mycity.ui.map.MapFragment;
import com.app.mycity.ui.profile.ProfileFragment;
import com.app.mycity.util.SessionManager;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_GUEST = "is_guest";
    private static final String HOST_TAG = "host_fragment";

    private ActivityMainBinding binding;
    private boolean fabExpanded = false;
    private SessionManager session;

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
        loadAvatar();

        getSupportFragmentManager().addOnBackStackChangedListener(this::syncHostVisibility);
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
    }

    private void setupToolbar() {
        binding.ivAvatar.setOnClickListener(v -> openHostFragment(new ProfileFragment(), "profile"));
    }

    private void setupFabMenu() {
        binding.fabTrigger.setOnClickListener(v -> {
            if (fabExpanded) collapseFab(); else expandFab();
        });
        binding.fabHome.setOnClickListener(v -> {
            collapseFab();
            popHost();
            binding.viewPager.setCurrentItem(0, true);
        });
        binding.fabAdd.setOnClickListener(v -> {
            collapseFab();
            openHostFragment(new CreateIssueFragment(), "create");
        });
        binding.fabArchive.setOnClickListener(v -> {
            collapseFab();
            openHostFragment(new ArchiveFragment(), "archive");
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
        animateMiniFab(binding.fabHome, true, 0);
        animateMiniFab(binding.fabAdd, true, 50);
        animateMiniFab(binding.fabArchive, true, 100);
        binding.fabTrigger.animate().rotation(180).setDuration(250).start();
    }

    private void collapseFab() {
        if (!fabExpanded) return;
        fabExpanded = false;
        animateMiniFab(binding.fabArchive, false, 0);
        animateMiniFab(binding.fabAdd, false, 50);
        animateMiniFab(binding.fabHome, false, 100);
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

    private void loadAvatar() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        new UserRepository().get(u.getUid()).addOnSuccessListener(snap -> {
            if (snap == null || !snap.exists()) return;
            String url = snap.getString("avatarUrl");
            if (url != null && !url.isEmpty()) {
                Glide.with(this).load(url).placeholder(R.drawable.ic_avatar_placeholder)
                        .into(binding.ivAvatar);
            }
        });
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

    public void popHost() {
        getSupportFragmentManager().popBackStack();
    }

    private void syncHostVisibility() {
        boolean hasHost = getSupportFragmentManager().getBackStackEntryCount() > 0;
        binding.fragmentHost.setVisibility(hasHost ? View.VISIBLE : View.GONE);
        binding.viewPager.setVisibility(hasHost ? View.GONE : View.VISIBLE);
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
