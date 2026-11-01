// Amethyst's build of ComposeMediaPlayer 0.11.4's macOS native player (MIT, see LICENSE), from
// mediaplayer/src/jvmMain/native/macos/NativeVideoPlayer.swift at tag v0.11.4.
//
// One change: the audio processing tap is gone (setupAudioTap, its five callbacks and the call in
// openUri). It passed the sound through unchanged, left over from an audio-level display removed
// upstream in 7b8cc87, and macOS's audio thread crashed in its process callback
// (AudioQueueProcessingTapGetSourceAudio, a null read) within minutes of scrolling a video feed.
// The rest is the upstream file as is. Drop this directory once upstream ships without the tap.

import AVFoundation
import CoreGraphics
import CoreVideo
import Foundation
import AppKit

/// Class that manages video playback and frame capture into an optimized shared buffer.
/// Frame capture rate adapts to the lower of screen refresh rate and video frame rate.
/// Includes full HLS (HTTP Live Streaming) support with adaptive bitrate streaming.
class MacVideoPlayer {
    private var player: AVPlayer?
    private var videoOutput: AVPlayerItemVideoOutput?

    // Timer for capturing frames at adaptive rate
    private var displayLink: Timer?

    // Track the video's native frame rate
    private var videoFrameRate: Float = 0.0

    // Track the screen's refresh rate
    private var screenRefreshRate: Float = 60.0

    // The actual capture frame rate (minimum of video and screen rates)
    private var captureFrameRate: Float = 0.0

    // Latest decoded CVPixelBuffer retained directly — no intermediate copy.
    // The JNI side locks it for reading, copies to the Skia bitmap, then unlocks.
    private var latestPixelBuffer: CVPixelBuffer? = nil
    private var lockedPixelBuffer: CVPixelBuffer? = nil
    private let bufferLock = NSLock()

    // Frame dimensions (scaled output — may be smaller than native to save RAM)
    private var frameWidth: Int = 0
    private var frameHeight: Int = 0

    // Native video resolution (unscaled, as reported by the asset)
    private var nativeVideoWidth: Int = 0
    private var nativeVideoHeight: Int = 0

    // Audio volume control (0.0 to 1.0)
    private var volume: Float = 1.0

    // Flag to track if playback is active
    private var isPlaying: Bool = false
    private var isReadyForPlayback = false
    private var pendingPlay = false

    // Playback speed control (1.0 is normal speed)
    private var playbackSpeed: Float = 1.0

    // Metadata properties
    private var videoTitle: String? = nil
    private var videoBitrate: Int64 = 0
    private var videoMimeType: String? = nil
    private var audioChannels: Int = 0
    private var audioSampleRate: Int = 0

    // HLS-specific properties
    private var isHLSStream: Bool = false
    private var availableBitrates: [Float] = []
    private var currentBitrate: Float = 0
    private var preferredPeakBitRate: Double = 0
    private var bufferStatus: Float = 0.0
    private var isBuffering: Bool = false
    private var networkStatus: String = "Unknown"

    // Observers for HLS monitoring
    private var playerItemObserver: NSKeyValueObservation?
    private var playerObserver: NSKeyValueObservation?
    private var timeControlStatusObserver: NSKeyValueObservation?
    private var bufferEmptyObserver: NSKeyValueObservation?
    private var bufferLikelyToKeepUpObserver: NSKeyValueObservation?
    private var bufferFullObserver: NSKeyValueObservation?
    private var presentationSizeObserver: NSKeyValueObservation?

    // Display aspect ratio (width / height) derived from AVPlayerItem.presentationSize.
    // Cached here and updated from the KVO callback so getDisplayAspectRatio() can be called
    // from the frame-decoding thread without touching the live AVPlayerItem off the main thread.
    private let aspectLock = NSLock()
    private var cachedDisplayAspectRatio: Double = 0.0

    // End-of-playback flag (set by AVPlayerItemDidPlayToEndTime, consumed once by the Kotlin side)
    private var didPlayToEnd: Bool = false
    private var playbackEndObserver: NSObjectProtocol?

    // HLS Error tracking
    private var lastError: String? = nil
    private var errorCount: Int = 0

    init() {
        // Detect screen refresh rate
        detectScreenRefreshRate()

        // Configure AVAudioSession for better HLS audio handling
        configureAudioSession()
    }

    /// Configures the audio session for optimal HLS playback
    private func configureAudioSession() {
        // Note: AVAudioSession is iOS/tvOS only. For macOS, we'll use different audio configuration
        // macOS handles audio differently through Core Audio
    }

    /// Detects the current screen refresh rate
    private func detectScreenRefreshRate() {
        if let mainScreen = NSScreen.main {
            // Use CoreVideo DisplayLink to get refresh rate on macOS
            var displayID: CGDirectDisplayID = CGMainDisplayID()
            if let screenNumber = mainScreen.deviceDescription[
                NSDeviceDescriptionKey("NSScreenNumber")] as? NSNumber
            {
                displayID = CGDirectDisplayID(screenNumber.uint32Value)
            }

            var displayLink: CVDisplayLink?
            let error = CVDisplayLinkCreateWithCGDisplay(displayID, &displayLink)

            if error == kCVReturnSuccess, let link = displayLink {
                let period = CVDisplayLinkGetNominalOutputVideoRefreshPeriod(link)
                let timeValue = period.timeValue
                let timeScale = period.timeScale

                if timeValue > 0 && timeScale > 0 {
                    // Convert to Hz (frames per second)
                    let refreshRate = Double(timeScale) / Double(timeValue)
                    screenRefreshRate = Float(refreshRate)
                }
            } else {
                // Fallback if we can't get the refresh rate
                screenRefreshRate = 60.0
            }
        } else {
            screenRefreshRate = 60.0
        }
    }

    /// Checks if the URL is an HLS stream
    private func isHLSUrl(_ url: URL) -> Bool {
        let urlString = url.absoluteString.lowercased()
        return urlString.contains(".m3u8") ||
            urlString.contains("/playlist.m3u8") ||
            urlString.contains("/master.m3u8") ||
            urlString.contains("format=m3u8")
    }

    /// Configures the asset for HLS streaming
    private func configureHLSAsset(_ asset: AVURLAsset) -> AVURLAsset {
        // Configure asset for optimal HLS streaming
        let options: [String: Any] = [
            AVURLAssetPreferPreciseDurationAndTimingKey: true
        ]

        // Create new asset with HLS-optimized options
        return AVURLAsset(url: asset.url, options: options)
    }

    /// Sets up HLS-specific monitoring
    private func setupHLSMonitoring(for item: AVPlayerItem) {
        // Monitor playback buffer
        bufferEmptyObserver = item.observe(\.isPlaybackBufferEmpty, options: [.new]) { [weak self] item, _ in
            self?.handleBufferEmpty(item.isPlaybackBufferEmpty)
        }

        bufferLikelyToKeepUpObserver = item.observe(\.isPlaybackLikelyToKeepUp, options: [.new]) { [weak self] item, _ in
            self?.handleBufferLikelyToKeepUp(item.isPlaybackLikelyToKeepUp)
        }

        bufferFullObserver = item.observe(\.isPlaybackBufferFull, options: [.new]) { [weak self] item, _ in
            self?.handleBufferFull(item.isPlaybackBufferFull)
        }

        // Monitor loaded time ranges for buffer status
        playerItemObserver = item.observe(\.loadedTimeRanges, options: [.new]) { [weak self] item, _ in
            self?.updateBufferStatus(from: item)
        }

        // Monitor access log for bitrate changes
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAccessLog(_:)),
            name: .AVPlayerItemNewAccessLogEntry,
            object: item
        )

        // Monitor error log
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleErrorLog(_:)),
            name: .AVPlayerItemNewErrorLogEntry,
            object: item
        )

        // Monitor playback stalls
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handlePlaybackStall(_:)),
            name: .AVPlayerItemPlaybackStalled,
            object: item
        )
    }

    /// Handles buffer empty state
    private func handleBufferEmpty(_ isEmpty: Bool) {
        if isEmpty {
            isBuffering = true
            print("HLS: Buffer empty, buffering...")
        }
    }

    /// Handles buffer likely to keep up state
    private func handleBufferLikelyToKeepUp(_ isLikely: Bool) {
        if isLikely {
            isBuffering = false
            print("HLS: Buffer recovered, playback can continue")
        }
    }

    /// Handles buffer full state
    private func handleBufferFull(_ isFull: Bool) {
        if isFull {
            print("HLS: Buffer is full")
        }
    }

    /// Handles time control status changes
    private func handleTimeControlStatus(_ status: AVPlayer.TimeControlStatus) {
        switch status {
        case .paused:
            networkStatus = "Paused"
        case .waitingToPlayAtSpecifiedRate:
            networkStatus = "Buffering"
            isBuffering = true
        case .playing:
            networkStatus = "Playing"
            isBuffering = false
        @unknown default:
            networkStatus = "Unknown"
        }
    }

    /// Updates buffer status from loaded time ranges
    private func updateBufferStatus(from item: AVPlayerItem) {
        guard let timeRange = item.loadedTimeRanges.first?.timeRangeValue else {
            bufferStatus = 0.0
            return
        }

        let startSeconds = CMTimeGetSeconds(timeRange.start)
        let durationSeconds = CMTimeGetSeconds(timeRange.duration)
        let currentSeconds = CMTimeGetSeconds(item.currentTime())

        if currentSeconds > 0 {
            let bufferedSeconds = startSeconds + durationSeconds - currentSeconds
            // Normalize to 0-1 range (assuming 10 seconds is "full" buffer)
            bufferStatus = Float(min(bufferedSeconds / 10.0, 1.0))
        }
    }

    /// Handles access log entries for HLS monitoring
    @objc private func handleAccessLog(_ notification: Notification) {
        guard let item = notification.object as? AVPlayerItem,
              let accessLog = item.accessLog(),
              let lastEvent = accessLog.events.last else { return }

        // Update current bitrate
        if lastEvent.indicatedBitrate > 0 {
            currentBitrate = Float(lastEvent.indicatedBitrate)
        }

        // Log HLS streaming statistics
        print("""
                  HLS Access Log:
                  - Indicated Bitrate: \(lastEvent.indicatedBitrate) bps
                  - Observed Bitrate: \(lastEvent.observedBitrate) bps
                  - Stall Count: \(lastEvent.numberOfStalls)
                  - Downloaded Bytes: \(lastEvent.numberOfBytesTransferred)
                  - Segments Downloaded: \(lastEvent.numberOfMediaRequests)
              """)
    }

    /// Handles error log entries
    @objc private func handleErrorLog(_ notification: Notification) {
        guard let item = notification.object as? AVPlayerItem,
              let errorLog = item.errorLog(),
              let lastEvent = errorLog.events.last else { return }

        errorCount += 1
        lastError = lastEvent.errorComment ?? "Unknown HLS error"

        print("""
                  HLS Error Log:
                  - Error Domain: \(lastEvent.errorDomain)
                  - Error Code: \(lastEvent.errorStatusCode)
                  - Error Comment: \(lastEvent.errorComment ?? "None")
                  - Server Address: \(lastEvent.serverAddress ?? "Unknown")
              """)
    }

    /// Handles playback stalls
    @objc private func handlePlaybackStall(_ notification: Notification) {
        print("HLS: Playback stalled, attempting to recover...")
        isBuffering = true

        // Attempt to recover from stall
        if let player = player {
            player.play()
        }
    }

    /// Extracts available bitrates from HLS variants
    private func extractHLSVariants(from asset: AVAsset) {
        if #available(macOS 13.0, *) {
            Task {
                do {
                    // For HLS streams, try to get variant information
                    if let urlAsset = asset as? AVURLAsset {
                        let variants = try await urlAsset.load(.variants)

                        availableBitrates = []
                        for variant in variants {
                            if let peakBitRate = variant.peakBitRate {
                                availableBitrates.append(Float(peakBitRate))
                            }
                        }

                        if !availableBitrates.isEmpty {
                            availableBitrates.sort()
                            print("HLS: Available bitrates: \(availableBitrates)")
                        }
                    }
                } catch {
                    print("Error loading HLS variants: \(error.localizedDescription)")
                }
            }
        }
    }

    /// Sets the preferred maximum bitrate for HLS streams
    func setPreferredMaxBitrate(_ bitrate: Double) {
        preferredPeakBitRate = bitrate
        player?.currentItem?.preferredPeakBitRate = bitrate
        print("HLS: Set preferred max bitrate to \(bitrate) bps")
    }

    /// Forces a specific bitrate (if available)
    func forceQuality(bitrate: Float) {
        guard isHLSStream else { return }

        // Find the closest available bitrate
        let closest = availableBitrates.min(by: { abs($0 - bitrate) < abs($1 - bitrate) })

        if let targetBitrate = closest {
            setPreferredMaxBitrate(Double(targetBitrate))
        }
    }

    /// Detects the MIME type of a file by reading its magic bytes (file signature)
    private func detectMimeType(at url: URL) -> String? {
        guard url.isFileURL else { return nil }

        do {
            let fileHandle = try FileHandle(forReadingFrom: url)
            defer { try? fileHandle.close() }

            // Read the first 12 bytes to identify the file format
            guard let data = try fileHandle.read(upToCount: 12), data.count >= 4 else {
                return nil
            }

            let bytes = [UInt8](data)

            // MP4/MOV files start with size and 'ftyp' box
            if data.count >= 8 {
                let fourcc = String(bytes: bytes[4..<8], encoding: .ascii) ?? ""
                if fourcc == "ftyp" {
                    // Check the brand to differentiate between MP4 and MOV
                    if data.count >= 12 {
                        let brand = String(bytes: bytes[8..<12], encoding: .ascii) ?? ""
                        if brand.contains("qt") {
                            return "video/quicktime"
                        }
                    }
                    return "video/mp4"
                }
            }

            // WebM/Matroska files start with 0x1A 0x45 0xDF 0xA3
            if bytes.count >= 4 && bytes[0] == 0x1A && bytes[1] == 0x45 && bytes[2] == 0xDF && bytes[3] == 0xA3 {
                return "video/webm"
            }

            // FLV files start with 'FLV'
            if bytes.count >= 3 && bytes[0] == 0x46 && bytes[1] == 0x4C && bytes[2] == 0x56 {
                return "video/x-flv"
            }

            // AVI files start with 'RIFF' ... 'AVI '
            if bytes.count >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46 &&
               bytes[8] == 0x41 && bytes[9] == 0x56 && bytes[10] == 0x49 && bytes[11] == 0x20 {
                return "video/x-msvideo"
            }

            // MPEG-TS files start with 0x47 (sync byte)
            if bytes[0] == 0x47 {
                return "video/mp2t"
            }

            return nil
        } catch {
            print("Error detecting MIME type: \(error.localizedDescription)")
            return nil
        }
    }


    /// Extracts metadata from the asset
    private func extractMetadata(from asset: AVAsset) {
        // Reset metadata values
        videoTitle = nil
        videoBitrate = 0
        videoMimeType = nil
        audioChannels = 0
        audioSampleRate = 0

        // Extract title from metadata
        if #available(macOS 13.0, *) {
            Task {
                do {
                    let commonMetadata = try await asset.load(.commonMetadata)
                    if let titleItem = AVMetadataItem.metadataItems(from: commonMetadata, filteredByIdentifier: .commonIdentifierTitle).first {
                        let titleValue = try await titleItem.load(.value)
                        if let title = titleValue as? String {
                            videoTitle = title
                        }
                    }
                } catch {
                    print("Error loading metadata: \(error.localizedDescription)")
                }
            }
        } else {
            // Fallback for older OS versions
            let commonMetadata = asset.commonMetadata
            if let titleItem = AVMetadataItem.metadataItems(from: commonMetadata, filteredByIdentifier: .commonIdentifierTitle).first,
               let title = titleItem.value as? String {
                videoTitle = title
            }
        }

        // For HLS streams, extract variant information
        if isHLSStream {
            extractHLSVariants(from: asset)
            videoMimeType = "application/x-mpegURL"
        }

        // Try to get bitrate from the asset directly
        if let urlAsset = asset as? AVURLAsset, !isHLSStream {
            // Try to get file size for non-HLS content
            do {
                let fileAttributes = try FileManager.default.attributesOfItem(atPath: urlAsset.url.path)
                if let fileSize = fileAttributes[.size] as? NSNumber {
                    let fileSizeInBytes = fileSize.int64Value

                    // Get duration in seconds
                    if #available(macOS 13.0, *) {
                        Task {
                            do {
                                let duration = try await asset.load(.duration)
                                let durationInSeconds = CMTimeGetSeconds(duration)

                                if durationInSeconds > 0 {
                                    // Calculate bitrate: (fileSize * 8) / durationInSeconds
                                    let calculatedBitrate = Int64(Double(fileSizeInBytes * 8) / durationInSeconds)
                                    videoBitrate = calculatedBitrate
                                    print("Calculated bitrate from file size: \(calculatedBitrate) bits/s")
                                }
                            } catch {
                                print("Error loading duration: \(error.localizedDescription)")
                            }
                        }
                    } else {
                        let durationInSeconds = CMTimeGetSeconds(asset.duration)

                        if durationInSeconds > 0 {
                            // Calculate bitrate: (fileSize * 8) / durationInSeconds
                            let calculatedBitrate = Int64(Double(fileSizeInBytes * 8) / durationInSeconds)
                            videoBitrate = calculatedBitrate
                            print("Calculated bitrate from file size: \(calculatedBitrate) bits/s")
                        }
                    }
                }
            } catch {
                // This is expected for HLS streams
                if !isHLSStream {
                    print("Error getting file attributes: \(error.localizedDescription)")
                }
            }
        }

        // Extract format information
        if #available(macOS 13.0, *) {
            Task {
                do {
                    // Load tracks asynchronously
                    let videoTracks = try await asset.loadTracks(withMediaType: .video)
                    let audioTracks = try await asset.loadTracks(withMediaType: .audio)

                    // Extract video bitrate and format
                    if let videoTrack = videoTracks.first {
                        // Try to get estimated data rate directly from the track
                        if #available(macOS 13.0, *) {
                            do {
                                let estimatedDataRate = try await videoTrack.load(.estimatedDataRate)
                                if estimatedDataRate > 0 && !isHLSStream {
                                    videoBitrate = Int64(estimatedDataRate)
                                    print("Got bitrate from estimatedDataRate: \(videoBitrate) bits/s")
                                }
                            } catch {
                                print("Error getting estimatedDataRate: \(error.localizedDescription)")
                            }
                        }

                        // Get estimated data rate (bitrate) from format description
                        let formatDescriptions = try await videoTrack.load(.formatDescriptions)
                        if let formatDescription = formatDescriptions.first {
                            let extensions = CMFormatDescriptionGetExtensions(formatDescription) as Dictionary?
                            if let dict = extensions,
                               let bitrate = dict[kCMFormatDescriptionExtension_VerbatimSampleDescription] as? Dictionary<String, Any>,
                               let avgBitrate = bitrate["avg-bitrate"] as? Int64 {
                                videoBitrate = avgBitrate
                                print("Got bitrate from format description: \(videoBitrate) bits/s")
                            }

                            // Get MIME type for non-HLS content
                            if !isHLSStream {
                                let mediaSubType = CMFormatDescriptionGetMediaSubType(formatDescription)
                                let mediaType = CMFormatDescriptionGetMediaType(formatDescription)

                                if mediaType == kCMMediaType_Video {
                                    switch mediaSubType {
                                    case kCMVideoCodecType_H264:
                                        videoMimeType = "video/h264"
                                    case kCMVideoCodecType_HEVC:
                                        videoMimeType = "video/hevc"
                                    case kCMVideoCodecType_MPEG4Video:
                                        videoMimeType = "video/mp4v-es"
                                    case kCMVideoCodecType_MPEG2Video:
                                        videoMimeType = "video/mpeg2"
                                    default:
                                        videoMimeType = "video/mp4"
                                    }
                                }
                            }
                        }
                    }

                    // Extract audio channels and sample rate
                    if let audioTrack = audioTracks.first {
                        let formatDescriptions = try await audioTrack.load(.formatDescriptions)
                        if let formatDescription = formatDescriptions.first  {
                            let basicDescription = CMAudioFormatDescriptionGetStreamBasicDescription(formatDescription)
                            if let basicDesc = basicDescription {
                                audioChannels = Int(basicDesc.pointee.mChannelsPerFrame)
                                audioSampleRate = Int(basicDesc.pointee.mSampleRate)
                            }
                        }
                    }
                } catch {
                    print("Error extracting metadata: \(error.localizedDescription)")
                }
            }
        } else {
            // Fallback for older OS versions
            // Extract video bitrate and format
            if let videoTrack = asset.tracks(withMediaType: .video).first {
                // Try to get estimated data rate directly from the track
                let estimatedDataRate = videoTrack.estimatedDataRate
                if estimatedDataRate > 0 && !isHLSStream {
                    videoBitrate = Int64(estimatedDataRate)
                    print("Got bitrate from estimatedDataRate (legacy): \(videoBitrate) bits/s")
                }

                if let formatDescriptions = videoTrack.formatDescriptions as? [CMFormatDescription],
                   let formatDescription = formatDescriptions.first {
                    let extensions = CMFormatDescriptionGetExtensions(formatDescription) as Dictionary?
                    if let dict = extensions,
                       let bitrate = dict[kCMFormatDescriptionExtension_VerbatimSampleDescription] as? Dictionary<String, Any>,
                       let avgBitrate = bitrate["avg-bitrate"] as? Int64 {
                        videoBitrate = avgBitrate
                        print("Got bitrate from format description (legacy): \(videoBitrate) bits/s")
                    }

                    // Get MIME type for non-HLS content
                    if !isHLSStream {
                        let mediaSubType = CMFormatDescriptionGetMediaSubType(formatDescription)
                        let mediaType = CMFormatDescriptionGetMediaType(formatDescription)

                        if mediaType == kCMMediaType_Video {
                            switch mediaSubType {
                            case kCMVideoCodecType_H264:
                                videoMimeType = "video/h264"
                            case kCMVideoCodecType_HEVC:
                                videoMimeType = "video/hevc"
                            case kCMVideoCodecType_MPEG4Video:
                                videoMimeType = "video/mp4v-es"
                            case kCMVideoCodecType_MPEG2Video:
                                videoMimeType = "video/mpeg2"
                            default:
                                videoMimeType = "video/mp4"
                            }
                        }
                    }
                }
            }

            // Extract audio channels and sample rate
            if let audioTrack = asset.tracks(withMediaType: .audio).first {
                if let formatDescriptions = audioTrack.formatDescriptions as? [CMAudioFormatDescription],
                   let formatDescription = formatDescriptions.first {
                    let basicDescription = CMAudioFormatDescriptionGetStreamBasicDescription(formatDescription)
                    if let basicDesc = basicDescription {
                        audioChannels = Int(basicDesc.pointee.mChannelsPerFrame)
                        audioSampleRate = Int(basicDesc.pointee.mSampleRate)
                    }
                }
            }
        }
    }

    /// Detects the video's native frame rate from its asset
    private func detectVideoFrameRate(from asset: AVAsset) {
        // For HLS streams, default to 30 fps as it's variable
        if isHLSStream {
            videoFrameRate = 30.0
            updateCaptureFrameRate()
            return
        }

        asset.loadTracks(withMediaType: .video) { [self] tracks, error in
            guard let videoTrack = tracks?.first, error == nil else {
                print(
                    "Error loading video tracks: \(error?.localizedDescription ?? "Unknown")"
                )
                return
            }

            // Replace deprecated nominalFrameRate property
            if #available(macOS 13.0, *) {
                Task {
                    do {
                        let frameRate = try await videoTrack.load(.nominalFrameRate)
                        self.videoFrameRate = Float(frameRate)
                        if self.videoFrameRate <= 0 {
                            // Fallback to common default if detection fails
                            self.videoFrameRate = 30.0
                        }

                        // Set capture rate to the lower of the two rates
                        self.updateCaptureFrameRate()
                    } catch {
                        print("Error loading nominal frame rate: \(error.localizedDescription)")
                        // Fallback to common default if detection fails
                        self.videoFrameRate = 30.0
                        self.updateCaptureFrameRate()
                    }
                }
            } else {
                // Use deprecated property for older OS versions
                videoFrameRate = Float(videoTrack.nominalFrameRate)
                if videoFrameRate <= 0 {
                    // Fallback to common default if detection fails
                    videoFrameRate = 30.0
                }

                // Set capture rate to the lower of the two rates
                updateCaptureFrameRate()
            }
        }
    }

    /// Updates the capture frame rate based on screen and video rates
    private func updateCaptureFrameRate() {
        captureFrameRate = min(screenRefreshRate, videoFrameRate)
        // Update display link if it exists
        if isPlaying {
            configureDisplayLink()
        }
    }

    /// Opens the video from the given URI (local or network)
    func openUri(_ uri: String) {
        isReadyForPlayback = false
        pendingPlay = false

        // Clean up previous observers
        cleanupObservers()

        // Determine the URL (local or network)
        let url: URL = {
            if let parsedURL = URL(string: uri), parsedURL.scheme != nil {
                return parsedURL
            } else {
                return URL(fileURLWithPath: uri)
            }
        }()

        // Check if this is an HLS stream
        isHLSStream = isHLSUrl(url)

        if isHLSStream {
            print("Detected HLS stream: \(url)")
        }

        let mimeType = detectMimeType(at:url)
        var asset = AVURLAsset(url: url, options: mimeType != nil ? ["AVURLAssetOutOfBandMIMETypeKey": mimeType!] : nil)
        // Configure asset for HLS if needed
        if isHLSStream {
            asset = configureHLSAsset(asset)
        }

        // Extract metadata from the asset
        extractMetadata(from: asset)

        // Detect video frame rate
        detectVideoFrameRate(from: asset)

        // Retrieve the video track to obtain the actual dimensions
        asset.loadTracks(withMediaType: .video) { [self] tracks, error in
            guard let videoTrack = tracks?.first, error == nil else {
                print(
                    "Error loading video tracks: \(error?.localizedDescription ?? "Unknown")"
                )
                // For HLS streams without video track info yet, use default dimensions
                if isHLSStream {
                    frameWidth = 1920
                    frameHeight = 1080
                    nativeVideoWidth = frameWidth
                    nativeVideoHeight = frameHeight
                    setupVideoOutputAndPlayer(with: asset)
                }
                return
            }

            if #available(macOS 13.0, *) {
                Task { [weak self, asset] in
                    guard let self = self else { return }
                    do {
                        // Use the modern API to load naturalSize and preferredTransform
                        let naturalSize = try await videoTrack.load(.naturalSize)
                        let transform = try await videoTrack.load(.preferredTransform)

                        let effectiveSize = naturalSize.applying(transform)
                        self.frameWidth = Int(abs(effectiveSize.width))
                        self.frameHeight = Int(abs(effectiveSize.height))
                        self.nativeVideoWidth = self.frameWidth
                        self.nativeVideoHeight = self.frameHeight

                        // Build a video composition that applies the preferred transform so
                        // that AVPlayerItemVideoOutput delivers pixel buffers already rotated
                        // to display orientation (fixes portrait videos rendering sideways).
                        let videoComposition: AVVideoComposition? = self.isHLSStream
                            ? nil
                            : (try? await AVVideoComposition.videoComposition(withPropertiesOf: asset))

                        // Continue with player setup
                        self.setupVideoOutputAndPlayer(with: asset, videoComposition: videoComposition)
                    } catch {
                        print("Error loading video track properties: \(error.localizedDescription)")
                        // Use default dimensions for HLS if loading fails
                        if self.isHLSStream {
                            self.frameWidth = 1920
                            self.frameHeight = 1080
                            self.setupVideoOutputAndPlayer(with: asset, videoComposition: nil)
                        }
                    }
                }
            } else {
                // Fallback for older OS versions using deprecated properties
                let naturalSize = videoTrack.naturalSize
                let transform = videoTrack.preferredTransform

                let effectiveSize = naturalSize.applying(transform)
                frameWidth = Int(abs(effectiveSize.width))
                frameHeight = Int(abs(effectiveSize.height))
                nativeVideoWidth = frameWidth
                nativeVideoHeight = frameHeight

                // Build a video composition that applies the preferred transform (see modern
                // path above for rationale). Skip for HLS streams.
                let videoComposition: AVVideoComposition? = isHLSStream
                    ? nil
                    : AVMutableVideoComposition(propertiesOf: asset)

                // Continue with player setup
                setupVideoOutputAndPlayer(with: asset, videoComposition: videoComposition)
            }
        }
    }

    // Retains the latest CVPixelBuffer for zero-copy JNI access.
    // Updates frame dimensions for HLS streams where resolution may change dynamically.
    private func retainLatestPixelBuffer(_ pixelBuffer: CVPixelBuffer) {
        let w = CVPixelBufferGetWidth(pixelBuffer)
        let h = CVPixelBufferGetHeight(pixelBuffer)
        if isHLSStream && (w != frameWidth || h != frameHeight) {
            frameWidth = w
            frameHeight = h
            nativeVideoWidth = w
            nativeVideoHeight = h
        }
        bufferLock.lock()
        latestPixelBuffer = pixelBuffer
        bufferLock.unlock()
    }

    // Locks the latest CVPixelBuffer and returns its base address for direct reading.
    // outInfo must point to an array of 3 int32_t: [width, height, bytesPerRow].
    // Caller MUST call unlockLatestFrame() after reading.
    func lockLatestFrame(_ outInfo: UnsafeMutablePointer<Int32>) -> UnsafeMutableRawPointer? {
        bufferLock.lock()
        guard let pb = latestPixelBuffer else {
            bufferLock.unlock()
            return nil
        }
        lockedPixelBuffer = pb
        bufferLock.unlock()

        CVPixelBufferLockBaseAddress(pb, .readOnly)
        guard let addr = CVPixelBufferGetBaseAddress(pb) else {
            CVPixelBufferUnlockBaseAddress(pb, .readOnly)
            lockedPixelBuffer = nil
            return nil
        }
        outInfo[0] = Int32(CVPixelBufferGetWidth(pb))
        outInfo[1] = Int32(CVPixelBufferGetHeight(pb))
        outInfo[2] = Int32(CVPixelBufferGetBytesPerRow(pb))
        return addr
    }

    // Unlocks the CVPixelBuffer previously locked by lockLatestFrame().
    func unlockLatestFrame() {
        if let pb = lockedPixelBuffer {
            CVPixelBufferUnlockBaseAddress(pb, .readOnly)
            lockedPixelBuffer = nil
        }
    }

    // Helper method to setup video output and player
    private func setupVideoOutputAndPlayer(with asset: AVAsset, videoComposition: AVVideoComposition? = nil) {
        // Create attributes for the CVPixelBuffer (BGRA format) with IOSurface for better performance
        let pixelBufferAttributes: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: frameWidth,
            kCVPixelBufferHeightKey as String: frameHeight,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:],
        ]
        videoOutput = AVPlayerItemVideoOutput(pixelBufferAttributes: pixelBufferAttributes)

        let item = AVPlayerItem(asset: asset)

        // Apply the video composition (if any) so that pixel buffers delivered to
        // AVPlayerItemVideoOutput are pre-rotated to the display orientation.
        if let videoComposition = videoComposition {
            item.videoComposition = videoComposition
        }

        // Configure for HLS if needed
        if isHLSStream {
            // Set buffer duration for HLS
            item.preferredForwardBufferDuration = 5.0  // 5 seconds of buffer

            // Set initial preferred peak bitrate if specified
            if preferredPeakBitRate > 0 {
                item.preferredPeakBitRate = preferredPeakBitRate
            }

            // Enable automatic waiting behavior for HLS
            if #available(macOS 13.0, *) {
                item.automaticallyPreservesTimeOffsetFromLive = true
            }

            // Setup HLS monitoring
            setupHLSMonitoring(for: item)
        }

        if let output = videoOutput {
            item.add(output)
        }

        player = AVPlayer(playerItem: item)

        // Monitor time control status for all media types (buffering, paused, playing)
        timeControlStatusObserver = player?.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            self?.handleTimeControlStatus(player.timeControlStatus)
        }

        // Cache the display aspect ratio whenever presentationSize changes (e.g. HLS variant
        // switches). Reading presentationSize here keeps the live AVPlayerItem access on the
        // observation thread instead of the frame-decoding thread. .initial seeds the value now.
        presentationSizeObserver = item.observe(\.presentationSize, options: [.initial, .new]) { [weak self] item, _ in
            self?.updateCachedDisplayAspectRatio(from: item.presentationSize)
        }

        // Observe end of playback for all media types
        playbackEndObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: nil
        ) { [weak self] _ in
            self?.didPlayToEnd = true
        }

        // Configure player for HLS
        if isHLSStream {
            player?.automaticallyWaitsToMinimizeStalling = true
        }

        // Set initial volume
        player?.volume = volume

        // For non-HLS content, capture initial frame
        if !isHLSStream {
            captureInitialFrame()
        }

        // Mark as ready for playback
        self.isReadyForPlayback = true

        // If playback was pending, start playback
        if self.pendingPlay {
            DispatchQueue.main.async {
                self.play()
            }
        }
    }

    /// Captures initial frame to display without starting the display link
    private func captureInitialFrame() {
        guard let output = videoOutput, player?.currentItem != nil, !isHLSStream else { return }

        // Seek to the beginning to ensure we have a frame
        let zeroTime = CMTime.zero
        player?.seek(to: zeroTime)

        // Try to get the first frame
        if output.hasNewPixelBuffer(forItemTime: zeroTime),
           let pixelBuffer = output.copyPixelBuffer(forItemTime: zeroTime, itemTimeForDisplay: nil)
        {
            retainLatestPixelBuffer(pixelBuffer)
        }
    }

    /// Configures the timer with the appropriate frame rate
    private func configureDisplayLink() {
        stopDisplayLink()  // Ensure previous link is invalidated

        // For macOS, use a timer with the appropriate interval
        let interval = 1.0 / Double(captureFrameRate)
        displayLink = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            self?.captureFrame()
        }
    }

    /// Stops the timer
    private func stopDisplayLink() {
        displayLink?.invalidate()
        displayLink = nil
    }

    /// Captures the latest frame from the video output if available.
    @objc private func captureFrame() {
        guard let output = videoOutput,
              let item = player?.currentItem,
              isPlaying == true
        else { return }  // Skip capture if video is not playing

        let currentTime = item.currentTime()
        if output.hasNewPixelBuffer(forItemTime: currentTime),
           let pixelBuffer = output.copyPixelBuffer(
               forItemTime: currentTime, itemTimeForDisplay: nil)
        {
            retainLatestPixelBuffer(pixelBuffer)
        }
    }


    /// Starts video playback and begins frame capture at the optimized frame rate.
    func play() {
        if isReadyForPlayback {
            isPlaying = true
            player?.play()
            configureDisplayLink()
        } else {
            // Mark that playback is pending
            pendingPlay = true
        }
    }

    /// Pauses video playback and stops frame capture.
    func pause() {
        isPlaying = false
        player?.pause()
        stopDisplayLink()

        // Capture the current frame to display while paused (not for HLS)
        if !isHLSStream, let output = videoOutput, let item = player?.currentItem {
            let currentTime = item.currentTime()
            if output.hasNewPixelBuffer(forItemTime: currentTime),
               let pixelBuffer = output.copyPixelBuffer(
                   forItemTime: currentTime, itemTimeForDisplay: nil)
            {
                retainLatestPixelBuffer(pixelBuffer)
            }
        }
    }

    /// Sets the volume level (0.0 to 1.0)
    func setVolume(level: Float) {
        volume = max(0.0, min(1.0, level))  // Clamp between 0.0 and 1.0

        // Manage the multi-channel case (>2 channels)
        if let playerItem = player?.currentItem, audioChannels > 2 {
            // Apply volume via an AudioMix if we have more than 2 channels
            if #available(macOS 13.0, *) {
                Task { @MainActor in
                    do {
                        let audioTracks = try await playerItem.asset.loadTracks(withMediaType: .audio)
                        if let audioTrack = audioTracks.first {
                            let parameters = AVMutableAudioMixInputParameters(track: audioTrack)
                            parameters.setVolume(volume, at: CMTime.zero)

                            let audioMix = AVMutableAudioMix()
                            audioMix.inputParameters = [parameters]
                            playerItem.audioMix = audioMix
                        }
                    } catch {
                        print("Error loading audio tracks for volume adjustment: \(error.localizedDescription)")
                    }
                }
            } else {
                // Fallback for older OS versions
                if let audioTrack = playerItem.asset.tracks(withMediaType: .audio).first {
                    let parameters = AVMutableAudioMixInputParameters(track: audioTrack)
                    parameters.setVolume(volume, at: CMTime.zero)

                    let audioMix = AVMutableAudioMix()
                    audioMix.inputParameters = [parameters]
                    playerItem.audioMix = audioMix
                }
            }
        } else {
            // For stereo and mono channels, use the standard method
            player?.volume = volume
        }
    }

    /// Gets the current volume level (0.0 to 1.0)
    func getVolume() -> Float {
        return volume
    }

    /// Sets the playback speed (0.5 to 2.0, where 1.0 is normal speed)
    func setPlaybackSpeed(speed: Float) {
        playbackSpeed = max(0.5, min(2.0, speed))  // Clamp between 0.5 and 2.0
        player?.rate = playbackSpeed
    }

    /// Gets the current playback speed (0.5 to 2.0, where 1.0 is normal speed)
    func getPlaybackSpeed() -> Float {
        return playbackSpeed
    }

    /// Returns the width of the video frame in pixels
    func getFrameWidth() -> Int { return frameWidth }

    /// Returns the height of the video frame in pixels
    func getFrameHeight() -> Int { return frameHeight }

    /// Recomputes the cached display aspect ratio from a presentationSize. Called on the KVO
    /// observation thread; the value is read elsewhere from the frame-decoding thread.
    private func updateCachedDisplayAspectRatio(from size: CGSize) {
        let ratio = (size.width > 0 && size.height > 0) ? Double(size.width) / Double(size.height) : 0.0
        aspectLock.lock()
        cachedDisplayAspectRatio = ratio
        aspectLock.unlock()
    }

    /// Returns the correct display aspect ratio (width / height) of the current video.
    ///
    /// Derived from `AVPlayerItem.presentationSize`, which AVFoundation computes with the pixel
    /// aspect ratio and clean aperture already applied. This is the geometry the video should be
    /// drawn at — it can differ from the raw decoded pixel-buffer dimensions for anamorphic /
    /// non-square pixel content, which would otherwise be stretched. The value is cached from a
    /// KVO observer (see setup) so this is safe to call off the main thread; returns 0 when not
    /// yet available so the caller can fall back to the raw frame dimensions.
    func getDisplayAspectRatio() -> Double {
        aspectLock.lock()
        defer { aspectLock.unlock() }
        return cachedDisplayAspectRatio
    }

    /// Scales the output to fit within (width, height) while preserving the native aspect ratio.
    /// Never upscales beyond the native resolution. Recreates the pixel buffer output at the new size.
    /// Returns true if dimensions actually changed.
    func setOutputSize(width: Int, height: Int) -> Bool {
        guard width > 0, height > 0 else { return false }
        guard nativeVideoWidth > 0, nativeVideoHeight > 0 else { return false }

        let scaleX = Double(width) / Double(nativeVideoWidth)
        let scaleY = Double(height) / Double(nativeVideoHeight)
        let scale = min(scaleX, scaleY, 1.0) // never upscale

        // Enforce even dimensions (required by many codecs)
        let newWidth = max(2, (Int(Double(nativeVideoWidth) * scale) / 2) * 2)
        let newHeight = max(2, (Int(Double(nativeVideoHeight) * scale) / 2) * 2)

        if newWidth == frameWidth && newHeight == frameHeight { return false }

        frameWidth = newWidth
        frameHeight = newHeight

        // Recreate AVPlayerItemVideoOutput with updated hint dimensions
        if let item = player?.currentItem {
            if let old = videoOutput {
                item.remove(old)
            }
            let attrs: [String: Any] = [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
                kCVPixelBufferWidthKey as String: newWidth,
                kCVPixelBufferHeightKey as String: newHeight,
                kCVPixelBufferIOSurfacePropertiesKey as String: [:]
            ]
            let newOutput = AVPlayerItemVideoOutput(pixelBufferAttributes: attrs)
            item.add(newOutput)
            videoOutput = newOutput
        }

        return true
    }

    /// Returns the detected video frame rate
    func getVideoFrameRate() -> Float { return videoFrameRate }

    /// Returns the detected screen refresh rate
    func getScreenRefreshRate() -> Float { return screenRefreshRate }

    /// Returns the current capture frame rate (minimum of video and screen rates)
    func getCaptureFrameRate() -> Float { return captureFrameRate }

    /// Returns the video title if available
    func getVideoTitle() -> String? { return videoTitle }

    /// Returns the video bitrate in bits per second
    func getVideoBitrate() -> Int64 { return videoBitrate }

    /// Returns the video MIME type if available
    func getVideoMimeType() -> String? { return videoMimeType }

    /// Returns the number of audio channels
    func getAudioChannels() -> Int { return audioChannels }

    /// Returns the audio sample rate in Hz
    func getAudioSampleRate() -> Int { return audioSampleRate }

    /// Returns true if this is an HLS stream
    func getIsHLSStream() -> Bool { return isHLSStream }

    /// Returns available bitrates for HLS streams
    func getAvailableBitrates() -> [Float] { return availableBitrates }

    /// Returns current bitrate for HLS streams
    func getCurrentBitrate() -> Float { return currentBitrate }

    /// Returns buffer status (0.0 to 1.0)
    func getBufferStatus() -> Float { return bufferStatus }

    /// Returns whether the player is currently buffering
    func getIsBuffering() -> Bool { return isBuffering }

    /// Returns network status string
    func getNetworkStatus() -> String { return networkStatus }

    /// Returns last error if any
    func getLastError() -> String? { return lastError }

    /// Returns the duration of the video in seconds.
    func getDuration() -> Double {
        guard let item = player?.currentItem else { return 0 }

        // For live HLS streams, duration might be indefinite
        if isHLSStream && item.duration.isIndefinite {
            return -1  // Indicate live stream
        }

        // Use item.duration which is not deprecated
        return CMTimeGetSeconds(item.duration)
    }

    /// Returns the current playback time in seconds.
    func getCurrentTime() -> Double {
        guard let item = player?.currentItem else { return 0 }
        return CMTimeGetSeconds(item.currentTime())
    }

    /// Seeks to the specified time (in seconds).
    func seekTo(time: Double) {
        guard let player = player else { return }
        let newTime = CMTime(seconds: time, preferredTimescale: 600)

        // For HLS, use tolerance for more efficient seeking
        if isHLSStream {
            let tolerance = CMTime(seconds: 1.0, preferredTimescale: 600)
            player.seek(to: newTime, toleranceBefore: tolerance, toleranceAfter: tolerance)
        } else {
            player.seek(to: newTime)

            // Update frame at the new position if paused
            if !isPlaying, let output = videoOutput {
                if output.hasNewPixelBuffer(forItemTime: newTime),
                   let pixelBuffer = output.copyPixelBuffer(
                       forItemTime: newTime, itemTimeForDisplay: nil)
                {
                    retainLatestPixelBuffer(pixelBuffer)
                }
            }
        }
    }

    /// Consumes the end-of-playback flag. Returns true once per playback completion.
    func consumeDidPlayToEnd() -> Bool {
        if didPlayToEnd {
            didPlayToEnd = false
            return true
        }
        return false
    }

    /// Clean up observers
    private func cleanupObservers() {
        playerItemObserver?.invalidate()
        playerObserver?.invalidate()
        timeControlStatusObserver?.invalidate()
        bufferEmptyObserver?.invalidate()
        bufferLikelyToKeepUpObserver?.invalidate()
        bufferFullObserver?.invalidate()
        presentationSizeObserver?.invalidate()
        presentationSizeObserver = nil
        // Drop the cached aspect ratio so a reopened player can't briefly show the old video's ratio.
        updateCachedDisplayAspectRatio(from: .zero)

        if let observer = playbackEndObserver {
            NotificationCenter.default.removeObserver(observer)
            playbackEndObserver = nil
        }
        didPlayToEnd = false

        NotificationCenter.default.removeObserver(self)
    }

    /// Disposes of the video player and releases resources.
    func dispose() {
        pause()
        cleanupObservers()
        player = nil
        videoOutput = nil
        if let pb = lockedPixelBuffer {
            CVPixelBufferUnlockBaseAddress(pb, .readOnly)
            lockedPixelBuffer = nil
        }
        latestPixelBuffer = nil
    }
}

/// MARK: - C Exported Functions for JNA

@_cdecl("createVideoPlayer")
public func createVideoPlayer() -> UnsafeMutableRawPointer? {
    let player = MacVideoPlayer()
    return Unmanaged.passRetained(player).toOpaque()
}

@_cdecl("openUri")
public func openUri(_ context: UnsafeMutableRawPointer?, _ uri: UnsafePointer<CChar>?) {
    guard let context = context,
          let uriCStr = uri,
          let swiftUri = String(validatingUTF8: uriCStr)
    else {
        print("Invalid parameters for openUri")
        return
    }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    // Use a background queue for heavy operations to avoid blocking the main thread
    DispatchQueue.global(qos: .userInitiated).async {
        player.openUri(swiftUri)
    }
}

@_cdecl("playVideo")
public func playVideo(_ context: UnsafeMutableRawPointer?) {
    guard let context = context else { return }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.play()
    }
}

@_cdecl("pauseVideo")
public func pauseVideo(_ context: UnsafeMutableRawPointer?) {
    guard let context = context else { return }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.pause()
    }
}

@_cdecl("setVolume")
public func setVolume(_ context: UnsafeMutableRawPointer?, _ volume: Float) {
    guard let context = context else { return }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.setVolume(level: volume)
    }
}

@_cdecl("getVolume")
public func getVolume(_ context: UnsafeMutableRawPointer?) -> Float {
    guard let context = context else { return 0.0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getVolume()
}

@_cdecl("lockLatestFrame")
public func lockLatestFrame(_ context: UnsafeMutableRawPointer?, _ outInfo: UnsafeMutablePointer<Int32>?) -> UnsafeMutableRawPointer? {
    guard let context = context, let outInfo = outInfo else { return nil }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.lockLatestFrame(outInfo)
}

@_cdecl("unlockLatestFrame")
public func unlockLatestFrame(_ context: UnsafeMutableRawPointer?) {
    guard let context = context else { return }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    player.unlockLatestFrame()
}

@_cdecl("getFrameWidth")
public func getFrameWidth(_ context: UnsafeMutableRawPointer?) -> Int32 {
    guard let context = context else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return Int32(player.getFrameWidth())
}

@_cdecl("getFrameHeight")
public func getFrameHeight(_ context: UnsafeMutableRawPointer?) -> Int32 {
    guard let context = context else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return Int32(player.getFrameHeight())
}

@_cdecl("getDisplayAspectRatio")
public func getDisplayAspectRatio(_ context: UnsafeMutableRawPointer?) -> Double {
    guard let context = context else { return 0.0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getDisplayAspectRatio()
}

@_cdecl("setOutputSize")
public func setOutputSize(_ context: UnsafeMutableRawPointer?, _ width: Int32, _ height: Int32) -> Int32 {
    guard let context = context else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.setOutputSize(width: Int(width), height: Int(height)) ? 1 : 0
}

@_cdecl("getVideoFrameRate")
public func getVideoFrameRate(_ context: UnsafeMutableRawPointer?) -> Float {
    guard let context = context else { return 0.0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getVideoFrameRate()
}

@_cdecl("getScreenRefreshRate")
public func getScreenRefreshRate(_ context: UnsafeMutableRawPointer?) -> Float {
    guard let context = context else { return 0.0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getScreenRefreshRate()
}

@_cdecl("getCaptureFrameRate")
public func getCaptureFrameRate(_ context: UnsafeMutableRawPointer?) -> Float {
    guard let context = context else { return 0.0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getCaptureFrameRate()
}

@_cdecl("getVideoDuration")
public func getVideoDuration(_ context: UnsafeMutableRawPointer?) -> Double {
    guard let context = context else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getDuration()
}

@_cdecl("getCurrentTime")
public func getCurrentTime(_ context: UnsafeMutableRawPointer?) -> Double {
    guard let context = context else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getCurrentTime()
}

@_cdecl("seekTo")
public func seekTo(_ context: UnsafeMutableRawPointer?, _ time: Double) {
    guard let context = context else { return }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.seekTo(time: time)
    }
}

@_cdecl("disposeVideoPlayer")
public func disposeVideoPlayer(_ context: UnsafeMutableRawPointer?) {
    guard let context = context else { return }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeRetainedValue()
    DispatchQueue.main.async {
        player.dispose()
    }
}

@_cdecl("setPlaybackSpeed")
public func setPlaybackSpeed(_ context: UnsafeMutableRawPointer?, _ speed: Float) {
    guard let context = context else { return }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.setPlaybackSpeed(speed: speed)
    }
}

@_cdecl("getPlaybackSpeed")
public func getPlaybackSpeed(_ context: UnsafeMutableRawPointer?) -> Float {
    guard let context = context else { return 1.0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getPlaybackSpeed()
}

@_cdecl("getVideoTitle")
public func getVideoTitle(_ context: UnsafeMutableRawPointer?) -> UnsafePointer<CChar>? {
    guard let context = context else { return nil }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    if let title = player.getVideoTitle() {
        let cString = strdup(title)
        return UnsafePointer<CChar>(cString)
    }
    return nil
}

@_cdecl("getVideoBitrate")
public func getVideoBitrate(_ context: UnsafeMutableRawPointer?) -> Int64 {
    guard let context = context else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getVideoBitrate()
}

@_cdecl("getVideoMimeType")
public func getVideoMimeType(_ context: UnsafeMutableRawPointer?) -> UnsafePointer<CChar>? {
    guard let context = context else { return nil }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    if let mimeType = player.getVideoMimeType() {
        let cString = strdup(mimeType)
        return UnsafePointer<CChar>(cString)
    }
    return nil
}

@_cdecl("getAudioChannels")
public func getAudioChannels(_ context: UnsafeMutableRawPointer?) -> Int32 {
    guard let context = context else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return Int32(player.getAudioChannels())
}

@_cdecl("getAudioSampleRate")
public func getAudioSampleRate(_ context: UnsafeMutableRawPointer?) -> Int32 {
    guard let context = context else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return Int32(player.getAudioSampleRate())
}

@_cdecl("consumeDidPlayToEnd")
public func consumeDidPlayToEnd(_ context: UnsafeMutableRawPointer?) -> Int32 {
    guard let context = context else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.consumeDidPlayToEnd() ? 1 : 0
}

// HLS-specific C exports
@_cdecl("getIsHLSStream")
public func getIsHLSStream(_ context: UnsafeMutableRawPointer?) -> Bool {
    guard let context = context else { return false }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getIsHLSStream()
}

@_cdecl("getAvailableBitrates")
public func getAvailableBitrates(_ context: UnsafeMutableRawPointer?, _ buffer: UnsafeMutablePointer<Float>?, _ maxCount: Int32) -> Int32 {
    guard let context = context, let buffer = buffer else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    let bitrates = player.getAvailableBitrates()
    let count = min(Int(maxCount), bitrates.count)
    for i in 0..<count {
        buffer[i] = bitrates[i]
    }
    return Int32(count)
}

@_cdecl("getCurrentBitrate")
public func getCurrentBitrate(_ context: UnsafeMutableRawPointer?) -> Float {
    guard let context = context else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getCurrentBitrate()
}

@_cdecl("setPreferredMaxBitrate")
public func setPreferredMaxBitrate(_ context: UnsafeMutableRawPointer?, _ bitrate: Double) {
    guard let context = context else { return }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.setPreferredMaxBitrate(bitrate)
    }
}

@_cdecl("forceQuality")
public func forceQuality(_ context: UnsafeMutableRawPointer?, _ bitrate: Float) {
    guard let context = context else { return }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.forceQuality(bitrate: bitrate)
    }
}

@_cdecl("getBufferStatus")
public func getBufferStatus(_ context: UnsafeMutableRawPointer?) -> Float {
    guard let context = context else { return 0 }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getBufferStatus()
}

@_cdecl("getIsBuffering")
public func getIsBuffering(_ context: UnsafeMutableRawPointer?) -> Bool {
    guard let context = context else { return false }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getIsBuffering()
}

@_cdecl("getNetworkStatus")
public func getNetworkStatus(_ context: UnsafeMutableRawPointer?) -> UnsafePointer<CChar>? {
    guard let context = context else { return nil }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    let status = player.getNetworkStatus()
    let cString = strdup(status)
    return UnsafePointer<CChar>(cString)
}

@_cdecl("getLastError")
public func getLastError(_ context: UnsafeMutableRawPointer?) -> UnsafePointer<CChar>? {
    guard let context = context else { return nil }
    let player = Unmanaged<MacVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    if let error = player.getLastError() {
        let cString = strdup(error)
        return UnsafePointer<CChar>(cString)
    }
    return nil
}