package com.linkguard.browser;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * 语言包。
 *
 * <p>文案不放在 res/values-xx 里，而是放在 <b>assets/lang/&lt;code&gt;.json</b>：
 * 加一种语言只要丢一个 JSON 进 assets/lang 目录，不用改资源、不用碰代码。
 *
 * <p>占位符用 <code>{0}</code> / <code>{1}</code>，比 Android 的 %1$s 更好写也更好翻译。
 */
public final class Lang {

    /** 可选语言，顺序即设置页里的显示顺序。 */
    public static final String[] CODES = {"zh", "en"};

    /** 语言自称（各语言显示自己的名字，不翻译）。 */
    private static final String[] SELF_NAMES = {"中文", "English"};

    private static final String DIR = "lang/";
    private static final String FALLBACK = "zh";

    private static String loadedCode;
    private static final Map<String, String> TABLE = new HashMap<String, String>();

    /**
     * 语言偏好的内存副本。
     * s() 每次取文案都会走到 ensure()，若每次都读 SharedPreferences
     * 就是一屏几百次哈希查找 —— 缓存下来。
     */
    private static String prefCache;
    private static boolean prefLoaded;

    private Lang() {
    }

    public static int indexOf(String code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(code)) {
                return i;
            }
        }
        return 0;
    }

    public static String selfName(int i) {
        return (i >= 0 && i < SELF_NAMES.length) ? SELF_NAMES[i] : SELF_NAMES[0];
    }

    public static String normalize(String code) {
        return (code != null && indexOf2(code) >= 0) ? code : FALLBACK;
    }

    private static int indexOf2(String code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(code)) {
                return i;
            }
        }
        return -1;
    }

    /** 跟随系统时挑一个最接近的（这里只区分中/英）。 */
    public static String systemDefault(Context c) {
        Locale l = Locale.getDefault();
        String tag = l == null ? "" : l.getLanguage();
        if (tag != null && tag.toLowerCase(Locale.ROOT).startsWith("zh")) {
            return "zh";
        }
        // 系统语言是中文以外的一律给英文；但要确认英文包在不在
        return has(c, "en") ? "en" : "zh";
    }

    private static boolean has(Context c, String code) {
        try {
            InputStream is = c.getAssets().open(DIR + code + ".json");
            is.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void ensure(Context c) {
        if (!prefLoaded) {
            prefCache = Prefs.lang(c);
            prefLoaded = true;
        }
        String code = normalize(prefCache);
        if (code.equals(loadedCode) && !TABLE.isEmpty()) {
            return;
        }
        TABLE.clear();
        read(c, code);
        if (TABLE.isEmpty() && !FALLBACK.equals(code)) {
            read(c, FALLBACK);
        }
        loadedCode = code;
    }

    private static void read(Context c, String code) {
        InputStream is = null;
        try {
            AssetManager am = c.getAssets();
            is = am.open(DIR + code + ".json");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            JSONObject o = new JSONObject(new String(bo.toByteArray(), "UTF-8"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                TABLE.put(k, o.optString(k, ""));
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** 取一条文案；取不到就返回 key 本身，方便一眼看出漏翻。 */
    public static String s(Context c, String key) {
        ensure(c);
        String v = TABLE.get(key);
        return v == null ? key : v;
    }

    /** 取一条带占位符的文案，替换 {0} {1} … */
    public static String f(Context c, String key, Object... args) {
        String v = s(c, key);
        for (int i = 0; i < args.length; i++) {
            String token = "{" + i + "}";
            if (v.contains(token)) {
                v = v.replace(token, args[i] == null ? "" : String.valueOf(args[i]));
            }
        }
        return v;
    }

    /** 切换语言后清缓存，下次取值重新读。 */
    public static void invalidate() {
        loadedCode = null;
        prefLoaded = false;
        prefCache = null;
        TABLE.clear();
    }
}
