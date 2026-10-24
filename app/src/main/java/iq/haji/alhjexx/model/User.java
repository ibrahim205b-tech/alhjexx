package iq.haji.alhjexx.model;

public class User {
    public static final String MANAGER = "MANAGER";
    public static final String CASHIER = "CASHIER";

    public long id;
    public String name;
    public String role;
    public boolean active = true;

    public boolean isManager() {
        return MANAGER.equals(role);
    }

    public static String roleLabel(String role) {
        return MANAGER.equals(role) ? "مدير" : "كاشير";
    }

    @Override
    public String toString() {
        return name + " (" + roleLabel(role) + ")";
    }
}
