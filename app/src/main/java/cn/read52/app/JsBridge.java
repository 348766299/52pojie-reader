package cn.read52.app;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

/** JS -> Java 通道。WebView 会在非 UI 线程调用，统一切回主线程。 */
final class JsBridge {

    interface Host {
        void onOpenImage(String json);
        void onLinks(String json);
        void onPageInfo(String json);
    }

    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());

    JsBridge(Host host) { this.host = host; }

    @JavascriptInterface
    public void openImage(final String json) {
        main.post(new Runnable() {
            @Override public void run() { host.onOpenImage(json); }
        });
    }

    @JavascriptInterface
    public void onLinks(final String json) {
        main.post(new Runnable() {
            @Override public void run() { host.onLinks(json); }
        });
    }

    @JavascriptInterface
    public void onPageInfo(final String json) {
        main.post(new Runnable() {
            @Override public void run() { host.onPageInfo(json); }
        });
    }
}
