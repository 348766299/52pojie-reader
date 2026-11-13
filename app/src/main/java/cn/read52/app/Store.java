package cn.read52.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 收藏夹与阅读历史：本地 JSON 存储，按 URL 去重，最新的排在最前面。 */
final class Store {

    static final String KEY_FAV = "favorites";
    static final String KEY_HIS = "history";

    static final class Item {
        final String title;
        final String url;
        final long time;

        Item(String title, String url, long time) {
            this.title = title;
            this.url = url;
            this.time = time;
        }

        static Item from(JSONObject o) {
            return new Item(o.optString("title", "未命名"), o.optString("url", ""), o.optLong("time", 0));
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("title", title);
                o.put("url", url);
                o.put("time", time);
            } catch (Exception ignored) { }
            return o;
        }
    }

    private Store() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("r52_store", Context.MODE_PRIVATE);
    }

    static List<Item> load(Context c, String key) {
        List<Item> list = new ArrayList<>();
        String raw = sp(c).getString(key, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Item.from(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) { }
        return list;
    }

    private static void save(Context c, String key, List<Item> list) {
        JSONArray arr = new JSONArray();
        for (Item it : list) arr.put(it.toJson());
        sp(c).edit().putString(key, arr.toString()).apply();
    }

    static void add(Context c, String key, String title, String url, int limit) {
        if (url == null || url.isEmpty()) return;
        if (title == null || title.trim().isEmpty()) title = url;
        List<Item> list = load(c, key);
        List<Item> next = new ArrayList<>();
        next.add(new Item(title.trim(), url, System.currentTimeMillis()));
        for (Item it : list) {
            if (!url.equals(it.url)) next.add(it);
            if (next.size() >= limit) break;
        }
        save(c, key, next);
    }

    static void remove(Context c, String key, int position) {
        List<Item> list = load(c, key);
        if (position >= 0 && position < list.size()) {
            list.remove(position);
            save(c, key, list);
        }
    }

    static void clear(Context c, String key) {
        sp(c).edit().remove(key).apply();
    }
}
