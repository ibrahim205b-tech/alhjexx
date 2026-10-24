package iq.haji.alhjexx.ui;

import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;

import iq.haji.alhjexx.data.UserRepo;
import iq.haji.alhjexx.model.User;
import iq.haji.alhjexx.security.Session;

/** طلب موافقة مدير للعمليات الحساسة. المدير المسجّل دخوله لا يُطلب منه الرمز مجدداً. */
public final class Auth {

    public interface OnManager {
        void granted(User manager);
    }

    private Auth() {
    }

    public static void requireManager(Context c, String action, OnManager onOk) {
        User u = Session.user();
        if (u != null && u.isManager()) {
            onOk.granted(u);
            return;
        }
        LinearLayout f = Ui.form(c);
        f.addView(Ui.text(c, "هذه العملية تتطلب موافقة مدير:\n" + action, 14,
                iq.haji.alhjexx.R.color.text_2, false));
        final EditText pin = Ui.pin(c, "رمز المدير");
        f.addView(pin, Ui.lp(c, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 12));
        Ui.showForm(c, "موافقة المدير", f, "تأكيد", () -> {
            User m = new UserRepo(c).findManagerByPin(pin.getText().toString());
            if (m == null) {
                // رسالة عامة لا تكشف أي معلومات عن الرموز أو المستخدمين.
                pin.setError("الرمز غير صحيح");
                pin.setText("");
                return false;
            }
            onOk.granted(m);
            return true;
        });
    }
}
