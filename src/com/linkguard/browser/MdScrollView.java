package com.linkguard.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * 带最大高度限制的 ScrollView。
 *
 * <p>对话框内容（长名单、关于文字）超过这个高度时在内部滚动，
 * 而不是把窗口撑出屏幕、把底部按钮挤没。
 */
public class MdScrollView extends ScrollView {

    private int maxHeight;

    public MdScrollView(Context c) {
        super(c);
    }

    public MdScrollView(Context c, AttributeSet a) {
        super(c, a);
    }

    public void setMaxHeight(int px) {
        maxHeight = px;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (maxHeight > 0) {
            heightSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
