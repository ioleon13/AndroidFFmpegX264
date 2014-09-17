#!/bin/bash

pushd `dirname $0`
. settings.sh

if [[ $mini_feature == 1 ]]; then
	echo "Using minimal featurelist, some features would be not support."
	feature_flags="--disable-everything \
	--enable-decoders --enable-demuxers --enable-parsers \
	--enable-muxers --enable-encoders --enable-protocols \
	--enable-encoder=libx264 --enable-libx264 --enable-decoder=h264 --enable-muxer=h264 --enable-demuxer=h264 \
	--enable-decoder=rawvideo \
	--enable-protocol=file \
	--enable-hwaccels"
fi

pushd ffmpeg

TOOLCHAIN=/tmp/vplayer
SYSROOT=$TOOLCHAIN/sysroot/
$NDK_HOME/build/tools/make-standalone-toolchain.sh --platform=android-19 --install-dir=$TOOLCHAIN

export PATH=$TOOLCHAIN/bin:$PATH
export CC="arm-linux-androideabi-gcc"
export LD=arm-linux-androideabi-ld
export AR=arm-linux-androideabi-ar

CFLAGS="-Os -fPIC -marm"

FFMPEG_FLAGS="--target-os=linux \
  --arch=arm \
  --sysroot=$SYSROOT \
  --enable-cross-compile \
  --cross-prefix=arm-linux-androideabi- \
  --enable-shared \
  --enable-static \
  --disable-symver \
  --disable-doc \
  --disable-ffplay \
  --disable-ffmpeg \
  --disable-ffprobe \
  --disable-ffserver \
  --disable-avdevice \
  --disable-avfilter \
  --disable-filters \
  --disable-devices \
  --disable-pthreads \
  --disable-everything \
  --disable-swresample \
  --enable-gpl \
  --enable-muxers \
  --enable-encoders \
  --enable-protocols \
  --enable-parsers \
  --enable-demuxers \
  --enable-decoders \
  --enable-bsfs \
  --enable-network \
  --enable-swscale \
  --enable-libx264 \
  --enable-encoder=libx264 \
  --enable-decoder=h264 \
  --enable-muxer=h264 \
  --enable-demuxer=h264 \
  --disable-demuxer=sbg \
  --disable-demuxer=dts \
  --disable-parser=dca \
  --disable-decoder=dca \
  --extra-libs=-lx264 \
  --enable-asm \
  --enable-version3"

VERSION=armv7

EXTRA_CFLAGS="-I../x264/android_x264/include -march=armv7-a"
EXTRA_LDFLAGS="-L../x264/android_x264/lib"

FFMPEG_FLAGS="$FFMPEG_FLAGS --prefix=../output"

sh  ./configure $FFMPEG_FLAGS --extra-cflags="$CFLAGS $EXTRA_CFLAGS" --extra-ldflags="$EXTRA_LDFLAGS"

popd;popd
