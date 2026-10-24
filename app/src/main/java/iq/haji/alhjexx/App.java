package iq.haji.alhjexx;

import android.app.Application;

import iq.haji.alhjexx.data.Db;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // فتح قاعدة البيانات مبكراً لإنشاء الجداول عند أول تشغيل.
        Db.get(this).getWritableDatabase();
    }
}
