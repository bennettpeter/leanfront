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
git -C ffmpeg checkout n6.1.2

if [[ ! -d ffmpeg ]] ; then
    git clone git@github.com:bennettpeter/glide.git
fi
git -C glide fetch
git -C glide checkout leanfront

if [[ ! -d media ]] ; then
    git clone git@github.com:bennettpeter/media.git
fi
git -C media fetch
git -C media checkout 1.4.0-lf
