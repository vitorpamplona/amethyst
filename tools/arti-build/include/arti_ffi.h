/*
 * C ABI of the Arti wrapper (tools/arti-build/src/ffi.rs), built for iOS by build-arti-ios.sh with
 * `--features ffi`. Kotlin/Native reads this header through iosApp/src/nativeInterop/cinterop/arti.def.
 *
 * Written by hand, not generated: it is nine functions, and build-arti-ios.sh fails when the
 * archive does not export every one declared here. Keep the two files in step.
 *
 * Return codes: 0 = ok, negative = error (the meaning per call is in ffi.rs), -100 = the call
 * panicked and the panic was caught.
 */
#ifndef AMETHYST_ARTI_FFI_H
#define AMETHYST_ARTI_FFI_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* One log line, NUL-terminated, valid only during the call. Runs on Arti's worker threads and
 * must not call back into this library. */
typedef void (*arti_log_callback)(const char *line);

/* Static version string. Never free it. */
const char *arti_version(void);

/* Registers the log callback (NULL removes it). Also routes Rust panic messages to it. */
int32_t arti_set_log_callback(arti_log_callback callback);

/* Creates the TorClient with OnDemand bootstrap and starts the directory download in the
 * background. 0 also when it already exists. */
int32_t arti_initialize(const char *data_dir);

/* 1 = ready for traffic, 0 = not yet, -1 = no client. */
int32_t arti_is_bootstrapped(void);

/* Directory-download progress 0..1000, or -1 when there is no client. */
int32_t arti_bootstrap_progress_permille(void);

/* Binds the SOCKS5 listener on 127.0.0.1:port, replacing a running one. */
int32_t arti_start_socks_proxy(int32_t port);

/* Closes the listener; accepted connections run to their end, the client stays. */
int32_t arti_stop_socks_proxy(void);

/* Drops the client and every task holding it, releasing the state file lock. */
int32_t arti_destroy(void);

/* SOCKS connections accepted since the library loaded (for tests). */
int64_t arti_socks_accepted_count(void);

#ifdef __cplusplus
}
#endif

#endif /* AMETHYST_ARTI_FFI_H */
