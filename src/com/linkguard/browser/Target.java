package com.linkguard.browser;

import org.json.JSONObject;

/** 一个“可以被拉起的目标”（某个浏览器）。 */
public final class Target {

    public final String label;
    public final String pkg;
    public final String act;
    /** 是否是这个网址当前配置的默认浏览器。 */
    public final boolean preferred;

    public Target(String label, String pkg, String act, boolean preferred) {
        this.label = label == null ? "" : label;
        this.pkg = pkg == null ? "" : pkg;
        this.act = act == null ? "" : act;
        this.preferred = preferred;
    }

    public boolean valid() {
        return !pkg.isEmpty();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("pkg", pkg);
            o.put("act", act);
            o.put("label", label);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Target fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        String pkg = o.optString("pkg", "");
        if (pkg.isEmpty()) {
            return null;
        }
        return new Target(o.optString("label", pkg), pkg, o.optString("act", ""), false);
    }
}
