package com.linkguard.browser;

import android.content.Context;
import android.content.SharedPreferences;

/** 全局设置的读写。 */
public final class Prefs {

    private static final String NAME = "linkguard";

    private static final String K_DEF_PKG = "default_pkg";
    private static final String K_DEF_ACT = "default_act";
    private static final String K_DEF_LABEL = "default_label";
    private static final String K_ASK = "ask_when_no_rule";
    private static final String K_TOAST = "toast_on_block";
    private static final String K_SHOW_DIALOG = "show_dialog";
    private static final String K_THEME = "theme";

    private Prefs() {
    }

    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String defaultPkg(Context c) {
        return get(c).getString(K_DEF_PKG, "");
    }

    public static String defaultAct(Context c) {
        return get(c).getString(K_DEF_ACT, "");
    }

    public static String defaultLabel(Context c) {
        return get(c).getString(K_DEF_LABEL, "");
    }

    public static boolean hasDefault(Context c) {
        return !defaultPkg(c).isEmpty();
    }

    public static void setDefault(Context c, String pkg, String act, String label) {
        get(c).edit()
                .putString(K_DEF_PKG, pkg == null ? "" : pkg)
                .putString(K_DEF_ACT, act == null ? "" : act)
                .putString(K_DEF_LABEL, label == null ? "" : label)
                .apply();
    }

    public static void clearDefault(Context c) {
        setDefault(c, "", "", "");
    }

    /** true = 即使设置了默认浏览器，没有规则的网址也先询问。 */
    public static boolean askWhenNoRule(Context c) {
        return get(c).getBoolean(K_ASK, true);
    }

    public static void setAskWhenNoRule(Context c, boolean v) {
        get(c).edit().putBoolean(K_ASK, v).apply();
    }

    /** true = 被拉起时先弹窗询问；false = 直接按默认设置打开。 */
    public static boolean showDialog(Context c) {
        return get(c).getBoolean(K_SHOW_DIALOG, true);
    }

    public static void setShowDialog(Context c, boolean v) {
        get(c).edit().putBoolean(K_SHOW_DIALOG, v).apply();
    }

    /** 配色方案 key，见 {@link Themes#KEYS}。 */
    public static String theme(Context c) {
        return get(c).getString(K_THEME, "blue");
    }

    public static void setTheme(Context c, String key) {
        get(c).edit().putString(K_THEME, key).apply();
    }

    public static boolean toastOnBlock(Context c) {
        return get(c).getBoolean(K_TOAST, true);
    }

    public static void setToastOnBlock(Context c, boolean v) {
        get(c).edit().putBoolean(K_TOAST, v).apply();
    }
}
