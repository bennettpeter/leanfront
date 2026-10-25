# Developer Guide for leanfront

## Android Development

See https://developer.android.com/guide to learn android development. There are a number of differences when developing for TV.

## Leanfront

Leanfront is written exclusively in java. It does not use Compose, which is a newer user interface feature that requires the Kotlin programming language. For info on TV development see https://developer.android.com/training/tv. Leanfront is built using the android leanback libraries.

Leanfront TV version started as a fork of the Google sample leanback video application. It started as an alternative to mythfrontend for android when I discovered that the android version of mythfrontend could not display 4K video. With mythfrontend the video was resized to 1920x1080, even on a 4K TV with a 4K enabled android device.

Leanfront uses android media3, formerly known as ExoPlayer, for video playback (https://github.com/androidx/media). There is some customization of media3 to cater for special features of leanfront:

- A pull request to support software decoding via ffmpeg. See https://github.com/androidx/media/pull/1591. The pull request is from rabbitknight, works very well, but has never been accepted into media3. This builds upon the ffmpeg audio feature which is a standard part of media3.
- A patch to fix a bug where playback hangs under certain conditions if there is an audio track with no audio data.
- A fix for CEA subtitle frames out of sequence.
- Customize playback speed selection (used by mobile version of leanfront).

We build the media3 components rather than import them from a repository. This is a requirement of the standard ffmpeg audio feature, and is needed because of the customization.

The customization is part of a fork of media3, https://github.com/bennettpeter/media. In that fork, each release of media3 that is included in a release of leanfront is branched into a leanfront specific branch, for example branch 1.11.1-lf is the customized version of release 1.11.1.

Building leanfront requires building parts of ffmpeg and libyuv. libyuv is used by the ffmpeg video software decoder. The script update_dependencies.sh clones https://github.com/FFmpeg/FFmpeg,  https://github.com/bennettpeter/media and https://github.com/bennettpeter/libyuv. The script build_ffmpeg.sh builds them. These must be run before attempting to build leanfront.

All customization is built into a single commit. Upgrading to a new version of media3 requires creating a branch based on the release tag and cherry-picking the commit with the customization. If there were extra commits in the prior versions, I combine them into one when moving to a new version.

I have removed all warnings flagged by Android Studio. Android Studio typically warns about all kinds of things, most of which are not real problems.

Media3 version 1.9 updated the minimum android SDK version to 23. This means many older fire sticks will not support it as they are on version 22. They cannot install leanfront newer than 3ff1a53. If needed I could create a branch for them that remains on 1.9 but includes other new features.

- Fire TV - 1st Gen (2014)
- Fire TV - 2nd Gen (2015)
- Fire TV Stick - 1st Gen (2014)
- Fire TV Stick - Basic Edition (2017)
- Fire TV Stick - 2nd Gen (2016-2019)

## Workarounds

I have injected a class androidx.leanback.widget.WidgetAccess so that I can get access to some android interfaces that are defined as private, protected or final. Without that, I would have to re-implement many user interface classes. I believe that few production applications use the androidx.leanback classes because they are very restrictive.

There is a subtitle bug where a video with 30 bitmap subtitles plays in fits and starts because recent changes formatted the subtitles during extract instead of during render. I use the experimentalSetLegacyDecodingEnabled method to keep using the old way until they fix this.

## Signing certificate

I created the signing certificate, which I have used for leanfront TV and mobile, and for mythfrontend. I submitted the certificate to google. The certificate is used for signing all builds and is used by Google for signing apk files that are downloaded from the play store. It is not used by Amazon for the downloads from the amazon store.

As a result of this, Google Play Store installs can be upgraded with builds signed by my certificate, but Amazon installs cannot without first uninstalling.

## Distributing the application

You can build leanfront yourself, install and run it on your own android TV or Amazon fire stick device. If you use your own certificate or a development certificate you will have to uninstall any version installed with the official certificate before you can install your build.

To distribute an official app you need to be added to the Google project and register your signing certificate, or get a copy of the official signing certificate.

Google and Amazon have a requirement that they test each release before making it available in the play store or for automatic upgrade of existing users. In order to accommodate this I have a test mythbackend at home with a required user / password on remote connections. I open port 6544 on my router to port 6544 on the test backend. There is a hardcoded test in the application that if you set demo as the ip address, it substitutes my home ip address which is hardcoded in strings.xml. The user and password are also hardcoded there. Instructions to Google and Amazon specify that they must enter demo as the IP address. I start this backend when I submit an update to the play store or Amazon. I shut it down when the app has been added to the play store and Amazon store.

After submitting to the play store and amazon it normally takes 2 to 6 days to be accepted. It is normally done in India so it happens overnight. TV applications take longer than mobile applications. Most times they do not do any testing. They test maybe one time out of each three submissions.
