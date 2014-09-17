#!/bin/bash

pushd `dirname $0`
. settings.sh

$NDK_HOME/build/tools/make-standalone-toolchain.sh --platform=android-19 --install-dir=./toolchain
