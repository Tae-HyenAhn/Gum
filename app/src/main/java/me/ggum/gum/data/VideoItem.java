package me.ggum.gum.data;

import android.graphics.Bitmap;

import java.util.Date;

import me.ggum.gum.utils.VideoInfoUtil;

/**
 * Created by sb on 2017. 1. 10..
 */

public class VideoItem {

    private String videoPath;
    private long duration;
    private int width;
    private int height;
    private String dateTaken;
    private double frameRate;
    private int orientation;
    private Bitmap thumb;
    private boolean check;
    private String thumbPath;


    public VideoItem(String videoPath, long duration, int width, int height, String dateTaken, double frameRate, int orientation, Bitmap thumb, boolean check, String thumbPath) {
        this.videoPath = videoPath;
        this.duration = duration;
        this.width = width;
        this.height = height;
        this.dateTaken = dateTaken;
        this.frameRate = frameRate;
        this.orientation = orientation;
        this.thumb = thumb;
        this.check = check;
        this.thumbPath = thumbPath;

    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getDateTaken() {
        return dateTaken;
    }

    public void setDateTaken(String dateTaken) {
        this.dateTaken = dateTaken;
    }

    public double getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(double frameRate) {
        this.frameRate = frameRate;
    }

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public Bitmap getThumb() {
        return thumb;
    }

    public void setThumb(Bitmap thumb) {
        this.thumb = thumb;
    }

    public boolean isCheck() {
        return check;
    }

    public void setCheck(boolean check) {
        this.check = check;
    }

    public String getThumbPath() {
        return thumbPath;
    }

    public void setThumbPath(String thumbPath) {
        this.thumbPath = thumbPath;
    }

}
