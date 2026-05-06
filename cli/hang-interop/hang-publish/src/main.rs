//! hang-publish — reference moq-lite / hang audio publisher.
//!
//! Opens a broadcast at `<relay>/<broadcast>`, publishes a hang
//! catalog with one Opus / Container::Legacy audio rendition, and
//! pumps Opus-encoded sine-wave frames in groups of 5 for
//! `--duration` seconds. Used by the cross-stack interop tests for
//! the Rust → Amethyst direction. See
//! `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`.

use std::time::Duration;

use anyhow::{Context, anyhow};
use bytes::Bytes;
use clap::Parser;
use hang::catalog::{Audio, AudioCodec, AudioConfig, Catalog, Container};

const SAMPLE_RATE_HZ: u32 = 48_000;
/// 20 ms at 48 kHz — same frame size Amethyst speakers send.
const FRAME_SIZE_SAMPLES: usize = 960;
/// Microseconds per frame: 20_000 = 1_000_000 * 960 / 48_000.
const FRAME_DURATION_US: u64 = 20_000;
/// 5 frames per group → 100 ms group cadence, matching nests speaker.
const FRAMES_PER_GROUP: usize = 5;
/// Default audio rendition track name in the catalog. Amethyst's
/// listener subscribes to `audio/data` per `MoqLiteNestsListener.AUDIO_TRACK`,
/// so that's what we ship by default. Override via `--track-name`.
const DEFAULT_TRACK_NAME: &str = "audio/data";

#[derive(Parser, Debug)]
#[command(
    name = "hang-publish",
    about = "Reference moq-lite / hang audio publisher for cross-stack interop"
)]
struct Args {
    /// HTTPS URL of the relay, e.g. `https://127.0.0.1:34721`.
    #[arg(long)]
    relay_url: String,

    /// Optional JWT for the `?jwt=` query string.
    #[arg(long)]
    jwt: Option<String>,

    /// Broadcast namespace (path under the relay root).
    #[arg(long)]
    broadcast: String,

    /// Sine-wave frequency in Hz (mono only). For stereo, see
    /// `--freq-hz-right` once that lands.
    #[arg(long, default_value_t = 440)]
    freq_hz: u32,

    /// Maximum runtime in seconds.
    #[arg(long, default_value_t = 5)]
    duration: u64,

    /// Channel count: 1 (mono) or 2 (stereo). Stereo uses the same
    /// frequency on both channels for now; per-channel frequency is
    /// a Phase-2 follow-up.
    #[arg(long, default_value_t = 1)]
    channels: u32,

    /// Audio rendition track name. Default `audio/data` matches
    /// Amethyst's `MoqLiteNestsListener.AUDIO_TRACK`. Override for
    /// custom interop scenarios.
    #[arg(long, default_value_t = DEFAULT_TRACK_NAME.to_string())]
    track_name: String,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // rustls 0.23 requires an explicit crypto-provider install.
    let _ = rustls::crypto::aws_lc_rs::default_provider().install_default();

    let _ = tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .with_writer(std::io::stderr)
        .try_init();

    let args = Args::parse();

    let result = tokio::time::timeout(
        Duration::from_secs(args.duration + 5),
        run(args),
    )
    .await
    .context("hang-publish wallclock timeout")?;

    result
}

async fn run(args: Args) -> anyhow::Result<()> {
    let url = build_url(&args.relay_url, args.jwt.as_deref())?;

    let cfg = moq_native::ClientConfig::parse_from([
        "hang-publish",
        "--client-bind",
        "127.0.0.1:0",
        "--client-version",
        "moq-lite-03",
        "--tls-disable-verify=true",
    ]);
    let client = cfg.init().context("init moq client")?;

    // Set up the publish side: a producer this binary writes to,
    // and a consumer the moq-native client forwards to the relay.
    let origin = moq_lite::Origin::produce();
    let publish_consumer = origin.consume();

    // Start the reconnect loop in the background. It owns the
    // session lifecycle.
    let session_url = url.clone();
    let session = tokio::spawn(async move {
        let reconnect = client.with_publish(publish_consumer).reconnect(session_url);
        if let Err(err) = reconnect.closed().await {
            tracing::warn!(%err, "reconnect loop exited");
        }
    });

    // Result of the publish loop is what determines test pass/fail.
    let publish_result = publish(&origin, &args).await;

    // Once we drop origin all published broadcasts unannounce; the
    // reconnect task exits when the session closes.
    drop(session);

    publish_result
}

async fn publish(origin: &moq_lite::OriginProducer, args: &Args) -> anyhow::Result<()> {
    let mut broadcast = origin
        .create_broadcast(args.broadcast.as_str())
        .ok_or_else(|| anyhow!("broadcast '{}' not allowed by origin", args.broadcast))?;

    // 1. Catalog track. We declare one Opus rendition; the JSON
    //    payload mirrors what Amethyst's MoqLiteHangCatalog produces.
    let mut catalog_track = broadcast
        .create_track(hang::Catalog::default_track())
        .context("create catalog track")?;

    let mut renditions = std::collections::BTreeMap::new();
    renditions.insert(
        args.track_name.clone(),
        AudioConfig {
            codec: AudioCodec::Opus,
            sample_rate: SAMPLE_RATE_HZ,
            channel_count: args.channels,
            bitrate: Some(32_000),
            description: None,
            container: Container::Legacy,
            jitter: None,
        },
    );
    let catalog = Catalog {
        audio: Audio { renditions },
        ..Default::default()
    };
    let catalog_json =
        serde_json::to_vec(&catalog).context("serialize catalog json")?;

    let mut catalog_group = catalog_track
        .create_group(moq_lite::Group { sequence: 0 })
        .context("create catalog group")?;
    catalog_group
        .write_frame(catalog_json)
        .context("publish catalog frame")?;
    catalog_group.finish().ok();
    // We don't finish() the catalog_track itself yet — moq-lite
    // treats track-end as broadcast-end, and we want the audio
    // track to keep streaming.

    // 2. Audio track.
    let mut audio_track = broadcast
        .create_track(moq_lite::Track {
            name: args.track_name.clone(),
            priority: 1,
        })
        .context("create audio track")?;

    let channels = match args.channels {
        1 => opus::Channels::Mono,
        2 => opus::Channels::Stereo,
        n => anyhow::bail!("unsupported channel count: {n}"),
    };
    let mut encoder = opus::Encoder::new(SAMPLE_RATE_HZ, channels, opus::Application::Audio)
        .context("init opus encoder")?;
    encoder
        .set_bitrate(opus::Bitrate::Bits(32_000))
        .context("set opus bitrate")?;

    let total_frames = (args.duration * 1_000_000 / FRAME_DURATION_US) as usize;
    let phase_step =
        2.0_f64 * std::f64::consts::PI * (args.freq_hz as f64) / (SAMPLE_RATE_HZ as f64);
    let mut sample_idx: u64 = 0;
    // Sized to libopus's worst-case output for one 20 ms frame.
    let mut opus_buf = vec![0u8; 4_000];

    let frame_period = Duration::from_micros(FRAME_DURATION_US);
    let mut next_send = tokio::time::Instant::now();
    let mut group_idx: u64 = 0;
    let mut frames_in_group = 0usize;
    let mut group: Option<moq_lite::GroupProducer> = None;

    for frame_no in 0..total_frames {
        // Generate one PCM frame at the configured frequency.
        let mut pcm = vec![0i16; FRAME_SIZE_SAMPLES * args.channels as usize];
        for i in 0..FRAME_SIZE_SAMPLES {
            let t = sample_idx + i as u64;
            let v = ((t as f64) * phase_step).sin();
            let s = (v * 16_383.0) as i16;
            for ch in 0..(args.channels as usize) {
                pcm[i * args.channels as usize + ch] = s;
            }
        }
        sample_idx += FRAME_SIZE_SAMPLES as u64;

        let n = encoder
            .encode(&pcm, &mut opus_buf)
            .context("encode opus packet")?;
        let opus_packet = Bytes::copy_from_slice(&opus_buf[..n]);

        // Wrap the Opus packet in a hang Legacy frame: VarInt
        // timestamp prefix + raw codec payload.
        let frame = hang::container::Frame {
            timestamp: hang::container::Timestamp::from_micros(
                (frame_no as u64) * FRAME_DURATION_US,
            )
            .context("frame timestamp out of range")?,
            payload: opus_packet.into(),
        };

        // Start a new group every FRAMES_PER_GROUP frames. The 5-frame
        // group cadence matches Amethyst's NestMoqLiteBroadcaster default
        // and produces ~100 ms groups.
        if frames_in_group == 0 {
            if let Some(mut g) = group.take() {
                g.finish().ok();
            }
            group = Some(
                audio_track
                    .create_group(moq_lite::Group { sequence: group_idx })
                    .context("create audio group")?,
            );
            group_idx += 1;
        }

        let g = group.as_mut().expect("group always Some after init");
        frame.encode(g).context("encode hang frame into group")?;
        frames_in_group += 1;
        if frames_in_group == FRAMES_PER_GROUP {
            frames_in_group = 0;
        }

        next_send += frame_period;
        tokio::time::sleep_until(next_send).await;
    }

    if let Some(mut g) = group.take() {
        g.finish().ok();
    }
    audio_track.finish().ok();
    catalog_track.finish().ok();

    tracing::info!(
        frames = total_frames,
        groups = group_idx,
        "hang-publish done"
    );
    Ok(())
}

/// Build the WebTransport URL the publisher connects to.
///
/// `relay_url` is taken as the *full* URL the publisher connects to
/// (scheme + authority + optional path). `broadcast` is the relative
/// announce-suffix passed to `Origin::create_broadcast`, NOT appended
/// to the URL. Callers that want the publisher's URL path to also be
/// `broadcast` should pass `--relay-url=<host>/<broadcast>` and
/// `--broadcast=<broadcast>` (the simple Rust↔Rust shape).
fn build_url(relay_url: &str, jwt: Option<&str>) -> anyhow::Result<url::Url> {
    let trimmed = relay_url.trim_end_matches('/');
    let raw = if let Some(jwt) = jwt {
        format!("{trimmed}?jwt={jwt}")
    } else {
        trimmed.to_string()
    };
    url::Url::parse(&raw).with_context(|| format!("malformed relay url: {raw}"))
}
