LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

include $(CLEAR_VARS)

LOCAL_MODULE    := mount.fusedroid
LOCAL_SRC_FILES := fusexmp.c
LOCAL_STATIC_LIBRARIES := fuse

include $(BUILD_EXECUTABLE)
