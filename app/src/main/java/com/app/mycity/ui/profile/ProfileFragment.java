package com.app.mycity.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import com.app.mycity.util.FirebaseErrors;
import com.app.mycity.util.SessionManager;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.concurrent.TimeUnit;

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
        adapter.setOnAuthorClick(uid -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openUserProfile(uid);
            }
        });
        b.rvMy.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvMy.setItemAnimator(null);
        b.rvMy.setAdapter(adapter);

        b.tabs.addTab(b.tabs.newTab().setText(R.string.profile_my_active));
        b.tabs.addTab(b.tabs.newTab().setText(R.string.profile_my_resolved));
        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                b.rvMy.animate().alpha(0f).setDuration(110).withEndAction(() -> {
                    showResolved = tab.getPosition() == 1;
                    subscribeMyIssues();
                    b.rvMy.animate().alpha(1f).setDuration(180).start();
                }).start();
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

        b.btnLinkEmail.setOnClickListener(v -> showLinkEmailDialog());
        b.btnLinkPhone.setOnClickListener(v -> showLinkPhoneDialog());

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
            String email = profile.getEmail();
            String phone = profile.getPhone();
            b.tvEmail.setText(email != null ? email : "");
            b.tvPhone.setText(phone != null ? phone : "");
            b.btnLinkEmail.setVisibility(TextUtils.isEmpty(email) ? View.VISIBLE : View.GONE);
            b.btnLinkPhone.setVisibility(TextUtils.isEmpty(phone) ? View.VISIBLE : View.GONE);
            b.ivCrown.setVisibility(profile.isAdmin() ? View.VISIBLE : View.GONE);
            if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                Glide.with(b.ivAvatar).load(profile.getAvatarUrl())
                        .placeholder(R.drawable.ic_avatar_placeholder).into(b.ivAvatar);
            }
            int count = profile.getIssueCount();
            if (count > 0) {
                b.tvBadge.setVisibility(View.VISIBLE);
                b.tvBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                b.tvBadge.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Счётчик заявок")
                        .setMessage("Вы подали " + count + " заявок(и), принятых к рассмотрению или уже решённых.")
                        .setPositiveButton("Понятно", null)
                        .show());
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

    private void showLinkEmailDialog() {
        LinearLayout form = verticalForm();
        EditText etEmail = new EditText(requireContext());
        etEmail.setHint("Email");
        etEmail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText etPass = new EditText(requireContext());
        etPass.setHint("Пароль (минимум 6 символов)");
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(etEmail);
        form.addView(etPass);

        new AlertDialog.Builder(requireContext())
                .setTitle("Привязать email")
                .setView(form)
                .setPositiveButton("Привязать", (d, w) -> {
                    String email = etEmail.getText().toString().trim();
                    String pass = etPass.getText().toString();
                    if (!email.contains("@") || pass.length() < 6) {
                        toast("Проверьте email и пароль");
                        return;
                    }
                    linkEmail(email, pass);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void linkEmail(String email, String password) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        AuthCredential cred = EmailAuthProvider.getCredential(email, password);
        user.linkWithCredential(cred)
                .addOnSuccessListener(r -> {
                    userRepo.upsert(user.getUid(), null, email, null);
                    toast("Email привязан");
                })
                .addOnFailureListener(e -> toast(FirebaseErrors.humanize(e)));
    }

    private void showLinkPhoneDialog() {
        EditText etPhone = new EditText(requireContext());
        etPhone.setHint("9 цифр после +375");
        etPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        LinearLayout form = verticalForm();
        form.addView(etPhone);

        new AlertDialog.Builder(requireContext())
                .setTitle("Привязать телефон")
                .setView(form)
                .setPositiveButton("Отправить код", (d, w) -> {
                    String raw = etPhone.getText().toString().replaceAll("[^0-9]", "");
                    if (raw.length() < 9) { toast("Введите 9 цифр"); return; }
                    String e164 = "+375" + raw.substring(Math.max(0, raw.length() - 9));
                    sendPhoneLinkCode(e164);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void sendPhoneLinkCode(String e164) {
        toast("Отправка кода на " + e164 + "…");
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(e164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(requireActivity())
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        linkPhoneCredential(credential, e164);
                    }
                    @Override public void onVerificationFailed(@NonNull FirebaseException e) {
                        toast("Ошибка: " + e.getMessage());
                    }
                    @Override public void onCodeSent(@NonNull String verId,
                                                     @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        promptOtp(verId, e164);
                    }
                })
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void promptOtp(String verificationId, String e164) {
        if (b == null) return;
        EditText etOtp = new EditText(requireContext());
        etOtp.setHint("Код из SMS");
        etOtp.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout form = verticalForm();
        form.addView(etOtp);

        new AlertDialog.Builder(requireContext())
                .setTitle("Код подтверждения")
                .setView(form)
                .setPositiveButton("Подтвердить", (d, w) -> {
                    String code = etOtp.getText().toString().trim();
                    if (code.length() < 6) { toast("6 цифр"); return; }
                    linkPhoneCredential(PhoneAuthProvider.getCredential(verificationId, code), e164);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void linkPhoneCredential(PhoneAuthCredential credential, String e164) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.linkWithCredential(credential)
                .addOnSuccessListener(r -> {
                    userRepo.upsert(user.getUid(), null, null, e164);
                    toast("Телефон привязан");
                })
                .addOnFailureListener(e -> toast(FirebaseErrors.humanize(e)));
    }

    private LinearLayout verticalForm() {
        LinearLayout ll = new LinearLayout(requireContext());
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ll.setPadding(pad, pad, pad, 0);
        return ll;
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) { userListener.remove(); userListener = null; }
        if (myListener != null) { myListener.remove(); myListener = null; }
        b = null;
    }
}
