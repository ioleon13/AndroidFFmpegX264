#!/bin/bash

# set the path of NDK, or export the NDK path
if [[ "x$NDK_HOME" == "x" ]]; then
	NDK_HOME=/home/liya/03.tools/android-ndk-r10
fi

# mini_feature set to 1 means using only a small number of features of ffmpeg, set this to 0 if you want everything.
if [[ "x$mini_feature" == "x" ]]; then
	mini_feature=1
fi

if [[ ! -d $NDK_HOME ]]; then
	echo "$NDK_HOME was not a directory. Exiting."
	exit 1
fi

function current_dir {
	echo "$(cd "$(dirname $0)"; pwd)"
}

export PATH=$PATH:$NDK_HOME:$(current_dir)/toolchain/bin

echo $PATH
