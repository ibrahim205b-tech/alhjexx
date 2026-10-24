package iq.haji.alhjexx.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import iq.haji.alhjexx.R;
import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.core.Money;
import iq.haji.alhjexx.data.CatalogRepo;
import iq.haji.alhjexx.model.Category;
import iq.haji.alhjexx.model.Product;
import iq.haji.alhjexx.model.ProductSize;
import iq.haji.alhjexx.security.Session;

/** إدارة المنيو: الأقسام (إضافة، تسمية، ترتيب، إخفاء) والأصناف (سعر، قسم، أحجام، تعطيل). */
public class MenuAdminActivity extends PageActivity {

    private CatalogRepo repo;
    private final List<Category> cats = new ArrayList<>();
    private long selectedCat;
    private RowAdapter catAdapter, productAdapter;
    private TextView tvProductsTitle;

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
        setPageTitle("المنيو: الأقسام والأصناف");
        repo = new CatalogRepo(this);
        addAction("＋ قسم", false, this::addCategory);
        addAction("＋ صنف", true, () -> editProduct(null));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(row);

        LinearLayout catCol = column();
        catCol.addView(Ui.text(this, "الأقسام (اضغط للخيارات)", 14, R.color.text_2, true));
        ListView lvCats = new ListView(this);
        catAdapter = new RowAdapter(this);
        lvCats.setAdapter(catAdapter);
        lvCats.setOnItemClickListener((p, v, pos, id) -> onCategory((Category) catAdapter.row(pos).tag));
        catCol.addView(lvCats, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        row.addView(catCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4f));

        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 12), 1));

        LinearLayout prodCol = column();
        tvProductsTitle = Ui.text(this, "", 14, R.color.text_2, true);
        prodCol.addView(tvProductsTitle);
        ListView lvProducts = new ListView(this);
        productAdapter = new RowAdapter(this);
        lvProducts.setAdapter(productAdapter);
        lvProducts.setOnItemClickListener((p, v, pos, id) -> onProduct((Product) productAdapter.row(pos).tag));
        prodCol.addView(lvProducts, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        row.addView(prodCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 7f));

        reload();
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.card);
        int p = Ui.dp(this, 10);
        c.setPadding(p, p, p, p);
        return c;
    }

    private void reload() {
        cats.clear();
        cats.addAll(repo.categories(false));
        boolean found = false;
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Category c : cats) {
            if (c.id == selectedCat) {
                found = true;
            }
        }
        if (!found) {
            selectedCat = cats.isEmpty() ? 0 : cats.get(0).id;
        }
        for (Category c : cats) {
            int count = repo.products(c.id, false).size();
            RowAdapter.Row r = new RowAdapter.Row((c.id == selectedCat ? "● " : "") + c.name, count + " صنف", "", c);
            if (!c.active) {
                r.badge("مخفي", R.color.warning);
            }
            rows.add(r);
        }
        catAdapter.set(rows);

        List<RowAdapter.Row> pr = new ArrayList<>();
        String catName = "";
        for (Category c : cats) {
            if (c.id == selectedCat) {
                catName = c.name;
            }
        }
        tvProductsTitle.setText(cats.isEmpty() ? "أضف قسماً أولاً" : "أصناف قسم: " + catName + " (اضغط للتعديل)");
        if (selectedCat > 0) {
            for (Product p : repo.products(selectedCat, false)) {
                String sub;
                if (p.hasSizes()) {
                    StringBuilder sb = new StringBuilder();
                    for (ProductSize s : p.activeSizes()) {
                        if (sb.length() > 0) {
                            sb.append("  •  ");
                        }
                        sb.append(s.name).append(" ").append(Money.number(s.price));
                    }
                    sub = sb.toString();
                } else {
                    sub = "";
                }
                RowAdapter.Row r = new RowAdapter.Row(p.name, sub, p.hasSizes() ? "أحجام" : Money.format(p.price), p);
                if (!p.active) {
                    r.badge("غير متوفر", R.color.warning);
                }
                pr.add(r);
            }
        }
        productAdapter.set(pr);
    }

    // ---------------- الأقسام ----------------

    private void addCategory() {
        LinearLayout f = Ui.form(this);
        final EditText e = Ui.input(this, "اسم القسم", InputType.TYPE_CLASS_TEXT, "");
        f.addView(e);
        Ui.showForm(this, "قسم جديد", f, getString(R.string.save), () -> {
            try {
                selectedCat = repo.addCategory(e.getText().toString(), Session.actor());
                reload();
                return true;
            } catch (AppException ex) {
                e.setError(ex.getMessage());
                return false;
            }
        });
    }

    private void onCategory(final Category c) {
        if (c.id != selectedCat) {
            selectedCat = c.id;
            reload();
            return;
        }
        String[] items = {"تعديل الاسم", "تحريك للأعلى", "تحريك للأسفل",
                c.active ? "إخفاء القسم مؤقتاً" : "إظهار القسم", "حذف / أرشفة"};
        Ui.show(Ui.dialog(this, c.name).setItems(items, (d, which) -> {
            switch (which) {
                case 0:
                    renameCategory(c);
                    break;
                case 1:
                    repo.moveCategory(c.id, -1);
                    reload();
                    break;
                case 2:
                    repo.moveCategory(c.id, 1);
                    reload();
                    break;
                case 3:
                    repo.setCategoryActive(c.id, !c.active, Session.actor());
                    reload();
                    break;
                default:
                    Ui.confirm(this, "حذف القسم", "يُحذف القسم إذا كان فارغاً. إن كان مستخدماً في طلبات سابقة يُؤرشف ويختفي فقط.",
                            "متابعة", () -> {
                                try {
                                    repo.deleteCategory(c.id, Session.actor());
                                    reload();
                                } catch (AppException e) {
                                    Ui.error(this, e.getMessage());
                                }
                            });
                    break;
            }
        }).setNegativeButton(R.string.close, null));
    }

    private void renameCategory(final Category c) {
        LinearLayout f = Ui.form(this);
        final EditText e = Ui.input(this, "اسم القسم", InputType.TYPE_CLASS_TEXT, c.name);
        f.addView(e);
        Ui.showForm(this, "تعديل اسم القسم", f, getString(R.string.save), () -> {
            try {
                repo.renameCategory(c.id, e.getText().toString(), Session.actor());
                reload();
                return true;
            } catch (AppException ex) {
                e.setError(ex.getMessage());
                return false;
            }
        });
    }

    // ---------------- الأصناف ----------------

    private void onProduct(final Product p) {
        String[] items = {"تعديل (الاسم، السعر، القسم، الأحجام)",
                p.active ? "تعطيل (غير متوفر)" : "إعادة تفعيل", "حذف / أرشفة"};
        Ui.show(Ui.dialog(this, p.name).setItems(items, (d, which) -> {
            if (which == 0) {
                editProduct(repo.product(p.id));
            } else if (which == 1) {
                repo.setProductActive(p.id, !p.active, Session.actor());
                reload();
            } else {
                String msg = repo.isProductUsed(p.id)
                        ? "هذا الصنف مستخدم في طلبات سابقة، لذلك سيُؤرشف ويختفي من المنيو دون حذف بياناته."
                        : "حذف الصنف نهائياً؟";
                Ui.confirm(this, "حذف الصنف", msg, "متابعة", () -> {
                    repo.deleteProduct(p.id, Session.actor());
                    reload();
                });
            }
        }).setNegativeButton(R.string.close, null));
    }

    private final class SizeRow {
        final ProductSize size;
        final LinearLayout view;
        final EditText name;
        final EditText price;

        SizeRow(ProductSize s) {
            size = s;
            view = new LinearLayout(MenuAdminActivity.this);
            view.setOrientation(LinearLayout.HORIZONTAL);
            name = Ui.input(MenuAdminActivity.this, "الحجم (مثال: وسط)", InputType.TYPE_CLASS_TEXT, s.name);
            price = Ui.money(MenuAdminActivity.this, "السعر", s.price > 0 ? String.valueOf(s.price) : "");
            TextView remove = Ui.button(MenuAdminActivity.this, "✕", false);
            view.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
            pp.setMarginStart(Ui.dp(MenuAdminActivity.this, 6));
            view.addView(price, pp);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Ui.dp(MenuAdminActivity.this, 44), Ui.dp(MenuAdminActivity.this, 44));
            rp.setMarginStart(Ui.dp(MenuAdminActivity.this, 6));
            view.addView(remove, rp);
            remove.setOnClickListener(v -> {
                ((LinearLayout) view.getParent()).removeView(view);
                sizeRows.remove(this);
            });
        }
    }

    private final List<SizeRow> sizeRows = new ArrayList<>();

    private void editProduct(final Product existing) {
        if (cats.isEmpty()) {
            Ui.toast(this, "أضف قسماً أولاً");
            return;
        }
        sizeRows.clear();
        LinearLayout f = Ui.form(this);
        final EditText name = Ui.input(this, "اسم الصنف", InputType.TYPE_CLASS_TEXT, existing == null ? "" : existing.name);
        f.addView(Ui.label(this, "اسم الصنف"));
        f.addView(name);

        f.addView(Ui.label(this, "القسم"));
        final Spinner sp = new Spinner(this);
        List<String> names = new ArrayList<>();
        int sel = 0;
        long want = existing != null ? existing.categoryId : selectedCat;
        for (int i = 0; i < cats.size(); i++) {
            names.add(cats.get(i).name + (cats.get(i).active ? "" : " (مخفي)"));
            if (cats.get(i).id == want) {
                sel = i;
            }
        }
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        sp.setSelection(sel);
        f.addView(sp);

        f.addView(Ui.label(this, "السعر (د.ع) — يُتجاهل إذا أُضيفت أحجام"));
        final EditText price = Ui.money(this, "السعر", existing == null || existing.price == 0 ? "" : String.valueOf(existing.price));
        f.addView(price);

        final CheckBox active = new CheckBox(this);
        active.setText("متوفر ويظهر في شاشة الكاشير");
        active.setTextColor(getColor(R.color.text));
        active.setChecked(existing == null || existing.active);
        f.addView(active, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));

        f.addView(Ui.label(this, "الأحجام (اختياري): لكل حجم سعر مستقل"));
        final LinearLayout sizesBox = new LinearLayout(this);
        sizesBox.setOrientation(LinearLayout.VERTICAL);
        f.addView(sizesBox);
        if (existing != null) {
            for (ProductSize s : existing.activeSizes()) {
                SizeRow r = new SizeRow(s);
                sizeRows.add(r);
                sizesBox.addView(r.view, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 6));
            }
        }
        TextView addSize = Ui.button(this, "＋ إضافة حجم", false);
        addSize.setOnClickListener(v -> {
            SizeRow r = new SizeRow(new ProductSize());
            sizeRows.add(r);
            sizesBox.addView(r.view, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 6));
        });
        f.addView(addSize, Ui.lp(this, LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42), 8));
        f.addView(Ui.text(this, "تعديل السعر لا يغيّر الفواتير السابقة؛ كل طلب يحتفظ بالسعر وقت بيعه.",
                11, R.color.text_3, false), Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 10));

        Ui.showForm(this, existing == null ? "صنف جديد" : "تعديل الصنف", Ui.scroll(this, f), getString(R.string.save), () -> {
            Product p = new Product();
            p.id = existing == null ? 0 : existing.id;
            p.name = name.getText().toString();
            p.categoryId = cats.get(sp.getSelectedItemPosition()).id;
            p.active = active.isChecked();
            for (SizeRow r : sizeRows) {
                String n = r.name.getText().toString().trim();
                String pr = r.price.getText().toString().trim();
                if (n.isEmpty() && pr.isEmpty()) {
                    continue;
                }
                long v = Money.parse(pr);
                if (n.isEmpty() || v <= 0) {
                    r.price.setError("أدخل اسم الحجم وسعره");
                    return false;
                }
                ProductSize s = new ProductSize();
                s.id = r.size.id;
                s.name = n;
                s.price = v;
                p.sizes.add(s);
            }
            if (p.sizes.isEmpty()) {
                long v = Money.parse(price.getText().toString());
                if (v <= 0) {
                    price.setError("أدخل سعراً صحيحاً أكبر من صفر");
                    return false;
                }
                p.price = v;
            }
            try {
                repo.saveProduct(p, Session.actor());
                selectedCat = p.categoryId;
                reload();
                Ui.toast(this, "تم الحفظ");
                return true;
            } catch (AppException e) {
                Ui.error(this, e.getMessage());
                return false;
            }
        });
    }
}
