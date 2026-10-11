package com.linkguard.browser;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * 能把子视图裁成圆角的 FrameLayout。
 *
 * <p><b>为什么不用 android:clipToOutline：</b>
 * 它靠 RenderNode 的 outline 裁剪，而 WebView 渲染在自己的图层里，父级 outline 裁不到，
 * 结果是上两角直角、下两角圆角。
 *
 * <p><b>为什么也不用 canvas.clipPath：</b>
 * 硬件加速下 WebView 同样在独立 RenderNode 里合成，父级画布的裁剪路径对它一样无效 ——
 * 换了写法没换机制，还是不生效（实测验证过）。
 *
 * <p><b>实际做法：画完之后把四个角补回去。</b>
 * 先让子视图（含 WebView）正常绘制，再用背景色把「圆角矩形之外、方角之内」那四块小三角盖掉。
 * 这条路径不依赖任何图层机制，无论子视图怎么渲染都遮得住。
 */
public class RoundedBox extends FrameLayout {

    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float radius;
    /** 四角补色，应当等于本容器身后的底色。 */
    private int cornerColor;

    /** 底部渐隐的高度与颜色。 */
    private float fadeHeight;
    private int fadeColor;
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient fadeGradient;
    private float fadeBuiltFor = -1f;

    public RoundedBox(Context c) {
        this(c, null);
    }

    public RoundedBox(Context c, AttributeSet attrs) {
        this(c, attrs, 0);
    }

    public RoundedBox(Context c, AttributeSet attrs, int defStyle) {
        super(c, attrs, defStyle);
        if (attrs != null) {
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.RoundedBox);
            radius = a.getDimension(R.styleable.RoundedBox_lgRadius, 0f);
            a.recycle();
        }
    }

    public void setRadius(float r) {
        radius = r;
        invalidate();
    }

    /** 设置四角补色（容器身后的底色）。不设则不补角。 */
    public void setCornerColor(int color) {
        cornerColor = color;
        paint.setColor(color);
        invalidate();
    }

    /**
     * 底部渐隐。和补角一样必须在 super.dispatchDraw 之后由父级亲手画 ——
     * 作为兄弟视图放在布局里是压不住 WebView 的图层的。
     */
    public void setBottomFade(float height, int color) {
        fadeHeight = height;
        fadeColor = color;
        fadeGradient = null;
        invalidate();
    }

    private void drawFade(Canvas canvas, float w, float h) {
        if (fadeHeight <= 0f || fadeColor == 0) {
            return;
        }
        float top = Math.max(0f, h - fadeHeight);
        if (fadeGradient == null || fadeBuiltFor != h) {
            // 上透明 -> 下底色
            fadeGradient = new LinearGradient(0, top, 0, h, 0x00FFFFFF & fadeColor, fadeColor,
                    Shader.TileMode.CLAMP);
            fadePaint.setShader(fadeGradient);
            fadeBuiltFor = h;
        }
        canvas.drawRect(0, top, w, h, fadePaint);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawFade(canvas, getWidth(), getHeight());
        if (radius <= 0f || cornerColor == 0) {
            return;
        }
        final float w = getWidth();
        final float h = getHeight();
        final float r = Math.min(radius, Math.min(w, h) / 2f);

        // 左上：moveTo(0,0) -> (r,0) -> 弧到 (0,r) -> 闭合
        oval.set(0, 0, 2 * r, 2 * r);
        path.reset();
        path.moveTo(0, 0);
        path.lineTo(r, 0);
        path.arcTo(oval, 270, -90, false);
        path.close();
        canvas.drawPath(path, paint);

        // 右上
        oval.set(w - 2 * r, 0, w, 2 * r);
        path.reset();
        path.moveTo(w, 0);
        path.lineTo(w - r, 0);
        path.arcTo(oval, 270, 90, false);
        path.close();
        canvas.drawPath(path, paint);

        // 右下
        oval.set(w - 2 * r, h - 2 * r, w, h);
        path.reset();
        path.moveTo(w, h);
        path.lineTo(w, h - r);
        path.arcTo(oval, 0, 90, false);
        path.close();
        canvas.drawPath(path, paint);

        // 左下
        oval.set(0, h - 2 * r, 2 * r, h);
        path.reset();
        path.moveTo(0, h);
        path.lineTo(r, h);
        path.arcTo(oval, 90, 90, false);
        path.close();
        canvas.drawPath(path, paint);
    }
}
