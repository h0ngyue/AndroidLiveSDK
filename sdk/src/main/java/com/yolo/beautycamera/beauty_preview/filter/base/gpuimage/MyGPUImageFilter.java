/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yolo.beautycamera.beauty_preview.filter.base.gpuimage;

import android.opengl.GLES20;

import com.yolo.beautycamera.beauty_preview.utils.OpenGlUtils;
import com.yolo.beautycamera.beauty_preview.utils.Rotation;
import com.yolo.beautycamera.beauty_preview.utils.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import jp.co.cyberagent.android.gpuimage.GPUImageFilter;

public class MyGPUImageFilter extends GPUImageFilter {

    protected int mIntputWidth, mIntputHeight;
    protected FloatBuffer mGLCubeBuffer;
    protected FloatBuffer mGLTextureBuffer;

    public MyGPUImageFilter() {
        this(NO_FILTER_VERTEX_SHADER, NO_FILTER_FRAGMENT_SHADER);
    }

    public MyGPUImageFilter(final String vertexShader, final String fragmentShader) {
        super(vertexShader, fragmentShader);

        mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
    }


    // 这个一定要调用，因为子类里可能设置了一些东西,比如setTexelSize
    public void onInputSizeChanged(final int width, final int height) {
        mIntputWidth = width;
        mIntputHeight = height;
    }

    public void onDraw(final int textureId, final FloatBuffer cubeBuffer,
                       final FloatBuffer textureBuffer) {
        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();
        if (!isInitialized()) {
//            return OpenGlUtils.NOT_INIT;
            return;
        }

        cubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, cubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                textureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }
        onDrawArraysPre();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        onDrawArraysAfter();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
//        return OpenGlUtils.ON_DRAWN;

    }

//    /**
//     *
//     * @param textureId
//     * @return
//     */
//    public int onDraw(final int textureId) {
//		GLES20.glUseProgram(mGLProgId);
//		runPendingOnDrawTasks();
//		if (!mIsInitialized)
//			return OpenGlUtils.NOT_INIT;
//
//		mGLCubeBuffer.position(0);
//		GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, mGLCubeBuffer);
//		GLES20.glEnableVertexAttribArray(mGLAttribPosition);
//		mGLTextureBuffer.position(0);
//		GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
//		     mGLTextureBuffer);
//		GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
//
//		if (textureId != OpenGlUtils.NO_TEXTURE) {
//            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//		    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
//		    GLES20.glUniform1i(mGLUniformTexture, 0);
//		}
//		onDrawArraysPre();
//		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
//		GLES20.glDisableVertexAttribArray(mGLAttribPosition);
//		GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
//		onDrawArraysAfter();
//		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
//		return OpenGlUtils.ON_DRAWN;
//	}

    protected void onDrawArraysAfter() {
    }


    public int getIntputWidth() {
        return mIntputWidth;
    }

    public int getIntputHeight() {
        return mIntputHeight;
    }

}
