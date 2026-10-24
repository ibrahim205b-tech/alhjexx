package iq.haji.alhjexx.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import iq.haji.alhjexx.security.Session;

/** أساس كل الشاشات بعد تسجيل الدخول: اتجاه RTL، وإعادة التوجيه لشاشة الدخول إذا انتهت الجلسة. */
public abstract class BaseActivity extends Activity {

    /** الشاشات الإدارية تعيد true فتُغلق إذا لم يكن المستخدم مديراً. */
    protected boolean managerOnly() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (!Session.isLoggedIn()) {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        }
    }

    protected boolean sessionValid() {
        if (!Session.isLoggedIn() || isFinishing()) {
            return false;
        }
        if (managerOnly() && !Session.isManager()) {
            Ui.toast(this, "هذه الشاشة للمدير فقط");
            finish();
            return false;
        }
        return true;
    }
}
