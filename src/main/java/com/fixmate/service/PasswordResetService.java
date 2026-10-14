package com.fixmate.service;

import com.fixmate.entity.PasswordResetToken;
import com.fixmate.exception.InvalidPasswordResetTokenException;
import com.fixmate.repository.PasswordResetTokenRepository;
import com.fixmate.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** Academic demo recovery; the displayed OTP deliberately does not verify email ownership. */
@Service
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
    }

    @Transactional
    public String requestReset(String email) {
        var user = users.findByEmailForUpdate(email.trim().toLowerCase(Locale.ROOT))
                .filter(account -> account.isActive())
                .orElseThrow(() -> new IllegalArgumentException("No active account exists for this email"));
        String challenge = randomToken();
        var record = tokens.findByUserIdForUpdate(user.getId()).orElse(null);
        if (record == null) record = new PasswordResetToken(user, hash(challenge), Instant.now().plusSeconds(900));
        else record.replace(hash(challenge), Instant.now().plusSeconds(900));
        tokens.save(record);
        return challenge;
    }

    @Transactional
    public String verifyOtp(String challenge, String otp) {
        var record = validToken(challenge);
        if (record.isOtpVerified() || !"123456".equals(otp)) throw new IllegalArgumentException("Incorrect demo OTP");
        String resetToken = randomToken();
        record.replace(hash(resetToken), Instant.now().plusSeconds(900));
        record.verifyOtp();
        tokens.save(record);
        return resetToken;
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        var record = validToken(rawToken);
        if (!record.isOtpVerified()) throw new InvalidPasswordResetTokenException();
        record.markUsed(Instant.now());
        record.getUser().updatePasswordHash(encoder.encode(newPassword));
        tokens.save(record);
        users.save(record.getUser());
    }

    private PasswordResetToken validToken(String rawToken) {
        return tokens.findByTokenHashForUpdate(hash(rawToken))
                .filter(token -> token.getUsedAt() == null && token.getExpiresAt().isAfter(Instant.now()) && token.getUser().isActive())
                .orElseThrow(InvalidPasswordResetTokenException::new);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
