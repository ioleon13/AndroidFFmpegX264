#!/bin/bash

#export PREBUILT=$NDK_HOME/toolchains/arm-linux-androideabi-4.8/prebuilt/linux-x86_64/bin
#The directory in Mac os
export PREBUILT=$NDK_HOME/toolchains/arm-linux-androideabi-4.8/prebuilt/darwin-x86_64/bin
export PLATFORM=$NDK_HOME/platforms/android-19/arch-arm
pushd `dirname $0`
. settings.sh

pushd x264

./configure --prefix=./android_x264 \
--enable-static \
--enable-pic \
--disable-asm \
--disable-cli \
--host=arm-linux \
--cross-prefix=$PREBUILT/arm-linux-androideabi- \
--sysroot=$PLATFORM

popd;popd
