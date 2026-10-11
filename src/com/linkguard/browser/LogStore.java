package com.linkguard.browser;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/** 最近的拦截 / 打开记录，只保留最近 100 条。 */
public final class LogStore {

    public static final String ACTION_BLOCKED = "blocked";
    public static final String ACTION_APP_BLOCKED = "app_blocked";
    public static final String ACTION_ONCE = "once";
    public static final String ACTION_ALWAYS = "always";
    public static final String ACTION_OPENED = "opened";

    private static final String KEY = "logs";
    private static final int MAX = 100;

    private LogStore() {
    }

    public static void add(Context c, String root, String url, String action, String target) {
        try {
            JSONArray old = all(c);
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("root", root == null ? "" : root);
            o.put("url", url == null ? "" : url);
            o.put("a", action == null ? "" : action);
            o.put("tg", target == null ? "" : target);

            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < old.length() && out.length() < MAX; i++) {
                out.put(old.get(i));
            }
            Prefs.get(c).edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static JSONArray all(Context c) {
        try {
            return new JSONArray(Prefs.get(c).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void clear(Context c) {
        Prefs.get(c).edit().putString(KEY, "[]").apply();
    }
}
