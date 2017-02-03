package me.ggum.gum.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by sb on 2017. 1. 15..
 */

public class TrimProgressView extends View {

    private static final float MAX_SIZE = 100;
    private float rectHeight;

    private Paint rectPaint = new Paint();

    private float width;


    public TrimProgressView(Context context) {
        super(context);
    }

    public TrimProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TrimProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        setClickable(true);
        super.onFinishInflate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        width = getMeasuredWidth();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        rectPaint.setStyle(Paint.Style.FILL);
        rectPaint.setAntiAlias(false);
        rectPaint.setColor(Color.BLACK);
        //canvas.drawRect(new RectF(0, width, width, width-100), rectPaint);
        canvas.drawRect(new RectF(0, 0, width, rectHeight), rectPaint);

    }

    public void setRectHeight(float rectHeight){
        if(rectHeight <= width){
            this.rectHeight = rectHeight;
            postInvalidate();
        }
    }
}
