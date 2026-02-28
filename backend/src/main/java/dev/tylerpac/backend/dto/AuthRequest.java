package dev.tylerpac.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthRequest {

    @NotBlank(message = "username_required")
    @Size(min = 3, max = 50, message = "username_invalid")
    private String username;

    @NotBlank(message = "password_required")
    @Size(min = 6, max = 100, message = "password_invalid")
    private String password;

    @Email(message = "email_invalid")
    private String email;

    public AuthRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
