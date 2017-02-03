package me.ggum.gum.decoder;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import me.ggum.gum.encoder_trim.AudioEncoderCore;
import me.ggum.gum.encoder_trim.TrimMuxerWrapper;

/**
 * Created by sb on 2017. 1. 22..
 */

public class PreAudioDecoder extends Thread {
    private static final String TAG = "PreAudioDecoder";

    private static final int TIMEOUT_US = 1000;
    private MediaExtractor mExtractor;
    private MediaCodec mDecoder;

    private boolean eosReceived;

    private int mSampleRate = 0;

    private long startTime, endTime;

    private AudioEncoderCore mEncoder;
    private TrimMuxerWrapper muxerWrapper;

    private OnPreAudioDecoderListener listener;

    public void setOnPreAudioDecoderListener(OnPreAudioDecoderListener listener){
        this.listener = listener;
    }

    public void init(String path, long startTime, long endTime, TrimMuxerWrapper muxerWrapper){
        eosReceived = false;
        this.startTime = startTime;
        this.endTime = endTime;
        this.muxerWrapper = muxerWrapper;

        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        int channel = 0;
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                mExtractor.selectTrack(i);
                Log.d(TAG, "format : " + format);
                ByteBuffer csd = format.getByteBuffer("csd-0");

                for (int k = 0; k < csd.capacity(); ++k) {
                    Log.e(TAG, "csd : " + csd.array()[k]);
                }
                mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channel = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                break;
            }
        }

        MediaFormat format = makeAACCodecSpecificData(MediaCodecInfo.CodecProfileLevel.AACObjectLC, mSampleRate, channel);
        if (format == null)
            return;

        try {
            mDecoder = MediaCodec.createDecoderByType("audio/mp4a-latm");
            mDecoder.configure(format, null, null, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (mDecoder == null) {
            Log.e(TAG, "Can't find video info!");
            return;
        }

        mDecoder.start();

        mEncoder = new AudioEncoderCore(muxerWrapper, mSampleRate, channel);

    }

    @Override
    public void run() {

        boolean isInput = true;
        boolean isFirst = true;
        ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
        ByteBuffer[] outputBuffers = mDecoder.getOutputBuffers();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        int buffsize = AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        // create an audiotrack object
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffsize,
                AudioTrack.MODE_STREAM);
        audioTrack.play();
        if(listener != null){
            listener.onStartAudioDecode();
        }
        while (!eosReceived) {
           if(isInput){
               int inIndex = mDecoder.dequeueInputBuffer(TIMEOUT_US);
               if (inIndex >= 0) {
                   ByteBuffer buffer = inputBuffers[inIndex];
                   int sampleSize = mExtractor.readSampleData(buffer, 0);
                   if (sampleSize < 0) {
                       // We shouldn't stop the playback at this point, just pass the EOS
                       // flag to mDecoder, we will get it again from the
                       // dequeueOutputBuffer
                       Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                       mDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                   } else {

                       mExtractor.advance();

                       if(isFirst){

                           mExtractor.seekTo(startTime*1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

                           isFirst = false;
                       }

                       long presentationTimeUs = mExtractor.getSampleTime();


                       mDecoder.queueInputBuffer(inIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);

                   }
               }
           }

            if(!isInput){
                mEncoder.release();
                mEncoder = null;

                mDecoder.stop();
                mDecoder.release();
                mDecoder = null;

                mExtractor.release();
                mExtractor = null;

                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;

                break;
            }

            int outIndex = mDecoder.dequeueOutputBuffer(info, TIMEOUT_US);
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    outputBuffers = mDecoder.getOutputBuffers();
                    break;

                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    MediaFormat format = mDecoder.getOutputFormat();
                    Log.d(TAG, "New format " + format);
                    audioTrack.setPlaybackRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));

                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d(TAG, "dequeueOutputBuffer timed out!");
                    break;

                default:
                    ByteBuffer outBuffer = outputBuffers[outIndex];
                    Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + outBuffer);

                    final byte[] chunk = new byte[info.size];
                    outBuffer.get(chunk); // Read the buffer all at once
                    outBuffer.clear(); // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN



                    audioTrack.write(chunk, info.offset, info.offset + info.size);
                    mEncoder.drainAudio(outBuffer, info.size, getPTSUs());

                     // AudioTrack write data
                    mDecoder.releaseOutputBuffer(outIndex, false);
                    break;
            }

            // All decoded frames have been rendered, we can stop playing now
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                break;
            }


        }

        if(mEncoder != null){
            mEncoder.release();
            mEncoder = null;
        }

        if(mDecoder != null){
            mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }

        if(mExtractor != null){
            mExtractor.release();
            mExtractor = null;
        }

        if(audioTrack != null){
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
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
     * The code profile, Sample rate, channel Count is used to
     * produce the AAC Codec SpecificData.
     * Android 4.4.2/frameworks/av/media/libstagefright/avc_utils.cpp refer
     * to the portion of the code written.
     *
     * MPEG-4 Audio refer : http://wiki.multimedia.cx/index.php?title=MPEG-4_Audio#Audio_Specific_Config
     *
     * @param audioProfile is MPEG-4 Audio Object Types
     * @param sampleRate
     * @param channelConfig
     * @return MediaFormat
     */
    private MediaFormat makeAACCodecSpecificData(int audioProfile, int sampleRate, int channelConfig) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelConfig);

        int samplingFreq[] = {
                96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
                16000, 12000, 11025, 8000
        };

        // Search the Sampling Frequencies
        int sampleIndex = -1;
        for (int i = 0; i < samplingFreq.length; ++i) {
            if (samplingFreq[i] == sampleRate) {
                Log.d("TAG", "kSamplingFreq " + samplingFreq[i] + " i : " + i);
                sampleIndex = i;
            }
        }

        if (sampleIndex == -1) {
            return null;
        }

        ByteBuffer csd = ByteBuffer.allocate(2);
        csd.put((byte) ((audioProfile << 3) | (sampleIndex >> 1)));

        csd.position(1);
        csd.put((byte) ((byte) ((sampleIndex << 7) & 0x80) | (channelConfig << 3)));
        csd.flip();
        format.setByteBuffer("csd-0", csd); // add csd-0

        for (int k = 0; k < csd.capacity(); ++k) {
            Log.e("TAG", "csd : " + csd.array()[k]);
        }

        return format;
    }

    public void close() {
        eosReceived = true;
    }

    public interface OnPreAudioDecoderListener{
        void onStartAudioDecode();
    }
}
