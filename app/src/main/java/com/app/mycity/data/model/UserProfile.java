package com.app.mycity.data.model;

import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Date;

@IgnoreExtraProperties
public class UserProfile {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";
    public static final String BLOCK_NONE = "none";
    public static final String BLOCK_TEMPORARY = "temporary";
    public static final String BLOCK_PERMANENT = "permanent";

    private String uid;

    private String displayName;
    private String email;
    private String phone;
    private String avatarUrl;
    private int issueCount;
    private Date createdAt;
    private String role;
    private String blockedType;
    private String blockedReason;
    private Date blockedUntil;
    private Date blockedAt;

    public UserProfile() { }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isAdmin() { return ROLE_ADMIN.equals(role); }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public int getIssueCount() { return issueCount; }
    public void setIssueCount(int issueCount) { this.issueCount = issueCount; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getBlockedType() { return blockedType; }
    public void setBlockedType(String blockedType) { this.blockedType = blockedType; }

    public String getBlockedReason() { return blockedReason; }
    public void setBlockedReason(String blockedReason) { this.blockedReason = blockedReason; }

    public Date getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(Date blockedUntil) { this.blockedUntil = blockedUntil; }

    public Date getBlockedAt() { return blockedAt; }
    public void setBlockedAt(Date blockedAt) { this.blockedAt = blockedAt; }

    public boolean isPermanentlyBlocked() {
        return BLOCK_PERMANENT.equals(blockedType);
    }

    public boolean isTemporarilyBlocked() {
        if (!BLOCK_TEMPORARY.equals(blockedType)) return false;
        return blockedUntil != null && blockedUntil.after(new Date());
    }

    public boolean isBlockedNow() {
        return isPermanentlyBlocked() || isTemporarilyBlocked();
    }
}
