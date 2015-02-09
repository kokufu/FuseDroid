FuseDroid
=========

FuseDroid is an Android application that allows you to mount a dir on another dir, with read and write permission to all other apps.
By using this app, you can access a non permitted file, such as /data/data/{package_name}, with a non rooted app.

This is an experimental app.
If you find any bugs, please kindly contact me through PullRequest or email.

[FuseDroid on Google Play](https://play.google.com/store/apps/details?id=com.kokufu.android.apps.fusedroid)

## System Requirements
To use this app, the android device must have the following:

1. su binary (SuperUser or SuperSU) must be installed.
2. Fuse kernel must be installed.

You can check whether the fuse kernel is installed or not by using the following commands: 
<pre>
$ adb shell cat /proc/filesystems
nodev	sysfs
nodev	rootfs
nodev	bdev
nodev	proc
nodev	debugfs
nodev	sockfs
nodev	pipefs
nodev	anon_inodefs
nodev	tmpfs
nodev	inotifyfs
nodev	devpts
        ext2
nodev	ramfs
        vfat
<b>nodev	fuse</b>
        fuseblk
nodev	fusectl
        yaffs
        yaffs2
</pre>

## Projects
This app consists of two projects:
**FuseDroid** is an Android application, and **mout.fusedroid** is an executable binary which allows you to mount.

## Build
To build this app, the followings are required.

1. installed android-ndk
1. installed android-sdk
1. the paths are made correctly.

### Preparing
For the first time only, execute the below:
<pre>
$ cd project/FuseDroid
$ android update project -p ./
</pre>

### Building
Build like below.
(On Windows, use gradlew.bat instead of gradlew.)
<pre>
$ cd project
$ ./gradlew build
</pre>

Then you can find apks in <code>project/FuseDroid/build/outputs/apk</code>

## Credits
This application uses fuse (Filesystem in Userspace).<br />
[http://fuse.sourceforge.net/](http://fuse.sourceforge.net/)

## Acknowledgement
This app was inspired by [fuse-android](https://github.com/seth-hg/fuse-android)

