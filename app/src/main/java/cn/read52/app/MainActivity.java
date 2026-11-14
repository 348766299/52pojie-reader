package cn.read52.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 吾爱阅读 · 主界面
 *
 * 设计取向：不自己实现论坛的网络协议，而是用 WebView 承载原站（登录态、签到、
 * 回复、附件全部保留），再通过注入 CSS/JS 专门解决「手机上看帖不方便」这件事。
 * 站点改版时只需调整 assets/enhance.css 和 enhance.js，不会像纯抓取型客户端那样整体失效。
 */
public class MainActivity extends Activity implements JsBridge.Host {

    private static final String HOME = "https://www.52pojie.cn/forum.php";
    private static final String FORUM = "https://www.52pojie.cn/forum.php?mod=guide&view=newthread";
    private static final String TASK = "https://www.52pojie.cn/home.php?mod=task";
    private static final String UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final int REQ_FILE = 1001;

    private WebView web;
    private ProgressBar progress;
    private TextView tvTitle;
    private View rootView, topBar, bottomBar;
    private Prefs prefs;

    private String currentUrl = HOME;
    private String bootScript, jsB64, cssB64;
    private boolean injected = false;
    private boolean showingError = false;
    private boolean pageIsThread = false;
    private long lastBack = 0;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLog.install(this);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);

        rootView = findViewById(R.id.root);
        topBar = findViewById(R.id.top_bar);
        bottomBar = findViewById(R.id.bottom_bar);
        tvTitle = findViewById(R.id.tv_title);
        progress = findViewById(R.id.progress);
        web = findViewById(R.id.web);

        cacheAssets();
        bindButtons();
        setupWebView();
        applyTheme();

        Uri deepLink = getIntent() == null ? null : getIntent().getData();
        web.loadUrl(deepLink != null ? deepLink.toString() : HOME);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri u = intent == null ? null : intent.getData();
        if (u != null) web.loadUrl(u.toString());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyTheme();
        if (injected && prefs.night() == Prefs.NIGHT_FOLLOW) inject();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private void cacheAssets() {
        try {
            bootScript = Assets.read(this, "boot.js");
            jsB64 = Assets.b64(Assets.read(this, "enhance.js"));
            cssB64 = Assets.b64(Assets.read(this, "enhance.css"));
        } catch (Exception e) {
            bootScript = null;
            toast("增强脚本读取失败：" + e.getMessage());
        }
    }

    private void bindButtons() {
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goBack(); }
        });
        findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { web.reload(); }
        });
        findViewById(R.id.btn_more).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMoreMenu(); }
        });
        tvTitle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSearch(); }
        });
        findViewById(R.id.nav_home).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { web.loadUrl(HOME); }
        });
        findViewById(R.id.nav_forum).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { web.loadUrl(FORUM); }
        });
        findViewById(R.id.nav_read).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showModeDialog(); }
        });
        findViewById(R.id.nav_fav).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showList(Store.KEY_FAV, "我的收藏"); }
        });
        findViewById(R.id.nav_settings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        // 站点大量链接带 target="_blank"（首页「人气热门（更多 New~）」、
        // 帖子列表标题等）。开启多窗口后这类点击会回调 onCreateWindow，
        // 若把主 WebView 当作新窗口复用，同一个 WebView 会被两个窗口持有，
        // 在部分机型上直接触发 native 崩溃 / 闪退。单 WebView 应用关掉多窗口，
        // 让 _blank 统一在当前页面导航，是最稳的做法。
        s.setSupportMultipleWindows(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(100);
        s.setUserAgentString(UA);
        s.setAllowFileAccess(false);
        s.setGeolocationEnabled(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new JsBridge(this), "R52Bridge");
        web.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request == null ? null : request.getUrl());
            }

            // 部分 ROM / 老页面仍会走这个已废弃的重载，一并接管，避免漏判 scheme
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (TextUtils.isEmpty(url)) return true;
                Uri u;
                try { u = Uri.parse(url); } catch (Exception e) { return true; }
                return handleUrl(u);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                currentUrl = url;
                injected = false;
                progress.setVisibility(View.VISIBLE);
                progress.setProgress(5);
                if (!TextUtils.isEmpty(url)) tvTitle.setText(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (showingError) { showingError = false; return; }
                currentUrl = url;
                String t = view.getTitle();
                tvTitle.setText(TextUtils.isEmpty(t) ? url : cleanTitle(t));
                inject();
                view.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (web != null && !isFinishing() && !isDestroyed()) injectIfNeeded();
                    }
                }, 500);
                recordHistory(url, view.getTitle());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) showErrorPage();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (prefs.nopic() && request != null && request.getUrl() != null
                        && isImage(request.getUrl().toString())) {
                    return new WebResourceResponse("image/png", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progress.setProgress(Math.max(5, p));
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (!TextUtils.isEmpty(title)) tvTitle.setText(cleanTitle(title));
            }

            // 登录 / 发帖时可能需要选择图片或附件
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             WebChromeClient.FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(Intent.createChooser(params.createIntent(), "选择文件"), REQ_FILE);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                openExternal(url));

        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { return handleLongClick(); }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || filePathCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            results = new Uri[]{ data.getData() };
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    /* ---------------- 注入 ---------------- */

    private int effectiveMode(String url) {
        if (prefs.autoReader()) return isThreadUrl(url) ? Prefs.MODE_READER : Prefs.MODE_OPT;
        return prefs.mode();
    }

    private JSONObject optionsJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("mode", effectiveMode(currentUrl));
            o.put("scale", (double) prefs.scale());
            o.put("night", prefs.isNight(this));
            o.put("tidy", prefs.tidy());
            o.put("zoom", prefs.zoom());
            o.put("nopic", prefs.nopic());
            o.put("onlyOp", prefs.onlyOp());
        } catch (Exception ignored) { }
        return o;
    }

    private void inject() {
        if (web == null || bootScript == null || isFinishing() || isDestroyed()) return;
        try {
            String script = bootScript + "\nR52BOOT(" + Assets.quote(jsB64) + ","
                    + Assets.quote(cssB64) + "," + optionsJson() + ");";
            web.evaluateJavascript(script, null);
            injected = true;
        } catch (Throwable t) {
            CrashLog.note(this, t);
        }
    }

    /** 少数页面有异步渲染，只在脚本确实没落地时补一次，避免重建阅读层丢失滚动位置。 */
    private void injectIfNeeded() {
        if (web == null || bootScript == null || isFinishing() || isDestroyed()) return;
        try {
            web.evaluateJavascript("(window.R52&&window.R52.__v)?'1':'0'", new ValueCallback<String>() {
                @Override public void onReceiveValue(String value) {
                    if (!"\"1\"".equals(value)) inject();
                }
            });
        } catch (Throwable t) {
            CrashLog.note(this, t);
        }
    }

    /** 统一处理跳转：http(s) 留在 WebView；伪协议直接忽略；其余交给系统。 */
    private boolean handleUrl(Uri u) {
        if (u == null) return true;
        String scheme = u.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
        String raw = u.toString().toLowerCase(Locale.ROOT);
        if (raw.startsWith("javascript:") || raw.startsWith("about:")
                || raw.startsWith("data:") || raw.startsWith("blob:")
                || raw.startsWith("file:")) return true;
        openExternal(u.toString());
        return true;
    }

    /* ---------------- 外观 ---------------- */

    private void applyTheme() {
        boolean night = prefs.isNight(this);
        int bar = getColor(night ? R.color.bg_bar_night : R.color.bg_bar);
        int bg = getColor(night ? R.color.bg_main_night : R.color.bg_main);
        int fg = getColor(night ? R.color.text_primary_night : R.color.text_primary);
        rootView.setBackgroundColor(bg);
        topBar.setBackgroundColor(bar);
        bottomBar.setBackgroundColor(bar);
        if (web != null) web.setBackgroundColor(bg);
        tint(topBar, fg);
        tint(bottomBar, fg);
        getWindow().setStatusBarColor(bar);
        getWindow().setNavigationBarColor(bar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (night) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decor.setSystemUiVisibility(flags);
        }
    }

    private void tint(View group, int color) {
        if (group instanceof TextView) ((TextView) group).setTextColor(color);
        if (group instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) group;
            for (int i = 0; i < g.getChildCount(); i++) tint(g.getChildAt(i), color);
        }
    }

    /* ---------------- 菜单 ---------------- */

    private void showModeDialog() {
        final String[] items = { "原版（站点原样）", "优化排版（推荐）", "沉浸阅读（只读卡片）" };
        int checked = effectiveMode(currentUrl);
        new AlertDialog.Builder(this)
                .setTitle("阅读模式")
                .setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        prefs.autoReader(false);
                        prefs.mode(which);
                        inject();
                        d.dismiss();
                        toast("已切换：" + items[which]);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showMoreMenu() {
        final String[] items = {
            "收藏本页", "阅读历史", "提取下载链接", "只看楼主（开/关）", "站内搜索",
            "论坛任务 · 签到入口", "复制当前链接", "用浏览器打开", "分享本页", "关于"
        };
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0:
                                Store.add(MainActivity.this, Store.KEY_FAV,
                                        cleanTitle(tvTitle.getText().toString()), currentUrl, 500);
                                toast("已加入收藏");
                                break;
                            case 1: showList(Store.KEY_HIS, "阅读历史"); break;
                            case 2:
                                web.evaluateJavascript(
                                        "if(window.R52&&R52.extractLinks){R52.extractLinks();}", null);
                                break;
                            case 3:
                                prefs.onlyOp(!prefs.onlyOp());
                                inject();
                                toast(prefs.onlyOp() ? "只看楼主：开" : "只看楼主：关");
                                break;
                            case 4: showSearch(); break;
                            case 5: web.loadUrl(TASK); break;
                            case 6: copy(currentUrl); toast("链接已复制"); break;
                            case 7: openExternal(currentUrl); break;
                            case 8: share(); break;
                            case 9: showAbout(); break;
                            default: break;
                        }
                    }
                })
                .show();
    }

    private void showSearch() {
        final EditText et = new EditText(this);
        et.setHint("输入关键词");
        et.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("搜索")
                .setView(padded(et))
                .setPositiveButton("论坛站内", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String k = et.getText().toString().trim();
                        if (k.isEmpty()) return;
                        web.loadUrl("https://www.52pojie.cn/search.php?mod=forum&srchtxt="
                                + enc(k) + "&searchsubmit=yes");
                    }
                })
                .setNeutralButton("百度站内", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String k = et.getText().toString().trim();
                        if (k.isEmpty()) return;
                        web.loadUrl("https://www.baidu.com/s?wd=" + enc("site:52pojie.cn " + k));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("关于 吾爱阅读")
                .setMessage("版本 1.0.1（修复首页热门 / 帖子链接闪退）\n\n"
                        + "基于 WebView 的论坛阅读增强客户端：保留原站全部功能，"
                        + "通过注入脚本优化手机排版、提供沉浸阅读、夜间模式、字号调节、"
                        + "收藏与下载链接提取。\n\n"
                        + "所有数据只保存在本机，不上传、不请求任何第三方统计接口。\n\n"
                        + "仅供个人阅读使用，请遵守论坛规则与版权要求。")
                .setPositiveButton("知道了", null)
                .show();
    }

    /* ---------------- 设置 ---------------- */

    private void showSettings() {
        ScrollView scroll = new ScrollView(this);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(8), pad, dp(8));
        scroll.addView(box);

        box.addView(section("显示"));

        final Spinner nightSp = new Spinner(this);
        ArrayAdapter<String> nightAd = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{ "跟随系统", "始终开启", "始终关闭" });
        nightAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        nightSp.setAdapter(nightAd);
        nightSp.setSelection(prefs.night());
        box.addView(labeled("夜间模式", nightSp));

        final TextView fontLabel = new TextView(this);
        fontLabel.setText("正文字号：" + prefs.font() + "%");
        fontLabel.setTextSize(14);
        box.addView(fontLabel);
        final SeekBar fontBar = new SeekBar(this);
        fontBar.setMax(8);
        fontBar.setProgress((prefs.font() - 80) / 10);
        fontBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                fontLabel.setText("正文字号：" + (80 + p * 10) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
        box.addView(fontBar);

        box.addView(section("阅读"));

        final TextView modeHint = new TextView(this);
        modeHint.setText("默认模式（关闭下面的自动切换后生效）");
        modeHint.setTextSize(12);
        box.addView(modeHint);
        final Spinner modeSp = new Spinner(this);
        ArrayAdapter<String> modeAd = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{ "原版", "优化排版", "沉浸阅读" });
        modeAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSp.setAdapter(modeAd);
        modeSp.setSelection(prefs.mode());
        box.addView(modeSp);

        final Switch swAuto = toggle("进入帖子自动切换沉浸阅读");
        swAuto.setChecked(prefs.autoReader());
        box.addView(swAuto);
        final Switch swTidy = toggle("精简页面（隐藏页脚 / 侧栏 / 推广位）");
        swTidy.setChecked(prefs.tidy());
        box.addView(swTidy);
        final Switch swZoom = toggle("允许双指缩放（修复站点禁止缩放）");
        swZoom.setChecked(prefs.zoom());
        box.addView(swZoom);
        final Switch swNoPic = toggle("隐藏图片（省流量）");
        swNoPic.setChecked(prefs.nopic());
        box.addView(swNoPic);

        box.addView(section("数据"));

        Button btnClearHis = new Button(this);
        btnClearHis.setText("清空阅读历史");
        btnClearHis.setAllCaps(false);
        btnClearHis.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Store.clear(MainActivity.this, Store.KEY_HIS);
                toast("阅读历史已清空");
            }
        });
        box.addView(btnClearHis);

        Button btnClearFav = new Button(this);
        btnClearFav.setText("清空收藏");
        btnClearFav.setAllCaps(false);
        btnClearFav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Store.clear(MainActivity.this, Store.KEY_FAV);
                toast("收藏已清空");
            }
        });
        box.addView(btnClearFav);

        Button btnClearCache = new Button(this);
        btnClearCache.setText("清除网页缓存（不影响登录状态）");
        btnClearCache.setAllCaps(false);
        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                web.clearCache(true);
                toast("缓存已清除");
            }
        });
        box.addView(btnClearCache);

        Button btnCrash = new Button(this);
        btnCrash.setText("查看崩溃日志（排障用）");
        btnCrash.setAllCaps(false);
        btnCrash.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showCrashLog(); }
        });
        box.addView(btnCrash);

        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(scroll)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        prefs.night(nightSp.getSelectedItemPosition());
                        prefs.font(80 + fontBar.getProgress() * 10);
                        prefs.mode(modeSp.getSelectedItemPosition());
                        prefs.autoReader(swAuto.isChecked());
                        prefs.tidy(swTidy.isChecked());
                        prefs.zoom(swZoom.isChecked());
                        prefs.nopic(swNoPic.isChecked());
                        applyTheme();
                        inject();
                        toast("设置已保存");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 没有 logcat 的环境里，让用户能直接把崩溃堆栈复制出来。 */
    private void showCrashLog() {
        if (!CrashLog.exists(this)) {
            toast("暂无崩溃记录");
            return;
        }
        final String log = CrashLog.read(this);
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(log);
        tv.setTextSize(11);
        tv.setTextIsSelectable(true);
        int p = dp(16);
        tv.setPadding(p, p, p, p);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("崩溃日志（可长按选中，或直接复制全部）")
                .setView(sv)
                .setPositiveButton("复制全部", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        copy(log);
                        toast("已复制崩溃日志");
                    }
                })
                .setNeutralButton("清空", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        CrashLog.clear(MainActivity.this);
                        toast("已清空");
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(getColor(R.color.accent));
        t.setPadding(0, dp(16), 0, dp(4));
        return t;
    }

    private Switch toggle(String text) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextSize(14);
        s.setPadding(0, dp(10), 0, dp(10));
        return s;
    }

    private View labeled(String label, View control) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        l.addView(t);
        control.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        l.addView(control);
        return l;
    }

    private View padded(View v) {
        FrameLayout f = new FrameLayout(this);
        int p = dp(20);
        f.setPadding(p, dp(6), p, 0);
        f.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return f;
    }

    /* ---------------- 列表 / 链接 / 图片 ---------------- */

    private void showList(final String key, final String title) {
        final List<Store.Item> data = Store.load(this, key);
        if (data.isEmpty()) {
            toast(title + "还是空的");
            return;
        }
        final boolean night = prefs.isNight(this);
        final int fg = night ? 0xFFDDE1E8 : 0xFF1F2328;
        final int sub = night ? 0xFF858C99 : 0xFF727984;
        final int bg = night ? 0xFF1A1D23 : 0xFFFFFFFF;

        ArrayAdapter<Store.Item> adapter = new ArrayAdapter<Store.Item>(
                this, android.R.layout.simple_list_item_2, data) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                View v = super.getView(pos, cv, parent);
                v.setBackgroundColor(bg);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                Store.Item it = getItem(pos);
                if (t1 != null) {
                    t1.setText(it == null ? "" : it.title);
                    t1.setTextColor(fg);
                    t1.setSingleLine(true);
                    t1.setEllipsize(TextUtils.TruncateAt.END);
                }
                if (t2 != null && it != null) {
                    t2.setText(fmt(it.time) + "  ·  " + it.url);
                    t2.setTextColor(sub);
                    t2.setSingleLine(true);
                    t2.setEllipsize(TextUtils.TruncateAt.END);
                }
                return v;
            }
        };

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(title + "（共 " + data.size() + " 条，长按删除）")
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        web.loadUrl(data.get(which).url);
                    }
                })
                .setNegativeButton("关闭", null)
                .create();

        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                if (night && dlg.getWindow() != null) {
                    dlg.getWindow().setBackgroundDrawable(new ColorDrawable(bg));
                }
                if (dlg.getListView() == null) return;
                dlg.getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent, View v, final int pos, long id) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("删除这条记录？")
                                .setMessage(data.get(pos).title)
                                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d2, int w) {
                                        Store.remove(MainActivity.this, key, pos);
                                        dlg.dismiss();
                                        showList(key, title);
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                        return true;
                    }
                });
            }
        });
        dlg.show();
    }

    @Override
    public void onLinks(String json) {
        try {
            final JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) {
                toast("本页没有找到网盘 / 附件链接");
                return;
            }
            final String[] labels = new String[arr.length()];
            final String[] urls = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                urls[i] = o.optString("url", "");
                labels[i] = (i + 1) + ". " + o.optString("text", urls[i]);
            }
            new AlertDialog.Builder(this)
                    .setTitle("找到 " + arr.length() + " 个下载链接（点击复制）")
                    .setItems(labels, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int which) {
                            copy(urls[which]);
                            toast("链接已复制");
                        }
                    })
                    .setPositiveButton("全部复制", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            StringBuilder sb = new StringBuilder();
                            for (String u : urls) sb.append(u).append('\n');
                            copy(sb.toString());
                            toast("已复制全部链接");
                        }
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Exception e) {
            toast("链接解析失败");
        }
    }

    @Override
    public void onPageInfo(String json) {
        try {
            pageIsThread = new JSONObject(json).optBoolean("thread", false);
        } catch (Exception ignored) { }
    }

    @Override
    public void onOpenImage(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONArray a = o.optJSONArray("urls");
            ArrayList<String> urls = new ArrayList<>();
            if (a != null) {
                for (int i = 0; i < a.length(); i++) urls.add(a.optString(i));
            }
            int idx = Math.max(0, Math.min(o.optInt("index", 0), Math.max(0, urls.size() - 1)));
            try {
                showImageDialog(urls, idx);
            } catch (Throwable t) {
                // 个别机型创建第二个 WebView 会失败，降级为用外部浏览器打开
                CrashLog.note(this, t);
                if (!urls.isEmpty()) openExternal(urls.get(idx));
            }
        } catch (Exception ignored) { }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showImageDialog(final ArrayList<String> urls, int index) {
        if (urls.isEmpty() || isFinishing()) return;
        final Dialog dlg = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.BLACK);

        final WebView iv = new WebView(this);
        WebSettings s = iv.getSettings();
        s.setJavaScriptEnabled(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        iv.setBackgroundColor(Color.BLACK);
        box.addView(iv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF141414);
        final int[] cur = { index };
        final Button prev = barButton("上一张");
        final Button count = barButton("");
        final Button next = barButton("下一张");
        final Button open = barButton("打开");
        final Button close = barButton("关闭");
        Button[] all = { prev, count, next, open, close };
        for (Button b : all) {
            bar.addView(b, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        box.addView(bar);

        final Runnable render = new Runnable() {
            @Override public void run() {
                String u = urls.get(cur[0]);
                count.setText((cur[0] + 1) + "/" + urls.size());
                String html = "<!DOCTYPE html><html><head>"
                        + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes'>"
                        + "<style>html,body{margin:0;width:100%;height:100%;background:#000}"
                        + "body{display:-webkit-box;-webkit-box-pack:center;-webkit-box-align:center}"
                        + "img{max-width:100%;max-height:100%;object-fit:contain}</style>"
                        + "</head><body><img src='" + escHtml(u) + "' alt='图片加载失败'></body></html>";
                iv.loadDataWithBaseURL(u, html, "text/html", "utf-8", null);
            }
        };
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (cur[0] > 0) { cur[0]--; render.run(); }
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (cur[0] < urls.size() - 1) { cur[0]++; render.run(); }
            }
        });
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openExternal(urls.get(cur[0])); }
        });
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); }
        });

        dlg.setContentView(box);
        dlg.show();
        render.run();
    }

    private Button barButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setAllCaps(false);
        return b;
    }

    /* ---------------- 杂项 ---------------- */

    private boolean handleLongClick() {
        WebView.HitTestResult r = web.getHitTestResult();
        if (r == null) return false;
        int type = r.getType();
        final String extra = r.getExtra();
        if (TextUtils.isEmpty(extra)) return false;
        final boolean image = type == WebView.HitTestResult.IMAGE_TYPE;
        String[] items;
        if (image) {
            items = new String[]{ "复制图片地址", "用浏览器打开图片" };
        } else if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            items = new String[]{ "复制链接", "用浏览器打开", "收藏该链接" };
        } else {
            return false;
        }
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) { copy(extra); toast("已复制"); }
                        else if (which == 1) { openExternal(extra); }
                        else {
                            Store.add(MainActivity.this, Store.KEY_FAV, extra, extra, 500);
                            toast("已收藏");
                        }
                    }
                })
                .show();
        return true;
    }

    private void showErrorPage() {
        showingError = true;
        String html = "<!DOCTYPE html><html><head><meta name='viewport' "
                + "content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:#f5f6f8;color:#333;"
                + "text-align:center;padding:64px 24px}h2{font-size:18px}p{color:#888;"
                + "font-size:14px;line-height:1.8}button{margin-top:20px;padding:10px 28px;"
                + "border:0;border-radius:20px;background:#d23f3a;color:#fff;font-size:14px}</style>"
                + "</head><body><h2>页面加载失败</h2>"
                + "<p>请检查网络连接，或稍后重试。<br>论坛偶发维护时也可能导致加载失败。</p>"
                + "<button onclick='location.reload()'>重新加载</button></body></html>";
        web.loadDataWithBaseURL(currentUrl, html, "text/html", "utf-8", null);
    }

    private void goBack() {
        if (web != null && web.canGoBack()) web.goBack();
        else toast("已经是最早的页面了");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) {
            super.onBackPressed();
        } else {
            lastBack = now;
            toast("再按一次退出");
        }
    }

    private void share() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, tvTitle.getText().toString());
        i.putExtra(Intent.EXTRA_TEXT, tvTitle.getText() + "\n" + currentUrl);
        try { startActivity(Intent.createChooser(i, "分享")); }
        catch (Exception e) { toast("没有可用的分享应用"); }
    }

    private void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("link", s == null ? "" : s));
    }

    private void openExternal(String url) {
        if (TextUtils.isEmpty(url)) return;
        Uri u;
        try { u = Uri.parse(url); } catch (Exception e) { return; }
        if (u == null || u.getScheme() == null) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
        catch (Exception e) { toast("没有应用可以打开该链接"); }
    }

    private void recordHistory(String url, String title) {
        if (!isThreadUrl(url)) return;
        Store.add(this, Store.KEY_HIS, cleanTitle(title), url.split("#")[0], 200);
    }

    private static boolean isThreadUrl(String u) {
        return u != null && (u.contains("mod=viewthread") || u.matches(".*thread-\\d+.*"));
    }

    private static boolean isImage(String u) {
        String s = u.toLowerCase(Locale.ROOT);
        return s.contains(".png") || s.contains(".jpg") || s.contains(".jpeg")
                || s.contains(".gif") || s.contains(".webp") || s.contains("image/");
    }

    private static String cleanTitle(String t) {
        if (t == null) return "";
        return t.replaceAll("\\s*-\\s*吾爱破解.*$", "").trim();
    }

    private static String escHtml(String s) {
        return s.replace("&", "&amp;").replace("'", "&#39;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (UnsupportedEncodingException e) { return s; }
    }

    private static String fmt(long t) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(t));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
