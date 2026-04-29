package com.app.mycity.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.app.mycity.databinding.ActivityRegisterBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.FirebaseErrors;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;
    private boolean isEmailMode = true;

    private static final int MIN_PASSWORD_LENGTH = 8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        setupTabs();
        setupListeners();
    }

    private void setupTabs() {
        binding.tabEmail.setOnClickListener(v -> switchToEmailMode());
        binding.tabPhone.setOnClickListener(v -> switchToPhoneMode());
        switchToEmailMode();
    }

    private void switchToEmailMode() {
        isEmailMode = true;
        binding.layoutEmail.setVisibility(View.VISIBLE);
        binding.layoutPhone.setVisibility(View.GONE);
        binding.tabEmail.setSelected(true);
        binding.tabPhone.setSelected(false);
    }

    private void switchToPhoneMode() {
        isEmailMode = false;
        binding.layoutEmail.setVisibility(View.GONE);
        binding.layoutPhone.setVisibility(View.VISIBLE);
        binding.tabEmail.setSelected(false);
        binding.tabPhone.setSelected(true);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRegister.setOnClickListener(v -> {
            if (isEmailMode) registerWithEmail();
            else startPhoneRegister();
        });
    }

    private void registerWithEmail() {
        String email           = binding.etEmail.getText().toString().trim();
        String password        = binding.etPassword.getText().toString();
        String passwordConfirm = binding.etPasswordConfirm.getText().toString();

        if (!validateEmailInput(email, password, passwordConfirm)) return;
        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> { setLoading(false); showPostRegisterDialog(); })
                .addOnFailureListener(e -> { setLoading(false); showError(FirebaseErrors.humanize(e)); });
    }

    private void startPhoneRegister() {
        // etPhone содержит только 9 цифр (prefix "+375 " задан в TextInputLayout)
        String digits = binding.etPhone.getText().toString().trim();
        if (TextUtils.isEmpty(digits) || digits.length() < 9) {
            binding.tilPhone.setError("Введите 9 цифр номера");
            return;
        }
        binding.tilPhone.setError(null);
        PhoneAuthBottomSheet.newInstance(digits, true)
                .show(getSupportFragmentManager(), "phone_register");
    }

    public void onPhoneRegisterSuccess() { showPostRegisterDialog(); }

    private void showPostRegisterDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Регистрация завершена!")
                .setMessage("Хотите войти с этим аккаунтом?")
                .setPositiveButton("Войти", (d, w) -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("К выбору входа", (d, w) -> {
                    auth.signOut();
                    Intent intent = new Intent(this, SplashActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setCancelable(false)
                .show();
    }

    private boolean validateEmailInput(String email, String password, String passwordConfirm) {
        boolean ok = true;
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError("Введите корректный email"); ok = false;
        } else { binding.tilEmail.setError(null); }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            binding.tilPassword.setError("Минимум " + MIN_PASSWORD_LENGTH + " символов"); ok = false;
        } else { binding.tilPassword.setError(null); }

        if (!password.equals(passwordConfirm)) {
            binding.tilPasswordConfirm.setError("Пароли не совпадают"); ok = false;
        } else { binding.tilPasswordConfirm.setError(null); }

        return ok;
    }

    private void showError(String msg) {
        binding.tilEmail.setError(msg);
    }

    private void setLoading(boolean loading) {
        binding.btnRegister.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}