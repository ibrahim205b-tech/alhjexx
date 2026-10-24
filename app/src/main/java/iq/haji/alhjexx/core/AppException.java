package iq.haji.alhjexx.core;

/** خطأ متوقع برسالة عربية جاهزة للعرض على المستخدم. */
public class AppException extends RuntimeException {
    public AppException(String message) {
        super(message);
    }
}
