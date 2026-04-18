package com.app.mycity.util;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;

public final class FirebaseErrors {

    private FirebaseErrors() {}

    public static String humanize(Throwable t) {
        if (t instanceof FirebaseNetworkException) {
            return "Нет подключения к интернету";
        }
        if (t instanceof FirebaseAuthException) {
            switch (((FirebaseAuthException) t).getErrorCode()) {
                case "ERROR_USER_NOT_FOUND":             return "Пользователь не найден";
                case "ERROR_WRONG_PASSWORD":             return "Неверный пароль";
                case "ERROR_INVALID_EMAIL":              return "Некорректный email";
                case "ERROR_EMAIL_ALREADY_IN_USE":       return "Этот email уже занят";
                case "ERROR_WEAK_PASSWORD":              return "Слишком слабый пароль";
                case "ERROR_TOO_MANY_REQUESTS":          return "Слишком много попыток. Повторите позже";
                case "ERROR_NETWORK_REQUEST_FAILED":     return "Нет подключения к интернету";
                case "ERROR_INVALID_VERIFICATION_CODE":  return "Неверный код подтверждения";
                case "ERROR_SESSION_EXPIRED":            return "Срок действия кода истёк. Запросите новый";
                case "ERROR_INVALID_PHONE_NUMBER":       return "Некорректный номер телефона";
                case "ERROR_CREDENTIAL_ALREADY_IN_USE":  return "Этот аккаунт уже используется";
                case "ERROR_PROVIDER_ALREADY_LINKED":    return "Метод уже привязан к аккаунту";
                case "ERROR_REQUIRES_RECENT_LOGIN":      return "Войдите заново для этого действия";
                case "ERROR_USER_DISABLED":              return "Аккаунт заблокирован";
                case "ERROR_INVALID_CREDENTIAL":         return "Неверные учётные данные";
            }
        }
        String msg = t.getMessage();
        if (msg != null) {
            if (msg.contains("network") || msg.contains("NETWORK")) return "Нет подключения к интернету";
            if (msg.contains("no user"))       return "Пользователь не найден";
            if (msg.contains("password"))      return "Неверный пароль";
            if (msg.contains("email already")) return "Этот email уже занят";
        }
        return "Произошла ошибка. Попробуйте ещё раз";
    }
}
