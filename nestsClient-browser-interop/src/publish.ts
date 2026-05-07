// Phase 4 (T16) browser-publisher harness.
//
// Inverse of `listen.ts`: drives a sine `OscillatorNode` through the
// WebCodecs `AudioEncoder` (Opus mode) and pushes each encoded packet
// onto a moq-lite track via `Container.Legacy.Producer`, prefixed with
// a Varint-encoded timestamp the watcher (`Container.Legacy.Format`)
// will strip on decode. Also publishes a `catalog.json` track that
// matches `MoqLiteHangCatalog.opus48k` byte-for-byte so the Amethyst
// listener (and `hang-listen` for cross-validation) can discover the
// audio rendition.
//
// Status: Phase 4.A scaffold only — wire the Connection.connect +
// catalog publish + first-frame send. Phase 4.C extends this for I4
// reverse / I14 / I15 scenarios. Until then, the I1-forward smoke test
// (Amethyst speaker → Chromium listener) is the path that lights this
// harness up.

import * as Moq from "@moq/lite";
import * as Container from "@moq/hang/container";

const params = new URLSearchParams(location.search);
const relayParam = params.get("relay");
const broadcastParam = params.get("broadcast");
const trackParam = params.get("track") ?? "audio/data";
const catalogTrack = params.get("catalogTrack") ?? "catalog.json";
const freqHz = Number(params.get("freqHz") ?? "440");
const channels = Number(params.get("channels") ?? "1");
const durationSec = Number(params.get("duration") ?? "5");
const wsPort = Number(params.get("wsPort") ?? "0");

function required(v: string | null, name: string): string {
    if (!v) throw new Error(`publish.html: missing ?${name}=`);
    return v;
}
const relayUrlString = required(relayParam, "relay");
const broadcastName = required(broadcastParam, "broadcast");

const status = (msg: string) => {
    const el = document.getElementById("status");
    if (el) el.textContent = msg;
    console.log("[publish]", msg);
};

async function main() {
    // Optional WS back-channel for the test driver to read out
    // status — currently only used by Phase 4.C scenarios that want
    // to assert the publisher reached `playing` before the listener
    // attaches.
    let ws: WebSocket | undefined;
    if (wsPort) {
        ws = new WebSocket(`ws://127.0.0.1:${wsPort}/pcm`);
        await new Promise<void>((resolve) => {
            ws!.addEventListener("open", () => resolve(), { once: true });
            ws!.addEventListener("error", () => resolve(), { once: true });
        });
    }
    const sendDone = () => {
        if (ws?.readyState === WebSocket.OPEN) ws.send("done");
    };

    const relayUrl = new URL(relayUrlString);
    status(`connecting to ${relayUrl.toString()}`);
    const conn = await Moq.Connection.connect(relayUrl, {
        websocket: { enabled: false },
    });
    (window as any).__moqVersion = conn.version;
    status(`connected, alpn=${conn.version}`);

    // Build a publishable Broadcast — the relay opens SUBSCRIBE bidis
    // back to us per track and we serve them via `broadcast.subscribe`
    // (despite the name, on the publish side `subscribe` is what the
    // relay calls to *request* the track).
    const broadcast = new Moq.Broadcast();
    conn.publish(Moq.Path.from(broadcastName), broadcast);
    status(`announced ${broadcastName}`);

    // Catalog: match `MoqLiteHangCatalog.opus48k(audioTrackName, channels)`
    // byte-for-byte. Field order matters less than the content because
    // hang.js uses zod parsing, but we keep the shape canonical.
    const catalogJson = JSON.stringify({
        audio: {
            renditions: {
                [trackParam]: {
                    codec: "opus",
                    container: { kind: "legacy" },
                    sampleRate: 48000,
                    numberOfChannels: channels,
                    jitter: 20,
                },
            },
        },
    });
    const catalogBytes = new TextEncoder().encode(catalogJson);

    // -- Audio encoder pump --------------------------------------------
    // Build oscillator → MediaStreamAudioDestinationNode → MediaStreamTrack
    // pipeline; then loop pulling AudioData out of an MSTrack reader
    // via `MediaStreamTrackProcessor` and feed each frame into the
    // WebCodecs AudioEncoder. Encoded outputs land in `Producer.encode`.
    const ctx = new AudioContext({ sampleRate: 48_000, latencyHint: "interactive" });
    await ctx.resume();
    const osc = ctx.createOscillator();
    osc.frequency.value = freqHz;
    osc.type = "sine";
    const dst = ctx.createMediaStreamDestination();
    osc.connect(dst);
    osc.start();

    const audioTrack = dst.stream.getAudioTracks()[0];
    // @ts-expect-error MediaStreamTrackProcessor is Chrome-only
    const processor = new MediaStreamTrackProcessor({ track: audioTrack });
    const reader = (processor.readable as ReadableStream<AudioData>).getReader();

    // Serve catalog + audio tracks as they're requested by the relay.
    const handleRequests = async () => {
        for (;;) {
            const req = await broadcast.requested();
            if (!req) return;
            if (req.track.name === catalogTrack) {
                // One-shot emit-on-subscribe, like Amethyst speaker's
                // `catalogPublisher.setOnNewSubscriber`.
                const group = req.track.appendGroup();
                group.writeFrame(catalogBytes);
                group.close();
            } else if (req.track.name === trackParam) {
                // The audio track is fed by the encoder pump below;
                // nothing to do here other than accept the request
                // (the Producer below writes into `req.track`).
                (window as any).__audioTrack = req.track;
            }
        }
    };
    handleRequests().catch((e) => console.error("[publish] requests:", e));

    // Wait until the relay subscribes to the audio track, then start the
    // encoder pump. The test driver is responsible for spawning the
    // listener AFTER the publisher reports `data-state="publishing"`.
    const audioMoqTrack: Moq.Track = await new Promise((resolve) => {
        const probe = setInterval(() => {
            const t = (window as any).__audioTrack as Moq.Track | undefined;
            if (t) {
                clearInterval(probe);
                resolve(t);
            }
        }, 20);
    });
    const producer = new Container.Legacy.Producer(audioMoqTrack);

    const encoder = new AudioEncoder({
        output: (chunk, _meta) => {
            const data = new Uint8Array(chunk.byteLength);
            chunk.copyTo(data);
            // Force a new group at the start so the first frame is a
            // keyframe — the moq-lite Container.Legacy.Producer requires
            // it for the first packet.
            const isKey = (window as any).__producerStarted !== true;
            (window as any).__producerStarted = true;
            producer.encode(data, chunk.timestamp as any, isKey);
        },
        error: (e) => console.error("[publish] AudioEncoder", e),
    });
    encoder.configure({
        codec: "opus",
        sampleRate: 48_000,
        numberOfChannels: channels,
        bitrate: 32_000,
    });

    document.body.dataset.state = "publishing";
    status("publishing");

    const deadline = performance.now() + durationSec * 1000;
    let framesIn = 0;
    while (performance.now() < deadline) {
        const { done, value } = await reader.read();
        if (done || !value) break;
        try {
            encoder.encode(value);
            framesIn++;
        } finally {
            value.close();
        }
    }
    status(`flushing, framesIn=${framesIn}`);

    try {
        await encoder.flush();
    } catch (e) {
        console.warn("[publish] flush:", e);
    }
    encoder.close();
    osc.stop();
    audioTrack.stop();
    producer.close();
    broadcast.close();
    conn.close();
    sendDone();

    document.body.dataset.state = "done";
    status(`done. framesIn=${framesIn}`);
}

main().catch((e) => {
    console.error("[publish] fatal:", e);
    document.body.dataset.state = "error";
    status(`ERROR: ${e?.stack ?? e}`);
});
