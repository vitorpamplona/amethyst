/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * JNI bridge for the C secp256k1 implementation.
 * Maps Kotlin/JVM calls to the C library functions.
 *
 * JNI class: com.vitorpamplona.quartz.utils.Secp256k1C
 */
#include <jni.h>
#include "secp256k1_c.h"
#include <string.h>
#include <stdlib.h>

#define JNI_CLASS "com/vitorpamplona/quartz/utils/Secp256k1C"

/* Helper: extract byte array from JNI with bounds checking */
static int get_bytes(JNIEnv *env, jbyteArray arr, uint8_t *out, int expected_len) {
    if (!arr) return 0;
    jint len = (*env)->GetArrayLength(env, arr);
    if (len != expected_len) return 0;
    (*env)->GetByteArrayRegion(env, arr, 0, len, (jbyte *)out);
    return 1;
}

/* Helper: create Java byte array from native buffer */
static jbyteArray make_bytes(JNIEnv *env, const uint8_t *data, int len) {
    jbyteArray arr = (*env)->NewByteArray(env, len);
    if (!arr) return NULL;
    (*env)->SetByteArrayRegion(env, arr, 0, len, (const jbyte *)data);
    return arr;
}

/* ==================== Library Init ==================== */

JNIEXPORT void JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativeInit(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    secp256k1c_init();
}

/* ==================== Key Operations ==================== */

JNIEXPORT jbyteArray JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativePubkeyCreate(
    JNIEnv *env, jclass cls, jbyteArray seckey
) {
    (void)cls;
    uint8_t sk[32], pub[65];
    if (!get_bytes(env, seckey, sk, 32)) return NULL;
    if (!secp256k1c_pubkey_create(pub, sk)) return NULL;
    return make_bytes(env, pub, 65);
}

JNIEXPORT jbyteArray JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativePubkeyCompress(
    JNIEnv *env, jclass cls, jbyteArray pubkey
) {
    (void)cls;
    uint8_t pub65[65], pub33[33];
    if (!get_bytes(env, pubkey, pub65, 65)) return NULL;
    if (!secp256k1c_pubkey_compress(pub33, pub65)) return NULL;
    return make_bytes(env, pub33, 33);
}

JNIEXPORT jboolean JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativeSecKeyVerify(
    JNIEnv *env, jclass cls, jbyteArray seckey
) {
    (void)cls;
    uint8_t sk[32];
    if (!get_bytes(env, seckey, sk, 32)) return JNI_FALSE;
    return secp256k1c_seckey_verify(sk) ? JNI_TRUE : JNI_FALSE;
}

/* ==================== Schnorr Sign ==================== */

JNIEXPORT jbyteArray JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativeSchnorrSign(
    JNIEnv *env, jclass cls, jbyteArray msg, jbyteArray seckey, jbyteArray auxrand
) {
    (void)cls;
    uint8_t sk[32], aux[32], sig[64];
    if (!get_bytes(env, seckey, sk, 32)) return NULL;

    jint msg_len = (*env)->GetArrayLength(env, msg);
    uint8_t *msg_buf = (uint8_t *)(*env)->GetByteArrayElements(env, msg, NULL);
    if (!msg_buf) return NULL;

    uint8_t *aux_ptr = NULL;
    if (auxrand) {
        if (get_bytes(env, auxrand, aux, 32)) {
            aux_ptr = aux;
        }
    }

    int ok = secp256k1c_schnorr_sign(sig, msg_buf, (size_t)msg_len, sk, aux_ptr);
    (*env)->ReleaseByteArrayElements(env, msg, (jbyte *)msg_buf, JNI_ABORT);

    return ok ? make_bytes(env, sig, 64) : NULL;
}

JNIEXPORT jbyteArray JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativeSchnorrSignXOnly(
    JNIEnv *env, jclass cls, jbyteArray msg, jbyteArray seckey,
    jbyteArray xonlyPub, jbyteArray auxrand
) {
    (void)cls;
    uint8_t sk[32], xonly[32], aux[32], sig[64];
    if (!get_bytes(env, seckey, sk, 32)) return NULL;
    if (!get_bytes(env, xonlyPub, xonly, 32)) return NULL;

    jint msg_len = (*env)->GetArrayLength(env, msg);
    uint8_t *msg_buf = (uint8_t *)(*env)->GetByteArrayElements(env, msg, NULL);
    if (!msg_buf) return NULL;

    uint8_t *aux_ptr = NULL;
    if (auxrand) {
        if (get_bytes(env, auxrand, aux, 32)) {
            aux_ptr = aux;
        }
    }

    int ok = secp256k1c_schnorr_sign_xonly(sig, msg_buf, (size_t)msg_len, sk, xonly, aux_ptr);
    (*env)->ReleaseByteArrayElements(env, msg, (jbyte *)msg_buf, JNI_ABORT);

    return ok ? make_bytes(env, sig, 64) : NULL;
}

/* ==================== Schnorr Verify ==================== */

JNIEXPORT jboolean JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativeSchnorrVerify(
    JNIEnv *env, jclass cls, jbyteArray sig, jbyteArray msg, jbyteArray pub
) {
    (void)cls;
    uint8_t s[64], p[32];
    if (!get_bytes(env, sig, s, 64)) return JNI_FALSE;
    if (!get_bytes(env, pub, p, 32)) return JNI_FALSE;

    jint msg_len = (*env)->GetArrayLength(env, msg);
    uint8_t *msg_buf = (uint8_t *)(*env)->GetByteArrayElements(env, msg, NULL);
    if (!msg_buf) return JNI_FALSE;

    int ok = secp256k1c_schnorr_verify(s, msg_buf, (size_t)msg_len, p);
    (*env)->ReleaseByteArrayElements(env, msg, (jbyte *)msg_buf, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativeSchnorrVerifyFast(
    JNIEnv *env, jclass cls, jbyteArray sig, jbyteArray msg, jbyteArray pub
) {
    (void)cls;
    uint8_t s[64], p[32];
    if (!get_bytes(env, sig, s, 64)) return JNI_FALSE;
    if (!get_bytes(env, pub, p, 32)) return JNI_FALSE;

    jint msg_len = (*env)->GetArrayLength(env, msg);
    uint8_t *msg_buf = (uint8_t *)(*env)->GetByteArrayElements(env, msg, NULL);
    if (!msg_buf) return JNI_FALSE;

    int ok = secp256k1c_schnorr_verify_fast(s, msg_buf, (size_t)msg_len, p);
    (*env)->ReleaseByteArrayElements(env, msg, (jbyte *)msg_buf, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativeSchnorrVerifyBatch(
    JNIEnv *env, jclass cls, jbyteArray pub,
    jobjectArray sigsArray, jobjectArray msgsArray
) {
    (void)cls;
    uint8_t p[32];
    if (!get_bytes(env, pub, p, 32)) return JNI_FALSE;

    jint count = (*env)->GetArrayLength(env, sigsArray);
    if (count != (*env)->GetArrayLength(env, msgsArray)) return JNI_FALSE;
    if (count == 0) return JNI_TRUE;

    /* Allocate arrays for the batch */
    const uint8_t **sigs = (const uint8_t **)malloc((size_t)count * sizeof(uint8_t *));
    const uint8_t **msgs = (const uint8_t **)malloc((size_t)count * sizeof(uint8_t *));
    size_t *lens = (size_t *)malloc((size_t)count * sizeof(size_t));
    uint8_t **sig_bufs = (uint8_t **)malloc((size_t)count * sizeof(uint8_t *));
    jbyte **msg_ptrs = (jbyte **)malloc((size_t)count * sizeof(jbyte *));
    jbyteArray *msg_arrs = (jbyteArray *)malloc((size_t)count * sizeof(jbyteArray));

    if (!sigs || !msgs || !lens || !sig_bufs || !msg_ptrs || !msg_arrs) {
        free(sigs); free(msgs); free(lens); free(sig_bufs); free(msg_ptrs); free(msg_arrs);
        return JNI_FALSE;
    }

    for (jint i = 0; i < count; i++) {
        /* Extract signature bytes */
        jbyteArray sig_arr = (jbyteArray)(*env)->GetObjectArrayElement(env, sigsArray, i);
        sig_bufs[i] = (uint8_t *)malloc(64);
        get_bytes(env, sig_arr, sig_bufs[i], 64);
        sigs[i] = sig_bufs[i];
        (*env)->DeleteLocalRef(env, sig_arr);

        /* Extract message bytes */
        msg_arrs[i] = (jbyteArray)(*env)->GetObjectArrayElement(env, msgsArray, i);
        lens[i] = (size_t)(*env)->GetArrayLength(env, msg_arrs[i]);
        msg_ptrs[i] = (*env)->GetByteArrayElements(env, msg_arrs[i], NULL);
        msgs[i] = (const uint8_t *)msg_ptrs[i];
    }

    int ok = secp256k1c_schnorr_verify_batch(p, sigs, msgs, lens, (size_t)count);

    /* Cleanup */
    for (jint i = 0; i < count; i++) {
        (*env)->ReleaseByteArrayElements(env, msg_arrs[i], msg_ptrs[i], JNI_ABORT);
        (*env)->DeleteLocalRef(env, msg_arrs[i]);
        free(sig_bufs[i]);
    }
    free(sigs); free(msgs); free(lens); free(sig_bufs); free(msg_ptrs); free(msg_arrs);

    return ok ? JNI_TRUE : JNI_FALSE;
}

/* ==================== Tweak Operations ==================== */

JNIEXPORT jbyteArray JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativePrivKeyTweakAdd(
    JNIEnv *env, jclass cls, jbyteArray seckey, jbyteArray tweak
) {
    (void)cls;
    uint8_t sk[32], tw[32], result[32];
    if (!get_bytes(env, seckey, sk, 32)) return NULL;
    if (!get_bytes(env, tweak, tw, 32)) return NULL;
    if (!secp256k1c_privkey_tweak_add(result, sk, tw)) return NULL;
    return make_bytes(env, result, 32);
}

JNIEXPORT jbyteArray JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativePubKeyTweakMul(
    JNIEnv *env, jclass cls, jbyteArray pubkey, jbyteArray tweak
) {
    (void)cls;
    uint8_t tw[32];
    if (!get_bytes(env, tweak, tw, 32)) return NULL;

    jint pub_len = (*env)->GetArrayLength(env, pubkey);
    uint8_t *pub_buf = (uint8_t *)(*env)->GetByteArrayElements(env, pubkey, NULL);
    if (!pub_buf) return NULL;

    /* Output same size as input */
    int out_len = (pub_len == 33) ? 33 : 65;
    uint8_t *result = (uint8_t *)malloc((size_t)out_len);
    if (!result) {
        (*env)->ReleaseByteArrayElements(env, pubkey, (jbyte *)pub_buf, JNI_ABORT);
        return NULL;
    }

    int ok = secp256k1c_pubkey_tweak_mul(result, (size_t)out_len,
                                          pub_buf, (size_t)pub_len, tw);
    (*env)->ReleaseByteArrayElements(env, pubkey, (jbyte *)pub_buf, JNI_ABORT);

    jbyteArray ret = NULL;
    if (ok) ret = make_bytes(env, result, out_len);
    free(result);
    return ret;
}

JNIEXPORT jbyteArray JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativeEcdhXOnly(
    JNIEnv *env, jclass cls, jbyteArray xonlyPub, jbyteArray scalar
) {
    (void)cls;
    uint8_t pub[32], sc[32], result[32];
    if (!get_bytes(env, xonlyPub, pub, 32)) return NULL;
    if (!get_bytes(env, scalar, sc, 32)) return NULL;
    if (!secp256k1c_ecdh_xonly(result, pub, sc)) return NULL;
    return make_bytes(env, result, 32);
}

/* ==================== SHA-256 (for benchmarking hardware vs software) ==================== */

JNIEXPORT jbyteArray JNICALL
Java_com_vitorpamplona_quartz_utils_Secp256k1C_nativeSha256(
    JNIEnv *env, jclass cls, jbyteArray data
) {
    (void)cls;
    jint len = (*env)->GetArrayLength(env, data);
    uint8_t *buf = (uint8_t *)(*env)->GetByteArrayElements(env, data, NULL);
    if (!buf) return NULL;

    uint8_t out[32];
    secp256k1_sha256_hash(out, buf, (size_t)len);
    (*env)->ReleaseByteArrayElements(env, data, (jbyte *)buf, JNI_ABORT);
    return make_bytes(env, out, 32);
}
