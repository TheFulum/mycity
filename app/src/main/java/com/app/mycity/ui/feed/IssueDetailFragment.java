package com.app.mycity.ui.feed;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

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
import com.app.mycity.util.DateUtils;
import com.app.mycity.util.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

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
    private Issue currentIssue;
    private Comment myComment;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentIssueDetailBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        issueId = requireArguments().getString(ARG_ID);

        photoAdapter = new PhotoPagerAdapter();
        b.photoPager.setAdapter(photoAdapter);

        reportAdapter = new PhotoPagerAdapter();
        b.reportPager.setAdapter(reportAdapter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String myUid = user != null ? user.getUid() : null;

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
        b.rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvComments.setAdapter(commentAdapter);
        b.rvComments.setNestedScrollingEnabled(false);

        b.photoPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { updateIndicators(position); }
        });

        boolean canComment = user != null && !new SessionManager(requireContext()).isGuest();
        b.commentForm.setVisibility(canComment ? View.VISIBLE : View.GONE);
        b.btnSendComment.setOnClickListener(v -> sendComment());

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

    private void renderIssue(Issue issue) {
        b.tvTitle.setText(issue.getTitle());
        b.tvAddress.setText(issue.getAddress() != null ? issue.getAddress() : "");
        b.tvDescription.setText(issue.getDescription());
        b.tvDate.setText(DateUtils.format(issue.getCreatedAt()));

        if (issue.isResolved()) {
            b.tvStatus.setText(R.string.status_resolved);
            b.tvStatus.setBackgroundResource(R.drawable.bg_status_resolved);
        } else {
            b.tvStatus.setText(R.string.status_active);
            b.tvStatus.setBackgroundResource(R.drawable.bg_status_active);
        }

        photoAdapter.submit(issue.getPhotoUrls());
        rebuildIndicators(issue.getPhotoUrls() == null ? 0 : issue.getPhotoUrls().size());

        if (issue.isResolved() && (issue.getResolveReport() != null || issue.getResolvedBy() != null)) {
            b.reportBlock.setVisibility(View.VISIBLE);
            b.tvResolvedBy.setText(issue.getResolvedBy() != null ? issue.getResolvedBy() : "");
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
            Comment c = (myComment != null) ? myComment : new Comment();
            c.setAuthorId(user.getUid());
            c.setAuthorName(name);
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
    public void onDestroyView() {
        super.onDestroyView();
        if (issueListener != null) { issueListener.remove(); issueListener = null; }
        if (commentsListener != null) { commentsListener.remove(); commentsListener = null; }
        b = null;
    }
}
