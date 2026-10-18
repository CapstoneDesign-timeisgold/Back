package jiki.jiki.user;

import java.text.Normalizer;

public enum SystemAccountType {
    SETTLEMENT("admin");

    private final String reservedUsername;

    SystemAccountType(String reservedUsername) {
        this.reservedUsername = reservedUsername;
    }

    public String getReservedUsername() {
        return reservedUsername;
    }

    public static boolean isReservedUsername(String username) {
        if (username == null) return false;
        String normalized = Normalizer.normalize(username, Normalizer.Form.NFKC).strip();
        for (SystemAccountType type : values()) {
            if (type.reservedUsername.equalsIgnoreCase(normalized)) return true;
        }
        return false;
    }
}
