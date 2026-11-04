// jni_bridge.c — JNI bridge for macOS NativeVideoPlayer
// Calls Swift @_cdecl exports and registers them as JNI native methods.

#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

// ---------------------------------------------------------------------------
// Forward declarations of Swift C exports
// ---------------------------------------------------------------------------

extern void*  createVideoPlayer(void);
extern void   openUri(void* ctx, const char* uri);
extern void   playVideo(void* ctx);
extern void   pauseVideo(void* ctx);
extern void   setVolume(void* ctx, float volume);
extern float  getVolume(void* ctx);
extern void*  lockLatestFrame(void* ctx, int32_t* outInfo);
extern void   unlockLatestFrame(void* ctx);
extern int32_t getFrameWidth(void* ctx);
extern int32_t getFrameHeight(void* ctx);
extern double getDisplayAspectRatio(void* ctx);
extern int32_t setOutputSize(void* ctx, int32_t width, int32_t height);
extern float  getVideoFrameRate(void* ctx);
extern float  getScreenRefreshRate(void* ctx);
extern float  getCaptureFrameRate(void* ctx);
extern double getVideoDuration(void* ctx);
extern double getCurrentTime(void* ctx);
extern void   seekTo(void* ctx, double time);
extern void   disposeVideoPlayer(void* ctx);
extern void   setPlaybackSpeed(void* ctx, float speed);
extern float  getPlaybackSpeed(void* ctx);
extern const char* getVideoTitle(void* ctx);
extern int64_t     getVideoBitrate(void* ctx);
extern const char* getVideoMimeType(void* ctx);
extern int32_t getAudioChannels(void* ctx);
extern int32_t getAudioSampleRate(void* ctx);
extern int32_t consumeDidPlayToEnd(void* ctx);

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------

static inline void* toCtx(jlong h) {
    return (void*)(uintptr_t)(uint64_t)h;
}

// ---------------------------------------------------------------------------
// JNI implementations
// ---------------------------------------------------------------------------

static jlong JNICALL jni_CreatePlayer(JNIEnv* env, jclass cls) {
    void* ctx = createVideoPlayer();
    return ctx ? (jlong)(uintptr_t)ctx : 0L;
}

static void JNICALL jni_OpenUri(JNIEnv* env, jclass cls, jlong handle, jstring uri) {
    if (!handle || !uri) return;
    const char* cUri = (*env)->GetStringUTFChars(env, uri, NULL);
    if (!cUri) return;
    openUri(toCtx(handle), cUri);
    (*env)->ReleaseStringUTFChars(env, uri, cUri);
}

static void JNICALL jni_Play(JNIEnv* env, jclass cls, jlong handle) {
    if (handle) playVideo(toCtx(handle));
}

static void JNICALL jni_Pause(JNIEnv* env, jclass cls, jlong handle) {
    if (handle) pauseVideo(toCtx(handle));
}

static void JNICALL jni_SetVolume(JNIEnv* env, jclass cls, jlong handle, jfloat volume) {
    if (handle) setVolume(toCtx(handle), (float)volume);
}

static jfloat JNICALL jni_GetVolume(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? getVolume(toCtx(handle)) : 0.0f;
}

// Locks the latest CVPixelBuffer and fills outInfo[3] = {width, height, bytesPerRow}.
// Returns the base address of the locked buffer, or 0 on failure.
// Caller MUST call jni_UnlockFrame after reading.
static jlong JNICALL jni_LockFrame(JNIEnv* env, jclass cls, jlong handle, jintArray outInfo) {
    if (!handle || !outInfo) return 0L;
    int32_t info[3] = {0, 0, 0};
    void* addr = lockLatestFrame(toCtx(handle), info);
    if (!addr) return 0L;
    (*env)->SetIntArrayRegion(env, outInfo, 0, 3, (jint*)info);
    return (jlong)(uintptr_t)addr;
}

static void JNICALL jni_UnlockFrame(JNIEnv* env, jclass cls, jlong handle) {
    if (handle) unlockLatestFrame(toCtx(handle));
}

static jobject JNICALL jni_WrapPointer(JNIEnv* env, jclass cls, jlong address, jlong size) {
    if (!address || size <= 0) return NULL;
    return (*env)->NewDirectByteBuffer(env, (void*)(uintptr_t)(uint64_t)address, (jlong)size);
}

static jint JNICALL jni_GetFrameWidth(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? (jint)getFrameWidth(toCtx(handle)) : 0;
}

static jint JNICALL jni_GetFrameHeight(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? (jint)getFrameHeight(toCtx(handle)) : 0;
}

static jdouble JNICALL jni_GetDisplayAspectRatio(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? (jdouble)getDisplayAspectRatio(toCtx(handle)) : 0.0;
}

static jint JNICALL jni_SetOutputSize(JNIEnv* env, jclass cls, jlong handle, jint width, jint height) {
    return handle ? (jint)setOutputSize(toCtx(handle), (int32_t)width, (int32_t)height) : 0;
}

static jfloat JNICALL jni_GetVideoFrameRate(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? getVideoFrameRate(toCtx(handle)) : 0.0f;
}

static jfloat JNICALL jni_GetScreenRefreshRate(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? getScreenRefreshRate(toCtx(handle)) : 0.0f;
}

static jfloat JNICALL jni_GetCaptureFrameRate(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? getCaptureFrameRate(toCtx(handle)) : 0.0f;
}

static jdouble JNICALL jni_GetVideoDuration(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? getVideoDuration(toCtx(handle)) : 0.0;
}

static jdouble JNICALL jni_GetCurrentTime(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? getCurrentTime(toCtx(handle)) : 0.0;
}

static void JNICALL jni_SeekTo(JNIEnv* env, jclass cls, jlong handle, jdouble time) {
    if (handle) seekTo(toCtx(handle), (double)time);
}

static void JNICALL jni_DisposePlayer(JNIEnv* env, jclass cls, jlong handle) {
    if (handle) disposeVideoPlayer(toCtx(handle));
}

static void JNICALL jni_SetPlaybackSpeed(JNIEnv* env, jclass cls, jlong handle, jfloat speed) {
    if (handle) setPlaybackSpeed(toCtx(handle), (float)speed);
}

static jfloat JNICALL jni_GetPlaybackSpeed(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? getPlaybackSpeed(toCtx(handle)) : 1.0f;
}

static jstring JNICALL jni_GetVideoTitle(JNIEnv* env, jclass cls, jlong handle) {
    if (!handle) return NULL;
    const char* s = getVideoTitle(toCtx(handle));
    if (!s) return NULL;
    jstring result = (*env)->NewStringUTF(env, s);
    free((void*)s);
    return result;
}

static jlong JNICALL jni_GetVideoBitrate(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? (jlong)getVideoBitrate(toCtx(handle)) : 0L;
}

static jstring JNICALL jni_GetVideoMimeType(JNIEnv* env, jclass cls, jlong handle) {
    if (!handle) return NULL;
    const char* s = getVideoMimeType(toCtx(handle));
    if (!s) return NULL;
    jstring result = (*env)->NewStringUTF(env, s);
    free((void*)s);
    return result;
}

static jint JNICALL jni_GetAudioChannels(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? (jint)getAudioChannels(toCtx(handle)) : 0;
}

static jint JNICALL jni_GetAudioSampleRate(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? (jint)getAudioSampleRate(toCtx(handle)) : 0;
}

static jboolean JNICALL jni_ConsumeDidPlayToEnd(JNIEnv* env, jclass cls, jlong handle) {
    return handle ? (jboolean)(consumeDidPlayToEnd(toCtx(handle)) != 0) : JNI_FALSE;
}

// ---------------------------------------------------------------------------
// Registration table
// ---------------------------------------------------------------------------

static const JNINativeMethod g_methods[] = {
    { "nCreatePlayer",           "()J",                         (void*)jni_CreatePlayer },
    { "nOpenUri",                "(JLjava/lang/String;)V",      (void*)jni_OpenUri },
    { "nPlay",                   "(J)V",                        (void*)jni_Play },
    { "nPause",                  "(J)V",                        (void*)jni_Pause },
    { "nSetVolume",              "(JF)V",                       (void*)jni_SetVolume },
    { "nGetVolume",              "(J)F",                        (void*)jni_GetVolume },
    { "nLockFrame",              "(J[I)J",                      (void*)jni_LockFrame },
    { "nUnlockFrame",            "(J)V",                        (void*)jni_UnlockFrame },
    { "nWrapPointer",            "(JJ)Ljava/nio/ByteBuffer;",   (void*)jni_WrapPointer },
    { "nGetFrameWidth",          "(J)I",                        (void*)jni_GetFrameWidth },
    { "nGetFrameHeight",         "(J)I",                        (void*)jni_GetFrameHeight },
    { "nGetDisplayAspectRatio",  "(J)D",                        (void*)jni_GetDisplayAspectRatio },
    { "nSetOutputSize",          "(JII)I",                      (void*)jni_SetOutputSize },
    { "nGetVideoFrameRate",      "(J)F",                        (void*)jni_GetVideoFrameRate },
    { "nGetScreenRefreshRate",   "(J)F",                        (void*)jni_GetScreenRefreshRate },
    { "nGetCaptureFrameRate",    "(J)F",                        (void*)jni_GetCaptureFrameRate },
    { "nGetVideoDuration",       "(J)D",                        (void*)jni_GetVideoDuration },
    { "nGetCurrentTime",         "(J)D",                        (void*)jni_GetCurrentTime },
    { "nSeekTo",                 "(JD)V",                       (void*)jni_SeekTo },
    { "nDisposePlayer",          "(J)V",                        (void*)jni_DisposePlayer },
    { "nSetPlaybackSpeed",       "(JF)V",                       (void*)jni_SetPlaybackSpeed },
    { "nGetPlaybackSpeed",       "(J)F",                        (void*)jni_GetPlaybackSpeed },
    { "nGetVideoTitle",          "(J)Ljava/lang/String;",       (void*)jni_GetVideoTitle },
    { "nGetVideoBitrate",        "(J)J",                        (void*)jni_GetVideoBitrate },
    { "nGetVideoMimeType",       "(J)Ljava/lang/String;",       (void*)jni_GetVideoMimeType },
    { "nGetAudioChannels",       "(J)I",                        (void*)jni_GetAudioChannels },
    { "nGetAudioSampleRate",     "(J)I",                        (void*)jni_GetAudioSampleRate },
    { "nConsumeDidPlayToEnd",    "(J)Z",                        (void*)jni_ConsumeDidPlayToEnd },
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = NULL;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK)
        return -1;

    jclass cls = (*env)->FindClass(
        env, "io/github/kdroidfilter/composemediaplayer/mac/MacNativeBridge");
    if (!cls) return -1;

    int count = (int)(sizeof(g_methods) / sizeof(g_methods[0]));
    if ((*env)->RegisterNatives(env, cls, g_methods, count) < 0)
        return -1;

    return JNI_VERSION_1_6;
}
