package com.app.mycity.ui.citypost;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.app.mycity.R;
import com.app.mycity.data.model.CityPost;
import com.app.mycity.data.model.Comment;
import com.app.mycity.data.model.UserProfile;
import com.app.mycity.data.repository.CityPostCommentRepository;
import com.app.mycity.data.repository.CityPostRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.FragmentCityPostDetailBinding;
import com.app.mycity.ui.feed.CommentAdapter;
import com.app.mycity.ui.feed.PhotoGalleryActivity;
import com.app.mycity.ui.feed.PhotoPagerAdapter;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.DateUtils;
import com.app.mycity.util.GeoUtils;
import com.app.mycity.util.SessionManager;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class CityPostDetailFragment extends Fragment {

    private static final String ARG_ID = "post_id";

    public static CityPostDetailFragment newInstance(String postId) {
        CityPostDetailFragment f = new CityPostDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ID, postId);
        f.setArguments(args);
        return f;
    }

    private FragmentCityPostDetailBinding b;
    private final CityPostRepository postRepo = new CityPostRepository();
    private final CityPostCommentRepository commentRepo = new CityPostCommentRepository();
    private final UserRepository userRepo = new UserRepository();

    private ListenerRegistration postListener;
    private ListenerRegistration commentsListener;
    private CommentAdapter commentAdapter;
    private PhotoPagerAdapter photoAdapter;

    private String postId;
    private String myUid;
    private boolean isAdmin;
    private CityPost currentPost;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentCityPostDetailBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        postId = requireArguments().getString(ARG_ID);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        myUid = user != null ? user.getUid() : null;

        photoAdapter = new PhotoPagerAdapter();
        photoAdapter.setOnPhotoClick(index -> launchGallery(photoAdapter.getUrls(), index));
        b.photoPager.setAdapter(photoAdapter);
        b.photoPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicators(position);
            }
        });

        commentAdapter = new CommentAdapter(myUid, isAdmin, commentAdapterCallbacks());
        commentAdapter.setOnAuthorClick(uid -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openUserProfile(uid);
        });
        b.rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvComments.setItemAnimator(null);
        b.rvComments.setAdapter(commentAdapter);
        b.rvComments.setNestedScrollingEnabled(false);

        boolean canComment = user != null && !new SessionManager(requireContext()).isGuest();
        b.commentForm.setVisibility(canComment ? View.VISIBLE : View.GONE);
        b.btnSendComment.setOnClickListener(v -> sendComment());

        b.btnBack.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).popHost();
        });

        b.likeRow.setOnClickListener(v -> toggleLike());

        if (myUid != null) {
            userRepo.get(myUid).addOnSuccessListener(snap -> {
                if (snap != null && snap.exists()) {
                    isAdmin = "admin".equals(snap.getString("role"));
                    commentAdapter = new CommentAdapter(myUid, isAdmin, commentAdapterCallbacks());
                    commentAdapter.setOnAuthorClick(uid -> {
                        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openUserProfile(uid);
                    });
                    if (b != null) b.rvComments.setAdapter(commentAdapter);
                }
            });
        }

        subscribePost();
        subscribeComments();
    }

    private CommentAdapter.Callbacks commentAdapterCallbacks() {
        return new CommentAdapter.Callbacks() {
            @Override
            public void onEdit(Comment c) {
                boolean mine = myUid != null && myUid.equals(c.getAuthorId());
                if (!mine && !isAdmin) return;
                b.etComment.setText(c.getText());
                b.etComment.setTag(c.getId());
                b.etComment.requestFocus();
            }

            @Override
            public void onDelete(Comment c) {
                boolean mine = myUid != null && myUid.equals(c.getAuthorId());
                if (!mine && !isAdmin) return;
                new MaterialAlertDialogBuilder(requireContext(), R.style.DialogTheme)
                        .setTitle("Удалить комментарий?")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Удалить", (d, w) -> commentRepo.delete(postId, c.getId(), null))
                        .show();
            }

            @Override
            public void onReply(Comment c) {
                if (commentAdapter != null) commentAdapter.openInlineReply(c);
            }

            @Override
            public void onGoToComment(String commentId) {
                int pos = commentAdapter.findPositionById(commentId);
                if (pos >= 0) b.rvComments.scrollToPosition(pos);
            }

            @Override
            public void onSendInlineReply(Comment parent, String text) {
                sendInlineReply(parent, text);
            }
        };
    }

    private void sendInlineReply(Comment parent, String text) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        if (parent == null || parent.getId() == null) return;
        String trimmed = text != null ? text.trim() : "";
        if (trimmed.isEmpty()) return;

        userRepo.get(user.getUid()).addOnSuccessListener(snap -> {
            UserProfile profile = snap != null && snap.exists() ? snap.toObject(UserProfile.class) : null;
            if (profile != null && profile.isBlockedNow()) {
                showBlockedActionDialog();
                return;
            }
            String name = snap != null && snap.exists() ? snap.getString("displayName") : null;
            if (name == null || name.isEmpty()) {
                name = user.getDisplayName() != null ? user.getDisplayName()
                        : (user.getEmail() != null ? user.getEmail() : "Пользователь");
            }
            String role = snap != null && snap.exists() ? snap.getString("role") : null;

            Comment c = new Comment();
            c.setAuthorId(user.getUid());
            c.setAuthorName(name);
            c.setAuthorRole(role);
            c.setRating(0);
            c.setText(trimmed);
            c.setParentCommentId(parent.getId());

            commentRepo.upsert(postId, c, (ok, err) -> {
                if (!ok && getContext() != null) {
                    Toast.makeText(requireContext(), "Ошибка отправки ответа", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void subscribePost() {
        postListener = postRepo.listenOne(postId, (post, err) -> {
            if (b == null) return;
            if (post == null) return;
            currentPost = post;
            renderPost(post);
        });
    }

    private void subscribeComments() {
        commentsListener = commentRepo.listen(postId, (list, err) -> {
            if (b == null) return;
            commentAdapter.submit(list);
            b.tvNoComments.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void renderPost(CityPost post) {
        b.tvTitle.setText(post.getTitle() != null ? post.getTitle() : "");
        b.tvDescription.setText(post.getDescription() != null ? post.getDescription() : "");
        b.tvDate.setText(DateUtils.format(post.getCreatedAt()));

        String authorName = post.getAuthorName();
        if (authorName != null && !authorName.isEmpty()) {
            b.tvAuthorName.setText(authorName);
            b.tvAuthorName.setVisibility(View.VISIBLE);
            String aid = post.getAuthorId();
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

        if (post.getAuthorAvatarUrl() != null && !post.getAuthorAvatarUrl().isEmpty()) {
            Glide.with(this).load(post.getAuthorAvatarUrl())
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .into(b.ivAvatar);
        } else {
            b.ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
        }

        b.tvCategory.setText(categoryLabel(post.getCategory()));

        List<String> urls = post.getPhotoUrls();
        if (urls != null && !urls.isEmpty()) {
            b.photoContainer.setVisibility(View.VISIBLE);
            photoAdapter.submit(urls);
            rebuildIndicators(urls.size());
        } else {
            b.photoContainer.setVisibility(View.GONE);
        }

        if (post.hasLocation()) {
            b.locationRow.setVisibility(View.VISIBLE);
            String addr = post.getAddress();
            if (addr == null || addr.isEmpty()) addr = GeoUtils.formatCoords(post.getLat(), post.getLng());
            b.tvAddress.setText(GeoUtils.displayAddress(addr));
            final double lat = post.getLat();
            final double lng = post.getLng();
            final String title = post.getTitle() != null ? post.getTitle() : "";
            b.locationRow.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity)
                    ((MainActivity) getActivity()).openMapFullscreen(lat, lng, title);
            });
        } else {
            b.locationRow.setVisibility(View.GONE);
            b.locationRow.setOnClickListener(null);
        }

        b.tvLikeCount.setText(String.valueOf(post.getLikeCount()));
        boolean liked = myUid != null && post.getLikedBy() != null && post.getLikedBy().contains(myUid);
        b.ivLike.setImageResource(liked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
    }

    private String categoryLabel(String category) {
        if (category == null) return "";
        switch (category) {
            case CityPost.CATEGORY_NATURE: return getString(R.string.city_detail_category_nature);
            case CityPost.CATEGORY_PLACE: return getString(R.string.city_detail_category_place);
            case CityPost.CATEGORY_VENUE: return getString(R.string.city_detail_category_venue);
            case CityPost.CATEGORY_HISTORY: return getString(R.string.city_detail_category_history);
            default: return category;
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
        if (b == null) return;
        for (int i = 0; i < b.photoIndicators.getChildCount(); i++) {
            b.photoIndicators.getChildAt(i).setSelected(i == position);
        }
    }

    private void toggleLike() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || currentPost == null) {
            toast("Требуется вход");
            return;
        }
        if (new SessionManager(requireContext()).isGuest()) {
            toast("Требуется вход");
            return;
        }
        String uid = user.getUid();
        boolean liked = currentPost.getLikedBy() != null && currentPost.getLikedBy().contains(uid);
        if (liked) {
            postRepo.unlike(postId, uid);
        } else {
            postRepo.like(postId, uid);
        }
    }

    private void sendComment() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String text = b.etComment.getText() == null ? "" : b.etComment.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            b.tilComment.setError(getString(R.string.error_required));
            return;
        }
        b.tilComment.setError(null);
        b.btnSendComment.setEnabled(false);

        Object tag = b.etComment.getTag();
        final String editingId = tag instanceof String ? (String) tag : null;

        userRepo.get(user.getUid()).addOnSuccessListener(snap -> {
            UserProfile profile = snap != null && snap.exists() ? snap.toObject(UserProfile.class) : null;
            if (profile != null && profile.isBlockedNow()) {
                if (b != null) b.btnSendComment.setEnabled(true);
                showBlockedActionDialog();
                return;
            }
            String name = snap != null && snap.exists() ? snap.getString("displayName") : null;
            if (name == null || name.isEmpty()) {
                name = user.getDisplayName() != null ? user.getDisplayName()
                        : (user.getEmail() != null ? user.getEmail() : "Пользователь");
            }
            String role = snap != null && snap.exists() ? snap.getString("role") : null;

            Comment c = new Comment();
            if (editingId != null) c.setId(editingId);
            c.setAuthorId(user.getUid());
            c.setAuthorName(name);
            c.setAuthorRole(role);
            c.setRating(0);
            c.setText(text);

            commentRepo.upsert(postId, c, (ok, err) -> {
                if (b == null) return;
                b.btnSendComment.setEnabled(true);
                if (ok) {
                    b.etComment.setText("");
                    b.etComment.setTag(null);
                } else {
                    Toast.makeText(requireContext(), "Ошибка сохранения", Toast.LENGTH_SHORT).show();
                }
            });
        }).addOnFailureListener(e -> {
            if (b != null) b.btnSendComment.setEnabled(true);
        });
    }

    private void showBlockedActionDialog() {
        if (getContext() == null) return;
        new MaterialAlertDialogBuilder(requireContext(), R.style.DialogTheme)
                .setTitle("Действие недоступно")
                .setMessage("Вы заблокированы и не можете писать комментарии.")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void launchGallery(List<String> urls, int index) {
        if (urls == null || urls.isEmpty()) return;
        android.content.Intent intent = new android.content.Intent(requireContext(), PhotoGalleryActivity.class);
        intent.putStringArrayListExtra(PhotoGalleryActivity.EXTRA_URLS, new ArrayList<>(urls));
        intent.putExtra(PhotoGalleryActivity.EXTRA_INDEX, index);
        startActivity(intent);
    }

    private void toast(String msg) {
        Context ctx = getContext();
        if (ctx != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (postListener != null) { postListener.remove(); postListener = null; }
        if (commentsListener != null) { commentsListener.remove(); commentsListener = null; }
        b = null;
    }
}
