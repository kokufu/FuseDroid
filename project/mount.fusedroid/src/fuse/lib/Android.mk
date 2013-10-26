include $(CLEAR_VARS)

LOCAL_MODULE            := fuse
FILE_LIST               := buffer.c cuse_lowlevel.c fuse.c fuse_kern_chan.c fuse_loop.c fuse_loop_mt.c fuse_lowlevel.c fuse_mt.c fuse_opt.c fuse_session.c fuse_signals.c helper.c mount.c mount_util.c ulockmgr.c
LOCAL_SRC_FILES         := $(FILE_LIST:%=$(LOCAL_PATH)/fuse/lib/%)
#FILE_LIST               := $(wildcard $(LOCAL_PATH)/fuse/*.c)
#LOCAL_SRC_FILES         := $(FILE_LIST:$(LOCAL_PATH)/%=%)
LOCAL_C_INCLUDES        := src/fuse/include
LOCAL_EXPORT_C_INCLUDES := src/fuse/include
LOCAL_CFLAGS            := -DFUSERMOUNT_DIR=\"/usr/bin\" -D_FILE_OFFSET_BITS=64 
LOCAL_EXPORT_CFLAGS     := -D_FILE_OFFSET_BITS=64

include $(BUILD_STATIC_LIBRARY)
