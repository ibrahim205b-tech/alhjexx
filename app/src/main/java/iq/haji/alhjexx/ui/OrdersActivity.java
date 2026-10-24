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
import iq.haji.alhjexx.core.Dates;
import iq.haji.alhjexx.core.Money;
import iq.haji.alhjexx.data.OrderRepo;
import iq.haji.alhjexx.data.Settings;
import iq.haji.alhjexx.data.ShiftRepo;
import iq.haji.alhjexx.model.Order;
import iq.haji.alhjexx.model.OrderStatus;
import iq.haji.alhjexx.model.OrderType;
import iq.haji.alhjexx.model.PayMethod;
import iq.haji.alhjexx.model.Shift;
import iq.haji.alhjexx.model.User;
import iq.haji.alhjexx.print.PrintDoc;
import iq.haji.alhjexx.print.Printing;
import iq.haji.alhjexx.print.Templates;
import iq.haji.alhjexx.security.Session;

/** الطلبات السابقة: بحث حسب الفاتورة والتاريخ والنوع والوردية والحالة، إعادة طباعة، وإلغاء معتمد. */
public class OrdersActivity extends PageActivity {

    private OrderRepo orders;
    private Settings settings;
    private RowAdapter adapter;
    private DocPreview preview;
    private EditText etInvoice;
    private TextView btnDate;
    private Spinner spType, spStatus, spShift;
    private final List<Shift> shiftList = new ArrayList<>();
    private long day;
    private Order selected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ready()) {
            return;
        }
        setPageTitle("الطلبات السابقة");
        orders = new OrderRepo(this);
        settings = new Settings(this);
        day = Dates.startOfDay(System.currentTimeMillis());
        addAction("إعادة طباعة", false, this::reprint);
        addAction("إلغاء الطلب", true, this::cancelSelected);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        content.addView(root);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(filters);
        etInvoice = Ui.input(this, "رقم الفاتورة", InputType.TYPE_CLASS_TEXT, "");
        filters.addView(etInvoice, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
        btnDate = Ui.button(this, "", false);
        btnDate.setOnClickListener(v -> Ui.show(Ui.dialog(this, "التاريخ")
                .setItems(new String[]{"اختيار يوم", "كل التواريخ"}, (d, w) -> {
                    if (w == 0) {
                        Pickers.date(this, day, ms -> {
                            day = ms;
                            search();
                        });
                    } else {
                        day = 0;
                        search();
                    }
                })));
        filters.addView(btnDate, weighted(1.6f));
        spType = spinner(new String[]{"كل الأنواع", "داخل المطعم", "سفري"});
        filters.addView(spType, weighted(1.4f));
        spStatus = spinner(new String[]{"كل الحالات", "مدفوع", "ملغى"});
        filters.addView(spStatus, weighted(1.2f));
        List<String> shiftNames = new ArrayList<>();
        shiftNames.add("كل الورديات");
        for (Shift s : new ShiftRepo(this).recent(100)) {
            shiftList.add(s);
            shiftNames.add("وردية #" + s.shiftNo);
        }
        spShift = spinner(shiftNames.toArray(new String[0]));
        filters.addView(spShift, weighted(1.3f));
        TextView go = Ui.button(this, "بحث", true);
        go.setOnClickListener(v -> search());
        filters.addView(go, weighted(0.9f));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(body, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, 0, 10));
        ((LinearLayout.LayoutParams) body.getLayoutParams()).weight = 1f;

        ListView lv = new ListView(this);
        lv.setBackgroundResource(R.drawable.card);
        adapter = new RowAdapter(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((a, v, pos, id) -> select((Order) adapter.row(pos).tag));
        body.addView(lv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 5f));
        preview = new DocPreview(this);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4f);
        pl.setMarginStart(Ui.dp(this, 12));
        body.addView(preview, pl);
        search();
    }

    private LinearLayout.LayoutParams weighted(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), w);
        p.setMarginStart(Ui.dp(this, 6));
        return p;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        s.setBackgroundResource(R.drawable.input);
        return s;
    }

    private void search() {
        btnDate.setText(day > 0 ? Dates.date(day) : "كل التواريخ");
        OrderRepo.Query q = new OrderRepo.Query();
        q.invoiceNo = etInvoice.getText().toString();
        if (day > 0) {
            q.from = day;
            q.to = Dates.endOfDay(day);
        }
        int t = spType.getSelectedItemPosition();
        q.type = t == 1 ? OrderType.DINE_IN : (t == 2 ? OrderType.TAKEAWAY : null);
        int st = spStatus.getSelectedItemPosition();
        q.status = st == 1 ? OrderStatus.PAID : (st == 2 ? OrderStatus.CANCELLED : null);
        int sh = spShift.getSelectedItemPosition();
        q.shiftId = sh > 0 ? shiftList.get(sh - 1).id : 0;
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Order o : orders.search(q)) {
            RowAdapter.Row r = new RowAdapter.Row("فاتورة " + o.invoiceNo + "  •  " + OrderType.label(o.type),
                    Dates.dateTime(o.createdAt) + "  •  وردية #" + o.shiftNo + "  •  " + o.cashierName,
                    Money.format(o.total), o);
            if (o.isCancelled()) {
                r.badge("ملغى", R.color.error);
            }
            rows.add(r);
        }
        adapter.set(rows);
        if (rows.isEmpty()) {
            selected = null;
            PrintDoc d = new PrintDoc();
            d.center("لا توجد طلبات مطابقة", PrintDoc.NORMAL, false);
            preview.show(d);
        } else {
            select((Order) rows.get(0).tag);
        }
    }

    private PrintDoc invoiceDoc(Order o, boolean reprint) {
        return Templates.invoice(o, settings.restaurantName(), settings.get(Settings.RESTAURANT_PHONE, ""),
                settings.get(Settings.RECEIPT_FOOTER, Settings.DEFAULT_FOOTER),
                settings.getBool(Settings.TABLES_ENABLED, true), reprint);
    }

    private void select(Order o) {
        selected = orders.get(o.id);
        PrintDoc d = invoiceDoc(selected, selected.printCount > 0);
        if (selected.isCancelled()) {
            d.right("ملغى بواسطة: " + selected.cancelledBy, PrintDoc.SMALL, false);
            d.right("وقت الإلغاء: " + Dates.dateTime(selected.cancelledAt), PrintDoc.SMALL, false);
            d.right("السبب: " + selected.cancelReason, PrintDoc.SMALL, false);
            if (selected.refundedAmount > 0) {
                d.right("المُسترجع: " + Money.format(selected.refundedAmount), PrintDoc.SMALL, true);
            }
        }
        preview.show(d);
    }

    private void reprint() {
        if (selected == null) {
            Ui.toast(this, "اختر طلباً");
            return;
        }
        final Order o = orders.get(selected.id);
        Printing.print(this, invoiceDoc(o, o.printCount > 0), 1, (ok, msg) -> {
            if (ok) {
                orders.markPrinted(o.id);
                Ui.toast(this, "طُبعت الفاتورة " + o.invoiceNo);
            } else {
                Ui.error(this, msg);
            }
        });
    }

    private void cancelSelected() {
        if (selected == null) {
            Ui.toast(this, "اختر طلباً");
            return;
        }
        final Order o = orders.get(selected.id);
        if (o.isCancelled()) {
            Ui.toast(this, "الطلب ملغى مسبقاً");
            return;
        }
        Shift open = new ShiftRepo(this).openShift();
        if (open == null || open.id != o.shiftId) {
            Ui.error(this, "لا يمكن إلغاء طلب من وردية مغلقة. الإلغاء مسموح لطلبات الوردية المفتوحة فقط.");
            return;
        }
        Auth.requireManager(this, "إلغاء الفاتورة " + o.invoiceNo, manager -> showCancelForm(o, manager));
    }

    private void showCancelForm(final Order o, final User approver) {
        LinearLayout f = Ui.form(this);
        f.addView(Ui.text(this, "فاتورة " + o.invoiceNo + " — " + Money.format(o.total), 16, R.color.white, true));
        f.addView(Ui.text(this, "الطلب لن يُحذف؛ سيبقى في السجل بقيمته الأصلية ويُستبعد من صافي المبيعات.",
                12, R.color.text_3, false));
        f.addView(Ui.label(this, "سبب الإلغاء (إلزامي)"));
        final EditText reason = Ui.input(this, "مثال: طلب الزبون إلغاءه", InputType.TYPE_CLASS_TEXT, "");
        f.addView(reason);
        final long paidCash = o.paidBy(PayMethod.CASH);
        final long paidCard = o.paidBy(PayMethod.CARD);
        f.addView(Ui.label(this, "المبلغ المُعاد للزبون فعلياً (اتركه 0 إن لم يُعَد شيء)"));
        final EditText refundCash = Ui.money(this, "نقداً (حتى " + Money.number(paidCash) + ")", "0");
        f.addView(refundCash);
        final EditText refundCard = Ui.money(this, "إلكترونياً (حتى " + Money.number(paidCard) + ")", "0");
        if (paidCard > 0) {
            f.addView(refundCard, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 6));
        }
        Ui.showForm(this, "إلغاء طلب معتمد", Ui.scroll(this, f), "تأكيد الإلغاء", () -> {
            long rc = parseOrZero(refundCash);
            long rk = paidCard > 0 ? parseOrZero(refundCard) : 0;
            if (rc < 0 || rk < 0) {
                Ui.toast(this, "مبلغ استرجاع غير صحيح");
                return false;
            }
            try {
                orders.cancel(o.id, reason.getText().toString(), Session.user(), approver, rc, rk);
                Ui.toast(this, "أُلغي الطلب " + o.invoiceNo);
                search();
                return true;
            } catch (AppException e) {
                Ui.error(this, e.getMessage());
                return false;
            }
        });
    }

    private static long parseOrZero(EditText e) {
        String s = e.getText().toString().trim();
        return s.isEmpty() ? 0 : Money.parse(s);
    }
}
