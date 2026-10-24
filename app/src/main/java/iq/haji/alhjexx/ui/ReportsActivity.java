package iq.haji.alhjexx.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import iq.haji.alhjexx.R;
import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.core.Dates;
import iq.haji.alhjexx.data.CashRepo;
import iq.haji.alhjexx.data.CatalogRepo;
import iq.haji.alhjexx.data.ReportRepo;
import iq.haji.alhjexx.data.Settings;
import iq.haji.alhjexx.data.ShiftRepo;
import iq.haji.alhjexx.model.Category;
import iq.haji.alhjexx.model.Product;
import iq.haji.alhjexx.model.Shift;
import iq.haji.alhjexx.print.PrintDoc;
import iq.haji.alhjexx.print.Printing;
import iq.haji.alhjexx.print.Templates;

/** مركز التقارير: كل تقرير يُعرض بمعاينة مطابقة للطباعة ويُطبع مباشرة على الطابعة الحرارية. */
public class ReportsActivity extends PageActivity {

    private static final String[] TYPES = {
            "تقرير مبيعات اليوم",
            "تقرير فترة مخصصة",
            "التقرير الشهري",
            "تقرير وردية",
            "تقرير الأقسام",
            "تقرير صنف",
            "الطلبات الملغاة",
            "مقارنة بين ورديتين",
            "مقارنة بين يومين"
    };

    private ReportRepo reports;
    private ShiftRepo shifts;
    private Settings settings;
    private DocPreview preview;
    private int type;
    private long dateA, dateB;
    private TextView btnDateA, btnDateB, tvHint;
    private Spinner spShiftA, spShiftB, spCategory, spProduct;
    private final List<Shift> shiftList = new ArrayList<>();
    private final List<Category> catList = new ArrayList<>();
    private final List<Product> productList = new ArrayList<>();
    private PrintDoc current;
    private RowAdapter typeAdapter;

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
        setPageTitle("التقارير");
        reports = new ReportRepo(this);
        shifts = new ShiftRepo(this);
        settings = new Settings(this);
        long today = Dates.startOfDay(System.currentTimeMillis());
        dateA = today;
        dateB = today;
        addAction("طباعة", true, this::print);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(row);

        ListView lv = new ListView(this);
        lv.setBackgroundResource(R.drawable.card);
        typeAdapter = new RowAdapter(this);
        lv.setAdapter(typeAdapter);
        lv.setOnItemClickListener((a, v, pos, id) -> {
            type = pos;
            refreshTypes();
            updateControls();
            generate();
        });
        row.addView(lv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2.4f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setBackgroundResource(R.drawable.card);
        int p = Ui.dp(this, 12);
        controls.setPadding(p, p, p, p);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2.4f);
        cl.setMarginStart(Ui.dp(this, 10));
        row.addView(controls, cl);

        tvHint = Ui.text(this, "", 13, R.color.text_2, true);
        controls.addView(tvHint);
        btnDateA = Ui.button(this, "", false);
        btnDateA.setOnClickListener(v -> Pickers.date(this, dateA, ms -> {
            dateA = ms;
            updateControls();
            generate();
        }));
        controls.addView(btnDateA, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 44), 10));
        btnDateB = Ui.button(this, "", false);
        btnDateB.setOnClickListener(v -> Pickers.date(this, dateB, ms -> {
            dateB = ms;
            updateControls();
            generate();
        }));
        controls.addView(btnDateB, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 44), 8));

        List<String> shiftNames = new ArrayList<>();
        for (Shift s : shifts.recent(200)) {
            shiftList.add(s);
            shiftNames.add("وردية #" + s.shiftNo + " — " + Dates.date(s.openedAt) + (s.isOpen() ? " (مفتوحة)" : ""));
        }
        spShiftA = spinner(shiftNames);
        spShiftB = spinner(shiftNames);
        controls.addView(spShiftA, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 46), 8));
        controls.addView(spShiftB, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 46), 8));
        if (shiftList.size() > 1) {
            spShiftB.setSelection(1);
        }

        List<String> catNames = new ArrayList<>();
        catNames.add("كل الأقسام");
        CatalogRepo catalog = new CatalogRepo(this);
        for (Category c : catalog.categories(false)) {
            catList.add(c);
            catNames.add(c.name);
        }
        spCategory = spinner(catNames);
        controls.addView(spCategory, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 46), 8));

        List<String> prodNames = new ArrayList<>();
        for (Product pr : catalog.products(0, false)) {
            productList.add(pr);
            prodNames.add(pr.name);
        }
        spProduct = spinner(prodNames);
        controls.addView(spProduct, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 46), 8));

        TextView show = Ui.button(this, "عرض التقرير", true);
        show.setOnClickListener(v -> generate());
        controls.addView(show, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 46), 14));
        controls.addView(Ui.text(this, "صافي المبيعات = مجموع الأصناف للطلبات غير الملغاة − الخصومات. "
                + "الملغاة والاسترجاعات والمصروفات تُعرض منفصلة.", 11, R.color.text_3, false),
                Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 12));

        preview = new DocPreview(this);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4.6f);
        pl.setMarginStart(Ui.dp(this, 10));
        row.addView(preview, pl);

        refreshTypes();
        updateControls();
        generate();
    }

    private Spinner spinner(List<String> items) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        s.setBackgroundResource(R.drawable.input);
        return s;
    }

    private void refreshTypes() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (int i = 0; i < TYPES.length; i++) {
            rows.add(new RowAdapter.Row((i == type ? "● " : "") + TYPES[i], null, "", i));
        }
        typeAdapter.set(rows);
    }

    private void vis(View v, boolean show) {
        v.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateControls() {
        boolean range = type == 1 || type == 4 || type == 5 || type == 6;
        vis(btnDateA, type == 0 || type == 2 || range || type == 8);
        vis(btnDateB, range || type == 8);
        vis(spShiftA, type == 3 || type == 7);
        vis(spShiftB, type == 7);
        vis(spCategory, type == 4);
        vis(spProduct, type == 5);
        if (type == 2) {
            btnDateA.setText("الشهر: " + Dates.month(dateA));
        } else if (type == 8) {
            btnDateA.setText("اليوم الأول: " + Dates.date(dateA));
            btnDateB.setText("اليوم الثاني: " + Dates.date(dateB));
        } else if (range) {
            btnDateA.setText("من: " + Dates.date(dateA));
            btnDateB.setText("إلى: " + Dates.date(dateB));
        } else {
            btnDateA.setText("اليوم: " + Dates.date(dateA));
        }
        tvHint.setText(TYPES[type]);
    }

    private long[] range() {
        long a = Math.min(dateA, dateB);
        long b = Math.max(dateA, dateB);
        return new long[]{Dates.startOfDay(a), Dates.endOfDay(b)};
    }

    private String rangeLabel(long[] r) {
        String a = Dates.date(r[0]);
        String b = Dates.date(r[1] - 1);
        return a.equals(b) ? a : a + "  ←  " + b;
    }

    private void generate() {
        try {
            current = build();
            preview.show(current);
        } catch (AppException e) {
            Ui.error(this, e.getMessage());
        }
    }

    private PrintDoc build() {
        String name = settings.restaurantName();
        switch (type) {
            case 0: {
                long from = Dates.startOfDay(dateA);
                long to = Dates.endOfDay(dateA);
                return Templates.salesReport(name, "تقرير مبيعات اليوم", Dates.date(from),
                        reports.summary(ReportRepo.Filter.range(from, to)));
            }
            case 1: {
                long[] r = range();
                return Templates.salesReport(name, "تقرير فترة", rangeLabel(r),
                        reports.summary(ReportRepo.Filter.range(r[0], r[1])));
            }
            case 2: {
                long from = Dates.startOfMonth(dateA);
                long to = Dates.endOfMonth(dateA);
                return Templates.salesReport(name, "التقرير الشهري", Dates.month(from),
                        reports.summary(ReportRepo.Filter.range(from, to)));
            }
            case 3: {
                Shift s = pickShift(spShiftA);
                return Templates.shiftReport(name, s, reports.summary(ReportRepo.Filter.shift(s.id)),
                        new CashRepo(this).expenses(s.id), new CashRepo(this).adjustments(s.id),
                        reports.cancelledOrdersOfShift(s.id), shifts.reopenCount(s.id));
            }
            case 4: {
                long[] r = range();
                int idx = spCategory.getSelectedItemPosition();
                long catId = idx > 0 ? catList.get(idx - 1).id : 0;
                return Templates.categoryReport(name, rangeLabel(r),
                        reports.categorySales(ReportRepo.Filter.range(r[0], r[1]), catId));
            }
            case 5: {
                if (productList.isEmpty()) {
                    throw new AppException("لا توجد أصناف");
                }
                long[] r = range();
                Product pr = productList.get(spProduct.getSelectedItemPosition());
                return Templates.productReport(name, pr.name, rangeLabel(r), reports.productSales(pr.id, r[0], r[1]));
            }
            case 6: {
                long[] r = range();
                return Templates.cancelledReport(name, rangeLabel(r), reports.cancelledOrders(r[0], r[1]));
            }
            case 7: {
                Shift a = pickShift(spShiftA);
                Shift b = pickShift(spShiftB);
                return Templates.comparison(name, "مقارنة ورديتين", "#" + a.shiftNo,
                        reports.summary(ReportRepo.Filter.shift(a.id)), "#" + b.shiftNo,
                        reports.summary(ReportRepo.Filter.shift(b.id)));
            }
            default: {
                long a0 = Dates.startOfDay(dateA);
                long b0 = Dates.startOfDay(dateB);
                return Templates.comparison(name, "مقارنة يومين", Dates.date(a0).substring(5),
                        reports.summary(ReportRepo.Filter.range(a0, Dates.endOfDay(a0))), Dates.date(b0).substring(5),
                        reports.summary(ReportRepo.Filter.range(b0, Dates.endOfDay(b0))));
            }
        }
    }

    private Shift pickShift(Spinner sp) {
        if (shiftList.isEmpty()) {
            throw new AppException("لا توجد ورديات بعد");
        }
        return shifts.get(shiftList.get(Math.max(0, sp.getSelectedItemPosition())).id);
    }

    private void print() {
        if (current == null) {
            generate();
        }
        if (current == null) {
            return;
        }
        Printing.print(this, current, 1, (ok, msg) -> {
            if (ok) {
                Ui.toast(this, "تم إرسال التقرير للطابعة");
            } else {
                Ui.show(Ui.dialog(this, "لم تتم الطباعة").setMessage(msg)
                        .setPositiveButton(R.string.retry, (d, w) -> print())
                        .setNegativeButton(R.string.close, null));
            }
        });
    }
}
