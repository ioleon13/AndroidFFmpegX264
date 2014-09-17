#!/bin/bash

pushd `dirname $0`
. settings.sh

pushd ffmpeg
make clean
make -j4
popd;popd
