package iq.haji.alhjexx.ui;

import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import iq.haji.alhjexx.R;
import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.core.CashDrawer;
import iq.haji.alhjexx.core.Dates;
import iq.haji.alhjexx.core.Money;
import iq.haji.alhjexx.data.CashRepo;
import iq.haji.alhjexx.data.ReportRepo;
import iq.haji.alhjexx.data.Settings;
import iq.haji.alhjexx.data.ShiftRepo;
import iq.haji.alhjexx.model.CashAdjustment;
import iq.haji.alhjexx.model.Shift;
import iq.haji.alhjexx.print.PrintDoc;
import iq.haji.alhjexx.print.Printing;
import iq.haji.alhjexx.print.Templates;
import iq.haji.alhjexx.security.Session;

/** الوردية الحالية: فتح، مصروفات، تسويات، تقرير، إغلاق. وقائمة الورديات السابقة. */
public class ShiftActivity extends PageActivity {

    private ShiftRepo shifts;
    private CashRepo cash;
    private ReportRepo reports;
    private Settings settings;
    private DocPreview preview;
    private RowAdapter history;
    private LinearLayout panel;
    private Shift shown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ready()) {
            return;
        }
        setPageTitle("الوردية");
        shifts = new ShiftRepo(this);
        cash = new CashRepo(this);
        reports = new ReportRepo(this);
        settings = new Settings(this);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(row);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.card);
        int p = Ui.dp(this, 14);
        panel.setPadding(p, p, p, p);
        row.addView(panel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4f));

        preview = new DocPreview(this);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 5f);
        pl.setMarginStart(Ui.dp(this, 12));
        row.addView(preview, pl);
        render();
    }

    private void render() {
        panel.removeAllViews();
        Shift open = shifts.openShift();
        if (open == null) {
            renderClosed();
        } else {
            renderOpen(open);
        }
        panel.addView(Ui.text(this, "الورديات السابقة (اضغط للعرض)", 13, R.color.text_2, true),
                Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 16));
        ListView lv = new ListView(this);
        history = new RowAdapter(this);
        lv.setAdapter(history);
        lv.setOnItemClickListener((a, v, pos, id) -> showShift((Shift) history.row(pos).tag));
        panel.addView(lv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Shift s : shifts.recent(60)) {
            RowAdapter.Row r = new RowAdapter.Row("وردية #" + s.shiftNo + " — " + s.cashierName,
                    Dates.dateTime(s.openedAt) + (s.isOpen() ? "" : "  ←  " + Dates.dateTime(s.closedAt)),
                    s.isOpen() ? "" : Money.format(s.expectedCash), s);
            r.badge(s.isOpen() ? "مفتوحة" : "مغلقة", s.isOpen() ? R.color.success : R.color.text_3);
            rows.add(r);
        }
        history.set(rows);
    }

    private void renderClosed() {
        panel.addView(Ui.text(this, "لا توجد وردية مفتوحة", 18, R.color.warning, true));
        panel.addView(Ui.text(this, "الكاشير: " + Session.name(), 13, R.color.text_2, false));
        panel.addView(Ui.label(this, "النقدية الافتتاحية في الصندوق (د.ع)"));
        final EditText opening = Ui.money(this, "مثال: 50000", "");
        panel.addView(opening);
        TextView btn = Ui.button(this, "فتح وردية جديدة", true);
        panel.addView(btn, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 48), 12));
        btn.setOnClickListener(v -> {
            String raw = opening.getText().toString().trim();
            long amount = raw.isEmpty() ? 0 : Money.parse(raw);
            if (amount < 0) {
                opening.setError("مبلغ غير صحيح");
                return;
            }
            try {
                Shift s = shifts.open(Session.user(), amount);
                Ui.hideKeyboard(this);
                Ui.toast(this, "فُتحت الوردية #" + s.shiftNo);
                render();
            } catch (AppException e) {
                Ui.error(this, e.getMessage());
            }
        });
        shown = null;
        PrintDoc d = new PrintDoc();
        d.center("افتح وردية لبدء البيع", PrintDoc.LARGE, true);
        preview.show(d);
    }

    private void renderOpen(final Shift s) {
        ReportRepo.Summary sum = reports.summary(ReportRepo.Filter.shift(s.id));
        long expected = CashDrawer.expected(s.openingCash, sum.cashReceived, sum.refundsCash,
                sum.expensesCash, sum.payIns, sum.payOuts);
        panel.addView(Ui.text(this, "الوردية #" + s.shiftNo + " مفتوحة", 18, R.color.success, true));
        panel.addView(Ui.text(this, "الكاشير: " + s.cashierName + "   •   منذ " + Dates.dateTime(s.openedAt),
                13, R.color.text_2, false));
        panel.addView(Ui.text(this, "عدد الطلبات: " + sum.orderCount + "   •   المتوقع في الصندوق: " + Money.format(expected),
                14, R.color.text, true), Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(buttons, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 12));
        if (settings.getBool(Settings.EXPENSES_ENABLED, true)) {
            addButton(buttons, "＋ مصروف", false, this::addExpense);
        }
        addButton(buttons, "تسوية صندوق", false, () -> Auth.requireManager(this, "تسوية نقدية للصندوق", this::addAdjustment));
        addButton(buttons, "طباعة التقرير", false, () -> print(s));
        TextView close = Ui.button(this, "إغلاق الوردية", true);
        close.setOnClickListener(v -> Auth.requireManager(this, "إغلاق الوردية #" + s.shiftNo, m -> closeShift(s, m.name)));
        panel.addView(close, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 48), 8));
        showShift(s);
    }

    private void addButton(LinearLayout parent, String text, boolean primary, Runnable r) {
        TextView b = Ui.button(this, text, primary);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        if (parent.getChildCount() > 0) {
            lp.setMarginStart(Ui.dp(this, 6));
        }
        parent.addView(b, lp);
    }

    private PrintDoc buildReport(Shift s) {
        return Templates.shiftReport(settings.restaurantName(), s, reports.summary(ReportRepo.Filter.shift(s.id)),
                cash.expenses(s.id), cash.adjustments(s.id), reports.cancelledOrdersOfShift(s.id), shifts.reopenCount(s.id));
    }

    private void showShift(final Shift s) {
        shown = shifts.get(s.id);
        preview.show(buildReport(shown));
        if (!shown.isOpen()) {
            String[] items = Session.isManager()
                    ? new String[]{"طباعة تقرير الوردية", "إعادة فتح الوردية (إجراء إداري)"}
                    : new String[]{"طباعة تقرير الوردية"};
            Ui.show(Ui.dialog(this, "وردية #" + shown.shiftNo).setItems(items, (d, which) -> {
                if (which == 0) {
                    print(shown);
                } else {
                    reopen(shown);
                }
            }).setNegativeButton(R.string.close, null));
        }
    }

    private void print(Shift s) {
        Printing.print(this, buildReport(shifts.get(s.id)), 1, (ok, msg) -> {
            if (ok) {
                Ui.toast(this, "طُبع تقرير الوردية");
            } else {
                Ui.show(Ui.dialog(this, "لم تتم الطباعة").setMessage(msg)
                        .setPositiveButton(R.string.retry, (d, w) -> print(s))
                        .setNegativeButton(R.string.close, null));
            }
        });
    }

    private void addExpense() {
        LinearLayout f = Ui.form(this);
        f.addView(Ui.label(this, "التصنيف"));
        final Spinner sp = new Spinner(this);
        final String[] cats = {"مشتريات", "مواد تنظيف", "غاز ووقود", "صيانة", "أجور", "نقل", "أخرى"};
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));
        f.addView(sp);
        f.addView(Ui.label(this, "المبلغ المدفوع نقداً من الصندوق"));
        final EditText amount = Ui.money(this, "المبلغ", "");
        f.addView(amount);
        f.addView(Ui.label(this, "الوصف"));
        final EditText desc = Ui.input(this, "مثال: خبز وخضار", InputType.TYPE_CLASS_TEXT, "");
        f.addView(desc);
        Ui.showForm(this, "تسجيل مصروف", f, getString(R.string.save), () -> {
            long v = Money.parse(amount.getText().toString());
            if (v <= 0) {
                amount.setError("أدخل مبلغاً صحيحاً");
                return false;
            }
            try {
                cash.addExpense(v, cats[sp.getSelectedItemPosition()], desc.getText().toString(), Session.name());
                render();
                return true;
            } catch (AppException e) {
                Ui.error(this, e.getMessage());
                return false;
            }
        });
    }

    private void addAdjustment(final iq.haji.alhjexx.model.User manager) {
        LinearLayout f = Ui.form(this);
        final String[] dirs = {"إيداع نقدي في الصندوق", "سحب نقدي من الصندوق"};
        final Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, dirs));
        f.addView(sp);
        final EditText amount = Ui.money(this, "المبلغ", "");
        f.addView(amount, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
        final EditText reason = Ui.input(this, "السبب (إلزامي)", InputType.TYPE_CLASS_TEXT, "");
        f.addView(reason, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
        Ui.showForm(this, "تسوية الصندوق", f, getString(R.string.save), () -> {
            long v = Money.parse(amount.getText().toString());
            if (v <= 0) {
                amount.setError("أدخل مبلغاً صحيحاً");
                return false;
            }
            try {
                cash.addAdjustment(sp.getSelectedItemPosition() == 0 ? CashAdjustment.IN : CashAdjustment.OUT,
                        v, reason.getText().toString(), manager.name);
                render();
                return true;
            } catch (AppException e) {
                Ui.error(this, e.getMessage());
                return false;
            }
        });
    }

    private void closeShift(final Shift s, final String byManager) {
        ReportRepo.Summary sum = reports.summary(ReportRepo.Filter.shift(s.id));
        final long expected = CashDrawer.expected(s.openingCash, sum.cashReceived, sum.refundsCash,
                sum.expensesCash, sum.payIns, sum.payOuts);
        LinearLayout f = Ui.form(this);
        f.addView(Ui.text(this, "المبلغ المتوقع في الصندوق: " + Money.format(expected), 16, R.color.white, true));
        f.addView(Ui.text(this, "= افتتاحي " + Money.number(s.openingCash) + " + نقد مقبوض " + Money.number(sum.cashReceived)
                + " − استرجاع نقدي " + Money.number(sum.refundsCash) + " − مصروفات " + Money.number(sum.expensesCash)
                + " + إيداعات " + Money.number(sum.payIns) + " − سحوبات " + Money.number(sum.payOuts),
                12, R.color.text_3, false));
        f.addView(Ui.label(this, "المبلغ الفعلي بعد العد"));
        final EditText counted = Ui.money(this, "المبلغ الموجود فعلاً", "");
        f.addView(counted);
        final TextView diff = Ui.text(this, "", 14, R.color.warning, true);
        f.addView(diff, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
        counted.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence x, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence x, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                long v = Money.parse(counted.getText().toString());
                if (v < 0) {
                    diff.setText("");
                    return;
                }
                long d = CashDrawer.difference(v, expected);
                diff.setText(d == 0 ? "مطابق" : (d > 0 ? "زيادة " : "عجز ") + Money.format(Math.abs(d)));
            }
        });
        final EditText note = Ui.input(this, "ملاحظة (اختياري)", InputType.TYPE_CLASS_TEXT, "");
        f.addView(note, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
        f.addView(Ui.text(this, "بعد الإغلاق تُحفظ النتائج نهائياً ولا تُعدَّل.", 12, R.color.text_3, false));
        Ui.showForm(this, "إغلاق الوردية #" + s.shiftNo, Ui.scroll(this, f), "إغلاق نهائي", () -> {
            long v = Money.parse(counted.getText().toString());
            if (v < 0) {
                counted.setError("أدخل المبلغ الفعلي");
                return false;
            }
            try {
                final Shift closed = shifts.close(s.id, v, note.getText().toString(), byManager);
                render();
                showShiftPreviewOnly(closed);
                if (settings.getBool(Settings.SHIFT_AUTO_PRINT, false)) {
                    print(closed);
                } else {
                    Ui.confirm(this, "أُغلقت الوردية", "طباعة تقرير الوردية الآن؟", "طباعة", () -> print(closed));
                }
                return true;
            } catch (AppException e) {
                Ui.error(this, e.getMessage());
                return false;
            }
        });
    }

    private void showShiftPreviewOnly(Shift s) {
        shown = s;
        preview.show(buildReport(s));
    }

    private void reopen(final Shift s) {
        LinearLayout f = Ui.form(this);
        f.addView(Ui.text(this, "إعادة فتح الوردية تُسجَّل في سجل العمليات مع نتائج الإغلاق السابقة.", 13, R.color.text_2, false));
        final EditText reason = Ui.input(this, "سبب إعادة الفتح (إلزامي)", InputType.TYPE_CLASS_TEXT, "");
        f.addView(reason, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 10));
        Ui.showForm(this, "إعادة فتح الوردية #" + s.shiftNo, f, "إعادة فتح", () -> {
            try {
                shifts.reopen(s.id, reason.getText().toString(), Session.manager());
                render();
                return true;
            } catch (AppException e) {
                Ui.error(this, e.getMessage());
                return false;
            }
        });
    }
}
