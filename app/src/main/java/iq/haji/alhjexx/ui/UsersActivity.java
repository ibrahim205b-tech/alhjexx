package iq.haji.alhjexx.ui;

import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;

import iq.haji.alhjexx.R;
import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.data.UserRepo;
import iq.haji.alhjexx.model.User;
import iq.haji.alhjexx.security.Session;

/** إدارة المستخدمين: مدير وكاشير، رموز PIN مخزنة بتجزئة، تعطيل بدل الحذف. */
public class UsersActivity extends PageActivity {

    private UserRepo users;
    private RowAdapter adapter;

    @Override
    protected boolean managerOnly() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ready()) {
            return;
        }
        setPageTitle("المستخدمون والصلاحيات");
        users = new UserRepo(this);
        addAction("＋ مستخدم", true, this::addUser);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        content.addView(col);
        col.addView(Ui.text(this, "الكاشير: إنشاء الطلبات وتأكيدها وطباعتها وفتح الوردية. "
                + "المدير: المنيو والأسعار والتقارير وإغلاق الورديات والإلغاءات والمستخدمون والإعدادات.",
                13, R.color.text_2, false));
        ListView lv = new ListView(this);
        lv.setBackgroundResource(R.drawable.card);
        adapter = new RowAdapter(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((a, v, pos, id) -> onUser((User) adapter.row(pos).tag));
        col.addView(lv, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, 0, 10));
        ((LinearLayout.LayoutParams) lv.getLayoutParams()).weight = 1f;
        reload();
    }

    private void reload() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (User u : users.list(false)) {
            RowAdapter.Row r = new RowAdapter.Row(u.name, User.roleLabel(u.role), "", u);
            if (!u.active) {
                r.badge("معطّل", R.color.warning);
            } else if (u.isManager()) {
                r.badge("مدير", R.color.success);
            }
            rows.add(r);
        }
        adapter.set(rows);
    }

    private void addUser() {
        LinearLayout f = Ui.form(this);
        final EditText name = Ui.input(this, "الاسم", InputType.TYPE_CLASS_TEXT, "");
        f.addView(Ui.label(this, "الاسم"));
        f.addView(name);
        f.addView(Ui.label(this, "الدور"));
        final Spinner role = new Spinner(this);
        role.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"كاشير", "مدير"}));
        f.addView(role);
        f.addView(Ui.label(this, "رمز PIN (4 - 8 أرقام)"));
        final EditText pin = Ui.pin(this, "الرمز");
        f.addView(pin);
        final EditText pin2 = Ui.pin(this, "تأكيد الرمز");
        f.addView(pin2, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 6));
        Ui.showForm(this, "مستخدم جديد", Ui.scroll(this, f), getString(R.string.save), () -> {
            if (!pin.getText().toString().equals(pin2.getText().toString())) {
                pin2.setError("الرمزان غير متطابقين");
                return false;
            }
            try {
                users.create(name.getText().toString(), role.getSelectedItemPosition() == 1 ? User.MANAGER : User.CASHIER,
                        pin.getText().toString(), Session.actor());
                reload();
                return true;
            } catch (AppException e) {
                Ui.error(this, e.getMessage());
                return false;
            }
        });
    }

    private void onUser(final User u) {
        String[] items = {"تعديل الاسم", "تغيير الرمز", u.active ? "تعطيل المستخدم" : "تفعيل المستخدم"};
        Ui.show(Ui.dialog(this, u.name).setItems(items, (d, which) -> {
            if (which == 0) {
                LinearLayout f = Ui.form(this);
                final EditText name = Ui.input(this, "الاسم", InputType.TYPE_CLASS_TEXT, u.name);
                f.addView(name);
                Ui.showForm(this, "تعديل الاسم", f, getString(R.string.save), () -> {
                    try {
                        users.rename(u.id, name.getText().toString(), Session.actor());
                        reload();
                        return true;
                    } catch (AppException e) {
                        name.setError(e.getMessage());
                        return false;
                    }
                });
            } else if (which == 1) {
                LinearLayout f = Ui.form(this);
                final EditText pin = Ui.pin(this, "الرمز الجديد");
                final EditText pin2 = Ui.pin(this, "تأكيد الرمز");
                f.addView(pin);
                f.addView(pin2, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 6));
                Ui.showForm(this, "تغيير رمز " + u.name, f, getString(R.string.save), () -> {
                    if (!pin.getText().toString().equals(pin2.getText().toString())) {
                        pin2.setError("الرمزان غير متطابقين");
                        return false;
                    }
                    try {
                        users.changePin(u.id, pin.getText().toString(), Session.actor());
                        Ui.toast(this, "تم تغيير الرمز");
                        return true;
                    } catch (AppException e) {
                        pin.setError(e.getMessage());
                        return false;
                    }
                });
            } else {
                if (Session.user() != null && Session.user().id == u.id && u.active) {
                    Ui.error(this, "لا يمكنك تعطيل حسابك الحالي");
                    return;
                }
                try {
                    users.setActive(u.id, !u.active, Session.actor());
                    reload();
                } catch (AppException e) {
                    Ui.error(this, e.getMessage());
                }
            }
        }).setNegativeButton(R.string.close, null));
    }
}
