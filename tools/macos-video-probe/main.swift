// Drives kdroidFilter's NativeVideoPlayer.swift (compiled alongside) the way MacVideoPlayerState does,
// and probes AVFoundation directly, for each URI given on the command line.
import AVFoundation
import Foundation

setvbuf(stdout, nil, _IONBF, 0)

func log(_ s: String) { print(s) }

func avProbe(_ uri: String) {
    let url = URL(string: uri)!.scheme != nil ? URL(string: uri)! : URL(fileURLWithPath: uri)
    let asset = AVURLAsset(url: url)
    let sem = DispatchSemaphore(value: 0)
    let start = Date()
    Task {
        do {
            let (playable, duration) = try await asset.load(.isPlayable, .duration)
            let tracks = try await asset.load(.tracks)
            log("  [av] playable=\(playable) duration=\(duration.seconds) tracks=\(tracks.map { $0.mediaType.rawValue }) in \(String(format: "%.2f", Date().timeIntervalSince(start)))s")
            for t in tracks {
                let (fmts, playableT) = try await t.load(.formatDescriptions, .isPlayable)
                for f in fmts {
                    let sub = CMFormatDescriptionGetMediaSubType(f)
                    let fourcc = String(format: "%c%c%c%c", (sub >> 24) & 0xff, (sub >> 16) & 0xff, (sub >> 8) & 0xff, sub & 0xff)
                    log("  [av]   \(t.mediaType.rawValue) \(fourcc) playable=\(playableT)")
                }
            }
        } catch {
            log("  [av] load failed: \(error) userInfo=\((error as NSError).userInfo)")
        }
        let item = AVPlayerItem(asset: asset)
        let player = AVPlayer(playerItem: item)
        player.isMuted = true
        player.play()
        var waited = 0.0
        while item.status == .unknown && waited < 15 { try? await Task.sleep(nanoseconds: 100_000_000); waited += 0.1 }
        log("  [av] item.status=\(item.status.rawValue) after \(String(format: "%.1f", waited))s error=\(String(describing: item.error)) underlying=\(String(describing: (item.error as NSError?)?.userInfo))")
        try? await Task.sleep(nanoseconds: 3_000_000_000)
        log("  [av] after 3s playing: currentTime=\(String(format: "%.2f", player.currentTime().seconds)) rate=\(player.rate) timeControl=\(player.timeControlStatus.rawValue) itemError=\(String(describing: item.error))")
        if let ev = item.errorLog()?.events { for e in ev { log("  [av] errorLog: \(e.errorStatusCode) \(e.errorDomain) \(e.errorComment ?? "")") } }
        player.pause()
        sem.signal()
    }
    sem.wait()
}

func enginePlay(_ uri: String) {
    guard let p = createVideoPlayer() else { log("  [engine] create failed"); return }
    let start = Date()
    uri.withCString { openUri(p, $0) }
    // MacVideoPlayerState polls 20 x 250 ms for the size, then fails with "Failed to retrieve video size".
    var dimsAt: Double? = nil
    for _ in 0..<120 {
        if getFrameWidth(p) > 0 && getFrameHeight(p) > 0 { dimsAt = Date().timeIntervalSince(start); break }
        Thread.sleep(forTimeInterval: 0.25)
    }
    if let d = dimsAt {
        log("  [engine] size \(getFrameWidth(p))x\(getFrameHeight(p)) after \(String(format: "%.2f", d))s\(d > 5 ? "  <-- past kdroidFilter's 5 s limit: 'Failed to retrieve video size'" : "")")
    } else {
        log("  [engine] NO SIZE after 30 s: kdroidFilter reports 'Failed to retrieve video size'")
        disposeVideoPlayer(p)
        return
    }
    for _ in 0..<40 { if getVideoDuration(p) > 0 { break }; Thread.sleep(forTimeInterval: 0.25) }
    log("  [engine] duration=\(getVideoDuration(p)) fps=\(getVideoFrameRate(p))")
    playVideo(p)
    var frames = 0
    var last: UnsafeMutableRawPointer? = nil
    var info = [Int32](repeating: 0, count: 8)
    for _ in 0..<200 {
        let f = info.withUnsafeMutableBufferPointer { lockLatestFrame(p, $0.baseAddress) }
        if let f = f { if f != last || frames == 0 { frames += 1 }; last = f; unlockLatestFrame(p) }
        Thread.sleep(forTimeInterval: 0.02)
    }
    log("  [engine] after 4s play: currentTime=\(String(format: "%.2f", getCurrentTime(p))) distinctFrames=\(frames) lastFrame=\(info[0])x\(info[1])")
    pauseVideo(p)
    Thread.sleep(forTimeInterval: 0.3)
    disposeVideoPlayer(p)
}


// How a feed card opens a video: paused (kdroidFilter's InitialPlayerState.PAUSE never calls play),
// shown on its first frame, moved to where it was (restorePosition), then played.
func enginePausedCard(_ uri: String) {
    guard let p = createVideoPlayer() else { log("  [card] create failed"); return }
    setVolume(p, 0)
    uri.withCString { openUri(p, $0) }
    var sized = false
    for _ in 0..<20 { if getFrameWidth(p) > 0 { sized = true; break }; Thread.sleep(forTimeInterval: 0.25) }
    if !sized { log("  [card] no size in 5 s"); disposeVideoPlayer(p); return }
    // kdroidFilter scales output to the card's surface.
    _ = setOutputSize(p, 360, 640)
    func countFrames(_ seconds: Double) -> (Int, Int32, Int32) {
        var frames = 0
        var last: UnsafeMutableRawPointer? = nil
        var info = [Int32](repeating: 0, count: 8)
        let end = Date().addingTimeInterval(seconds)
        while Date() < end {
            let f = info.withUnsafeMutableBufferPointer { lockLatestFrame(p, $0.baseAddress) }
            if let f = f { if f != last || frames == 0 { frames += 1 }; last = f; unlockLatestFrame(p) }
            Thread.sleep(forTimeInterval: 0.02)
        }
        return (frames, info[0], info[1])
    }
    let paused = countFrames(3)
    log("  [card] paused 3s: frames=\(paused.0) size=\(paused.1)x\(paused.2) time=\(String(format: "%.2f", getCurrentTime(p)))")
    seekTo(p, 0)
    let afterSeek = countFrames(1)
    log("  [card] paused after seek(0): frames=\(afterSeek.0) time=\(String(format: "%.2f", getCurrentTime(p)))")
    playVideo(p)
    let playing = countFrames(3)
    log("  [card] playing 3s: frames=\(playing.0) size=\(playing.1)x\(playing.2) time=\(String(format: "%.2f", getCurrentTime(p)))")
    pauseVideo(p)
    Thread.sleep(forTimeInterval: 0.3)
    disposeVideoPlayer(p)
}

DispatchQueue.global().async {
    for uri in CommandLine.arguments.dropFirst() {
        log("=== \(uri)")
        enginePlay(uri)
        enginePausedCard(uri)
        avProbe(uri)
    }
    exit(0)
}
// The engine's frame timer is scheduled on the main run loop, which dispatchMain() does not run.
RunLoop.main.run()
