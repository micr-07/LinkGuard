package com.linkguard.browser;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;

/** 配色方案（Material 3 风格）的注册与套用。 */
public final class Themes {

    /** 方案 key，顺序即设置页里的显示顺序。 */
    public static final String[] KEYS = {
            "blue", "purple", "teal", "green", "orange", "pink", "dark"
    };

    private static final String DEFAULT_KEY = "blue";

    private Themes() {
    }

    public static String normalize(String key) {
        if (key == null) {
            return DEFAULT_KEY;
        }
        for (String k : KEYS) {
            if (k.equals(key)) {
                return k;
            }
        }
        return DEFAULT_KEY;
    }

    public static String current(Context c) {
        return normalize(Prefs.theme(c));
    }

    public static void set(Context c, String key) {
        Prefs.setTheme(c, normalize(key));
    }

    /** 方案显示名，例如「默认蓝」。 */
    public static int nameRes(String key) {
        switch (normalize(key)) {
            case "purple":
                return R.string.scheme_purple;
            case "teal":
                return R.string.scheme_teal;
            case "green":
                return R.string.scheme_green;
            case "orange":
                return R.string.scheme_orange;
            case "pink":
                return R.string.scheme_pink;
            case "dark":
                return R.string.scheme_dark;
            default:
                return R.string.scheme_blue;
        }
    }

    /** 给普通界面套用当前配色。必须在 super.onCreate() 之前调用。 */
    public static void apply(Activity a) {
        a.setTheme(themeRes(current(a)));
    }

    /** 给拦截弹窗套用当前配色（透明窗口版本）。 */
    public static void applyIntercept(Activity a) {
        a.setTheme(interceptRes(current(a)));
    }

    private static int themeRes(String key) {
        switch (normalize(key)) {
            case "purple":
                return R.style.Theme_LG_Purple;
            case "teal":
                return R.style.Theme_LG_Teal;
            case "green":
                return R.style.Theme_LG_Green;
            case "orange":
                return R.style.Theme_LG_Orange;
            case "pink":
                return R.style.Theme_LG_Pink;
            case "dark":
                return R.style.Theme_LG_Dark2;
            default:
                return R.style.Theme_LG_Blue;
        }
    }

    private static int interceptRes(String key) {
        switch (normalize(key)) {
            case "purple":
                return R.style.Theme_LG_Purple_Intercept;
            case "teal":
                return R.style.Theme_LG_Teal_Intercept;
            case "green":
                return R.style.Theme_LG_Green_Intercept;
            case "orange":
                return R.style.Theme_LG_Orange_Intercept;
            case "pink":
                return R.style.Theme_LG_Pink_Intercept;
            case "dark":
                return R.style.Theme_LG_Dark2_Intercept;
            default:
                return R.style.Theme_LG_Intercept;
        }
    }

    /** 读取当前主题里某个颜色角色的值。 */
    public static int color(Context c, int attr) {
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
            if (tv.resourceId != 0) {
                try {
                    return c.getResources().getColor(tv.resourceId);
                } catch (Exception ignored) {
                }
            }
            return tv.data;
        }
        return 0xFF888888;
    }

    public static int primary(Context c) {
        return color(c, R.attr.lgPrimary);
    }

    public static int danger(Context c) {
        return color(c, R.attr.lgDanger);
    }

    public static int onSurfaceVariant(Context c) {
        return color(c, R.attr.lgOnSurfaceVariant);
    }

    public static int outlineVariant(Context c) {
        return color(c, R.attr.lgOutlineVariant);
    }
}
