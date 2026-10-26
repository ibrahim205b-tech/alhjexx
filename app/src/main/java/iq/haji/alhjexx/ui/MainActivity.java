package iq.haji.alhjexx.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import iq.haji.alhjexx.R;
import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.core.Dates;
import iq.haji.alhjexx.core.Money;
import iq.haji.alhjexx.core.OrderMath;
import iq.haji.alhjexx.data.CatalogRepo;
import iq.haji.alhjexx.data.OrderRepo;
import iq.haji.alhjexx.data.Settings;
import iq.haji.alhjexx.data.ShiftRepo;
import iq.haji.alhjexx.model.Cart;
import iq.haji.alhjexx.model.CartLine;
import iq.haji.alhjexx.model.Category;
import iq.haji.alhjexx.model.Order;
import iq.haji.alhjexx.model.OrderType;
import iq.haji.alhjexx.model.Product;
import iq.haji.alhjexx.model.ProductSize;
import iq.haji.alhjexx.model.Shift;
import iq.haji.alhjexx.model.User;
import iq.haji.alhjexx.print.BtPrinter;
import iq.haji.alhjexx.print.Printing;
import iq.haji.alhjexx.print.Templates;
import iq.haji.alhjexx.security.Session;

/** شاشة الكاشير: الأقسام، الأصناف، الطلب الجاري، الدفع والطباعة. */
public class MainActivity extends BaseActivity implements BtPrinter.Listener {

    private CatalogRepo catalog;
    private OrderRepo orders;
    private ShiftRepo shifts;
    private Settings settings;

    private Cart cart;
    private boolean confirming;
    private long lastOrderId;

    private final List<Category> categories = new ArrayList<>();
    private final List<Product> products = new ArrayList<>();
    private long selectedCategory;

    private TextView tvClock, chipShift, chipPrinter, chipUser, tvShiftWarning;
    private TextView tvTable, tvEmpty, tvItemCount;
    private TextView tvSubtotal, tvDiscount, tvDiscountLabel, tvTotal, tvLastInvoice;
    private TextView tvCategoryTitle, tvNoProducts, btnPrint, btnService, tvService;
    private View rowDiscount, rowService;
    private LineAdapter lineAdapter;
    private CategoryAdapter categoryAdapter;
    private ProductAdapter productAdapter;

    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            tvClock.setText(Dates.dateTime(System.currentTimeMillis()));
            clock.postDelayed(this, 20_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Session.isLoggedIn()) {
            return;
        }
        setContentView(R.layout.activity_main);
        catalog = new CatalogRepo(this);
        orders = new OrderRepo(this);
        shifts = new ShiftRepo(this);
        settings = new Settings(this);
        cart = CartStore.load(this);

        tvClock = findViewById(R.id.tvClock);
        chipShift = findViewById(R.id.chipShift);
        chipPrinter = findViewById(R.id.chipPrinter);
        chipUser = findViewById(R.id.chipUser);
        tvShiftWarning = findViewById(R.id.tvShiftWarning);
        tvTable = findViewById(R.id.tvTable);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvItemCount = findViewById(R.id.tvItemCount);
        tvSubtotal = findViewById(R.id.tvSubtotal);
        tvDiscount = findViewById(R.id.tvDiscount);
        tvDiscountLabel = findViewById(R.id.tvDiscountLabel);
        tvTotal = findViewById(R.id.tvTotal);
        tvLastInvoice = findViewById(R.id.tvLastInvoice);
        tvCategoryTitle = findViewById(R.id.tvCategoryTitle);
        tvNoProducts = findViewById(R.id.tvNoProducts);
        btnPrint = findViewById(R.id.btnPrint);
        rowDiscount = findViewById(R.id.rowDiscount);
        rowService = findViewById(R.id.rowService);
        tvService = findViewById(R.id.tvService);
        btnService = findViewById(R.id.btnService);
        btnService.setOnClickListener(v -> toggleService());

        ListView lvLines = findViewById(R.id.lvLines);
        lineAdapter = new LineAdapter();
        lvLines.setAdapter(lineAdapter);

        ListView lvCategories = findViewById(R.id.lvCategories);
        categoryAdapter = new CategoryAdapter();
        lvCategories.setAdapter(categoryAdapter);
        lvCategories.setOnItemClickListener((p, v, pos, id) -> selectCategory(categories.get(pos).id));

        GridView gv = findViewById(R.id.gvProducts);
        productAdapter = new ProductAdapter();
        gv.setAdapter(productAdapter);
        gv.setOnItemClickListener((p, v, pos, id) -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
            onProduct(products.get(pos));
        });

        tvTable.setOnClickListener(v -> editTable());
        findViewById(R.id.btnClear).setOnClickListener(v -> onClearOrder());
        // «طباعة»: يعتمد الطلب الجاري (الدفع) ثم يطبع فاتورته، أو يعيد طباعة آخر فاتورة إذا كان الطلب فارغاً.
        btnPrint.setOnClickListener(v -> {
            if (cart.lines.isEmpty()) {
                onPrint();
            } else {
                onConfirm();
            }
        });
        findViewById(R.id.btnAdmin).setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        findViewById(R.id.btnOrders).setOnClickListener(v -> startActivity(new Intent(this, OrdersActivity.class)));
        findViewById(R.id.btnLogout).setOnClickListener(v -> onLogout());
        chipShift.setOnClickListener(v -> startActivity(new Intent(this, ShiftActivity.class)));
        tvShiftWarning.setOnClickListener(v -> startActivity(new Intent(this, ShiftActivity.class)));
        chipPrinter.setOnClickListener(v -> {
            Intent i = new Intent(this, SettingsActivity.class);
            i.putExtra(SettingsActivity.EXTRA_PRINTER, true);
            if (Session.isManager()) {
                startActivity(i);
            } else {
                Auth.requireManager(this, "إعدادات الطابعة", m -> {
                    Session.elevate(m);
                    startActivity(i);
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!sessionValid()) {
            return;
        }
        // أي موافقة مدير مؤقتة تنتهي عند العودة لشاشة الكاشير.
        Session.dropElevation();
        BtPrinter.get(this).addListener(this);
        ((TextView) findViewById(R.id.tvRestaurant)).setText(settings.restaurantName());
        chipUser.setText(Session.name() + " • " + User.roleLabel(Session.user().role));
        clock.removeCallbacks(tick);
        clock.post(tick);
        loadCategories();
        refreshShift();
        refreshPrinterChip();
        refreshCart();
    }

    @Override
    protected void onPause() {
        clock.removeCallbacks(tick);
        if (cart != null) {
            CartStore.save(this, cart);
        }
        BtPrinter.get(this).removeListener(this);
        super.onPause();
    }

    @Override
    public void onPrinterState(BtPrinter.State state, String message) {
        refreshPrinterChip();
    }

    private void refreshPrinterChip() {
        BtPrinter.State s = BtPrinter.get(this).state();
        chipPrinter.setText(BtPrinter.stateLabel(s));
        int color = s == BtPrinter.State.CONNECTED ? R.color.success
                : (s == BtPrinter.State.ERROR || s == BtPrinter.State.NOT_SELECTED ? R.color.warning : R.color.text_2);
        chipPrinter.setTextColor(getColor(color));
    }

    private void refreshShift() {
        Shift s = shifts.openShift();
        if (s == null) {
            chipShift.setText("الوردية: مغلقة");
            chipShift.setTextColor(getColor(R.color.warning));
            tvShiftWarning.setVisibility(View.VISIBLE);
        } else {
            chipShift.setText("الوردية #" + s.shiftNo + " مفتوحة");
            chipShift.setTextColor(getColor(R.color.success));
            tvShiftWarning.setVisibility(View.GONE);
        }
    }

    // ---------------- المنيو ----------------

    private void loadCategories() {
        categories.clear();
        categories.addAll(catalog.categories(true));
        boolean found = false;
        for (Category c : categories) {
            if (c.id == selectedCategory) {
                found = true;
            }
        }
        if (!found) {
            selectedCategory = categories.isEmpty() ? 0 : categories.get(0).id;
        }
        categoryAdapter.notifyDataSetChanged();
        loadProducts();
    }

    private void selectCategory(long id) {
        selectedCategory = id;
        categoryAdapter.notifyDataSetChanged();
        loadProducts();
        View grid = findViewById(R.id.gvProducts);
        grid.setAlpha(0f);
        grid.setTranslationY(12f);
        grid.animate().alpha(1f).translationY(0f).setDuration(180).start();
    }

    private void loadProducts() {
        products.clear();
        String title = "";
        if (selectedCategory > 0) {
            products.addAll(catalog.products(selectedCategory, true));
            for (Category c : categories) {
                if (c.id == selectedCategory) {
                    title = c.name;
                }
            }
        }
        tvCategoryTitle.setText(title);
        productAdapter.notifyDataSetChanged();
        if (categories.isEmpty()) {
            tvNoProducts.setText("لا توجد أقسام بعد.\nمن الإدارة ← المنيو اضغط «⬇ القائمة الرسمية» لإضافة قائمة مطعم الحجي كاملة.");
            tvNoProducts.setVisibility(View.VISIBLE);
        } else if (products.isEmpty()) {
            tvNoProducts.setText("لا توجد أصناف متاحة في هذا القسم");
            tvNoProducts.setVisibility(View.VISIBLE);
        } else {
            tvNoProducts.setVisibility(View.GONE);
        }
    }

    private String categoryName(long id) {
        for (Category c : categories) {
            if (c.id == id) {
                return c.name;
            }
        }
        return "";
    }

    private void onProduct(final Product p) {
        if (confirming) {
            return;
        }
        if (!p.hasSizes()) {
            addLine(p, null);
            return;
        }
        final List<ProductSize> sizes = p.activeSizes();
        String[] labels = new String[sizes.size()];
        for (int i = 0; i < sizes.size(); i++) {
            labels[i] = sizes.get(i).name + "   —   " + Money.format(sizes.get(i).price);
        }
        Ui.show(Ui.dialog(this, p.name + " — اختر الحجم")
                .setItems(labels, (d, which) -> addLine(p, sizes.get(which)))
                .setNegativeButton(R.string.cancel, null));
    }

    private void addLine(Product p, ProductSize size) {
        String name = size == null ? p.name : p.name + " - " + size.name;
        long price = size == null ? p.price : size.price;
        cart.add(p.id, size == null ? 0 : size.id, p.categoryId, categoryName(p.categoryId), name, price);
        refreshCart();
    }

    // ---------------- الطلب الجاري ----------------

    private void setType(String type) {
        if (confirming) {
            return;
        }
        cart.type = type;
        if (!OrderType.DINE_IN.equals(type)) {
            cart.tableLabel = "";
        }
        refreshCart();
    }

    /** خدمة الصالة: ضغطة تضيفها (وتجعل الطلب داخل المطعم)، وضغطة ثانية تزيلها. */
    private void toggleService() {
        if (confirming) {
            return;
        }
        if (cart.serviceFee > 0) {
            cart.serviceFee = 0;
            cart.type = OrderType.TAKEAWAY;
        } else {
            cart.type = OrderType.DINE_IN;
            cart.serviceFee = settings.serviceFee();
        }
        refreshCart();
    }

    private void editTable() {
        LinearLayout f = Ui.form(this);
        final EditText e = Ui.input(this, "رقم الطاولة أو اسمها (اختياري)", InputType.TYPE_CLASS_TEXT, cart.tableLabel);
        f.addView(e);
        Ui.showForm(this, "الطاولة", f, getString(R.string.save), () -> {
            String t = e.getText().toString().trim();
            if (t.length() > 20) {
                e.setError("اسم الطاولة طويل");
                return false;
            }
            cart.tableLabel = t;
            refreshCart();
            return true;
        });
    }

    private void refreshCart() {
        cart.clampDiscount();
        boolean dine = OrderType.DINE_IN.equals(cart.type);
        boolean tables = settings.getBool(Settings.TABLES_ENABLED, true);
        tvTable.setVisibility(dine && tables ? View.VISIBLE : View.GONE);
        tvTable.setText(cart.tableLabel == null || cart.tableLabel.isEmpty()
                ? "الطاولة: اضغط للإدخال (اختياري)" : "الطاولة: " + cart.tableLabel);

        lineAdapter.notifyDataSetChanged();
        tvEmpty.setVisibility(cart.lines.isEmpty() ? View.VISIBLE : View.GONE);
        tvItemCount.setText(cart.lines.isEmpty() ? "" : cart.itemCount() + " قطعة");
        long sub = cart.subtotal();
        long disc;
        long total;
        try {
            disc = cart.discountAmount();
            total = cart.total();
        } catch (IllegalArgumentException e) {
            cart.discountType = OrderMath.DISCOUNT_NONE;
            cart.discountValue = 0;
            disc = 0;
            total = sub + cart.serviceFee;
        }
        tvSubtotal.setText(Money.format(sub));
        rowDiscount.setVisibility(disc > 0 ? View.VISIBLE : View.GONE);
        tvDiscountLabel.setText(OrderMath.DISCOUNT_PERCENT.equals(cart.discountType)
                ? "الخصم (" + cart.discountValue + "%)" : "الخصم");
        tvDiscount.setText("- " + Money.format(disc));
        boolean service = cart.serviceFee > 0;
        btnService.setSelected(service);
        btnService.setText(service ? "✓ خدمة صالة\n" + Money.format(cart.serviceFee)
                : "🍽 خدمة صالة\n+" + Money.format(settings.serviceFee()));
        rowService.setVisibility(service ? View.VISIBLE : View.GONE);
        tvService.setText("+ " + Money.format(cart.serviceFee));
        tvTotal.setText(Money.format(total));
        btnPrint.setEnabled(!confirming);
        btnPrint.setText(confirming ? "جارٍ الحفظ..." : "طباعة");
        if (lastOrderId > 0) {
            Order last = orders.get(lastOrderId);
            tvLastInvoice.setText(last == null ? "" : "آخر فاتورة معتمدة: " + last.invoiceNo + "  •  " + Money.format(last.total));
        } else {
            tvLastInvoice.setText("");
        }
    }

    private void resetCart() {
        cart = new Cart();
        CartStore.clear(this);
        refreshCart();
    }

    private void onNewOrder() {
        if (confirming) {
            return;
        }
        if (cart.lines.isEmpty()) {
            lastOrderId = 0;
            resetCart();
            return;
        }
        Ui.confirm(this, "طلب جديد", "الطلب الحالي غير معتمد وسيُحذف. متابعة؟", "حذف وبدء طلب جديد", () -> {
            lastOrderId = 0;
            resetCart();
        });
    }

    private void onClearOrder() {
        if (confirming) {
            return;
        }
        if (cart.lines.isEmpty()) {
            Ui.toast(this, "الطلب فارغ");
            return;
        }
        Ui.confirm(this, "إلغاء الطلب", "حذف كل أصناف الطلب الحالي؟ لم يُعتمد بعد ولن يُسجَّل.",
                "حذف الطلب", this::resetCart);
    }

    // ---------------- الخصم ----------------

    private void onDiscount() {
        if (confirming) {
            return;
        }
        if (!settings.getBool(Settings.DISCOUNT_ENABLED, true)) {
            Ui.toast(this, "الخصومات غير مفعّلة في الإعدادات");
            return;
        }
        if (cart.lines.isEmpty()) {
            Ui.toast(this, "أضف أصنافاً أولاً");
            return;
        }
        LinearLayout f = Ui.form(this);
        final RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rFixed = radio("مبلغ ثابت", 1);
        final RadioButton rPercent = radio("نسبة مئوية %", 2);
        rg.addView(rFixed);
        rg.addView(rPercent);
        rg.check(OrderMath.DISCOUNT_PERCENT.equals(cart.discountType) ? 2 : 1);
        f.addView(rg);
        final EditText value = Ui.money(this, "القيمة (0 لإزالة الخصم)",
                cart.discountValue > 0 ? String.valueOf(cart.discountValue) : "");
        f.addView(value, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 10));
        f.addView(Ui.text(this, "المجموع قبل الخصم: " + Money.format(cart.subtotal()), 13, R.color.text_2, false));
        Ui.showForm(this, "خصم على الطلب", f, "تطبيق", () -> {
            String raw = value.getText().toString().trim();
            long v = raw.isEmpty() ? 0 : Money.parse(raw);
            if (v < 0) {
                value.setError("قيمة غير صحيحة");
                return false;
            }
            final String type = v == 0 ? OrderMath.DISCOUNT_NONE
                    : (rg.getCheckedRadioButtonId() == 2 ? OrderMath.DISCOUNT_PERCENT : OrderMath.DISCOUNT_FIXED);
            final long amount;
            try {
                amount = OrderMath.discountAmount(cart.subtotal(), type, v);
            } catch (IllegalArgumentException e) {
                value.setError(e.getMessage());
                return false;
            }
            if (amount > 0 && needsDiscountApproval(cart.subtotal(), amount)) {
                Auth.requireManager(this, "خصم " + Money.format(amount), m -> applyDiscount(type, v));
            } else {
                applyDiscount(type, v);
            }
            return true;
        });
    }

    private boolean needsDiscountApproval(long subtotal, long amount) {
        if (Session.isManager()) {
            return false;
        }
        if (!settings.getBool(Settings.CASHIER_DISCOUNT, false)) {
            return true;
        }
        int maxPercent = settings.getInt(Settings.CASHIER_DISCOUNT_MAX_PERCENT, 10);
        return subtotal > 0 && amount * 100 > subtotal * (long) maxPercent;
    }

    private void applyDiscount(String type, long value) {
        cart.discountType = type;
        cart.discountValue = OrderMath.DISCOUNT_NONE.equals(type) ? 0 : value;
        refreshCart();
    }

    private RadioButton radio(String text, int id) {
        RadioButton r = new RadioButton(this);
        r.setId(id);
        r.setText(text);
        r.setTextColor(getColor(R.color.text));
        r.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 20), 0);
        return r;
    }

    // ---------------- الاعتماد والدفع ----------------

    private void onConfirm() {
        if (confirming) {
            return;
        }
        try {
            OrderRepo.validate(cart);
        } catch (AppException e) {
            Ui.toast(this, e.getMessage());
            return;
        }
        if (shifts.openShift() == null) {
            Ui.confirm(this, "لا توجد وردية مفتوحة", "لا يمكن اعتماد الطلبات بدون وردية. فتح وردية الآن؟",
                    "فتح وردية", () -> startActivity(new Intent(this, ShiftActivity.class)));
            return;
        }
        showPayment();
    }

    private void showPayment() {
        final long due = cart.total();
        final boolean cardOn = settings.getBool(Settings.CARD_ENABLED, true);
        final boolean mixedOn = cardOn && settings.getBool(Settings.MIXED_ENABLED, true);

        LinearLayout f = Ui.form(this);
        TextView dueView = Ui.text(this, Money.format(due), 30, R.color.white, true);
        dueView.setGravity(android.view.Gravity.CENTER);
        f.addView(Ui.text(this, OrderType.label(cart.type) + "  •  " + cart.itemCount() + " قطعة", 13, R.color.text_2, false));
        f.addView(dueView, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 6));

        final RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rg.addView(radio("نقداً", 1));
        if (cardOn) {
            rg.addView(radio("بطاقة / إلكتروني", 2));
        }
        if (mixedOn) {
            rg.addView(radio("مختلط", 3));
        }
        rg.check(1);
        f.addView(rg, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 10));

        final TextView receivedLabel = Ui.label(this, "المبلغ المستلم من الزبون (اختياري لحساب الباقي)");
        final EditText received = Ui.money(this, "مثال: 20000", "");
        final TextView cardLabel = Ui.label(this, "المبلغ المدفوع إلكترونياً");
        final EditText cardPart = Ui.money(this, "جزء البطاقة", "");
        final TextView info = Ui.text(this, "", 14, R.color.success, true);
        f.addView(receivedLabel);
        f.addView(received);
        f.addView(cardLabel);
        f.addView(cardPart);
        f.addView(info, Ui.lp(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 10));
        f.addView(Ui.text(this, "الدفع الإلكتروني تسجيل محاسبي فقط ولا يرتبط بجهاز دفع.", 11, R.color.text_3, false));

        final Runnable update = () -> {
            int m = rg.getCheckedRadioButtonId();
            boolean cashVisible = m == 1 || m == 3;
            receivedLabel.setVisibility(cashVisible ? View.VISIBLE : View.GONE);
            received.setVisibility(cashVisible ? View.VISIBLE : View.GONE);
            cardLabel.setVisibility(m == 3 ? View.VISIBLE : View.GONE);
            cardPart.setVisibility(m == 3 ? View.VISIBLE : View.GONE);
            long card = m == 2 ? due : (m == 3 ? Math.max(0, Money.parse(cardPart.getText().toString())) : 0);
            long cash = due - card;
            String text = "";
            if (m == 3) {
                text = card > due ? "جزء البطاقة أكبر من المستحق" : "نقداً: " + Money.format(cash) + "  +  بطاقة: " + Money.format(card);
            }
            long got = Money.parse(received.getText().toString());
            if (cashVisible && got > 0 && cash > 0) {
                text += (text.isEmpty() ? "" : "\n") + (got >= cash ? "الباقي للزبون: " + Money.format(got - cash)
                        : "المستلم أقل من المطلوب نقداً");
            }
            info.setText(text);
        };
        android.text.TextWatcher w = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                update.run();
            }
        };
        received.addTextChangedListener(w);
        cardPart.addTextChangedListener(w);
        rg.setOnCheckedChangeListener((g, id) -> update.run());
        update.run();

        Ui.showForm(this, "الدفع واعتماد الطلب", Ui.scroll(this, f), "اعتماد", () -> {
            int m = rg.getCheckedRadioButtonId();
            long card;
            if (m == 2) {
                card = due;
            } else if (m == 3) {
                card = Money.parse(cardPart.getText().toString());
                if (card <= 0 || card >= due) {
                    cardPart.setError("أدخل جزء البطاقة (أكبر من صفر وأقل من الإجمالي)");
                    return false;
                }
            } else {
                card = 0;
            }
            long cash = due - card;
            long got = Money.parse(received.getText().toString());
            if ((m == 1 || m == 3) && got > 0 && got < cash) {
                received.setError("المبلغ المستلم أقل من المطلوب");
                return false;
            }
            commit(cash, card);
            return true;
        });
    }

    private void commit(long cash, long card) {
        if (confirming) {
            return;
        }
        confirming = true;
        refreshCart();
        try {
            long id = orders.confirm(cart, cash, card, Session.user());
            lastOrderId = id;
            Order o = orders.get(id);
            CartStore.clear(this);
            cart = new Cart();
            confirming = false;
            refreshCart();
            Ui.toast(this, "تم اعتماد الطلب — فاتورة " + (o == null ? "" : o.invoiceNo));
            if (o != null) {
                printOrder(o);
            }
        } catch (AppException e) {
            confirming = false;
            refreshCart();
            Ui.error(this, e.getMessage());
        } catch (RuntimeException e) {
            confirming = false;
            refreshCart();
            Ui.error(this, "تعذر حفظ الطلب. لم يُسجَّل أي جزء منه. أعد المحاولة.");
        }
    }

    // ---------------- الطباعة ----------------

    private void onPrint() {
        if (!cart.lines.isEmpty()) {
            Ui.toast(this, "اعتمد الطلب الحالي أولاً ثم اطبع فاتورته");
            return;
        }
        Order o = lastOrderId > 0 ? orders.get(lastOrderId) : orders.lastOrder();
        if (o == null) {
            Ui.toast(this, "لا توجد فاتورة للطباعة");
            return;
        }
        printOrder(o);
    }

    private void printOrder(final Order o) {
        btnPrint.setEnabled(false);
        btnPrint.setText("جارٍ الطباعة...");
        boolean reprint = o.printCount > 0;
        Printing.print(this, Templates.invoice(o, settings.restaurantName(),
                        settings.get(Settings.RESTAURANT_PHONE, ""),
                        settings.get(Settings.RECEIPT_FOOTER, Settings.DEFAULT_FOOTER),
                        settings.getBool(Settings.TABLES_ENABLED, true), reprint),
                settings.getInt(Settings.RECEIPT_COPIES, 1), (ok, msg) -> {
                    btnPrint.setEnabled(true);
                    btnPrint.setText("طباعة");
                    if (ok) {
                        orders.markPrinted(o.id);
                        Ui.toast(this, "طُبعت الفاتورة " + o.invoiceNo);
                    } else {
                        Ui.show(Ui.dialog(this, "لم تتم الطباعة")
                                .setMessage(msg + "\n\nالطلب محفوظ ويمكن إعادة طباعته لاحقاً.")
                                .setPositiveButton(R.string.retry, (d, w) -> printOrder(orders.get(o.id)))
                                .setNegativeButton(R.string.close, null));
                    }
                });
    }

    private void onLogout() {
        Ui.confirm(this, "تسجيل الخروج", "الخروج من حساب " + Session.name() + "؟ الطلب الجاري يبقى محفوظاً كمسودة.",
                "خروج", () -> {
                    CartStore.save(this, cart);
                    Session.logout();
                    Intent i = new Intent(this, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                });
    }

    // ---------------- المحوّلات ----------------

    private final class CategoryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return categories.size();
        }

        @Override
        public Object getItem(int i) {
            return categories.get(i);
        }

        @Override
        public long getItemId(int i) {
            return categories.get(i).id;
        }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            TextView t = (TextView) (v != null ? v : getLayoutInflater().inflate(R.layout.item_category, parent, false));
            Category c = categories.get(i);
            t.setText(c.name);
            t.setSelected(c.id == selectedCategory);
            t.setTextColor(getColor(c.id == selectedCategory ? R.color.white : R.color.text));
            return t;
        }
    }

    private final class ProductAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return products.size();
        }

        @Override
        public Object getItem(int i) {
            return products.get(i);
        }

        @Override
        public long getItemId(int i) {
            return products.get(i).id;
        }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            View row = v != null ? v : getLayoutInflater().inflate(R.layout.item_product, parent, false);
            Product p = products.get(i);
            ((TextView) row.findViewById(R.id.tvName)).setText(p.name);
            ((TextView) row.findViewById(R.id.tvPrice)).setText(p.hasSizes()
                    ? "من " + Money.format(p.minPrice()) : Money.format(p.price));
            return row;
        }
    }

    private final class LineAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return cart.lines.size();
        }

        @Override
        public Object getItem(int i) {
            return cart.lines.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            View row = v != null ? v : getLayoutInflater().inflate(R.layout.item_line, parent, false);
            final CartLine l = cart.lines.get(i);
            ((TextView) row.findViewById(R.id.tvName)).setText(l.name);
            ((TextView) row.findViewById(R.id.tvUnit)).setText(Money.format(l.unitPrice));
            ((TextView) row.findViewById(R.id.tvQty)).setText(String.valueOf(l.qty));
            ((TextView) row.findViewById(R.id.tvTotal)).setText(Money.number(l.total()));
            row.findViewById(R.id.btnPlus).setOnClickListener(x -> {
                if (!confirming) {
                    cart.setQty(l, l.qty + 1);
                    refreshCart();
                }
            });
            row.findViewById(R.id.btnMinus).setOnClickListener(x -> {
                if (!confirming && l.qty > 1) {
                    cart.setQty(l, l.qty - 1);
                    refreshCart();
                }
            });
            row.findViewById(R.id.btnRemove).setOnClickListener(x -> {
                if (!confirming) {
                    cart.lines.remove(l);
                    refreshCart();
                }
            });
            row.findViewById(R.id.tvQty).setOnClickListener(x -> editQty(l));
            return row;
        }
    }

    private void editQty(final CartLine l) {
        if (confirming) {
            return;
        }
        LinearLayout f = Ui.form(this);
        final EditText e = Ui.money(this, "الكمية", String.valueOf(l.qty));
        f.addView(e);
        Ui.showForm(this, l.name, f, getString(R.string.save), () -> {
            long q = Money.parse(e.getText().toString());
            if (q < 1 || q > OrderMath.MAX_QTY) {
                e.setError("الكمية بين 1 و " + OrderMath.MAX_QTY);
                return false;
            }
            cart.setQty(l, (int) q);
            refreshCart();
            return true;
        });
    }
}
