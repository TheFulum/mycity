package com.app.mycity.ui.feed;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.app.mycity.R;
import com.app.mycity.data.model.Comment;
import com.app.mycity.data.model.Issue;
import com.app.mycity.data.repository.CommentRepository;
import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.FragmentIssueDetailBinding;
import com.app.mycity.ui.admin.AdminResolveBottomSheet;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.DateUtils;
import com.app.mycity.util.GeoUtils;
import com.app.mycity.util.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.List;

public class IssueDetailFragment extends Fragment {

    private static final String ARG_ID = "issue_id";

    public static IssueDetailFragment newInstance(String issueId) {
        IssueDetailFragment f = new IssueDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ID, issueId);
        f.setArguments(args);
        return f;
    }

    private FragmentIssueDetailBinding b;
    private final IssueRepository issueRepo = new IssueRepository();
    private final CommentRepository commentRepo = new CommentRepository();
    private final UserRepository userRepo = new UserRepository();

    private ListenerRegistration issueListener;
    private ListenerRegistration commentsListener;
    private CommentAdapter commentAdapter;
    private PhotoPagerAdapter photoAdapter;
    private PhotoPagerAdapter reportAdapter;

    private String issueId;
    private String myUid;
    private boolean isAdmin = false;
    private Issue currentIssue;
    private Comment myComment;
    private MapView mapView;
    private Marker mapMarker;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentIssueDetailBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        issueId = requireArguments().getString(ARG_ID);

        photoAdapter = new PhotoPagerAdapter();
        photoAdapter.setOnPhotoClick(index -> launchGallery(photoAdapter.getUrls(), index));
        b.photoPager.setAdapter(photoAdapter);

        reportAdapter = new PhotoPagerAdapter();
        reportAdapter.setOnPhotoClick(index -> launchGallery(reportAdapter.getUrls(), index));
        b.reportPager.setAdapter(reportAdapter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        myUid = user != null ? user.getUid() : null;

        commentAdapter = new CommentAdapter(myUid, new CommentAdapter.Callbacks() {

            @Override public void onEdit(Comment c) {
                b.commentRating.setRating(c.getRating());
                b.etComment.setText(c.getText());
                b.etComment.requestFocus();
            }
            @Override public void onDelete(Comment c) {
                new MaterialAlertDialogBuilder(requireContext(), R.style.DialogTheme)
                        .setTitle("Удалить комментарий?")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Удалить", (d, w) -> commentRepo.delete(issueId, c.getAuthorId(), null))
                        .show();
            }
        });
        commentAdapter.setOnAuthorClick(uid -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openUserProfile(uid);
        });
        b.rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvComments.setItemAnimator(null);
        b.rvComments.setAdapter(commentAdapter);
        b.rvComments.setNestedScrollingEnabled(false);

        b.photoPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { updateIndicators(position); }
        });

        boolean canComment = user != null && !new SessionManager(requireContext()).isGuest();
        b.commentForm.setVisibility(canComment ? View.VISIBLE : View.GONE);
        b.btnSendComment.setOnClickListener(v -> sendComment());

        if (myUid != null) {
            userRepo.get(myUid).addOnSuccessListener(snap -> {
                if (snap != null && snap.exists()) {
                    isAdmin = "admin".equals(snap.getString("role"));
                    if (currentIssue != null) updateAdminButton(currentIssue);
                }
            });
        }

        mapView = b.mapView;
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(false);
        mapView.setClickable(false);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.getController().setZoom((double) GeoUtils.DEFAULT_ZOOM);

        subscribeIssue();
        subscribeComments();
    }

    private void subscribeIssue() {
        issueListener = issueRepo.listenOne(issueId, (issue, err) -> {
            if (b == null || issue == null) return;
            currentIssue = issue;
            renderIssue(issue);
        });
    }

    private void subscribeComments() {
        commentsListener = commentRepo.listen(issueId, (list, err) -> {
            if (b == null) return;
            commentAdapter.submit(list);
            b.tvNoComments.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) {
                myComment = null;
                for (Comment c : list) {
                    if (u.getUid().equals(c.getAuthorId())) { myComment = c; break; }
                }
            }
        });
    }

    private void updateAdminButton(Issue issue) {
        if (b == null) return;
        boolean show = isAdmin && !issue.isResolved();
        b.btnAdminResolve.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            b.btnAdminResolve.setOnClickListener(v -> {
                AdminResolveBottomSheet bs = AdminResolveBottomSheet.newInstance(issue.getId());
                bs.show(getChildFragmentManager(), "admin_resolve");
            });
        }
    }

    private void showMoreMenu(Issue issue) {
        PopupMenu menu = new PopupMenu(requireContext(), b.btnMore);
        menu.getMenu().add(0, 1, 0, "Удалить");
        if (issue.isResolved()) {
            menu.getMenu().add(0, 3, 1, "Возобновить");
        } else {
            menu.getMenu().add(0, 2, 1, "Изменить");
        }
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: confirmDelete(issue); return true;
                case 2:
                    if (getActivity() instanceof MainActivity)
                        ((MainActivity) getActivity()).openEditIssue(issue.getId());
                    return true;
                case 3: showResumeDialog(issue); return true;
            }
            return false;
        });
        menu.show();
    }

    private void confirmDelete(Issue issue) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Удалить заявку?")
                .setMessage("Действие необратимо.")
                .setPositiveButton("Удалить", (d, w) ->
                        issueRepo.delete(issue.getId()).addOnSuccessListener(v -> {
                            Toast.makeText(requireContext(), "Заявка удалена", Toast.LENGTH_SHORT).show();
                            if (getActivity() instanceof MainActivity)
                                ((MainActivity) getActivity()).popHostToRoot();
                        }))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showResumeDialog(Issue issue) {
        EditText et = new EditText(requireContext());
        et.setHint("Причина возобновления");
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF9E9E9E);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad, pad, pad);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Возобновить заявку")
                .setView(et)
                .setPositiveButton("Подтвердить", (d, w) -> {
                    String reason = et.getText().toString().trim();
                    if (reason.length() < 10) {
                        Toast.makeText(requireContext(), "Минимум 10 символов", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    issue.setDescription(issue.getDescription() + "\n\n— Возобновлено: " + reason);
                    issue.setStatus(Issue.STATUS_ACTIVE);
                    issue.setResolvedAt(null);
                    issue.setResolvedBy(null);
                    issue.setResolvedByName(null);
                    issue.setResolveReport(null);
                    issue.setReportPhotoUrls(new ArrayList<>());
                    issueRepo.save(issue).addOnFailureListener(
                            e -> Toast.makeText(requireContext(), "Ошибка", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void renderIssue(Issue issue) {
        b.tvTitle.setText(issue.getTitle());
        b.tvAddress.setText(GeoUtils.displayAddress(issue.getAddress()));
        b.tvDescription.setText(issue.getDescription());
        b.tvDate.setText(DateUtils.format(issue.getCreatedAt()));

        boolean isAuthor = myUid != null && myUid.equals(issue.getAuthorId());
        b.btnMore.setVisibility(isAuthor ? View.VISIBLE : View.GONE);
        if (isAuthor) b.btnMore.setOnClickListener(v -> showMoreMenu(issue));

        updateAdminButton(issue);

        String authorName = issue.getAuthorName();
        if (authorName != null && !authorName.isEmpty()) {
            b.tvAuthorName.setText(authorName);
            b.tvAuthorName.setVisibility(View.VISIBLE);
            String aid = issue.getAuthorId();
            if (aid != null) {
                b.tvAuthorName.setOnClickListener(v -> {
                    if (getActivity() instanceof MainActivity)
                        ((MainActivity) getActivity()).openUserProfile(aid);
                });
            } else {
                b.tvAuthorName.setOnClickListener(null);
            }
        } else {
            b.tvAuthorName.setVisibility(View.GONE);
        }

        if (issue.isResolved()) {
            b.tvStatus.setText(R.string.status_resolved);
            b.tvStatus.setBackgroundResource(R.drawable.bg_status_resolved);
        } else {
            b.tvStatus.setText(R.string.status_active);
            b.tvStatus.setBackgroundResource(R.drawable.bg_status_active);
        }

        photoAdapter.submit(issue.getPhotoUrls());
        rebuildIndicators(issue.getPhotoUrls() == null ? 0 : issue.getPhotoUrls().size());

        GeoPoint point = new GeoPoint(issue.getLat(), issue.getLng());
        mapView.getController().setCenter(point);
        mapView.getController().setZoom((double) GeoUtils.DEFAULT_ZOOM);
        if (mapMarker == null) {
            mapMarker = new Marker(mapView);
            mapMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapMarker.setIcon(androidx.core.content.ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_marker));
            mapMarker.setInfoWindow(null);
            mapView.getOverlays().add(mapMarker);
        }
        mapMarker.setPosition(point);
        mapView.invalidate();

        final double capturedLat = issue.getLat();
        final double capturedLng = issue.getLng();
        final String capturedTitle = issue.getTitle() != null ? issue.getTitle() : "";
        b.btnMapFullscreen.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openMapFullscreen(
                        capturedLat, capturedLng, capturedTitle);
            }
        });

        if (issue.isResolved() && (issue.getResolveReport() != null || issue.getResolvedBy() != null)) {
            b.reportBlock.setVisibility(View.VISIBLE);
            String resolvedName = issue.getResolvedByName() != null
                    ? issue.getResolvedByName()
                    : (issue.getResolvedBy() != null ? issue.getResolvedBy() : "");
            b.tvResolvedBy.setText("Закрыл(а): " + resolvedName);
            String resolvedUid = issue.getResolvedBy();
            if (resolvedUid != null) {
                b.tvResolvedBy.setOnClickListener(v -> {
                    if (getActivity() instanceof MainActivity)
                        ((MainActivity) getActivity()).openUserProfile(resolvedUid);
                });
            } else {
                b.tvResolvedBy.setOnClickListener(null);
            }
            b.tvResolveReport.setText(issue.getResolveReport() != null ? issue.getResolveReport() : "");
            b.tvResolvedAt.setText(DateUtils.format(issue.getResolvedAt()));
            if (issue.getReportPhotoUrls() != null && !issue.getReportPhotoUrls().isEmpty()) {
                b.reportPager.setVisibility(View.VISIBLE);
                reportAdapter.submit(issue.getReportPhotoUrls());
            } else {
                b.reportPager.setVisibility(View.GONE);
            }
        } else {
            b.reportBlock.setVisibility(View.GONE);
        }
    }

    private void rebuildIndicators(int count) {
        b.photoIndicators.removeAllViews();
        if (count <= 1) return;
        int size = (int) (8 * getResources().getDisplayMetrics().density);
        int margin = (int) (4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_indicator_dot);
            dot.setSelected(i == 0);
            b.photoIndicators.addView(dot);
        }
    }

    private void updateIndicators(int position) {
        for (int i = 0; i < b.photoIndicators.getChildCount(); i++) {
            b.photoIndicators.getChildAt(i).setSelected(i == position);
        }
    }

    private void sendComment() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String text = b.etComment.getText().toString().trim();
        int rating = (int) b.commentRating.getRating();
        if (TextUtils.isEmpty(text)) {
            b.tilComment.setError(getString(R.string.error_required));
            return;
        }
        b.tilComment.setError(null);
        b.btnSendComment.setEnabled(false);

        userRepo.get(user.getUid()).addOnSuccessListener(snap -> {
            String name = snap != null && snap.exists() ? snap.getString("displayName") : null;
            if (name == null || name.isEmpty()) {
                name = user.getDisplayName() != null ? user.getDisplayName()
                        : (user.getEmail() != null ? user.getEmail() : "Пользователь");
            }
            String role = snap != null && snap.exists() ? snap.getString("role") : null;
            Comment c = (myComment != null) ? myComment : new Comment();
            c.setAuthorId(user.getUid());
            c.setAuthorName(name);
            c.setAuthorRole(role);
            c.setRating(rating);
            c.setText(text);
            commentRepo.upsert(issueId, c, (ok, err) -> {
                if (b == null) return;
                b.btnSendComment.setEnabled(true);
                if (ok) {
                    b.etComment.setText("");
                    Toast.makeText(requireContext(), "Комментарий сохранён", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Ошибка сохранения", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    private void launchGallery(java.util.List<String> urls, int index) {
        if (urls == null || urls.isEmpty()) return;
        Intent intent = new Intent(requireContext(), PhotoGalleryActivity.class);
        intent.putStringArrayListExtra(PhotoGalleryActivity.EXTRA_URLS, new ArrayList<>(urls));
        intent.putExtra(PhotoGalleryActivity.EXTRA_INDEX, index);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (issueListener != null) { issueListener.remove(); issueListener = null; }
        if (commentsListener != null) { commentsListener.remove(); commentsListener = null; }
        mapView = null;
        mapMarker = null;
        b = null;
    }
}
