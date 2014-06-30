LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := transcoder
LOCAL_SRC_FILES := transcoder.c
LOCAL_LDLIBS := -llog -ljnigraphics -lz
LOCAL_SHARED_LIBRARIES := libavformat libavcodec libswscale libavutil libavfilter libswresample libpostproc

include $(BUILD_SHARED_LIBRARY)
$(call import-module,ffmpeg-2.1.4/android/arm)
