package iq.haji.alhjexx.print;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import iq.haji.alhjexx.data.Settings;

/**
 * الاتصال بالطابعة الحرارية عبر Bluetooth الكلاسيكي (ملف SPP).
 * تُعرض الأجهزة المقترنة مسبقاً من إعدادات النظام؛ لا يحتاج ذلك إذن الموقع.
 * كل عمليات الاتصال تتم في خيط خلفي واحد بالترتيب.
 */
public final class BtPrinter {

    public enum State { NOT_SELECTED, DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    public interface Listener {
        void onPrinterState(State state, String message);
    }

    public interface Callback {
        void onDone(boolean ok, String message);
    }

    public static final class Device {
        public final String name;
        public final String address;

        Device(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static volatile BtPrinter instance;

    private final Context ctx;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private BluetoothSocket socket;
    private String socketAddress;
    private volatile State state = State.DISCONNECTED;
    private volatile String stateMessage = "";

    public static BtPrinter get(Context c) {
        if (instance == null) {
            synchronized (BtPrinter.class) {
                if (instance == null) {
                    instance = new BtPrinter(c.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private BtPrinter(Context ctx) {
        this.ctx = ctx;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public State state() {
        return selectedAddress().isEmpty() ? State.NOT_SELECTED : state;
    }

    public String stateMessage() {
        return stateMessage;
    }

    private void setState(State s, String msg) {
        state = s;
        stateMessage = msg == null ? "" : msg;
        main.post(() -> {
            for (Listener l : listeners) {
                l.onPrinterState(state(), stateMessage);
            }
        });
    }

    public static String stateLabel(State s) {
        switch (s) {
            case NOT_SELECTED:
                return "الطابعة: غير محددة";
            case CONNECTING:
                return "الطابعة: جارٍ الاتصال";
            case CONNECTED:
                return "الطابعة: متصلة";
            case ERROR:
                return "الطابعة: خطأ اتصال";
            default:
                return "الطابعة: تتصل عند الطباعة";
        }
    }

    /** إذن BLUETOOTH_CONNECT مطلوب على Android 12 وما بعده. */
    public static boolean hasPermission(Context c) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private BluetoothAdapter adapter() {
        BluetoothManager m = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        return m == null ? null : m.getAdapter();
    }

    public boolean isBluetoothAvailable() {
        return adapter() != null;
    }

    public boolean isBluetoothOn() {
        BluetoothAdapter a = adapter();
        return a != null && a.isEnabled();
    }

    @SuppressLint("MissingPermission")
    public List<Device> pairedDevices() {
        List<Device> out = new ArrayList<>();
        BluetoothAdapter a = adapter();
        if (a == null || !hasPermission(ctx)) {
            return out;
        }
        Set<BluetoothDevice> set = a.getBondedDevices();
        if (set == null) {
            return out;
        }
        for (BluetoothDevice d : set) {
            String name = d.getName();
            out.add(new Device(name == null || name.isEmpty() ? d.getAddress() : name, d.getAddress()));
        }
        return out;
    }

    public String selectedAddress() {
        return new Settings(ctx).get(Settings.PRINTER_ADDRESS, "").trim();
    }

    public String selectedName() {
        return new Settings(ctx).get(Settings.PRINTER_NAME, "").trim();
    }

    public void select(Device d) {
        Settings s = new Settings(ctx);
        s.set(Settings.PRINTER_ADDRESS, d.address);
        s.set(Settings.PRINTER_NAME, d.name);
        worker.execute(this::closeQuietly);
        setState(State.DISCONNECTED, "");
    }

    /** يرسل البيانات للطابعة، مع إعادة اتصال ومحاولة ثانية واحدة عند انقطاع الاتصال. */
    public void send(final byte[] data, final Callback cb) {
        worker.execute(() -> {
            String err = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    OutputStream out = ensureConnected();
                    int chunk = 2048;
                    for (int i = 0; i < data.length; i += chunk) {
                        out.write(data, i, Math.min(chunk, data.length - i));
                        out.flush();
                        Thread.sleep(15);
                    }
                    setState(State.CONNECTED, "");
                    err = null;
                    break;
                } catch (IOException e) {
                    closeQuietly();
                    err = "تعذر الإرسال للطابعة. تأكد أنها مشغّلة وقريبة ثم أعد المحاولة.";
                } catch (SecurityException e) {
                    err = "إذن الأجهزة القريبة غير ممنوح. امنحه من إعدادات الطابعة.";
                    break;
                } catch (IllegalStateException e) {
                    err = e.getMessage();
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    err = "توقفت الطباعة";
                    break;
                }
            }
            if (err != null) {
                setState(State.ERROR, err);
            }
            final String msg = err;
            main.post(() -> cb.onDone(msg == null, msg == null ? "تم الإرسال إلى الطابعة" : msg));
        });
    }

    @SuppressLint("MissingPermission")
    private OutputStream ensureConnected() throws IOException {
        String address = selectedAddress();
        if (address.isEmpty()) {
            throw new IllegalStateException("لم تُحدد طابعة. اخترها من الإعدادات ← الطابعة.");
        }
        if (!hasPermission(ctx)) {
            throw new SecurityException("no permission");
        }
        BluetoothAdapter a = adapter();
        if (a == null) {
            throw new IllegalStateException("هذا الجهاز لا يدعم Bluetooth");
        }
        if (!a.isEnabled()) {
            throw new IllegalStateException("Bluetooth مطفأ. شغّله ثم أعد المحاولة.");
        }
        if (socket != null && socket.isConnected() && address.equals(socketAddress)) {
            return socket.getOutputStream();
        }
        closeQuietly();
        setState(State.CONNECTING, "");
        // إيقاف البحث يحتاج إذن BLUETOOTH_SCAN على Android 12+ وهو غير مطلوب للاتصال بطابعة مقترنة،
        // فلا نجعل غيابه يُظهر رسالة «الإذن غير ممنوح» رغم منح إذن الأجهزة القريبة.
        try {
            a.cancelDiscovery();
        } catch (SecurityException ignored) {
            // لا يوجد بحث جارٍ من التطبيق أصلاً
        }
        BluetoothDevice device = a.getRemoteDevice(address);
        BluetoothSocket s;
        try {
            s = device.createRfcommSocketToServiceRecord(SPP);
            s.connect();
        } catch (IOException first) {
            s = device.createInsecureRfcommSocketToServiceRecord(SPP);
            s.connect();
        }
        socket = s;
        socketAddress = address;
        setState(State.CONNECTED, "");
        return s.getOutputStream();
    }

    private void closeQuietly() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // الإغلاق فقط
            }
        }
        socket = null;
        socketAddress = null;
    }
}
