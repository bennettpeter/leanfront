#!/bin/bash
#
# Copyright (C) 2019 The Android Open Source Project
# Copyright (c) 2019-2020 Peter Bennett
#
# Code from androidx/media
# <https:https://github.com/androidx/media>
# Modified by Peter Bennett
#
# This file is part of MythTV-leanfront.
#
# MythTV-leanfront is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# MythTV-leanfront is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with MythTV-leanfront.  If not, see <https://www.gnu.org/licenses/>.
#

scriptname=`readlink -e "$0"`
scriptpath=`dirname "$scriptname"`
scriptname=`basename "$scriptname" .sh`
set -e

cd "$scriptpath"

# Clear old builds
#~ rm -rf ffmpeg/android-libs/*
FFMPEG_PATH="$(pwd)/../ffmpeg"

cd ../media
FFMPEG_MODULE_PATH="$(pwd)/libraries/decoder_ffmpeg/src/main"
NDK_PATH=$HOME/Android/android-ndk
HOST_PLATFORM="linux-x86_64"
ENABLED_DECODERS=(mp3 aac ac3 eac3 dca truehd mlp vorbis opus flac alac pcm_mulaw pcm_alaw)
cd "${FFMPEG_MODULE_PATH}/jni"
rm -rf ffmpeg
ln -fs "$FFMPEG_PATH" ffmpeg

./build_ffmpeg.sh \
  "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" 21 "${ENABLED_DECODERS[@]}"

echo "ffmpeg build successfully completed"
