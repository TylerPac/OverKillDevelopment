package dev.tylerpac.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = true)
    private String password;

    @Column(nullable = true, unique = true)
    private String email;

    @Column(nullable = true, unique = true)
    private String steam64Id;

    @Column(nullable = true, unique = true)
    private String discordUserId;

    @Column(nullable = true)
    private String discordUsername;

    @Column(nullable = true, unique = true)
    private String githubUserId;

    @Column(nullable = true)
    private String githubUsername;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(unique = true)
    private String stripeCustomerId;

    @Column(nullable = false)
    private boolean premiumUser = false;

    @Column
    private String stripeSubscriptionId;

    @Column
    private String stripeSubscriptionStatus;

    @Column
    private boolean stripeSubscriptionCancelAtPeriodEnd;

    @Column
    private Instant stripeSubscriptionCancelAt;

    @Column
    private Instant stripeSubscriptionCurrentPeriodEnd;

    public User() {}

    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.emailVerified = false;
    }

    public static User fromSteamAccount(String steam64Id) {
        User user = new User();
        user.setUsername(steam64Id);
        user.setPassword(null);
        user.setEmail(null);
        user.setEmailVerified(true);
        user.setSteam64Id(steam64Id);
        return user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSteam64Id() { return steam64Id; }
    public void setSteam64Id(String steam64Id) { this.steam64Id = steam64Id; }
    public String getDiscordUserId() { return discordUserId; }
    public void setDiscordUserId(String discordUserId) { this.discordUserId = discordUserId; }
    public String getDiscordUsername() { return discordUsername; }
    public void setDiscordUsername(String discordUsername) { this.discordUsername = discordUsername; }
    public String getGithubUserId() { return githubUserId; }
    public void setGithubUserId(String githubUserId) { this.githubUserId = githubUserId; }
    public String getGithubUsername() { return githubUsername; }
    public void setGithubUsername(String githubUsername) { this.githubUsername = githubUsername; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }
    public boolean isPremiumUser() { return premiumUser; }
    public void setPremiumUser(boolean premiumUser) { this.premiumUser = premiumUser; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }
    public String getStripeSubscriptionStatus() { return stripeSubscriptionStatus; }
    public void setStripeSubscriptionStatus(String stripeSubscriptionStatus) { this.stripeSubscriptionStatus = stripeSubscriptionStatus; }
    public boolean isStripeSubscriptionCancelAtPeriodEnd() { return stripeSubscriptionCancelAtPeriodEnd; }
    public void setStripeSubscriptionCancelAtPeriodEnd(boolean stripeSubscriptionCancelAtPeriodEnd) { this.stripeSubscriptionCancelAtPeriodEnd = stripeSubscriptionCancelAtPeriodEnd; }
    public Instant getStripeSubscriptionCancelAt() { return stripeSubscriptionCancelAt; }
    public void setStripeSubscriptionCancelAt(Instant stripeSubscriptionCancelAt) { this.stripeSubscriptionCancelAt = stripeSubscriptionCancelAt; }
    public Instant getStripeSubscriptionCurrentPeriodEnd() { return stripeSubscriptionCurrentPeriodEnd; }
    public void setStripeSubscriptionCurrentPeriodEnd(Instant stripeSubscriptionCurrentPeriodEnd) { this.stripeSubscriptionCurrentPeriodEnd = stripeSubscriptionCurrentPeriodEnd; }
}
