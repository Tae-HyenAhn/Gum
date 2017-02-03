package me.ggum.gum.encoder_export;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * Created by sb on 2017. 1. 25..
 */

public class AudioEncoderCore extends Thread {
    private static final String TAG = "AudioEncoderCore_exp";

    private static final int TIMEOUT_US = 1000;
    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLE_RATE = 44100;
    private static final int BIT_RATE = 128000;
    private MediaCodec mEncoder;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mTrackId;

    private int mSampleRate;
    private int mChannel;

    private ExportMuxerWrapper muxerWrapper;

    public AudioEncoderCore(ExportMuxerWrapper muxerWrapper, int mSampleRate, int mChannel) {
        mTrackId = -1;
        this.mSampleRate = mSampleRate;
        this.mChannel = mChannel;
        this.muxerWrapper = muxerWrapper;

        MediaCodecInfo mediaCodecInfo = selectAudioCodec(MIME_TYPE);
        if(mediaCodecInfo == null){
            return;
        }

        MediaFormat eformat = MediaFormat.createAudioFormat(MIME_TYPE, mSampleRate, mChannel);
        eformat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        eformat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO);
        eformat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        eformat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 25000);
        eformat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, mChannel);


        try{
            mBufferInfo = new MediaCodec.BufferInfo();
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(eformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoder.start();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    public void drainAudio(ByteBuffer buf, int readBytes, long time){

        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] inputBuffers = mEncoder.getInputBuffers();
        int inputBufferIndex = -1;
        try{
            inputBufferIndex = mEncoder.dequeueInputBuffer(TIMEOUT_USEC);
        }catch (IllegalStateException e){
            return;
        }

        if(inputBufferIndex >= 0){
            final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            if(buf != null){
                Log.d(TAG, "INPUTBUFFER: "+inputBuffer.limit()+", BUF: "+buf.limit());

                inputBuffer.put(buf);
            }

            if(readBytes <= 0){
                mEncoder.queueInputBuffer(inputBufferIndex, 0, 0, time, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

            }else {

                mEncoder.queueInputBuffer(inputBufferIndex, 0, readBytes, time, 0);
            }
        }else if(inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER){

        }


        ByteBuffer[] outputBuffers = mEncoder.getOutputBuffers();
        int outputBufferIndex = -1;
        try{
            outputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        }catch(IllegalStateException e){
            return;
        }

        if(outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER){

        } else if(outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){
            outputBuffers = mEncoder.getOutputBuffers();
        } else if(outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
            MediaFormat newFormat = mEncoder.getOutputFormat();
            mTrackId = muxerWrapper.addTrack(newFormat);
            //VideoEncoderCore.mMuxer_trim
            //mTrackId = VideoEncoderCore.mMuxer_trim.addTrack(newFormat);
            if (mTrackId >= 0){
                //VideoEncoderCore.mMuxerStarted_trim = true;
            }
        }else if(outputBufferIndex <0){

        }else {
            ByteBuffer encodedData = outputBuffers[outputBufferIndex];
            if(encodedData == null){
                throw new RuntimeException("out");
            }
            if((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0){
                mBufferInfo.size = 0;
            }

            if(mBufferInfo.size != 0){
                encodedData.position(mBufferInfo.offset);
                encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                Log.d(TAG, "OK: ");

                muxerWrapper.writeSampleData(mTrackId, encodedData, mBufferInfo);
                //VideoEncoderCore.mMuxer_trim.writeSampleData(mTrackId, encodedData, mBufferInfo);
            }


            mEncoder.releaseOutputBuffer(outputBufferIndex, false);
        }
    }

    public void release(){
        if(muxerWrapper != null){
            muxerWrapper.release();

        }

        if(mEncoder != null){
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
    }

    private long prevOutputPTSUs = 0;
    /**
     * get next encoding presentationTimeUs
     * @return
     */
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

    /**
     * select the first codec that match a specific MIME type
     * @param mimeType
     * @return
     */
    private static final MediaCodecInfo selectAudioCodec(final String mimeType) {
        Log.v(TAG, "selectAudioCodec:");

        MediaCodecInfo result = null;
        // get the list of available codecs
        final int numCodecs = MediaCodecList.getCodecCount();
        LOOP:	for (int i = 0; i < numCodecs; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {	// skipp decoder
                continue;
            }
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                Log.i(TAG, "supportedType:" + codecInfo.getName() + ",MIME=" + types[j]);
                if (types[j].equalsIgnoreCase(mimeType)) {
                    if (result == null) {
                        result = codecInfo;
                        break LOOP;
                    }
                }
            }
        }
        return result;
    }
}
