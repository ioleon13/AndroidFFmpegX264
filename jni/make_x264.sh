#!/bin/bash

pushd `dirname $0`
. settings.sh

pushd x264
make clean
make
make install
popd;popd
