package me.ggum.gum.data;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

/**
 * Created by sb on 2017. 1. 24..
 */

public class CollectedEncodedData {

    private ByteBuffer outBuffer;
    private MediaCodec.BufferInfo info;

    public CollectedEncodedData(ByteBuffer outBuffer, MediaCodec.BufferInfo info) {
        this.outBuffer = outBuffer;
        this.info = info;
    }

    public ByteBuffer getOutBuffer() {
        return outBuffer;
    }

    public MediaCodec.BufferInfo getInfo() {
        return info;
    }
}
