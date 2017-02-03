package me.ggum.gum.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.view.View;

import me.ggum.gum.R;

/**
 * Created by sb on 2016. 12. 28..
 */

public class HoverImageButton extends ImageButton implements View.OnTouchListener {

    private static final String TAG = "HoverImageButton";
    private boolean isPressed;


    public HoverImageButton(Context context) {
        super(context);
        setOnTouchListener(this);
    }

    public HoverImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnTouchListener(this);
    }

    public HoverImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnTouchListener(this);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public HoverImageButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        int action = event.getAction();

        switch (action){
            case MotionEvent.ACTION_DOWN:
                Log.d(TAG, "DOWN");
                Animation bAnim = AnimationUtils.loadAnimation(getContext(), R.anim.button_scale_anim);
                startAnimation(bAnim);
                isPressed = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if(!v.isPressed()){
                    Animation aAnim = AnimationUtils.loadAnimation(getContext(), R.anim.button_scale_anim_after);
                    startAnimation(aAnim);
                    isPressed = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                if(isPressed){
                    Animation aAnim = AnimationUtils.loadAnimation(getContext(), R.anim.button_scale_anim_after);
                    startAnimation(aAnim);
                }
                break;
        }

        return false;
    }
}
