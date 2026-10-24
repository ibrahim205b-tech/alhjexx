package iq.haji.alhjexx.security;

/**
 * يحد من تخمين رموز PIN: بعد 5 محاولات خاطئة متتالية يُقفل الإدخال 60 ثانية،
 * وتتضاعف المدة مع تكرار الإقفال (حتى 15 دقيقة).
 */
public final class PinGuard {

    private static final int MAX_FAILS = 5;
    private static final long BASE_LOCK_MS = 60_000L;
    private static final long MAX_LOCK_MS = 15 * 60_000L;

    private static int fails;
    private static int lockouts;
    private static long lockedUntil;

    private PinGuard() {
    }

    /** الثواني المتبقية للإقفال، أو 0 إذا كان الإدخال مسموحاً. */
    public static synchronized long secondsLocked() {
        long left = lockedUntil - System.currentTimeMillis();
        return left > 0 ? (left + 999) / 1000 : 0;
    }

    public static synchronized void success() {
        fails = 0;
        lockouts = 0;
        lockedUntil = 0;
    }

    public static synchronized void failure() {
        fails++;
        if (fails >= MAX_FAILS) {
            long lock = Math.min(MAX_LOCK_MS, BASE_LOCK_MS << Math.min(lockouts, 4));
            lockedUntil = System.currentTimeMillis() + lock;
            lockouts++;
            fails = 0;
        }
    }

    public static String lockedMessage() {
        return "محاولات خاطئة كثيرة. حاول بعد " + secondsLocked() + " ثانية.";
    }
}
