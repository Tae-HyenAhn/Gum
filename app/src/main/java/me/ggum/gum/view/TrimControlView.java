package me.ggum.gum.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import me.ggum.gum.R;

/**
 * Created by sb on 2017. 1. 12..
 */

public class TrimControlView extends View implements View.OnTouchListener{
    private static final String TAG = "TrimControlView";


    public static final int LEFT_TOUCHED = 1;
    public static final int RIGHT_TOUCHED = 2;
    public static final int MID_TOUCHED = 3;

    private float rectStart, rectEnd;
    private float midPre, midDiffer;

    private long videoDuration = 1000;
    private long durationPerWidth;

    private int width, height;
    private Paint linePaint = new Paint();
    private Paint edgeOval = new Paint();
    private Paint outterRect = new Paint();
    private Paint innerOval = new Paint();


    private OnVideoTimeChangeListener listener;

    public TrimControlView(Context context) {
        super(context);
        setOnTouchListener(this);

    }

    public TrimControlView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnTouchListener(this);

    }

    public TrimControlView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnTouchListener(this);
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
        height = getMeasuredHeight();
        rectStart = width/2-width/5;
        rectEnd = width/2+width/5;

        if(listener != null)
            listener.onViewSizeOK(rectStart, rectEnd, 0.0f, width);

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

        setLinePaint();
        canvas.drawRect(new RectF(rectStart+width/22, 0, rectEnd-width/22, height/34), linePaint);
        canvas.drawRect(new RectF(rectStart+width/22, height-height/34, rectEnd-width/22, height), linePaint);

        setEdgeOval();
        canvas.drawRoundRect(new RectF(rectStart, 0, rectStart+width/22, height), 7*width/330, 7*width/330, edgeOval);
        canvas.drawRoundRect(new RectF(rectEnd-width/22, 0, rectEnd, height), 7*width/330, 7*width/330, edgeOval);

        setOutterRect();
        canvas.drawRect(new RectF(rectStart+width/44, 0, rectStart+width/22, height), outterRect);
        canvas.drawRect(new RectF(rectEnd-width/22, 0, rectEnd-width/44, height), outterRect);

        setInnerOval();
        canvas.drawRoundRect(new RectF(rectStart+width/44-width/330, height/2-25*height/102 , rectStart+width/44+width/330, height/2+25*height/102), 5*width/66, 5*width/66, innerOval);
        canvas.drawRoundRect(new RectF(rectEnd-width/44-width/330, height/2-25*height/102 , rectEnd-width/44+width/330, height/2+25*height/102), 5*width/66, 5*width/66, innerOval);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction();

        if(action == MotionEvent.ACTION_DOWN){
            midPre = event.getX();
            if(listener != null)
                listener.onStartVideoTimeChange();
            return true;
        }else if(action == MotionEvent.ACTION_MOVE){
            if(rectStart>=0 && rectEnd <= width && rectEnd-rectStart >= width/5){
                if(checkTouchedArea(event.getX()) == LEFT_TOUCHED){
                    rectStart = event.getX();


                    if(listener != null){
                        listener.onVideoTimeChange(rectStart, rectEnd, midDiffer, LEFT_TOUCHED);
                    }
                    invalidate();
                }else if(checkTouchedArea(event.getX()) == RIGHT_TOUCHED){
                    rectEnd = event.getX();

                    if(listener != null){
                        listener.onVideoTimeChange(rectStart, rectEnd, midDiffer, RIGHT_TOUCHED);
                    }
                    invalidate();
                }else if(checkTouchedArea(event.getX()) == MID_TOUCHED){
                    rectStart = rectStart + (event.getX()-midPre);

                    rectEnd = rectEnd + (event.getX()-midPre);
                    midDiffer = event.getX()-midPre;
                    midPre = event.getX();
                    if(listener != null){
                        listener.onVideoTimeChange(rectStart, rectEnd, midDiffer, MID_TOUCHED);
                    }
                    invalidate();
                }
            }else{
                if(rectStart<0) {
                    rectStart = 0;
                    invalidate();
                }

                if(rectEnd>width){
                    rectEnd = width;
                    invalidate();
                }

                if(rectEnd-rectStart<width/5){
                    while(true){
                        Log.d(TAG, "RECT_RUN");
                        if(Math.abs((rectStart-width/22)) > Math.abs((rectEnd-(width-width/22)))){
                            rectStart--;
                            invalidate();
                        }else{
                            rectEnd++;
                            invalidate();
                        }

                        if((rectEnd-rectStart)>=width/5){
                            Log.d(TAG, "braek RUN");
                            break;
                        }
                    }
                    invalidate();
                }

            }

            return true;
        }else if(action == MotionEvent.ACTION_UP){

            invalidate();
            if(listener != null)
                listener.onFinishVideoTimeChange();
            return false;
        }

        return false;
    }


    private int checkTouchedArea(float x){
        if((x>=rectStart-(width/22)) && (x<=rectStart+(width/22))){
            return LEFT_TOUCHED;
        }else if((x>=rectEnd-(width/22)) && (x<=rectEnd+(width/22))){
            return RIGHT_TOUCHED;
        }else if(x >= (((rectEnd+rectStart)/2)-(7*width/110)) && x <= (((rectEnd+rectStart)/2)+(7*width/110))){
            return MID_TOUCHED;
        }else{
            return -1;
        }
    }

    public void setLinePaint(){
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setAntiAlias(false);
        linePaint.setColor(getResources().getColor(R.color.gumPink));
    }

    public void setEdgeOval(){
        edgeOval.setStyle(Paint.Style.FILL);
        edgeOval.setAntiAlias(false);
        edgeOval.setColor(getResources().getColor(R.color.gumPink));
    }

    public void setOutterRect(){
        outterRect.setStyle(Paint.Style.FILL);
        outterRect.setAntiAlias(false);
        outterRect.setColor(getResources().getColor(R.color.gumPink));
    }


    public void setInnerOval(){
        innerOval.setStyle(Paint.Style.FILL);
        innerOval.setAntiAlias(false);
        innerOval.setColor(Color.WHITE);
    }


    public void setOnVideoTimeChangeListener(OnVideoTimeChangeListener listener){
        this.listener = listener;
    }

    public interface OnVideoTimeChangeListener{
        void onViewSizeOK(float start, float end, float midDffer, float width);
        void onStartVideoTimeChange();
        void onVideoTimeChange(float start, float end, float midDiffer, int touchState);
        void onFinishVideoTimeChange();
    }
}
