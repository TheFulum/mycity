package com.app.mycity.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.app.mycity.databinding.ActivityLoginBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.FirebaseErrors;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private boolean isEmailMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
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
        binding.btnLogin.setOnClickListener(v -> {
            if (isEmailMode) loginWithEmail();
            else startPhoneAuth();
        });
        binding.tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }

    private void loginWithEmail() {
        String email    = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString();

        if (!validateEmailInput(email, password)) return;
        setLoading(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> { setLoading(false); goToMain(); })
                .addOnFailureListener(e -> { setLoading(false); showError(FirebaseErrors.humanize(e)); });
    }

    private void startPhoneAuth() {
        // etPhone содержит только 9 цифр (без +375, т.к. prefix задан в TextInputLayout)
        String digits = binding.etPhone.getText().toString().trim();
        if (TextUtils.isEmpty(digits) || digits.length() < 9) {
            binding.tilPhone.setError("Введите 9 цифр номера");
            return;
        }
        binding.tilPhone.setError(null);
        PhoneAuthBottomSheet.newInstance(digits, false)
                .show(getSupportFragmentManager(), "phone_auth");
    }

    public void onPhoneAuthSuccess() { goToMain(); }

    private boolean validateEmailInput(String email, String password) {
        boolean ok = true;
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError("Введите корректный email"); ok = false;
        } else { binding.tilEmail.setError(null); }

        if (TextUtils.isEmpty(password)) {
            binding.tilPassword.setError("Введите пароль"); ok = false;
        } else { binding.tilPassword.setError(null); }
        return ok;
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}