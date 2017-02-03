package me.ggum.gum.gles;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.content.Context;
import android.opengl.GLES20;

/**
 * Created by sb on 2017. 1. 3..
 */

public class Model {
    FloatBuffer m_vertexBuffer;
    ShortBuffer m_indexBuffer;
    Texture m_texture;
    Shader m_shader;

    static float vertices[]=
            {
                    -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
                    -1.0f,  1.0f, 0.0f, 0.0f, 1.0f,
                    1.0f,  1.0f, 0.0f, 1.0f, 1.0f,
                    1.0f, -1.0f, 0.0f, 1.0f, 0.0f
            };
    static short indices[] =
            {
                    0, 2, 1,
                    0, 3, 2
            };
    int m_iNumIndices = 6;

    public Model(Context ctx, Shader shader)
    {
        ByteBuffer bbVertices = ByteBuffer.allocateDirect(vertices.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());

        m_vertexBuffer = bbVertices.asFloatBuffer();
        m_vertexBuffer.put(vertices);
        m_vertexBuffer.position(0);

        ByteBuffer bbIndices = ByteBuffer.allocateDirect(indices.length * 4);
        bbIndices.order(ByteOrder.nativeOrder());

        m_indexBuffer = bbIndices.asShortBuffer();
        m_indexBuffer.put(indices);
        m_indexBuffer.position(0);

        //objects
        m_texture = new Texture(ctx);
        m_shader = shader;
    }

    public void draw()
    {
        m_vertexBuffer.position(Shader.POSITION_OFFSET);
        int iPosId = m_shader.getPositionId();

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glEnableVertexAttribArray(iPosId);
        GLES20.glVertexAttribPointer(iPosId, 3, GLES20.GL_FLOAT, false,
                Shader.VERTEX_STRIDE, m_vertexBuffer);

        m_vertexBuffer.position(Shader.TEXCOORD_OFFSET);
        int iTexCoordId = m_shader.getTexCoordId();
        GLES20.glEnableVertexAttribArray(iTexCoordId);
        GLES20.glVertexAttribPointer(iTexCoordId, 2, GLES20.GL_FLOAT, false,
                Shader.VERTEX_STRIDE, m_vertexBuffer);

        m_texture.setTexture();

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, m_iNumIndices,
                GLES20.GL_UNSIGNED_SHORT, m_indexBuffer);

        GLES20.glDisableVertexAttribArray(iPosId);
        GLES20.glDisableVertexAttribArray(iTexCoordId);
        GLES20.glDisable(GLES20.GL_BLEND);

    }
}
