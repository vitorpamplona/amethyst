// C ABI for iOS (Kotlin/Native cinterop), built only with `--features ffi`.
//
// Android's JNI exports in lib.rs stay exactly as they are, and this module is not compiled
// without the feature, so the shipped libarti_android.so does not change by a byte (checked with
// verify-reproducible.sh, see README.md -> "iOS"). That is also why the bodies below repeat the
// JNI ones instead of both calling shared helpers: moving lib.rs code into helpers shifts the
// panic locations baked into the .so, which changes its bytes. Fold the two together the next
// time the Android library is rebuilt anyway (an Arti bump).
//
// The header Kotlin compiles against is include/arti_ffi.h; build-arti-ios.sh checks that every
// function declared there is exported by the archive.
//
// Every export runs inside catch_unwind. build-arti-ios.sh builds with panic = "unwind" (the
// Cargo profile's "abort" stays for Android), so a panic here becomes ARTI_ERR_PANIC for Kotlin
// instead of killing the app, and a panic inside an Arti task only ends that task.

use super::*;

use std::ffi::{c_char, CStr, CString};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::MutexGuard;

/// Returned by any export whose body panicked.
const ARTI_ERR_PANIC: i32 = -100;

/// `void (*)(const char *line)`. Called from Arti's worker threads, never re-entrantly from an
/// export: the callback must not call back into this library.
pub type ArtiLogCallback = extern "C" fn(*const c_char);

static C_LOG_CALLBACK: Mutex<Option<ArtiLogCallback>> = Mutex::new(None);

// A panic in one export must not poison the statics for every later call (that would turn one
// caught panic into a library that fails forever), so the C face takes the lock either way.
fn lock<T>(m: &Mutex<T>) -> MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// Hands one log line to the C callback. lib.rs's `send_log_to_java` calls this too, so the lines
/// the shared SOCKS handler logs reach Kotlin on iOS the same way they reach it on Android.
pub(crate) fn forward_log(message: &str) {
    let callback = *lock(&C_LOG_CALLBACK);
    if let Some(callback) = callback {
        // CString::new only fails on an interior NUL; replace those rather than drop the line.
        if let Ok(line) = CString::new(message.replace('\0', "\u{FFFD}")) {
            callback(line.as_ptr());
        }
    }
}

fn guard<F: FnOnce() -> i32>(name: &str, body: F) -> i32 {
    match catch_unwind(AssertUnwindSafe(body)) {
        Ok(code) => code,
        Err(_) => {
            forward_log(&format!("ERROR: {} panicked", name));
            ARTI_ERR_PANIC
        }
    }
}

/// Static, NUL-terminated version string. Never freed.
#[no_mangle]
pub extern "C" fn arti_version() -> *const c_char {
    concat!("Arti ", env!("CARGO_PKG_VERSION"), " (custom build with rustls, C ABI)\0")
        .as_ptr()
        .cast()
}

/// Registers (or, with NULL, removes) the log callback. Also routes Rust panic messages to it,
/// so a caught panic says where it happened.
#[no_mangle]
pub extern "C" fn arti_set_log_callback(callback: Option<ArtiLogCallback>) -> i32 {
    guard("arti_set_log_callback", || {
        *lock(&C_LOG_CALLBACK) = callback;
        if callback.is_some() {
            std::panic::set_hook(Box::new(|info| forward_log(&format!("ERROR: panic: {}", info))));
            log_info!("Log callback registered");
        }
        0
    })
}

/// Creates the process-lifetime TorClient (OnDemand bootstrap) and starts the directory download
/// in the background. Returns in about the time it takes to open the state files.
///
/// 0 = ok (or already initialized), -1 = bad data_dir, -2 = no Tokio runtime,
/// -3 = config or client creation failed, -100 = panicked.
#[no_mangle]
pub extern "C" fn arti_initialize(data_dir: *const c_char) -> i32 {
    guard("arti_initialize", || {
        if data_dir.is_null() {
            log_error!("arti_initialize: data_dir is NULL");
            return -1;
        }
        // SAFETY: the caller passes a NUL-terminated string that outlives this call.
        let data_dir_str = match unsafe { CStr::from_ptr(data_dir) }.to_str() {
            Ok(s) => s.to_owned(),
            Err(e) => {
                log_error!("Failed to convert data_dir: {:?}", e);
                return -1;
            }
        };
        initialize(data_dir_str)
    })
}

fn initialize(data_dir_str: String) -> i32 {
    if lock(&ARTI_CLIENT).is_some() {
        log_info!("Arti already initialized, reusing existing client");
        return 0;
    }

    log_info!("Initializing Arti with data directory: {}", data_dir_str);

    INIT_ONCE.call_once(|| {
        let _ = rustls::crypto::ring::default_provider().install_default();

        match tokio::runtime::Builder::new_multi_thread().enable_all().build() {
            Ok(rt) => {
                log_info!("Tokio runtime created successfully");
                *lock(&TOKIO_RUNTIME) = Some(rt);
            }
            Err(e) => {
                log_error!("Failed to create Tokio runtime: {:?}", e);
            }
        }
    });

    let runtime_guard = lock(&TOKIO_RUNTIME);
    let runtime = match runtime_guard.as_ref() {
        Some(rt) => rt,
        None => {
            log_error!("Tokio runtime not initialized");
            return -2;
        }
    };

    let data_path = PathBuf::from(data_dir_str);
    let cache_dir = data_path.join("cache");
    let state_dir = data_path.join("state");

    std::fs::create_dir_all(&cache_dir).ok();
    std::fs::create_dir_all(&state_dir).ok();

    let outcome: i32 = runtime.block_on(async {
        log_info!("Creating Arti client...");

        let mut builder = TorClientConfigBuilder::from_directories(state_dir, cache_dir);

        // fs-mistrust rejects a state dir with an "unsafe" owner anywhere up its parent chain.
        // On iOS the data dir is inside the app container, which the OS sandboxes like Android's
        // filesDir, so the check guards against nothing there; the simulator's container sits
        // under the developer's home, whose ownership the check does trip on. Same choice as
        // every non-Android target in lib.rs. Re-examine on a device in B12f.
        builder.storage().permissions().dangerously_trust_everyone();

        let config = match builder.build() {
            Ok(c) => c,
            Err(e) => {
                log_error!("Failed to build Tor config: {:?}", e);
                return -3;
            }
        };

        // OnDemand, unbootstrapped, async: the same reasons as the JNI initialize() in lib.rs.
        let client = match TorClient::builder()
            .config(config)
            .bootstrap_behavior(BootstrapBehavior::OnDemand)
            .create_unbootstrapped_async()
            .await
        {
            Ok(c) => c,
            Err(e) => {
                log_error!("Failed to create Tor client: {:?}", e);
                return -3;
            }
        };

        *lock(&ARTI_CLIENT) = Some(Arc::clone(&client));

        let handle = tokio::spawn(async move {
            let started = std::time::Instant::now();
            match client.bootstrap().await {
                Ok(()) => log_info!(
                    "Arti directory bootstrap complete after {}ms",
                    started.elapsed().as_millis()
                ),
                Err(e) => log_error!(
                    "Arti directory bootstrap failed after {}ms (streams will retry): {:?}",
                    started.elapsed().as_millis(),
                    e
                ),
            }
        });
        if let Some(previous) = lock(&BOOTSTRAP_TASK).replace(handle) {
            previous.abort();
        }

        log_info!("Arti client created (unbootstrapped; directory downloading in background)");
        0
    });

    if outcome == 0 {
        log_info!("Arti initialized successfully");
    }
    outcome
}

/// 1 = ready for traffic, 0 = not yet, -1 = no client. Live, like the JNI isBootstrapped().
#[no_mangle]
pub extern "C" fn arti_is_bootstrapped() -> i32 {
    guard("arti_is_bootstrapped", || match lock(&ARTI_CLIENT).as_ref() {
        Some(client) => client.bootstrap_status().ready_for_traffic() as i32,
        None => -1,
    })
}

/// Directory-download progress 0..1000, or -1 when there is no client.
#[no_mangle]
pub extern "C" fn arti_bootstrap_progress_permille() -> i32 {
    guard("arti_bootstrap_progress_permille", || match lock(&ARTI_CLIENT).as_ref() {
        Some(client) => (client.bootstrap_status().as_frac() * 1000.0).clamp(0.0, 1000.0) as i32,
        None => -1,
    })
}

/// SOCKS connections accepted since the library loaded. Lets a test prove a client really went
/// through the proxy (the count moves) rather than around it.
static SOCKS_ACCEPTED: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);

#[no_mangle]
pub extern "C" fn arti_socks_accepted_count() -> i64 {
    SOCKS_ACCEPTED.load(std::sync::atomic::Ordering::Relaxed) as i64
}

/// Binds the SOCKS5 listener on 127.0.0.1:port (replacing any running one).
/// 0 = ok, -1 = no client, -2 = no runtime, -3 = bind failed, -100 = panicked.
#[no_mangle]
pub extern "C" fn arti_start_socks_proxy(port: i32) -> i32 {
    guard("arti_start_socks_proxy", || start_socks_proxy(port))
}

fn start_socks_proxy(port: i32) -> i32 {
    log_info!("Starting SOCKS proxy on port {}", port);

    let client = match lock(&ARTI_CLIENT).as_ref() {
        Some(c) => Arc::clone(c),
        None => {
            log_error!("Arti client not initialized — call initialize() first");
            return -1;
        }
    };

    let runtime_guard = lock(&TOKIO_RUNTIME);
    let runtime = match runtime_guard.as_ref() {
        Some(rt) => rt,
        None => {
            log_error!("Tokio runtime not initialized");
            return -2;
        }
    };

    // abort() only requests cancellation; the old listener's socket closes when its task is
    // dropped. Wait for that, or re-binding the same port fails with "address in use" (measured:
    // the JNI start/stop, which do not wait, return -3 for a restart on the same port).
    if let Some(handle) = lock(&SOCKS_TASK).take() {
        log_info!("Aborting previous SOCKS server task");
        await_aborted(runtime.handle(), handle);
    }

    let addr = format!("127.0.0.1:{}", port);

    let listener = match runtime.block_on(async { tokio::net::TcpListener::bind(&addr).await }) {
        Ok(l) => {
            log_info!("SOCKS proxy bound to {}", addr);
            l
        }
        Err(e) => {
            log_error!("Failed to bind SOCKS proxy to {}: {:?}", addr, e);
            return -3;
        }
    };

    let handle = runtime.spawn(async move {
        loop {
            match listener.accept().await {
                Ok((stream, _peer_addr)) => {
                    SOCKS_ACCEPTED.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
                    let client_clone = Arc::clone(&client);
                    let h = tokio::spawn(async move {
                        if let Err(e) = handle_socks_connection(stream, client_clone).await {
                            log_error!("SOCKS connection error: {:?}", e);
                        }
                    });
                    let mut handlers = lock(&HANDLER_TASKS);
                    handlers.retain(|h| !h.is_finished());
                    handlers.push(h);
                }
                Err(e) => {
                    log_error!("Failed to accept SOCKS connection: {:?}", e);
                    break;
                }
            }
        }
    });

    *lock(&SOCKS_TASK) = Some(handle);
    log_info!("SOCKS proxy started on port {}", port);
    0
}

/// Aborts a task and waits (up to 1 s) until it has been dropped, so whatever it owned, such as a
/// bound listener, is released when this returns.
fn await_aborted(runtime: &tokio::runtime::Handle, handle: tokio::task::JoinHandle<()>) {
    handle.abort();
    runtime.block_on(async {
        let _ = tokio::time::timeout(tokio::time::Duration::from_secs(1), handle).await;
    });
}

/// Closes the listener. Connections already accepted keep flowing until they end, as on Android;
/// the TorClient stays alive.
#[no_mangle]
pub extern "C" fn arti_stop_socks_proxy() -> i32 {
    guard("arti_stop_socks_proxy", || {
        log_info!("Stopping SOCKS proxy...");

        let previous = lock(&SOCKS_TASK).take();
        let rt_handle = lock(&TOKIO_RUNTIME).as_ref().map(|rt| rt.handle().clone());
        if let (Some(handle), Some(rh)) = (previous, rt_handle) {
            await_aborted(&rh, handle);
        }

        log_info!("SOCKS proxy stopped");
        0
    })
}

/// Aborts the listener, the bootstrap task and every connection handler, then drops the client so
/// its state file lock is released and a later arti_initialize() can take it.
#[no_mangle]
pub extern "C" fn arti_destroy() -> i32 {
    guard("arti_destroy", || {
        log_info!("Destroying Arti client");

        let rt_handle = lock(&TOKIO_RUNTIME).as_ref().map(|rt| rt.handle().clone());

        let socks_handle = lock(&SOCKS_TASK).take();
        if let (Some(h), Some(rh)) = (socks_handle, rt_handle.as_ref()) {
            h.abort();
            rh.block_on(async {
                let _ = tokio::time::timeout(tokio::time::Duration::from_secs(1), h).await;
            });
        }

        if let Some(h) = lock(&BOOTSTRAP_TASK).take() {
            h.abort();
        }

        let handlers = std::mem::take(&mut *lock(&HANDLER_TASKS));
        for h in &handlers {
            h.abort();
        }
        drop(handlers);

        if let Some(rh) = rt_handle {
            rh.block_on(async {
                tokio::time::sleep(tokio::time::Duration::from_millis(500)).await;
            });
        }

        let _ = lock(&ARTI_CLIENT).take();

        log_info!("Arti client destroyed");
        0
    })
}
