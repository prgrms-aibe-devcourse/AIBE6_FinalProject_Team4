package com.kiwobollae.api.auth.dto.response;

// Single-use ticket returned once the reset code is confirmed. The client must
// echo this back in PasswordResetRequest so /password/reset can prove the
// caller is the same actor who completed the email verification step.
public record PasswordResetTicketResponse(String resetToken) {
}
