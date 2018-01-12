package com.cgfay.cainfilter.filter.base;

import android.graphics.PointF;
import android.opengl.GLES30;
import android.opengl.Matrix;


import com.cgfay.cainfilter.utils.GlUtil;
import com.cgfay.cainfilter.utils.TextureRotationUtils;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * 基类滤镜
 * Created by cain on 2017/7/9.
 */

public class BaseImageFilter {

    protected static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;                                   \n" +
            "uniform mat4 uTexMatrix;                                   \n" +
            "attribute vec4 aPosition;                                  \n" +
            "attribute vec4 aTextureCoord;                              \n" +
            "varying vec2 textureCoordinate;                            \n" +
            "void main() {                                              \n" +
            "    gl_Position = uMVPMatrix * aPosition;                  \n" +
            "    textureCoordinate =(uTexMatrix * aTextureCoord).xy;    \n" +
            "}                                                          \n";

    protected static final String FRAGMENT_SHADER_2D =
            "precision mediump float;                                   \n" +
            "varying vec2 textureCoordinate;                            \n" +
            "uniform sampler2D inputTexture;                                \n" +
            "void main() {                                              \n" +
            "    gl_FragColor = texture2D(inputTexture, textureCoordinate); \n" +
            "}                                                          \n";

    private static final FloatBuffer FULL_RECTANGLE_BUF =
            GlUtil.createFloatBuffer(TextureRotationUtils.CubeVertices);

    protected FloatBuffer mVertexArray = FULL_RECTANGLE_BUF;
    protected FloatBuffer mTexCoordArray = GlUtil.createFloatBuffer(TextureRotationUtils.TextureVertices);
    protected int mCoordsPerVertex = TextureRotationUtils.CoordsPerVertex;
    protected int mVertexCount = TextureRotationUtils.CubeVertices.length / mCoordsPerVertex;

    protected int mProgramHandle;
    protected int muMVPMatrixLoc;
    protected int maPositionLoc;
    protected int maTextureCoordLoc;
    protected int mInputTextureLoc;
    protected int mTexMatrixLoc;

    // 渲染的Image的宽高
    protected int mImageWidth;
    protected int mImageHeight;
    // 显示输出的宽高
    protected int mDisplayWidth;
    protected int mDisplayHeight;

    // 变换矩阵
    protected float[] mMVPMatrix = new float[16];
    // 缩放矩阵
    protected float[] mTexMatrix = new float[16];

    private final LinkedList<Runnable> mRunOnDraw;

    // FBO属性
    protected int[] mFramebuffers;
    protected int[] mFramebufferTextures;
    protected int mFrameWidth = -1;
    protected int mFrameHeight = -1;

    public BaseImageFilter() {
        this(VERTEX_SHADER, FRAGMENT_SHADER_2D);
    }

    public BaseImageFilter(String vertexShader, String fragmentShader) {
        mRunOnDraw = new LinkedList<>();
        mProgramHandle = GlUtil.createProgram(vertexShader, fragmentShader);
        maPositionLoc = GLES30.glGetAttribLocation(mProgramHandle, "aPosition");
        maTextureCoordLoc = GLES30.glGetAttribLocation(mProgramHandle, "aTextureCoord");
        muMVPMatrixLoc = GLES30.glGetUniformLocation(mProgramHandle, "uMVPMatrix");
        mInputTextureLoc = GLES30.glGetUniformLocation(mProgramHandle, "inputTexture");
        mTexMatrixLoc = GLES30.glGetUniformLocation(mProgramHandle, "uTexMatrix");
        initIdentityMatrix();
    }

    /**
     * Surface发生变化时调用
     * @param width
     * @param height
     */
    public void onInputSizeChanged(int width, int height) {
        mImageWidth = width;
        mImageHeight = height;
    }

    /**
     *  显示视图发生变化时调用
     * @param width
     * @param height
     */
    public void onDisplayChanged(int width, int height) {
        mDisplayWidth = width;
        mDisplayHeight = height;
    }

    /**
     * 绘制Frame
     * @param textureId
     */
    public boolean drawFrame(int textureId) {
        return drawFrame(textureId, mVertexArray, mTexCoordArray);
    }

    /**
     * 绘制Frame
     * @param textureId
     * @param vertexBuffer
     * @param textureBuffer
     */
    public boolean drawFrame(int textureId, FloatBuffer vertexBuffer,
                          FloatBuffer textureBuffer) {
        if (textureId == GlUtil.GL_NOT_INIT) {
            return false;
        }
        GLES30.glUseProgram(mProgramHandle);
        runPendingOnDrawTasks();

        vertexBuffer.position(0);
        GLES30.glVertexAttribPointer(maPositionLoc, mCoordsPerVertex,
                GLES30.GL_FLOAT, false, 0, vertexBuffer);
        GLES30.glEnableVertexAttribArray(maPositionLoc);

        textureBuffer.position(0);
        GLES30.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES30.GL_FLOAT, false, 0, textureBuffer);
        GLES30.glEnableVertexAttribArray(maTextureCoordLoc);

        GLES30.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMVPMatrix, 0);
        GLES30.glUniformMatrix4fv(mTexMatrixLoc, 1, false, mTexMatrix, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(getTextureType(), textureId);
        GLES30.glUniform1i(mInputTextureLoc, 0);
        onDrawArraysBegin();
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, mVertexCount);
        onDrawArraysAfter();
        GLES30.glDisableVertexAttribArray(maPositionLoc);
        GLES30.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES30.glBindTexture(getTextureType(), 0);
        onBeforeProgramRelease();
        GLES30.glUseProgram(0);
        return true;
    }

    /**
     * 绘制到FBO
     * @param textureId
     * @return FBO绑定的Texture
     */
    public int drawFrameBuffer(int textureId) {
        return drawFrameBuffer(textureId, mVertexArray, mTexCoordArray);
    }

    /**
     * 绘制到FBO
     * @param textureId
     * @param vertexBuffer
     * @param textureBuffer
     * @return FBO绑定的Texture
     */
    public int drawFrameBuffer(int textureId, FloatBuffer vertexBuffer, FloatBuffer textureBuffer) {
        if (mFramebuffers == null) {
            return textureId;
        }
        runPendingOnDrawTasks();
        GLES30.glViewport(0, 0, mFrameWidth, mFrameHeight);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFramebuffers[0]);
        GLES30.glUseProgram(mProgramHandle);
        vertexBuffer.position(0);
        GLES30.glVertexAttribPointer(maPositionLoc, mCoordsPerVertex,
                GLES30.GL_FLOAT, false, 0, vertexBuffer);
        GLES30.glEnableVertexAttribArray(maPositionLoc);

        textureBuffer.position(0);
        GLES30.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES30.GL_FLOAT, false, 0, textureBuffer);
        GLES30.glEnableVertexAttribArray(maTextureCoordLoc);

        GLES30.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMVPMatrix, 0);
        GLES30.glUniformMatrix4fv(mTexMatrixLoc, 1, false, mTexMatrix, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(getTextureType(), textureId);
        GLES30.glUniform1i(mInputTextureLoc, 0);
        onDrawArraysBegin();
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, mVertexCount);
        onDrawArraysAfter();
        GLES30.glDisableVertexAttribArray(maPositionLoc);
        GLES30.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES30.glBindTexture(getTextureType(), 0);
        onBeforeProgramRelease();
        GLES30.glUseProgram(0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, mDisplayWidth, mDisplayHeight);
        return mFramebufferTextures[0];
    }

    /**
     * 获取Texture类型
     * GLES30.TEXTURE_2D / GLES11Ext.GL_TEXTURE_EXTERNAL_OES等
     */
    public int getTextureType() {
        return GLES30.GL_TEXTURE_2D;
    }

    /**
     * 调用drawArrays之前，方便添加其他属性
     */
    public void onDrawArraysBegin() {

    }

    /**
     * drawArrays调用之后，方便销毁其他属性
     */
    public void onDrawArraysAfter() {

    }

    /**
     * 释放program之前，可以做其他操作
     */
    protected void onBeforeProgramRelease() {

    }

    /**
     * 释放资源
     */
    public void release() {
        GLES30.glDeleteProgram(mProgramHandle);
        mProgramHandle = -1;
        destroyFramebuffer();
    }

    public void initFramebuffer(int width, int height) {
        if (mFramebuffers != null && (mFrameWidth != width || mFrameHeight != height)) {
            destroyFramebuffer();
        }
        if (mFramebuffers == null) {
            mFrameWidth = width;
            mFrameHeight = height;
            mFramebuffers = new int[1];
            mFramebufferTextures = new int[1];
            GlUtil.createSampler2DFrameBuff(mFramebuffers, mFramebufferTextures, width, height);
        }
    }

    public void destroyFramebuffer() {
        if (mFramebufferTextures != null) {
            GLES30.glDeleteTextures(1, mFramebufferTextures, 0);
            mFramebufferTextures = null;
        }

        if (mFramebuffers != null) {
            GLES30.glDeleteFramebuffers(1, mFramebuffers, 0);
            mFramebuffers = null;
        }
        mImageWidth = -1;
        mImageHeight = -1;
    }

    /**
     * 初始化单位矩阵
     */
    public void initIdentityMatrix() {
        Matrix.setIdentityM(mMVPMatrix, 0);
        Matrix.setIdentityM(mTexMatrix, 0);
    }

    /**
     * 设置变换矩阵
     * @param matrix
     */
    public void setMVPMatrix(float[] matrix) {
        if (!Arrays.equals(mMVPMatrix, matrix)) {
            mMVPMatrix = matrix;
        }
    }

    ///------------------ 统一变量(uniform)设置 ------------------------///
    protected void setInteger(final int location, final int intValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES30.glUniform1i(location, intValue);
            }
        });
    }

    protected void setFloat(final int location, final float floatValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES30.glUniform1f(location, floatValue);
            }
        });
    }

    protected void setFloatVec2(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES30.glUniform2fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setFloatVec3(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES30.glUniform3fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setFloatVec4(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES30.glUniform4fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setFloatArray(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES30.glUniform1fv(location, arrayValue.length, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setPoint(final int location, final PointF point) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                float[] vec2 = new float[2];
                vec2[0] = point.x;
                vec2[1] = point.y;
                GLES30.glUniform2fv(location, 1, vec2, 0);
            }
        });
    }

    protected void setUniformMatrix3f(final int location, final float[] matrix) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                GLES30.glUniformMatrix3fv(location, 1, false, matrix, 0);
            }
        });
    }

    protected void setUniformMatrix4f(final int location, final float[] matrix) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                GLES30.glUniformMatrix4fv(location, 1, false, matrix, 0);
            }
        });
    }

    protected void runOnDraw(final Runnable runnable) {
        synchronized (mRunOnDraw) {
            mRunOnDraw.addLast(runnable);
        }
    }

    protected void runPendingOnDrawTasks() {
        while (!mRunOnDraw.isEmpty()) {
            mRunOnDraw.removeFirst().run();
        }
    }
}