package iq.haji.alhjexx.ui;

import android.app.DatePickerDialog;
import android.content.Context;

import java.util.Calendar;

/** اختيار التاريخ بنافذة النظام. */
final class Pickers {

    interface OnDate {
        void picked(long dayStartMs);
    }

    private Pickers() {
    }

    static void date(Context c, long initial, OnDate cb) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(initial > 0 ? initial : System.currentTimeMillis());
        DatePickerDialog d = new DatePickerDialog(c, (view, y, m, day) -> {
            Calendar r = Calendar.getInstance();
            r.clear();
            r.set(y, m, day, 0, 0, 0);
            cb.picked(r.getTimeInMillis());
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        d.show();
    }
}
