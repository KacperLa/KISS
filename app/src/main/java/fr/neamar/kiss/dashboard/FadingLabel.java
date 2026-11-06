package fr.neamar.kiss.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.View;
import android.widget.TextView;

/** Single-line visual label; the underlying text and accessible name are never shortened. */
public final class FadingLabel extends TextView {
    private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FadingLabel(Context context) {
        super(context);
        setSingleLine(true);
        setEllipsize(null);
        setHorizontalFadingEdgeEnabled(false);
        setFocusable(false);
        setShadowLayer(3, 0, 1, Color.BLACK);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int available = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        boolean overflows = getLayout() != null && getLayout().getLineCount() > 0
            && getLayout().getLineWidth(0) > available;
        if (!overflows || available <= 0) {
            super.onDraw(canvas);
            return;
        }
        int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.onDraw(canvas);
        float fade = Math.min(available / 3f, 32 * getResources().getDisplayMetrics().density);
        boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float start = rtl ? getCompoundPaddingLeft() : getWidth() - getCompoundPaddingRight() - fade;
        mask.setShader(new LinearGradient(start, 0, start + fade, 0,
            rtl ? Color.TRANSPARENT : Color.BLACK, rtl ? Color.BLACK : Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(start, 0, start + fade, getHeight(), mask);
        canvas.restoreToCount(layer);
    }
}
