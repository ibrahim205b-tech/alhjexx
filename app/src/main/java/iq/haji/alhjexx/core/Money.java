package iq.haji.alhjexx.core;

import java.util.Locale;

/**
 * المبالغ بالدينار العراقي تُخزَّن وتُحسب كأعداد صحيحة (long) دون كسور،
 * لتفادي أخطاء الأعداد العشرية العائمة.
 */
public final class Money {

    /** أقصى مبلغ مقبول في أي حقل مالي (مليار دينار). */
    public static final long MAX = 1_000_000_000L;

    private Money() {
    }

    /** 12500 → "12,500 د.ع" */
    public static String format(long amount) {
        return number(amount) + " د.ع";
    }

    /** 12500 → "12,500" */
    public static String number(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    /** يحوّل الأرقام العربية والفارسية إلى لاتينية ويحذف الفواصل والمسافات. */
    public static String normalizeDigits(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '٠' && c <= '٩') {
                sb.append((char) ('0' + (c - '٠')));
            } else if (c >= '۰' && c <= '۹') {
                sb.append((char) ('0' + (c - '۰')));
            } else if (c == ',' || c == '٬' || c == '،' || c == ' ') {
                // فواصل الآلاف تُتجاهل
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /**
     * يحلل مبلغاً صحيحاً غير سالب.
     * @return المبلغ، أو -1 إذا كان النص فارغاً أو غير صالح أو أكبر من الحد.
     */
    public static long parse(String s) {
        String n = normalizeDigits(s);
        if (n.isEmpty() || n.length() > 12) {
            return -1;
        }
        for (int i = 0; i < n.length(); i++) {
            if (n.charAt(i) < '0' || n.charAt(i) > '9') {
                return -1;
            }
        }
        long v = Long.parseLong(n);
        return v > MAX ? -1 : v;
    }
}
