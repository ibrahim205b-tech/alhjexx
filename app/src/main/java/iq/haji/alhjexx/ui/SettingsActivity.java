package iq.haji.alhjexx.ui;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.List;

import iq.haji.alhjexx.R;
import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.core.Dates;
import iq.haji.alhjexx.data.Audit;
import iq.haji.alhjexx.data.BackupManager;
import iq.haji.alhjexx.data.Settings;
import iq.haji.alhjexx.print.BtPrinter;
import iq.haji.alhjexx.print.Printing;
import iq.haji.alhjexx.print.Templates;
import iq.haji.alhjexx.security.Session;

/** كل الإعدادات في مكان واحد: المطعم، الطابعة، الطاولات، الدفع، الخصومات، المصروفات، المستخدمون، النسخ الاحتياطي. */
public class SettingsActivity extends PageActivity implements BtPrinter.Listener {

    public static final String EXTRA_PRINTER = "printer";

    private static final int REQ_BT = 41;
    private static final int REQ_BACKUP = 42;
    private static final int REQ_RESTORE = 43;

    private Settings settings;
    private LinearLayout col;
    private TextView tvPrinter;
    private LinearLayout printerCard;

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
        setPageTitle("الإعدادات");
        settings = new Settings(this);
        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        content.addView(sv);

        buildRestaurant();
        buildPrinter();
        buildOrders();
        buildUsers();
        buildBackup();

        if (getIntent().getBooleanExtra(EXTRA_PRINTER, false)) {
            sv.post(() -> sv.smoothScrollTo(0, printerCard.getTop()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BtPrinter.get(this).addListener(this);
        refreshPrinter();
    }

    @Override
    protected void onPause() {
        BtPrinter.get(this).removeListener(this);
        super.onPause();
    }

    @Override
    public void onPrinterState(BtPrinter.State state, String message) {
        refreshPrinter();
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.card);
        int p = Ui.dp(this, 16);
        c.setPadding(p, p, p, p);
        c.addView(Ui.text(this, title, 16, R.color.white, true));
        col.addView(c, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 10));
        return c;
    }

    private TextView button(LinearLayout parent, String text, boolean primary, Runnable r) {
        TextView b = Ui.button(this, text, primary);
        b.setOnClickListener(v -> r.run());
        parent.addView(b, Ui.lp(this, LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44), 10));
        return b;
    }

    private CheckBox toggle(LinearLayout parent, String text, final String key, boolean def) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextColor(getColor(R.color.text));
        cb.setChecked(settings.getBool(key, def));
        cb.setOnCheckedChangeListener((b, checked) -> {
            settings.setBool(key, checked);
            Audit.log(this, Session.actor(), "تعديل إعداد", text + " = " + (checked ? "مفعّل" : "معطّل"));
        });
        parent.addView(cb, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 6));
        return cb;
    }

    // ---------------- المطعم والفاتورة ----------------

    private void buildRestaurant() {
        LinearLayout c = card("المطعم والفاتورة");
        final EditText name = Ui.input(this, "اسم المطعم", InputType.TYPE_CLASS_TEXT, settings.restaurantName());
        final EditText phone = Ui.input(this, "رقم هاتف المطعم", InputType.TYPE_CLASS_PHONE,
                settings.get(Settings.RESTAURANT_PHONE, ""));
        final EditText footer = Ui.input(this, "الرسالة الختامية في الفاتورة", InputType.TYPE_CLASS_TEXT,
                settings.get(Settings.RECEIPT_FOOTER, Settings.DEFAULT_FOOTER));
        final EditText copies = Ui.money(this, "عدد نسخ الفاتورة (1-3)", String.valueOf(settings.getInt(Settings.RECEIPT_COPIES, 1)));
        c.addView(Ui.label(this, "اسم المطعم"));
        c.addView(name);
        c.addView(Ui.label(this, "رقم الهاتف"));
        c.addView(phone);
        c.addView(Ui.label(this, "الرسالة الختامية"));
        c.addView(footer);
        c.addView(Ui.label(this, "عدد نسخ الفاتورة"));
        c.addView(copies);
        toggle(c, "طباعة الفاتورة تلقائياً بعد تأكيد الطلب", Settings.AUTO_PRINT, true);
        button(c, "حفظ بيانات المطعم", true, () -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("أدخل اسم المطعم");
                return;
            }
            int cp = (int) Math.max(1, Math.min(3, iq.haji.alhjexx.core.Money.parse(copies.getText().toString())));
            settings.set(Settings.RESTAURANT_NAME, n);
            settings.set(Settings.RESTAURANT_PHONE, phone.getText().toString().trim());
            settings.set(Settings.RECEIPT_FOOTER, footer.getText().toString().trim());
            settings.setInt(Settings.RECEIPT_COPIES, cp);
            copies.setText(String.valueOf(cp));
            Audit.log(this, Session.actor(), "تعديل إعدادات المطعم", n);
            Ui.hideKeyboard(this);
            Ui.toast(this, "تم الحفظ");
        });
    }

    // ---------------- الطابعة ----------------

    private void buildPrinter() {
        printerCard = card("الطابعة الحرارية (Bluetooth — ورق 58mm)");
        tvPrinter = Ui.text(this, "", 14, R.color.text_2, false);
        printerCard.addView(tvPrinter, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 6));
        printerCard.addView(Ui.text(this, "اقرن الطابعة أولاً من إعدادات Bluetooth في الجهاز، ثم اخترها هنا. "
                + "جرّب «اختبار الطباعة» للتأكد من توافق الطابعة ووضوح النص العربي.", 12, R.color.text_3, false));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        printerCard.addView(row);
        TextView pick = Ui.button(this, "اختيار الطابعة", true);
        pick.setOnClickListener(v -> pickPrinter());
        row.addView(pick, rowLp(false));
        TextView test = Ui.button(this, "اختبار الطباعة", false);
        test.setOnClickListener(v -> testPrint());
        row.addView(test, rowLp(true));
        toggle(printerCard, "قطع الوصل تلقائياً بعد الطباعة (للطابعات التي فيها قاطع)", Settings.PRINT_CUT, true);
    }

    private LinearLayout.LayoutParams rowLp(boolean gap) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
        p.topMargin = Ui.dp(this, 10);
        if (gap) {
            p.setMarginStart(Ui.dp(this, 8));
        }
        return p;
    }

    private void refreshPrinter() {
        if (tvPrinter == null) {
            return;
        }
        BtPrinter bt = BtPrinter.get(this);
        String name = bt.selectedName();
        String msg = bt.stateMessage();
        tvPrinter.setText((name.isEmpty() ? "لم تُحدد طابعة" : "الطابعة المختارة: " + name) + "\n"
                + BtPrinter.stateLabel(bt.state()) + (msg.isEmpty() ? "" : "\n" + msg));
    }

    private void pickPrinter() {
        BtPrinter bt = BtPrinter.get(this);
        if (!bt.isBluetoothAvailable()) {
            Ui.error(this, "هذا الجهاز لا يدعم Bluetooth");
            return;
        }
        if (!BtPrinter.hasPermission(this)) {
            Ui.confirm(this, "إذن الأجهزة القريبة",
                    "يحتاج التطبيق إذن «الأجهزة القريبة» للاتصال بطابعة Bluetooth المقترنة فقط. "
                            + "لا يُستخدم لتحديد موقعك ولا يصل لأي بيانات أخرى.",
                    "متابعة", () -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
                        }
                    });
            return;
        }
        if (!bt.isBluetoothOn()) {
            Ui.error(this, "Bluetooth مطفأ. شغّله من إعدادات الجهاز ثم أعد المحاولة.");
            return;
        }
        final List<BtPrinter.Device> devices = bt.pairedDevices();
        if (devices.isEmpty()) {
            Ui.error(this, "لا توجد أجهزة مقترنة. اقرن الطابعة من إعدادات Bluetooth في الجهاز أولاً.");
            return;
        }
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            names[i] = devices.get(i).name + "\n" + devices.get(i).address;
        }
        Ui.show(Ui.dialog(this, "اختر الطابعة").setItems(names, (d, which) -> {
            bt.select(devices.get(which));
            Audit.log(this, Session.actor(), "اختيار طابعة", devices.get(which).name);
            refreshPrinter();
        }).setNegativeButton(R.string.cancel, null));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_BT) {
            if (BtPrinter.hasPermission(this)) {
                pickPrinter();
            } else {
                Ui.error(this, "لم يُمنح الإذن. يمكنك منحه لاحقاً من إعدادات التطبيق في الجهاز.");
            }
        }
    }

    private void testPrint() {
        BtPrinter bt = BtPrinter.get(this);
        if (bt.selectedAddress().isEmpty()) {
            Ui.toast(this, "اختر الطابعة أولاً");
            return;
        }
        Printing.print(this, Templates.testPage(settings.restaurantName(), bt.selectedName()), 1, (ok, msg) -> {
            refreshPrinter();
            if (ok) {
                Ui.toast(this, "أُرسلت صفحة الاختبار. تأكد من وضوح النص والأرقام.");
            } else {
                Ui.show(Ui.dialog(this, "فشل الاختبار").setMessage(msg)
                        .setPositiveButton(R.string.retry, (d, w) -> testPrint())
                        .setNegativeButton(R.string.close, null));
            }
        });
    }

    // ---------------- الطلبات والدفع والخصومات ----------------

    private void buildOrders() {
        LinearLayout c = card("الطلبات والدفع والخصومات والمصروفات");
        toggle(c, "استخدام أرقام/أسماء الطاولات للطلبات داخل المطعم", Settings.TABLES_ENABLED, true);
        final EditText service = Ui.money(this, "مبلغ خدمة الصالة", String.valueOf(settings.serviceFee()));
        c.addView(Ui.label(this, "مبلغ خدمة الصالة (د.ع) — يُضاف عند الضغط على زر «خدمة صالة»"));
        c.addView(service);
        button(c, "حفظ مبلغ الخدمة", false, () -> {
            long v = iq.haji.alhjexx.core.Money.parse(service.getText().toString());
            if (v <= 0) {
                service.setError("أدخل مبلغاً صحيحاً");
                return;
            }
            settings.set(Settings.SERVICE_FEE, String.valueOf(v));
            Audit.log(this, Session.actor(), "تعديل إعداد", "خدمة الصالة = " + v);
            Ui.toast(this, "تم الحفظ");
        });
        toggle(c, "تفعيل الدفع بالبطاقة / الإلكتروني (تسجيل فقط، بدون جهاز دفع)", Settings.CARD_ENABLED, true);
        toggle(c, "تفعيل الدفع المختلط (نقد + إلكتروني)", Settings.MIXED_ENABLED, true);
        toggle(c, "تفعيل الخصومات", Settings.DISCOUNT_ENABLED, true);
        toggle(c, "السماح للكاشير بالخصم دون موافقة مدير (ضمن الحد أدناه)", Settings.CASHIER_DISCOUNT, false);
        final EditText max = Ui.money(this, "أقصى نسبة خصم للكاشير %", String.valueOf(settings.getInt(Settings.CASHIER_DISCOUNT_MAX_PERCENT, 10)));
        c.addView(Ui.label(this, "أقصى نسبة خصم للكاشير بدون موافقة (%)"));
        c.addView(max);
        button(c, "حفظ الحد", false, () -> {
            long v = iq.haji.alhjexx.core.Money.parse(max.getText().toString());
            if (v < 0 || v > 100) {
                max.setError("نسبة بين 0 و 100");
                return;
            }
            settings.setInt(Settings.CASHIER_DISCOUNT_MAX_PERCENT, (int) v);
            Audit.log(this, Session.actor(), "تعديل إعداد", "حد خصم الكاشير = " + v + "%");
            Ui.toast(this, "تم الحفظ");
        });
        toggle(c, "تفعيل تسجيل المصروفات النقدية في الوردية", Settings.EXPENSES_ENABLED, true);
        toggle(c, "طباعة تقرير الوردية تلقائياً عند إغلاقها", Settings.SHIFT_AUTO_PRINT, false);
    }

    // ---------------- المستخدمون ----------------

    private void buildUsers() {
        LinearLayout c = card("المستخدمون والصلاحيات");
        c.addView(Ui.text(this, "إضافة كاشير أو مدير، تغيير الرموز، تعطيل المستخدمين. "
                + "رموز PIN تُخزن مشفرة بتجزئة ولا تظهر في أي مكان.", 12, R.color.text_3, false));
        button(c, "إدارة المستخدمين ورموز PIN", true, () -> startActivity(new Intent(this, UsersActivity.class)));
        button(c, "تغيير رمز المدير الحالي", false, this::changeOwnPin);
    }

    private void changeOwnPin() {
        final iq.haji.alhjexx.model.User m = Session.manager();
        if (m == null) {
            return;
        }
        LinearLayout f = Ui.form(this);
        final EditText old = Ui.pin(this, "الرمز الحالي");
        final EditText pin = Ui.pin(this, "الرمز الجديد (4 - 8 أرقام)");
        final EditText pin2 = Ui.pin(this, "تأكيد الرمز الجديد");
        f.addView(old);
        f.addView(pin, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
        f.addView(pin2, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
        final iq.haji.alhjexx.data.UserRepo users = new iq.haji.alhjexx.data.UserRepo(this);
        Ui.showForm(this, "تغيير رمز " + m.name, f, getString(R.string.save), () -> {
            if (!users.verify(m.id, old.getText().toString())) {
                old.setError("الرمز الحالي غير صحيح");
                return false;
            }
            if (!pin.getText().toString().equals(pin2.getText().toString())) {
                pin2.setError("الرمزان غير متطابقين");
                return false;
            }
            try {
                users.changePin(m.id, pin.getText().toString(), Session.actor());
                Ui.toast(this, "تم تغيير الرمز");
                return true;
            } catch (AppException e) {
                pin.setError(e.getMessage());
                return false;
            }
        });
    }

    // ---------------- النسخ الاحتياطي ----------------

    private void buildBackup() {
        LinearLayout c = card("النسخ الاحتياطي والاستعادة");
        c.addView(Ui.text(this, "النسخة تشمل كل المبيعات والورديات والمنيو والمستخدمين والإعدادات، وتُحفظ في المكان الذي تختاره "
                + "(مثل ذاكرة فلاش أو مجلد التنزيلات). لا تُرفع إلى الإنترنت.\n"
                + "تنبيه: حذف بيانات التطبيق أو إلغاء تثبيته يحذف كل البيانات نهائياً إذا لم توجد نسخة احتياطية خارجية.",
                12, R.color.warning, false));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        c.addView(row);
        TextView backup = Ui.button(this, "إنشاء نسخة احتياطية", true);
        backup.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/octet-stream");
            i.putExtra(Intent.EXTRA_TITLE, "alhjexx-backup-" + Dates.date(System.currentTimeMillis()).replace('/', '-') + ".db");
            startActivityForResult(i, REQ_BACKUP);
        });
        row.addView(backup, rowLp(false));
        TextView restore = Ui.button(this, "استعادة من نسخة", false);
        restore.setOnClickListener(v -> Ui.confirm(this, "استعادة البيانات",
                "الاستعادة تستبدل كل البيانات الحالية (المبيعات، الورديات، المنيو، المستخدمين) بمحتوى النسخة المختارة. "
                        + "يُنصح بإنشاء نسخة احتياطية من البيانات الحالية أولاً.",
                "اختيار ملف النسخة", () -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_RESTORE);
                }));
        row.addView(restore, rowLp(true));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        BackupManager bm = new BackupManager(this);
        if (requestCode == REQ_BACKUP) {
            try {
                bm.backupTo(uri, Session.actor());
                Ui.show(Ui.dialog(this, "تم إنشاء النسخة الاحتياطية")
                        .setMessage("احفظ الملف في مكان آمن خارج الجهاز.")
                        .setPositiveButton(R.string.ok, null));
            } catch (IOException e) {
                Ui.error(this, "تعذر إنشاء النسخة الاحتياطية: " + e.getMessage());
            }
        } else if (requestCode == REQ_RESTORE) {
            try {
                final File staged = bm.stage(uri);
                final BackupManager.Check check = BackupManager.verify(staged);
                if (!check.valid) {
                    //noinspection ResultOfMethodCallIgnored
                    staged.delete();
                    Ui.error(this, check.message);
                    return;
                }
                Ui.confirm(this, "تأكيد الاستعادة",
                        "النسخة صالحة وتحتوي: " + check.orders + " طلب، " + check.shifts + " وردية، " + check.products + " صنف.\n"
                                + "سيتم استبدال كل البيانات الحالية. لا يمكن التراجع إلا من نسخة احتياطية أخرى.",
                        "استبدال البيانات", () -> doRestore(bm, staged));
            } catch (IOException e) {
                Ui.error(this, "تعذر قراءة الملف: " + e.getMessage());
            }
        }
    }

    private void doRestore(BackupManager bm, File staged) {
        try {
            bm.restore(staged);
            Session.logout();
            Ui.toast(this, "تمت الاستعادة. سجّل الدخول من جديد.");
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        } catch (AppException | IOException e) {
            Ui.error(this, "فشلت الاستعادة: " + e.getMessage());
        }
    }
}
