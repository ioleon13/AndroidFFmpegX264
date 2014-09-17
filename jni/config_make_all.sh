#!/bin/bash

function die {
	echo "$1 failed" && exit 1
}

./configure_x264.sh || die "x264 configure"
./make_x264.sh || die "x264 make"
./configure_ffmpeg.sh || die "ffmpeg configure"
./make_ffmpeg.sh || die "ffmpeg make"
