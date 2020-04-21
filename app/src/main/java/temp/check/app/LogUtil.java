package temp.check.app;

import android.util.Log;

public class LogUtil {

    public static void v(String msg) {
        Log.v("LogUtil", msg);
    }

    public static void d(String msg) {
        Log.d("LogUtil", msg);
    }

    public static void i(String msg) {
        Log.i("LogUtil", msg);
    }

    public static void w(String msg) {
        Log.w("LogUtil", msg);
    }

    public static void e(String msg) {
        Log.e("LogUtil", msg);
    }
}
