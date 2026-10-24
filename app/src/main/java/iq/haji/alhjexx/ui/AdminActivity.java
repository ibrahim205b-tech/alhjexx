package iq.haji.alhjexx.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import iq.haji.alhjexx.R;
import iq.haji.alhjexx.data.Audit;
import iq.haji.alhjexx.security.Session;

/** لوحة الإدارة. الأدوات الإدارية تتطلب مديراً أو موافقة مدير برمز PIN. */
public class AdminActivity extends PageActivity {

    private GridLayout grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ready()) {
            return;
        }
        setPageTitle("الإدارة");
        grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        ScrollView sv = new ScrollView(this);
        sv.addView(grid);
        content.addView(sv);

        tile("⏱", "الوردية", "فتح وإغلاق الوردية، المصروفات وتسويات الصندوق", false, ShiftActivity.class);
        tile("🧾", "الطلبات السابقة", "بحث، إعادة طباعة، وإلغاء طلب معتمد", false, OrdersActivity.class);
        tile("🍽", "المنيو", "الأقسام والأصناف والأحجام والأسعار", true, MenuAdminActivity.class);
        tile("📊", "التقارير", "اليومي، الورديات، الأقسام، الأصناف، الملغاة، المقارنات", true, ReportsActivity.class);
        tile("👥", "المستخدمون", "الكاشير والمدير ورموز الدخول", true, UsersActivity.class);
        tile("⚙", "الإعدادات", "المطعم، الطابعة، الدفع، الخصومات، النسخ الاحتياطي", true, SettingsActivity.class);
        tile("📜", "سجل العمليات", "كل العمليات الإدارية والحساسة", true, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // موافقة المدير المؤقتة صالحة لفتح شاشة واحدة فقط.
        Session.dropElevation();
    }

    private void tile(String icon, String title, String sub, final boolean managerOnly, final Class<?> target) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackgroundResource(R.drawable.product_item);
        int p = Ui.dp(this, 16);
        t.setPadding(p, p, p, p);
        t.setClickable(true);
        t.setFocusable(true);

        TextView i = Ui.text(this, icon, 26, R.color.white, false);
        TextView h = Ui.text(this, title, 17, R.color.white, true);
        TextView s = Ui.text(this, sub, 12, R.color.text_3, false);
        h.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        t.addView(i);
        t.addView(h);
        t.addView(s);
        if (managerOnly) {
            TextView lock = Ui.text(this, "🔒 مدير", 11, R.color.warning, true);
            lock.setTypeface(Typeface.DEFAULT_BOLD);
            lock.setGravity(Gravity.START);
            lock.setPadding(0, Ui.dp(this, 6), 0, 0);
            t.addView(lock);
        }

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = Ui.dp(this, 150);
        int m = Ui.dp(this, 6);
        lp.setMargins(m, m, m, m);
        grid.addView(t, lp);

        t.setOnClickListener(v -> {
            Runnable go = () -> {
                if (target == null) {
                    showAudit();
                } else {
                    startActivity(new Intent(this, target));
                }
            };
            if (!managerOnly || Session.isManager()) {
                go.run();
            } else {
                Auth.requireManager(this, title, mgr -> {
                    Session.elevate(mgr);
                    go.run();
                });
            }
        });
    }

    private void showAudit() {
        List<String> rows = Audit.recent(this, 300);
        StringBuilder sb = new StringBuilder();
        for (String r : rows) {
            sb.append(r).append("\n\n");
        }
        TextView tv = Ui.text(this, rows.isEmpty() ? "لا توجد عمليات مسجلة" : sb.toString(), 13, R.color.text, false);
        int p = Ui.dp(this, 20);
        tv.setPadding(p, p, p, p);
        tv.setTextIsSelectable(true);
        Ui.show(Ui.dialog(this, "سجل العمليات (آخر 300)").setView(Ui.scroll(this, tv))
                .setPositiveButton(R.string.close, null));
    }
}
