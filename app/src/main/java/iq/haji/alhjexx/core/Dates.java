package iq.haji.alhjexx.core;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** تنسيق التواريخ والأوقات بأرقام لاتينية، وحساب حدود الأيام والأشهر. */
public final class Dates {

    private Dates() {
    }

    public static String dateTime(long ms) {
        return new SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US).format(new Date(ms));
    }

    public static String date(long ms) {
        return new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date(ms));
    }

    public static String time(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ms));
    }

    public static String month(long ms) {
        return new SimpleDateFormat("yyyy/MM", Locale.US).format(new Date(ms));
    }

    /** بادئة رقم الفاتورة: yyMMdd */
    public static String invoicePrefix(long ms) {
        return new SimpleDateFormat("yyMMdd", Locale.US).format(new Date(ms));
    }

    public static long startOfDay(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** بداية اليوم التالي (حد حصري). */
    public static long endOfDay(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfDay(ms));
        c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    public static long startOfMonth(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfDay(ms));
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    /** بداية الشهر التالي (حد حصري). */
    public static long endOfMonth(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfMonth(ms));
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis();
    }
}
