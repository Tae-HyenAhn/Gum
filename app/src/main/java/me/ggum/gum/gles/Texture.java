package me.ggum.gum.gles;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import me.ggum.gum.R;

/**
 * Created by sb on 2017. 1. 3..
 */

public class Texture {
    private int m_iTextureId;

    public Texture(Context ctx)
    {
        m_iTextureId = loadTexture(ctx, R.drawable.common_oval_tex_img);
    }

    public void setTexture()
    {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m_iTextureId);
    }

    public int loadTexture(Context ctx, int rsrcId)
    {
        int[] iTextureId = new int[1];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glGenTextures(1, iTextureId, 0);

        if(iTextureId[0] != 0)
        {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, iTextureId[0]);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;

            Bitmap bitmap = BitmapFactory.decodeResource(ctx.getResources(), rsrcId,
                    options);

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);


            bitmap.recycle();


            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_NEAREST);

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_NEAREST);

        }
        else
        {
            throw new RuntimeException("Error loading texture");
        }

        return iTextureId[0];
    }
}
