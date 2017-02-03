package me.ggum.gum.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import me.ggum.gum.R;

/**
 * Created by sb on 2017. 1. 17..
 */

public class EffectControlView extends View implements View.OnTouchListener{

    public static final int LEFT_TOUCHED = 0;
    public static final int RIGHT_TOUCHED = 1;
    public static final int MID_RECT_TOUCHED = 2;

    private int width, height;

    private Paint backLine = new Paint();
    private Paint bg = new Paint();
    private Paint innerOval = new Paint();
    private Paint outterOval = new Paint();
    private Paint midRect = new Paint();
    private Paint playRect = new Paint();

    private float start, end;
    private float playPoint;

    private float midPreTouch;
    private float midDifference;

    private OnEffectVideoTimeChangeListener listener;

    public EffectControlView(Context context) {
        super(context);
        setOnTouchListener(this);
    }

    public EffectControlView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnTouchListener(this);
    }

    public EffectControlView(Context context, AttributeSet attrs, int defStyleAttr) {
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
        start = width/2-width/6;
        end = width/2+width/6;
        playPoint = start;
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


        setBg();
        canvas.drawRect(new RectF(0, 0, width, height), bg);

        setBackLine();
        canvas.drawRect(new RectF(0, (height/2)-(((6*height)/50)/2), width, (height/2)+(((6*height)/50)/2)), backLine);


        setMidRect();
        canvas.drawRect(new RectF(start, (height/2)-(((6*height)/50)/2), end, (height/2)+(((6*height)/50)/2)), midRect);


        setOutterOval();
        canvas.drawCircle(start, height/2, (11*width/360), outterOval);
        canvas.drawCircle(end, height/2, (11*width/360), outterOval);

        setInnerOval();
        canvas.drawCircle(start, height/2, (11*width/360)-(4*width/360), innerOval);
        canvas.drawCircle(end, height/2, (11*width/360)-(4*width/360), innerOval);

        setPlayRect();
        canvas.drawRoundRect(new RectF(playPoint-(5*width/720), height/2-(25*height/100), playPoint+(5*width/720), height/2+(25*height/100)), (25*height/50), (25*height/50), playRect);

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        int action = event.getAction();

        if(action == MotionEvent.ACTION_DOWN){
            midPreTouch = event.getX();
            if(listener != null){
                listener.onStartTimeChange(midPreTouch);
            }
            return true;
        }else if(action == MotionEvent.ACTION_MOVE){
            if(checkTouchedArea(event.getX())==LEFT_TOUCHED){
                start = event.getX();
                playPoint = start;

                if(listener != null){
                    listener.onVideoTimeChange(start, end, midDifference, LEFT_TOUCHED);
                }
                invalidate();
            }else if(checkTouchedArea(event.getX())==RIGHT_TOUCHED){
                end = event.getX();
                playPoint = end;

                if(listener != null){
                    listener.onVideoTimeChange(start, end, midDifference, RIGHT_TOUCHED);
                }
                invalidate();
            }else if(checkTouchedArea(event.getX())==MID_RECT_TOUCHED){
                start = start+(event.getX()-midPreTouch);
                playPoint = start;
                end = end +(event.getX()-midPreTouch);
                midDifference = event.getX()-midPreTouch;
                midPreTouch = event.getX();

                if(listener != null){
                    listener.onVideoTimeChange(start, end, midDifference, MID_RECT_TOUCHED);
                }
                invalidate();
            }
            return true;
        }else if(action== MotionEvent.ACTION_UP){

            if(listener != null){
                listener.onFinishTimeChange();
            }

            return false;
        }

        return false;
    }

    private int checkTouchedArea(float x){
        if((x>=start-(22*width/360)) && (x<=start+(11*width/360))){
            return LEFT_TOUCHED;
        }else if((x>=end-(11*width/360)) && (x<=end+(22*width/360))){
            return RIGHT_TOUCHED;
        }else if((x>=start+(22*width/360)) && (x<=end-(22*width/360))){
            return MID_RECT_TOUCHED;
        }else{
            return -1;
        }
    }

    public void setStart(float start){
        this.start = start;
        postInvalidate();
    }

    public void setEnd(float end){
        this.end = end;
        postInvalidate();
    }

    public void setPlayPoint(float playPoint){
        this.playPoint = playPoint;
        postInvalidate();
    }



    private void setBg(){
        bg.setStyle(Paint.Style.FILL);
        bg.setAntiAlias(false);
        bg.setColor(Color.WHITE);
        bg.setAlpha(50);
    }

    private void setBackLine(){
        backLine.setStyle(Paint.Style.FILL);
        backLine.setAntiAlias(false);
        backLine.setColor(Color.WHITE);
        backLine.setAlpha(60);
    }

    private void setInnerOval(){
        innerOval.setStyle(Paint.Style.FILL);
        innerOval.setAntiAlias(false);
        innerOval.setColor(Color.WHITE);
    }

    private void setOutterOval(){
        outterOval.setStyle(Paint.Style.FILL);
        outterOval.setAntiAlias(false);
        outterOval.setColor(getResources().getColor(R.color.gumPink));
    }

    private void setMidRect(){
        midRect.setStyle(Paint.Style.FILL);
        midRect.setAntiAlias(false);
        midRect.setColor(getResources().getColor(R.color.gumPink));
    }

    private void setPlayRect(){
        playRect.setStyle(Paint.Style.FILL);
        playRect.setAntiAlias(false);
        playRect.setColor(Color.WHITE);
    }

    public void setOnEffectVideoTimeChangeListener(OnEffectVideoTimeChangeListener listener){
        this.listener = listener;
    }

    public interface OnEffectVideoTimeChangeListener{
        void onStartTimeChange(float pre);
        void onVideoTimeChange(float left, float right, float midDifference, int touchState);
        void onFinishTimeChange();
    }
}
