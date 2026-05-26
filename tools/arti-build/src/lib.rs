use jni::JNIEnv;
use jni::objects::{JClass, JString, JObject, GlobalRef};
use jni::sys::{jint, jstring};
use jni::JavaVM;

use arti_client::TorClient;
use arti_client::config::TorClientConfigBuilder;
use tor_rtcompat::PreferredRuntime;

use std::sync::{Arc, Mutex, Once};
use std::path::PathBuf;
use anyhow::Result;

// ============================================================================
// Global State
// ============================================================================

static ARTI_CLIENT: Mutex<Option<Arc<TorClient<PreferredRuntime>>>> = Mutex::new(None);
static TOKIO_RUNTIME: Mutex<Option<tokio::runtime::Runtime>> = Mutex::new(None);
static JAVA_VM: Mutex<Option<JavaVM>> = Mutex::new(None);
static LOG_CALLBACK: Mutex<Option<GlobalRef>> = Mutex::new(None);
static SOCKS_TASK: Mutex<Option<tokio::task::JoinHandle<()>>> = Mutex::new(None);
// Per-connection handler tasks. Tracked so destroy() can abort in-flight handlers
// — otherwise their Arc<TorClient> clones keep the client alive and the
// state file lock would not be released for the next initialize().
static HANDLER_TASKS: Mutex<Vec<tokio::task::JoinHandle<()>>> = Mutex::new(Vec::new());
static INIT_ONCE: Once = Once::new();

// ============================================================================
// Logging
// ============================================================================

fn send_log_to_java(message: String) {
    let vm_opt = JAVA_VM.lock().unwrap();
    let callback_opt = LOG_CALLBACK.lock().unwrap();

    if let (Some(vm), Some(callback)) = (vm_opt.as_ref(), callback_opt.as_ref()) {
        if let Ok(mut env) = vm.attach_current_thread() {
            if let Ok(jmessage) = env.new_string(&message) {
                let _ = env.call_method(
                    callback.as_obj(),
                    "onLogLine",
                    "(Ljava/lang/String;)V",
                    &[(&jmessage).into()]
                );
            }
        }
    }
}

macro_rules! log_info {
    ($($arg:tt)*) => {{
        let msg = format!($($arg)*);
        send_log_to_java(msg);
    }};
}

macro_rules! log_error {
    ($($arg:tt)*) => {{
        let msg = format!("ERROR: {}", format!($($arg)*));
        send_log_to_java(msg);
    }};
}

// ============================================================================
// JNI Functions — package: com.vitorpamplona.amethyst.ui.tor
// ============================================================================

#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_getVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    if JAVA_VM.lock().unwrap().is_none() {
        if let Ok(vm) = env.get_java_vm() {
            *JAVA_VM.lock().unwrap() = Some(vm);
        }
    }

    let version = format!("Arti {} (custom build with rustls)", env!("CARGO_PKG_VERSION"));
    let output = env.new_string(version).expect("Couldn't create java string!");
    output.into_raw()
}

#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_setLogCallback(
    env: JNIEnv,
    _class: JClass,
    callback: JObject,
) {
    if JAVA_VM.lock().unwrap().is_none() {
        if let Ok(vm) = env.get_java_vm() {
            *JAVA_VM.lock().unwrap() = Some(vm);
        }
    }

    if let Ok(global_ref) = env.new_global_ref(callback) {
        *LOG_CALLBACK.lock().unwrap() = Some(global_ref);
        log_info!("Log callback registered");
    }
}

/// Initialize Arti runtime and bootstrap the TorClient.
/// The TorClient is created once and reused for the app's lifetime.
#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_initialize(
    mut env: JNIEnv,
    _class: JClass,
    data_dir: JString,
) -> jint {
    if JAVA_VM.lock().unwrap().is_none() {
        if let Ok(vm) = env.get_java_vm() {
            *JAVA_VM.lock().unwrap() = Some(vm);
        }
    }

    // Already initialized — skip
    if ARTI_CLIENT.lock().unwrap().is_some() {
        log_info!("Arti already initialized, reusing existing client");
        return 0;
    }

    let data_dir_str: String = match env.get_string(&data_dir) {
        Ok(s) => s.into(),
        Err(e) => {
            log_error!("Failed to convert data_dir: {:?}", e);
            return -1;
        }
    };

    log_info!("Initializing Arti with data directory: {}", data_dir_str);

    INIT_ONCE.call_once(|| {
        // arti-v2.3.0's tor-rtcompat no longer installs a rustls CryptoProvider
        // implicitly — without this, TorClient::create_bootstrapped panics on
        // first TLS handshake. install_default() returns Err if a provider is
        // already installed, which is fine; we just want at-least-one.
        let _ = rustls::crypto::ring::default_provider().install_default();

        match tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .build()
        {
            Ok(rt) => {
                log_info!("Tokio runtime created successfully");
                *TOKIO_RUNTIME.lock().unwrap() = Some(rt);
            }
            Err(e) => {
                log_error!("Failed to create Tokio runtime: {:?}", e);
            }
        }
    });

    let runtime_guard = TOKIO_RUNTIME.lock().unwrap();
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

    let result: Result<()> = runtime.block_on(async {
        log_info!("Creating Arti client...");

        let config = TorClientConfigBuilder::from_directories(state_dir, cache_dir)
            .build()?;

        let client = TorClient::create_bootstrapped(config).await?;

        log_info!("Arti client created and bootstrapped");

        *ARTI_CLIENT.lock().unwrap() = Some(Arc::new(client));

        Ok(())
    });

    match result {
        Ok(_) => {
            log_info!("Arti initialized successfully");
            0
        }
        Err(e) => {
            log_error!("Failed to initialize Arti: {:?}", e);
            -3
        }
    }
}

/// Start the SOCKS5 proxy on the specified port.
/// Can be called multiple times — stops any existing listener first.
#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_startSocksProxy(
    _env: JNIEnv,
    _class: JClass,
    port: jint,
) -> jint {
    log_info!("Starting SOCKS proxy on port {}", port);

    // Stop any existing SOCKS server first
    if let Some(handle) = SOCKS_TASK.lock().unwrap().take() {
        log_info!("Aborting previous SOCKS server task");
        handle.abort();
    }

    let client_guard = ARTI_CLIENT.lock().unwrap();
    let client = match client_guard.as_ref() {
        Some(c) => Arc::clone(c),
        None => {
            log_error!("Arti client not initialized — call initialize() first");
            return -1;
        }
    };
    drop(client_guard);

    let runtime_guard = TOKIO_RUNTIME.lock().unwrap();
    let runtime = match runtime_guard.as_ref() {
        Some(rt) => rt,
        None => {
            log_error!("Tokio runtime not initialized");
            return -2;
        }
    };

    let addr = format!("127.0.0.1:{}", port);

    let bind_result = runtime.block_on(async {
        tokio::net::TcpListener::bind(&addr).await
    });

    let listener = match bind_result {
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
        log_info!("Sufficiently bootstrapped; system SOCKS now functional");

        loop {
            match listener.accept().await {
                Ok((stream, _peer_addr)) => {
                    let client_clone = Arc::clone(&client);
                    let h = tokio::spawn(async move {
                        if let Err(e) = handle_socks_connection(stream, client_clone).await {
                            log_error!("SOCKS connection error: {:?}", e);
                        }
                    });
                    let mut handlers = HANDLER_TASKS.lock().unwrap();
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

    *SOCKS_TASK.lock().unwrap() = Some(handle);
    log_info!("SOCKS proxy started on port {}", port);
    0
}

/// Handle a single SOCKS5 connection through Tor.
async fn handle_socks_connection(
    mut stream: tokio::net::TcpStream,
    client: Arc<TorClient<PreferredRuntime>>,
) -> Result<()> {
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    let mut buf = [0u8; 512];

    // SOCKS5 handshake: read version + methods
    let n = stream.read(&mut buf).await?;
    if n < 2 {
        return Err(anyhow::anyhow!("Invalid SOCKS handshake"));
    }

    // No auth required
    stream.write_all(&[0x05, 0x00]).await?;

    // Read request
    let n = stream.read(&mut buf).await?;
    if n < 10 {
        return Err(anyhow::anyhow!("Invalid SOCKS request"));
    }

    let version = buf[0];
    let cmd = buf[1];
    let atyp = buf[3];

    if version != 0x05 {
        return Err(anyhow::anyhow!("Unsupported SOCKS version: {}", version));
    }

    if cmd != 0x01 {
        stream.write_all(&[0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;
        return Err(anyhow::anyhow!("Unsupported SOCKS command: {}", cmd));
    }

    let (target_host, target_port) = match atyp {
        0x01 => {
            let ip = format!("{}.{}.{}.{}", buf[4], buf[5], buf[6], buf[7]);
            let port = u16::from_be_bytes([buf[8], buf[9]]);
            (ip, port)
        }
        0x03 => {
            let len = buf[4] as usize;
            if n < 5 + len + 2 {
                return Err(anyhow::anyhow!("Invalid domain name length"));
            }
            let domain = String::from_utf8_lossy(&buf[5..5 + len]).to_string();
            let port = u16::from_be_bytes([buf[5 + len], buf[5 + len + 1]]);
            (domain, port)
        }
        0x04 => {
            if n < 22 {
                stream.write_all(&[0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;
                return Err(anyhow::anyhow!("Truncated IPv6 request"));
            }
            let ip = format!(
                "{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}",
                buf[4], buf[5], buf[6], buf[7], buf[8], buf[9], buf[10], buf[11],
                buf[12], buf[13], buf[14], buf[15], buf[16], buf[17], buf[18], buf[19]
            );
            let port = u16::from_be_bytes([buf[20], buf[21]]);
            (ip, port)
        }
        _ => {
            stream.write_all(&[0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;
            return Err(anyhow::anyhow!("Unsupported address type: {}", atyp));
        }
    };

    let tor_stream = match client.connect((target_host.as_str(), target_port)).await {
        Ok(s) => s,
        Err(e) => {
            log_error!("Failed to connect through Tor to {}:{}: {:?}", target_host, target_port, e);
            stream.write_all(&[0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;
            return Err(e.into());
        }
    };

    // SOCKS5 success
    stream.write_all(&[0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;

    // Bidirectional forwarding
    let (mut client_read, mut client_write) = stream.split();
    let (mut tor_read, mut tor_write) = tor_stream.split();

    tokio::select! {
        r = tokio::io::copy(&mut client_read, &mut tor_write) => {
            if let Err(ref e) = r { log_error!("Client->Tor error: {:?}", e); }
        }
        r = tokio::io::copy(&mut tor_read, &mut client_write) => {
            if let Err(ref e) = r { log_error!("Tor->Client error: {:?}", e); }
        }
    };

    Ok(())
}

/// Stop the SOCKS proxy listener. The TorClient stays alive.
#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_stopSocksProxy(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    log_info!("Stopping SOCKS proxy...");

    if let Some(handle) = SOCKS_TASK.lock().unwrap().take() {
        handle.abort();
    }

    let rt_handle = TOKIO_RUNTIME
        .lock()
        .unwrap()
        .as_ref()
        .map(|rt| rt.handle().clone());
    if let Some(rh) = rt_handle {
        rh.block_on(async {
            tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
        });
    }

    // NOTE: TorClient is NOT destroyed — it persists for reuse.

    log_info!("SOCKS proxy stopped");
    0
}

/// Destroy the TorClient — used by self-heal paths in Kotlin when Tor is
/// stuck and the in-memory state (guards, circuits) needs to be rebuilt
/// from scratch. Aborts the SOCKS listener and all in-flight per-connection
/// handlers, then drops the static Arc so Arti's state file lock can be
/// released. The next call to [initialize] will create a fresh TorClient
/// (and re-bootstrap).
#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_destroy(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    log_info!("Destroying Arti client");

    // Clone the runtime handle and release the TOKIO_RUNTIME mutex immediately —
    // we will hold it for ~500ms below, and other JNI calls that need the runtime
    // (e.g. a Kotlin start() racing with us) would otherwise block on this mutex.
    let rt_handle = TOKIO_RUNTIME
        .lock()
        .unwrap()
        .as_ref()
        .map(|rt| rt.handle().clone());

    // Abort the listener and wait for it to actually terminate before draining
    // HANDLER_TASKS. The accept loop has no .await between `accept` and
    // `HANDLER_TASKS.push(h)`, so abort() alone is racy: a handler can be spawned
    // and pushed AFTER our drain. Awaiting the JoinHandle (with timeout) closes
    // that window — no new handlers can be pushed once the listener task is gone.
    let socks_handle = SOCKS_TASK.lock().unwrap().take();
    if let (Some(h), Some(rh)) = (socks_handle, rt_handle.as_ref()) {
        h.abort();
        rh.block_on(async {
            let _ = tokio::time::timeout(tokio::time::Duration::from_secs(1), h).await;
        });
    }

    // Abort all in-flight handlers — each holds an Arc<TorClient> clone, and
    // the client cannot drop (state file lock cannot release) while any clone
    // is alive.
    let handlers = std::mem::take(&mut *HANDLER_TASKS.lock().unwrap());
    for h in &handlers {
        h.abort();
    }
    drop(handlers);

    // Give tokio a moment to actually cancel and drop the task frames so the
    // handler Arcs are released before we drop our static one.
    if let Some(rh) = rt_handle {
        rh.block_on(async {
            tokio::time::sleep(tokio::time::Duration::from_millis(500)).await;
        });
    }

    // Drop the static Arc. If any handler is still holding a clone, the
    // TorClient stays alive until that handler finishes — in which case the
    // next initialize() will fail and Kotlin's clearAllArtiData retry path
    // will handle it.
    let _ = ARTI_CLIENT.lock().unwrap().take();

    log_info!("Arti client destroyed");
    0
}