/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <string.h>
#include <jni.h>
#include <stdio.h>
#include <android/log.h>
#include <stdlib.h>

#include <arpa/inet.h>
#include <x264/x264.h>

typedef struct {
	x264_param_t *param;
	x264_t *handle;
	x264_picture_t *picture;
	x264_nal_t *nal;
} Encoder;

/* Begin encode
 * file located at:
 *
 *   src/com/livecamera/encoder/h264encoder.java
 */
jlong
Java_com_livecamera_encoder_h264encoder_CompressBegin( JNIEnv* env, jobject thiz,
		jint width, jint height)
{
	Encoder *en = (Encoder*) malloc(sizeof(Encoder));
	en->param = (x264_param_t*) malloc(sizeof(x264_param_t));
	en->picture = (x264_picture_t*) malloc(sizeof(x264_picture_t));
	x264_param_default(en->param);                          //set default param
	en->param->i_log_level = X264_LOG_NONE;
	en->param->i_width = width;
	en->param->i_height = height;
	en->param->rc.i_lookahead = 0;
	en->param->i_bframe = 0;
	en->param->i_fps_num = 5;
	en->param->i_fps_den = 1;
	if ((en->handle = x264_encoder_open(en->param)) == 0) {
		return 0;
	}

	//create a new pic
	x264_picture_alloc(en->picture, X264_CSP_I420,
			en->param->i_width, en->param->i_height);

	return (jlong)en;
}

/**
 * When compress end, clear some resource
 */
jint
Java_com_livecamera_encoder_h264encoder_CompressEnd( JNIEnv* env, jobject thiz, jlong handle)
{
	Encoder *en = (Encoder*)handle;
	if (en->picture) {
		x264_picture_clean(en->picture);
		free(en->picture);
		en->picture = 0;
	}

	if (en->param) {
		free(en->param);
		en->param = 0;
	}

	if (en->handle) {
		x264_encoder_close(en->handle);
	}

	free(en);

	return 0;
}

/**
 * compress the buffer data
 */
jint
Java_com_livecamera_encoder_h264encoder_CompressBuffer( JNIEnv* env, jobject thiz, jlong handle,
		jint type, jbyteArray in, jint insize, jbyteArray out)
{
	Encoder *en = (Encoder*)handle;
	x264_picture_t pic_out;
	int i_data = 0;
	int nNal = -1;
	int result = 0;
	int i = 0;
	int j = 0;
	int nPix = 0;

	jbyte *Buf = (jbyte*)(*env)->GetByteArrayElements(env, in, 0);
	jbyte *h264Buf = (jbyte*)(*env)->GetByteArrayElements(env, out, 0);
	unsigned char *pTemp = h264Buf;
	int nPicSize = en->param->i_width * en->param->i_height;

	/**
	 * YUV => 4:2:0
     * YYYY
     * YYYY
     * UVUV
     */
	jbyte *y = en->picture->img.plane[0];
	jbyte *v = en->picture->img.plane[1];
	jbyte *u = en->picture->img.plane[2];
	memcpy(en->picture->img.plane[0], Buf, nPicSize);
	for (i=0; i < nPicSize / 4; i++) {
		*(u+i) = *(Buf + nPicSize + i*2);
		*(v+i) = *(Buf + nPicSize + i*2 + 1);
	}

	switch (type) {
	        case 0:
	        	en->picture->i_type = X264_TYPE_P;
	        	break;
            case 1:
            	en->picture->i_type = X264_TYPE_IDR;
            	break;
            case 2:
            	en->picture->i_type = X264_TYPE_I;
            	break;
            default:
            	en->picture->i_type = X264_TYPE_AUTO;
            	break;
	}

	if (x264_encoder_encode(en->handle, &(en->nal), &nNal, en->picture, &pic_out) < 0) {
		return -1;
	}

	for (i = 0; i < nNal; i++) {
		memcpy(pTemp, en->nal[i].p_payload, en->nal[i].i_payload);
		pTemp += en->nal[i].i_payload;
		result += en->nal[i].i_payload;
	}

	//release the buffer
	(*env)->ReleaseByteArrayElements(env, in, Buf, 0);
	(*env)->ReleaseByteArrayElements(env, out, h264Buf, 0);

	return result;
}
