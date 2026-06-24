package com.edumentor.common.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordUtil {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {}

    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String encoded) {
        if (rawPassword == null || encoded == null) {
            return false;
        }
        return ENCODER.matches(rawPassword, encoded);
    }

    public static boolean needsUpgrade(String encoded) {
        return !ENCODER.upgradeEncoding(encoded);
    }

    public static PasswordEncoder getEncoder() {
        return ENCODER;
    }
}
