package me.ggum.gum.data;

import java.io.Serializable;

/**
 * Created by sb on 2017. 1. 13..
 */

public class VideoInputData implements Serializable{
    private String path;
    private long duration;
    private int width, height;
    private double frameRate;
    private int orientation;

    public VideoInputData(String path, long duration, int width, int height, double frameRate, int orientation) {
        this.path = path;
        this.duration = duration;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.orientation = orientation;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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
}
