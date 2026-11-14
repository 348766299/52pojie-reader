package cn.read52.app;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 轻量崩溃记录：把未捕获异常的堆栈写到应用私有目录，方便没有 logcat 的环境排障。
 * 只写本机文件，不联网、不上传；写完仍交回系统默认处理器，保持原有闪退行为。
 */
final class CrashLog {

    private static final String FILE = "crash.log";
    private static final long MAX = 64 * 1024;

    private CrashLog() { }

    static void install(Context ctx) {
        final Context app = ctx.getApplicationContext();
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        if (old instanceof Recorder) return;   // 避免重复安装
        Thread.setDefaultUncaughtExceptionHandler(new Recorder(app, old));
    }

    private static final class Recorder implements Thread.UncaughtExceptionHandler {
        private final Context ctx;
        private final Thread.UncaughtExceptionHandler old;

        Recorder(Context c, Thread.UncaughtExceptionHandler o) {
            ctx = c;
            old = o;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            try { record(ctx, t, e); } catch (Throwable ignored) { }
            if (old != null) old.uncaughtException(t, e);
        }
    }

    /** 已捕获但值得留痕的异常（例如某个机型上创建第二个 WebView 失败）。 */
    static void note(Context c, Throwable e) {
        try { record(c.getApplicationContext(), Thread.currentThread(), e); }
        catch (Throwable ignored) { }
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    private static synchronized void record(Context c, Thread t, Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();

        String head = "==== " + stamp() + " ====\n"
                + "thread: " + (t == null ? "?" : t.getName()) + "\n"
                + "device: " + Build.MANUFACTURER + " " + Build.MODEL
                + " | Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "app: wuai-reader 1.0.1\n\n";

        FileOutputStream out = null;
        try {
            File f = file(c);
            if (f.exists() && f.length() > MAX) f.delete();
            out = new FileOutputStream(f, true);
            out.write((head + sw.toString() + "\n").getBytes("UTF-8"));
            out.flush();
        } catch (Exception ignored) { }
        finally {
            if (out != null) {
                try { out.close(); } catch (Exception ignored) { }
            }
        }
    }

    static boolean exists(Context c) {
        File f = file(c);
        return f.isFile() && f.length() > 0;
    }

    static String read(Context c) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file(c));
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            String s = new String(bo.toByteArray(), "UTF-8");
            return s.length() > 12000 ? s.substring(s.length() - 12000) : s;
        } catch (Exception e) {
            return "日志读取失败：" + e.getMessage();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) { }
            }
        }
    }

    static void clear(Context c) {
        try { file(c).delete(); } catch (Exception ignored) { }
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}
