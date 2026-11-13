package cn.read52.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** 全部设置都存在本地 SharedPreferences，不联网、不上传。 */
final class Prefs {

    static final int NIGHT_FOLLOW = 0;
    static final int NIGHT_ON = 1;
    static final int NIGHT_OFF = 2;

    static final int MODE_ORIGINAL = 0;   // 站点原样
    static final int MODE_OPT = 1;        // 优化排版（保留全部交互）
    static final int MODE_READER = 2;     // 沉浸阅读（只读卡片）

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("r52_prefs", Context.MODE_PRIVATE);
    }

    int night() { return sp.getInt("night", NIGHT_FOLLOW); }
    void night(int v) { sp.edit().putInt("night", v).apply(); }

    boolean isNight(Context c) {
        int v = night();
        if (v == NIGHT_ON) return true;
        if (v == NIGHT_OFF) return false;
        int mask = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    int font() { return sp.getInt("font", 100); }
    void font(int v) { sp.edit().putInt("font", Math.max(80, Math.min(160, v))).apply(); }
    float scale() { return font() / 100f; }

    int mode() { return sp.getInt("mode", MODE_OPT); }
    void mode(int v) { sp.edit().putInt("mode", v).apply(); }

    /** 打开帖子时自动切到沉浸阅读，离开帖子回到优化排版。手动选模式后自动关闭。 */
    boolean autoReader() { return sp.getBoolean("auto_reader", true); }
    void autoReader(boolean v) { sp.edit().putBoolean("auto_reader", v).apply(); }

    boolean tidy() { return sp.getBoolean("tidy", true); }
    void tidy(boolean v) { sp.edit().putBoolean("tidy", v).apply(); }

    boolean zoom() { return sp.getBoolean("zoom", true); }
    void zoom(boolean v) { sp.edit().putBoolean("zoom", v).apply(); }

    boolean nopic() { return sp.getBoolean("nopic", false); }
    void nopic(boolean v) { sp.edit().putBoolean("nopic", v).apply(); }

    boolean onlyOp() { return sp.getBoolean("only_op", false); }
    void onlyOp(boolean v) { sp.edit().putBoolean("only_op", v).apply(); }
}
