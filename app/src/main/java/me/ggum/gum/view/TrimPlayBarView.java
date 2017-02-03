package me.ggum.gum.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by sb on 2017. 1. 23..
 */

public class TrimPlayBarView extends View {

    private Paint playBar = new Paint();
    private int viewWidth, viewHeight;
    private float playPoint;

    public TrimPlayBarView(Context context) {
        super(context);
    }

    public TrimPlayBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TrimPlayBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        setClickable(false);
        super.onFinishInflate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = getMeasuredWidth();
        viewHeight = getMeasuredHeight();
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

        setPlayBarPaint();
        canvas.drawRoundRect(new RectF(playPoint-viewWidth/120, viewHeight/2- 25*viewHeight/51, playPoint+viewWidth/120, viewHeight/2+25*viewHeight/51), viewWidth/12, viewWidth/12, playBar);
    }

    public void setPlayPoint(float point){
        this.playPoint = point*viewWidth;
        postInvalidate();
    }

    private void setPlayBarPaint(){
        playBar.setStyle(Paint.Style.FILL);
        playBar.setAntiAlias(false);
        playBar.setColor(Color.WHITE);
    }
}
