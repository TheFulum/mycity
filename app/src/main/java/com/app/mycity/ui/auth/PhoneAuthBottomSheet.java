package com.app.mycity.ui.auth;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.app.mycity.databinding.BottomSheetPhoneAuthBinding;

import java.util.concurrent.TimeUnit;

public class PhoneAuthBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_PHONE = "phone";
    private static final String ARG_IS_REGISTER = "is_register";

    private BottomSheetPhoneAuthBinding binding;
    private FirebaseAuth auth;
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;
    private String phoneNumber; // в формате E.164, например +375441234567
    private boolean isRegister;

    /**
     * @param rawDigits  9 цифр без кода страны (то, что ввёл пользователь в поле с prefix "+375 ")
     * @param isRegister true — регистрация, false — вход
     */
    public static PhoneAuthBottomSheet newInstance(String rawDigits, boolean isRegister) {
        PhoneAuthBottomSheet sheet = new PhoneAuthBottomSheet();
        Bundle args = new Bundle();
        // Нормализуем: убираем пробелы/дефисы и добавляем +375
        String normalized = "+375" + rawDigits.replaceAll("[^0-9]", "");
        args.putString(ARG_PHONE, normalized);
        args.putBoolean(ARG_IS_REGISTER, isRegister);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        if (getArguments() != null) {
            phoneNumber = getArguments().getString(ARG_PHONE);
            isRegister  = getArguments().getBoolean(ARG_IS_REGISTER, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetPhoneAuthBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.tvPhoneHint.setText("Код отправлен на " + phoneNumber);

        binding.btnVerify.setOnClickListener(v -> verifyCode());
        binding.tvResend.setOnClickListener(v -> {
            // скрываем OTP-блок, показываем ожидание
            binding.layoutOtp.setVisibility(View.GONE);
            binding.layoutWaiting.setVisibility(View.VISIBLE);
            sendCode();
        });

        sendCode();
    }

    /** Отправка SMS через Firebase Phone Auth */
    private void sendCode() {
        // Показываем индикатор ожидания
        binding.layoutWaiting.setVisibility(View.VISIBLE);
        binding.layoutOtp.setVisibility(View.GONE);

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)          // E.164 формат
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(requireActivity())
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        // Авто-верификация (тестовые номера Firebase или некоторые Android-версии)
                        signInWithCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        Log.e("PhoneAuth", "ERROR: " + e.getClass().getSimpleName() + " | " + e.getMessage(), e);
                        if (binding == null) return;
                        binding.layoutWaiting.setVisibility(View.GONE);
                        Toast.makeText(requireContext(),
                                "Ошибка: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onCodeSent(@NonNull String verId,
                                           @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        if (binding == null) return;
                        verificationId = verId;
                        resendToken    = token;

                        // Скрываем "Отправляем SMS", показываем поле ввода кода
                        binding.layoutWaiting.setVisibility(View.GONE);
                        binding.layoutOtp.setVisibility(View.VISIBLE);
                        binding.tvResend.setVisibility(View.VISIBLE);
                    }
                })
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    /** Верификация введённого 6-значного кода */
    private void verifyCode() {
        String code = binding.etOtp.getText().toString().trim();
        if (TextUtils.isEmpty(code) || code.length() < 6) {
            binding.tilOtp.setError("Введите 6-значный код");
            return;
        }
        binding.tilOtp.setError(null);

        if (verificationId == null) {
            Toast.makeText(requireContext(), "Подождите отправки кода", Toast.LENGTH_SHORT).show();
            return;
        }

        setVerifyLoading(true);
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithCredential(credential);
    }

    private void signInWithCredential(PhoneAuthCredential credential) {
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    if (binding != null) setVerifyLoading(false);
                    dismiss();

                    Activity activity = requireActivity();
                    if (!isRegister && activity instanceof LoginActivity) {
                        ((LoginActivity) activity).onPhoneAuthSuccess();
                    } else if (isRegister && activity instanceof RegisterActivity) {
                        ((RegisterActivity) activity).onPhoneRegisterSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    if (binding != null) {
                        setVerifyLoading(false);
                        binding.tilOtp.setError("Неверный код. Попробуйте ещё раз");
                    }
                });
    }

    private void setVerifyLoading(boolean loading) {
        if (binding == null) return;
        binding.btnVerify.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}