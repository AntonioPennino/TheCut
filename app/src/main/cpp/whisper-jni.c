#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"
#include "ggml.h"

#define UNUSED(x) (void)(x)
#define TAG "WhisperJNI"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static size_t asset_read(void *ctx, void *output, size_t read_size) {
    return AAsset_read((AAsset *) ctx, output, read_size);
}

static bool asset_is_eof(void *ctx) {
    return AAsset_getRemainingLength64((AAsset *) ctx) <= 0;
}

static void asset_close(void *ctx) {
    AAsset_close((AAsset *) ctx);
}

static struct whisper_context *whisper_init_from_asset(
        JNIEnv *env,
        jobject assetManager,
        const char *asset_path
) {
    LOGI("Loading Whisper model from asset '%s'\n", asset_path);
    AAssetManager *asset_manager = AAssetManager_fromJava(env, assetManager);
    AAsset *asset = AAssetManager_open(asset_manager, asset_path, AASSET_MODE_STREAMING);
    if (!asset) {
        LOGE("Failed to open asset '%s'\n", asset_path);
        return NULL;
    }

    struct whisper_model_loader loader = {
            .context = asset,
            .read = &asset_read,
            .eof = &asset_is_eof,
            .close = &asset_close
    };

    struct whisper_context_params cparams = whisper_context_default_params();
    return whisper_init_with_params(&loader, cparams);
}

JNIEXPORT jlong JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_initContextFromAsset(
        JNIEnv *env, jobject thiz, jobject assetManager, jstring asset_path_str) {
    UNUSED(thiz);
    const char *asset_path_chars = (*env)->GetStringUTFChars(env, asset_path_str, NULL);
    struct whisper_context *context = whisper_init_from_asset(env, assetManager, asset_path_chars);
    (*env)->ReleaseStringUTFChars(env, asset_path_str, asset_path_chars);
    return (jlong) context;
}

JNIEXPORT jlong JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    const char *model_path_chars = (*env)->GetStringUTFChars(env, model_path_str, NULL);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *context = whisper_init_from_file_with_params(model_path_chars, cparams);
    (*env)->ReleaseStringUTFChars(env, model_path_str, model_path_chars);
    return (jlong) context;
}

JNIEXPORT void JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (context) {
        whisper_free(context);
    }
}

JNIEXPORT jint JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads, jstring language, jfloatArray audio_data) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (!context) {
        LOGE("fullTranscribe called with null context");
        return -1;
    }

    jfloat *audio_data_arr = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize audio_data_length = (*env)->GetArrayLength(env, audio_data);

    const char *lang_chars = language ? (*env)->GetStringUTFChars(env, language, NULL) : "auto";

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = lang_chars;
    params.n_threads = num_threads > 0 ? num_threads : 4;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;
    params.token_timestamps = true;

    whisper_reset_timings(context);

    LOGI("Running whisper_full with language='%s', threads=%d, samples=%d", lang_chars, params.n_threads, audio_data_length);
    int res = whisper_full(context, params, audio_data_arr, audio_data_length);
    if (res != 0) {
        LOGE("whisper_full returned error code %d", res);
    } else {
        LOGI("whisper_full completed successfully");
    }

    if (language) {
        (*env)->ReleaseStringUTFChars(env, language, lang_chars);
    }
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio_data_arr, JNI_ABORT);
    return res;
}

JNIEXPORT jint JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (!context) return 0;
    return whisper_full_n_segments(context);
}

JNIEXPORT jstring JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (!context) return (*env)->NewStringUTF(env, "");
    const char *text = whisper_full_get_segment_text(context, index);
    return (*env)->NewStringUTF(env, text ? text : "");
}

JNIEXPORT jlong JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_getTextSegmentT0(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (!context) return 0;
    return (jlong) whisper_full_get_segment_t0(context, index);
}

JNIEXPORT jlong JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_getTextSegmentT1(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (!context) return 0;
    return (jlong) whisper_full_get_segment_t1(context, index);
}

JNIEXPORT jint JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_getTextSegmentTokenCount(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint segment_index) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (!context) return 0;
    return whisper_full_n_tokens(context, segment_index);
}

JNIEXPORT jstring JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_getTextSegmentTokenText(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint segment_index, jint token_index) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (!context) return (*env)->NewStringUTF(env, "");
    const char *text = whisper_full_get_token_text(context, segment_index, token_index);
    return (*env)->NewStringUTF(env, text ? text : "");
}

JNIEXPORT jlong JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_getTextSegmentTokenT0(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint segment_index, jint token_index) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (!context) return 0;
    whisper_token_data data = whisper_full_get_token_data(context, segment_index, token_index);
    return (jlong) data.t0;
}

JNIEXPORT jlong JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_getTextSegmentTokenT1(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint segment_index, jint token_index) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (!context) return 0;
    whisper_token_data data = whisper_full_get_token_data(context, segment_index, token_index);
    return (jlong) data.t1;
}

JNIEXPORT jstring JNICALL
Java_com_antoniopennino_thecut_services_WhisperEngine_getSystemInfo(
        JNIEnv *env, jobject thiz) {
    UNUSED(thiz);
    const char *sysinfo = whisper_print_system_info();
    return (*env)->NewStringUTF(env, sysinfo ? sysinfo : "");
}
