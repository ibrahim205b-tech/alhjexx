package iq.haji.alhjexx.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import iq.haji.alhjexx.R;
import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.data.Settings;
import iq.haji.alhjexx.data.UserRepo;
import iq.haji.alhjexx.model.User;
import iq.haji.alhjexx.security.Session;

/**
 * شاشة البداية: عند أول تشغيل تُنشئ حساب المدير، وبعدها تسجيل الدخول باختيار المستخدم ورمزه.
 */
public class LoginActivity extends Activity {

    private UserRepo users;
    private TextView tvTitle, tvSubtitle;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        users = new UserRepo(this);
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        container = findViewById(R.id.container);
        ((TextView) findViewById(R.id.tvBrand)).setText(new Settings(this).restaurantName());
        findViewById(R.id.card).setAlpha(0f);
        findViewById(R.id.card).animate().alpha(1f).setDuration(260).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Session.isLoggedIn()) {
            openMain();
            return;
        }
        if (users.hasManager()) {
            showUsers();
        } else {
            showSetup();
        }
    }

    private void showSetup() {
        tvTitle.setText("إعداد أول تشغيل");
        tvSubtitle.setText("أنشئ حساب المدير. رمز المدير يحمي الأسعار والتقارير والإلغاءات.");
        container.removeAllViews();
        final EditText restaurant = Ui.input(this, "اسم المطعم", InputType.TYPE_CLASS_TEXT,
                new Settings(this).restaurantName());
        final EditText name = Ui.input(this, "اسم المدير", InputType.TYPE_CLASS_TEXT, "");
        final EditText pin = Ui.pin(this, "رمز المدير (4 - 8 أرقام)");
        final EditText pin2 = Ui.pin(this, "أعد كتابة الرمز");
        container.addView(Ui.label(this, "اسم المطعم"));
        container.addView(restaurant);
        container.addView(Ui.label(this, "اسم المدير"));
        container.addView(name);
        container.addView(Ui.label(this, "رمز PIN"));
        container.addView(pin);
        container.addView(pin2, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
        TextView save = Ui.button(this, "إنشاء الحساب والمتابعة", true);
        container.addView(save, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 48), 18));
        save.setOnClickListener(v -> {
            if (!pin.getText().toString().equals(pin2.getText().toString())) {
                pin2.setError("الرمزان غير متطابقين");
                return;
            }
            try {
                String r = restaurant.getText().toString().trim();
                if (!r.isEmpty()) {
                    new Settings(this).set(Settings.RESTAURANT_NAME, r);
                }
                long id = users.create(name.getText().toString(), User.MANAGER, pin.getText().toString(), "إعداد أول تشغيل");
                Session.login(users.get(id));
                Ui.hideKeyboard(this);
                openMain();
            } catch (AppException e) {
                Ui.error(this, e.getMessage());
            }
        });
    }

    private void showUsers() {
        tvTitle.setText("تسجيل الدخول");
        tvSubtitle.setText("اختر اسمك ثم أدخل رمزك");
        container.removeAllViews();
        List<User> list = users.list(true);
        LayoutInflater inf = getLayoutInflater();
        for (final User u : list) {
            TextView t = (TextView) inf.inflate(R.layout.item_user, container, false);
            t.setText(u.name + "   •   " + User.roleLabel(u.role));
            t.setOnClickListener(v -> askPin(u));
            container.addView(t);
        }
    }

    private void askPin(final User u) {
        LinearLayout f = Ui.form(this);
        final EditText pin = Ui.pin(this, "الرمز");
        f.addView(pin);
        Ui.showForm(this, u.name, f, "دخول", () -> {
            if (!users.verify(u.id, pin.getText().toString())) {
                pin.setError("الرمز غير صحيح");
                pin.setText("");
                return false;
            }
            Session.login(users.get(u.id));
            openMain();
            return true;
        });
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
