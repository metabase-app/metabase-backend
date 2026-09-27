package me.lewisblackburn.metabase.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,50}") String username,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 72) String password
) {
    // Avoid exposing the password if a request is logged during debugging.
    @Override
    public String toString() {
        return "SignupRequest[username=" + username + ", email=" + email + ", password=[REDACTED]]";
    }
}
