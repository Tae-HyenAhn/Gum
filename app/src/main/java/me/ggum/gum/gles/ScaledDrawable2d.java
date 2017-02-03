package me.ggum.gum.gles;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by sb on 2016. 12. 29..
 */

public class ScaledDrawable2d extends Drawable2d {
    private static final int SIZEOF_FLOAT = 4;

    private FloatBuffer mTweakedTexCoordArray;
    private float mScale = 1.0f;
    private boolean mRecalculate;


    /**
     * Trivial constructor.
     */
    public ScaledDrawable2d(Prefab shape) {
        super(shape);
        mRecalculate = true;
    }

    /**
     * Set the scale factor.
     */
    public void setScale(float scale) {
        if (scale < 0.0f || scale > 1.0f) {
            throw new RuntimeException("invalid scale " + scale);
        }
        mScale = scale;
        mRecalculate = true;
    }

    /**
     * Returns the array of texture coordinates.  The first time this is called, we generate
     * a modified version of the array from the parent class.
     * <p>
     * To avoid allocations, this returns internal state.  The caller must not modify it.
     */
    @Override
    public FloatBuffer getTexCoordArray() {
        if (mRecalculate) {
            //Log.v(TAG, "Scaling to " + mScale);
            FloatBuffer parentBuf = super.getTexCoordArray();
            int count = parentBuf.capacity();

            if (mTweakedTexCoordArray == null) {
                ByteBuffer bb = ByteBuffer.allocateDirect(count * SIZEOF_FLOAT);
                bb.order(ByteOrder.nativeOrder());
                mTweakedTexCoordArray = bb.asFloatBuffer();
            }

            // Texture coordinates range from 0.0 to 1.0, inclusive.  We do a simple scale
            // here, but we could get much fancier if we wanted to (say) zoom in and pan
            // around.
            FloatBuffer fb = mTweakedTexCoordArray;
            float scale = mScale;
            for (int i = 0; i < count; i++) {
                float fl = parentBuf.get(i);
                fl = ((fl - 0.5f) * scale) + 0.5f;
                fb.put(i, fl);
            }

            mRecalculate = false;
        }

        return mTweakedTexCoordArray;
    }
}
