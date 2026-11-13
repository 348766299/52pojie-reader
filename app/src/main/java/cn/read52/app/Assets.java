package cn.read52.app;

import android.content.Context;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 读取 assets 里的注入脚本，并做 UTF-8 Base64 编码。 */
final class Assets {

    private Assets() { }

    static String read(Context c, String name) throws IOException {
        InputStream in = c.getAssets().open(name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    /** 目标站点是 GBK 编码，脚本/样式走 Base64 可以完全绕开字符集与转义问题。 */
    static String b64(String s) {
        return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    static String quote(String s) {
        return JSONObject.quote(s == null ? "" : s);
    }
}
