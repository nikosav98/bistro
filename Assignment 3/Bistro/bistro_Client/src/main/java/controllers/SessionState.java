package controllers;

public class SessionState {
    private boolean guestSession;
    private String guestContact;
    private String currentUserId;
    private String currentUsername;
    private String lastLoginUsername;
    private String currentEmail;
    private String currentPhone;

    public void reset() {
        guestSession = false;
        guestContact = null;
        currentUserId = null;
        currentUsername = null;
        lastLoginUsername = null;
        currentEmail = null;
        currentPhone = null;
    }

    public String resolveUsername() {
        if (currentUsername != null && !currentUsername.isBlank()) {
            return currentUsername.trim();
        }
        if (lastLoginUsername != null && !lastLoginUsername.isBlank()) {
            return lastLoginUsername.trim();
        }
        return null;
    }

    public boolean isGuestSession() {
        return guestSession;
    }

    public void setGuestSession(boolean guestSession) {
        this.guestSession = guestSession;
    }

    public String getGuestContact() {
        return guestContact;
    }

    public void setGuestContact(String guestContact) {
        this.guestContact = guestContact;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public void setCurrentUsername(String currentUsername) {
        this.currentUsername = currentUsername;
    }

    public String getLastLoginUsername() {
        return lastLoginUsername;
    }

    public void setLastLoginUsername(String lastLoginUsername) {
        this.lastLoginUsername = lastLoginUsername;
    }

    public String getCurrentEmail() {
        return currentEmail;
    }

    public void setCurrentEmail(String currentEmail) {
        this.currentEmail = currentEmail;
    }

    public String getCurrentPhone() {
        return currentPhone;
    }

    public void setCurrentPhone(String currentPhone) {
        this.currentPhone = currentPhone;
    }
}