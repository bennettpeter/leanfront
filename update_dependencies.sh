#!/bin/bash

# This script downloads or updates the dependencies for
# building leanfront

scriptname=`readlink -e "$0"`
scriptpath=`dirname "$scriptname"`
scriptname=`basename "$scriptname" .sh`
set -e

cd "$scriptpath"

cd ..
if [[ ! -d ffmpeg ]] ; then
    git clone git@github.com:FFmpeg/FFmpeg.git ffmpeg
fi
git -C ffmpeg fetch
git -C ffmpeg checkout release/6.0 2>&1
git -C ffmpeg pull 2>&1

if [[ ! -d glide ]] ; then
    git clone git@github.com:bennettpeter/glide.git
fi
git -C glide fetch
git -C glide checkout leanfront 2>&1
git -C glide pull 2>&1

if [[ ! -d media ]] ; then
    git clone git@github.com:bennettpeter/media.git
fi
git -C media fetch
git -C media checkout 1.8.0-lf 2>&1
git -C media pull 2>&1
