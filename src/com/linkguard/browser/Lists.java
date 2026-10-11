package com.linkguard.browser;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 三份名单：
 * <ul>
 *   <li><b>黑名单</b>：命中的网址直接丢弃，只用小字提示。</li>
 *   <li><b>白名单</b>：命中的网址直接用「指定浏览器」打开，不再询问。</li>
 *   <li><b>应用屏蔽</b>：这些应用拉起的所有链接一律拦下。</li>
 * </ul>
 * 旧版本（v1.1 及以前）的 rules 表会在首次运行时自动迁移过来。
 */
public final class Lists {

    private static final String K_BLACK = "blacklist";
    private static final String K_WHITE = "whitelist";
    private static final String K_APP = "app_blocks";

    private static final String K_OLD_RULES = "rules";
    private static final String K_MIGRATED = "migrated_v2";

    private Lists() {
    }

    // ------------------------------------------------------------------
    // 迁移
    // ------------------------------------------------------------------

    public static void migrate(Context c) {
        if (Prefs.get(c).getBoolean(K_MIGRATED, false)) {
            return;
        }
        try {
            JSONObject old = new JSONObject(Prefs.get(c).getString(K_OLD_RULES, "{}"));
            Iterator<String> it = old.keys();
            while (it.hasNext()) {
                String domain = it.next();
                JSONObject r = old.optJSONObject(domain);
                if (r == null) {
                    continue;
                }
                String kind = r.optString("kind", "");
                if ("block".equals(kind)) {
                    addBlack(c, domain);
                } else if ("app".equals(kind)) {
                    String pkg = r.optString("pkg", "");
                    if (pkg.isEmpty()) {
                        addWhite(c, domain);
                    } else {
                        putWhite(c, domain, new Target(
                                r.optString("label", pkg), pkg, r.optString("act", ""), false));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        Prefs.get(c).edit().putBoolean(K_MIGRATED, true).remove(K_OLD_RULES).apply();
    }

    // ------------------------------------------------------------------
    // 黑名单
    // ------------------------------------------------------------------

    public static List<String> blacklist(Context c) {
        return readArray(c, K_BLACK);
    }

    public static boolean isBlack(Context c, String root) {
        return root != null && !root.isEmpty() && readArray(c, K_BLACK).contains(root);
    }

    public static void addBlack(Context c, String root) {
        if (root == null || root.isEmpty()) {
            return;
        }
        List<String> list = readArray(c, K_BLACK);
        if (!list.contains(root)) {
            list.add(root);
            writeArray(c, K_BLACK, list);
        }
    }

    public static void removeBlack(Context c, String root) {
        List<String> list = readArray(c, K_BLACK);
        if (list.remove(root)) {
            writeArray(c, K_BLACK, list);
        }
    }

    public static int blackCount(Context c) {
        return readArray(c, K_BLACK).size();
    }

    // ------------------------------------------------------------------
    // 白名单
    // ------------------------------------------------------------------

    /** 白名单里的所有网址，按字母排序。 */
    public static List<String> whitelist(Context c) {
        JSONObject o = readObject(c, K_WHITE);
        List<String> out = new ArrayList<String>();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            out.add(it.next());
        }
        Collections.sort(out);
        return out;
    }

    public static boolean isWhite(Context c, String root) {
        return root != null && !root.isEmpty() && readObject(c, K_WHITE).has(root);
    }

    /** 白名单里为该网址指定的浏览器；没有指定时返回 null（表示用默认浏览器）。 */
    public static Target whiteTarget(Context c, String root) {
        JSONObject o = readObject(c, K_WHITE).optJSONObject(root);
        if (o == null) {
            return null;
        }
        String pkg = o.optString("pkg", "");
        if (pkg.isEmpty()) {
            return null;
        }
        return new Target(o.optString("label", pkg), pkg, o.optString("act", ""), false);
    }

    /** 只加入白名单，不指定浏览器。 */
    public static void addWhite(Context c, String root) {
        putWhite(c, root, null);
    }

    public static void putWhite(Context c, String root, Target t) {
        if (root == null || root.isEmpty()) {
            return;
        }
        JSONObject o = readObject(c, K_WHITE);
        try {
            o.put(root, t == null ? new JSONObject() : t.toJson());
        } catch (Exception ignored) {
        }
        writeObject(c, K_WHITE, o);
    }

    public static void removeWhite(Context c, String root) {
        JSONObject o = readObject(c, K_WHITE);
        o.remove(root);
        writeObject(c, K_WHITE, o);
    }

    public static int whiteCount(Context c) {
        return readObject(c, K_WHITE).length();
    }

    // ------------------------------------------------------------------
    // 应用屏蔽
    // ------------------------------------------------------------------

    public static List<String> appBlocks(Context c) {
        return readArray(c, K_APP);
    }

    public static boolean isAppBlocked(Context c, String pkg) {
        return pkg != null && !pkg.isEmpty() && readArray(c, K_APP).contains(pkg);
    }

    public static void setAppBlocked(Context c, String pkg, boolean blocked) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        List<String> list = readArray(c, K_APP);
        boolean has = list.contains(pkg);
        if (blocked && !has) {
            list.add(pkg);
            writeArray(c, K_APP, list);
        } else if (!blocked && has) {
            list.remove(pkg);
            writeArray(c, K_APP, list);
        }
    }

    public static int appBlockCount(Context c) {
        return readArray(c, K_APP).size();
    }

    // ------------------------------------------------------------------
    // 底层读写
    // ------------------------------------------------------------------

    private static List<String> readArray(Context c, String key) {
        List<String> out = new ArrayList<String>();
        try {
            JSONArray arr = new JSONArray(Prefs.get(c).getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (!s.isEmpty() && !out.contains(s)) {
                    out.add(s);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void writeArray(Context c, String key, List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) {
            arr.put(s);
        }
        Prefs.get(c).edit().putString(key, arr.toString()).apply();
    }

    private static JSONObject readObject(Context c, String key) {
        try {
            return new JSONObject(Prefs.get(c).getString(key, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void writeObject(Context c, String key, JSONObject o) {
        Prefs.get(c).edit().putString(key, o.toString()).apply();
    }
}
