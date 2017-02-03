package me.ggum.gum.holder;

import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import me.ggum.gum.R;
import me.ggum.gum.data.VideoItem;
import me.ggum.gum.view.HoverImageButton;

/**
 * Created by sb on 2017. 1. 10..
 */

public class VideoThumbHolder extends RecyclerView.ViewHolder {

    public ImageView thumbCheckImg;
    public ImageView thumbBackImg;
    public TextView thumbDurationText;
    public CardView thumbCardView;

    private VideoItem item;

    private boolean isPressed;

    public VideoThumbHolder(View itemView) {
        super(itemView);
        thumbBackImg = (ImageView) itemView.findViewById(R.id.picker_thumb_img);
        thumbCheckImg = (ImageView) itemView.findViewById(R.id.picker_thumb_check);
        thumbDurationText = (TextView) itemView.findViewById(R.id.picker_thumb_text);
        thumbCardView = (CardView) itemView.findViewById(R.id.picker_thumb_cardview);
        thumbCardView.setOnTouchListener(touchListener);
    }

    private View.OnTouchListener touchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();

            switch (action){
                case MotionEvent.ACTION_DOWN:

                    Animation bAnim = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.button_scale_anim);
                    itemView.startAnimation(bAnim);
                    Animation aAnim = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.button_scale_anim_after);
                    itemView.startAnimation(aAnim);
                    break;

            }

            return false;
        }
    };

    public VideoItem getItem() {
        return item;
    }

    public void setItem(VideoItem item) {
        this.item = item;
    }
}
