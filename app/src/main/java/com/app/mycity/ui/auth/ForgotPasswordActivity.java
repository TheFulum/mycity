package com.app.mycity.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.app.mycity.databinding.ActivityForgotPasswordBinding;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSendReset.setOnClickListener(v -> sendResetEmail());
    }

    private void sendResetEmail() {
        String email = binding.etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError("Введите корректный email");
            return;
        }
        binding.tilEmail.setError(null);

        setLoading(true);

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    showSuccess();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("no user")) {
                        binding.tilEmail.setError("Пользователь с таким email не найден");
                    } else {
                        binding.tilEmail.setError("Ошибка. Попробуйте позже");
                    }
                });
    }

    private void showSuccess() {
        binding.layoutForm.setVisibility(View.GONE);
        binding.layoutSuccess.setVisibility(View.VISIBLE);

        binding.btnBackToLogin.setOnClickListener(v -> finish());
    }

    private void setLoading(boolean loading) {
        binding.btnSendReset.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}