package com.linkguard.browser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 网址 / 域名处理工具。 */
public final class HostUtil {

    /** 常见的“二级公共后缀”，用于把 m.example.com.cn 归到 example.com.cn。 */
    private static final Set<String> TWO_LEVEL = new HashSet<String>(Arrays.asList(
            "com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn", "ac.cn", "mil.cn",
            "com.hk", "org.hk", "edu.hk", "gov.hk", "net.hk", "idv.hk",
            "com.tw", "org.tw", "edu.tw", "gov.tw", "net.tw", "idv.tw",
            "com.mo", "edu.mo", "gov.mo", "net.mo", "org.mo",
            "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk", "sch.uk",
            "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp", "ad.jp", "ed.jp", "gr.jp", "lg.jp",
            "co.kr", "or.kr", "ne.kr", "re.kr", "pe.kr", "go.kr", "ac.kr",
            "co.in", "net.in", "org.in", "gov.in", "ac.in", "edu.in", "firm.in", "gen.in", "ind.in",
            "com.br", "net.br", "org.br", "gov.br", "edu.br",
            "com.mx", "com.ar", "com.co", "com.pe", "com.ve", "com.ec", "com.uy", "com.bo", "com.py",
            "com.au", "net.au", "org.au", "edu.au", "gov.au", "id.au",
            "co.nz", "net.nz", "org.nz", "govt.nz", "ac.nz",
            "com.sg", "com.my", "com.ph", "com.vn", "com.id", "co.id", "or.id", "ac.id", "go.id",
            "co.th", "or.th", "ac.th", "go.th", "in.th",
            "com.tr", "com.ru", "com.ua", "com.pl", "com.es", "com.pt", "com.gr", "com.it",
            "co.il", "org.il", "ac.il", "gov.il", "net.il",
            "co.za", "org.za", "gov.za", "co.ke", "co.tz", "co.ug",
            "com.ng", "com.eg", "com.sa", "com.ae", "co.ae", "com.qa", "com.kw", "com.bh", "com.om",
            "com.pk", "com.bd", "com.np", "com.lk", "com.kh", "com.la", "com.mm", "com.mt"));

    private HostUtil() {
    }

    /**
     * 取“根网址”：去掉 www. 前缀，并把子域名归并到可注册域名。
     * a.b.example.com -> example.com ； m.taobao.com.cn -> taobao.com.cn
     */
    public static String rootDomain(String host) {
        if (host == null) {
            return "";
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        while (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        if (h.startsWith("www.")) {
            h = h.substring(4);
        }
        if (h.isEmpty()) {
            return "";
        }
        if (h.indexOf(':') >= 0) {
            return h; // IPv6
        }
        if (isIpv4(h)) {
            return h;
        }
        String[] parts = h.split("\\.");
        if (parts.length <= 2) {
            return h;
        }
        String last2 = parts[parts.length - 2] + "." + parts[parts.length - 1];
        if (TWO_LEVEL.contains(last2)) {
            return parts[parts.length - 3] + "." + last2;
        }
        return last2;
    }

    private static boolean isIpv4(String h) {
        String[] p = h.split("\\.");
        if (p.length != 4) {
            return false;
        }
        for (String s : p) {
            if (s.isEmpty() || s.length() > 3) {
                return false;
            }
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                    return false;
                }
            }
            if (Integer.parseInt(s) > 255) {
                return false;
            }
        }
        return true;
    }

    /** 把用户输入的域名 / 网址统一成根网址。 */
    public static String normalizeInput(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        int i = s.indexOf("://");
        if (i >= 0) {
            s = s.substring(i + 3);
        }
        i = s.indexOf('/');
        if (i >= 0) {
            s = s.substring(0, i);
        }
        i = s.indexOf('?');
        if (i >= 0) {
            s = s.substring(0, i);
        }
        i = s.indexOf('#');
        if (i >= 0) {
            s = s.substring(0, i);
        }
        i = s.indexOf('@');
        if (i >= 0) {
            s = s.substring(i + 1);
        }
        i = s.lastIndexOf(':');
        if (i > 0 && s.indexOf(':') == i) {
            s = s.substring(0, i);
        }
        return rootDomain(s);
    }
}
