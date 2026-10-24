package iq.haji.alhjexx.security;

import iq.haji.alhjexx.model.User;

/**
 * المستخدم المسجّل دخوله حالياً، ومدير مؤقت عند موافقة مدير على فتح شاشة إدارية
 * أثناء جلسة كاشير. الموافقة المؤقتة تُلغى عند العودة إلى شاشة الكاشير.
 */
public final class Session {

    private static volatile User current;
    private static volatile User acting;

    private Session() {
    }

    public static User user() {
        return current;
    }

    public static void login(User u) {
        current = u;
        acting = null;
    }

    public static void logout() {
        current = null;
        acting = null;
    }

    public static boolean isLoggedIn() {
        return current != null;
    }

    /** مدير مسجّل، أو كاشير بموافقة مدير سارية. */
    public static boolean isManager() {
        User u = current;
        return (u != null && u.isManager()) || acting != null;
    }

    /** المدير الذي تُنسب له العمليات الإدارية الحالية. */
    public static User manager() {
        User u = current;
        if (u != null && u.isManager()) {
            return u;
        }
        return acting;
    }

    public static void elevate(User manager) {
        if (manager != null && manager.isManager()) {
            acting = manager;
        }
    }

    public static void dropElevation() {
        acting = null;
    }

    /** الاسم المسجّل في السجل للعملية: المدير المؤقت إن وُجد، وإلا المستخدم الحالي. */
    public static String actor() {
        User a = acting;
        if (a != null) {
            User u = current;
            return a.name + (u != null && u.id != a.id ? " (لجلسة " + u.name + ")" : "");
        }
        User u = current;
        return u == null ? "-" : u.name;
    }

    public static String name() {
        User u = current;
        return u == null ? "-" : u.name;
    }
}
