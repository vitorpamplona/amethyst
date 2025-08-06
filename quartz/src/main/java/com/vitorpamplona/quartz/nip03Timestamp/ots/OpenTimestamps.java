package com.vitorpamplona.quartz.nip03Timestamp.ots;

import android.util.Log;

import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.BitcoinBlockHeaderAttestation;
import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.EthereumBlockHeaderAttestation;
import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.LitecoinBlockHeaderAttestation;
import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.PendingAttestation;
import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.TimeAttestation;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.VerificationException;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpAppend;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpCrypto;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpSHA256;
import com.vitorpamplona.quartz.utils.Hex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The main class for timestamp operations.
 */
public class OpenTimestamps {

    BitcoinExplorer explorer;
    CalendarBuilder calBuilder;


    public OpenTimestamps(BitcoinExplorer explorer, CalendarBuilder builder) {
        this.explorer = explorer;
        this.calBuilder = builder;
    }

    /**
     * Show information on a detached timestamp.
     *
     * @param detachedTimestampFile The DetachedTimestampFile ots.
     * @return the string representation of the timestamp.
     */
    public String info(DetachedTimestampFile detachedTimestampFile) {
        return info(detachedTimestampFile, false);
    }

    /**
     * Show information on a detached timestamp with verbose option.
     *
     * @param detachedTimestampFile The DetachedTimestampFile ots.
     * @param verbose               Show verbose output.
     * @return the string representation of the timestamp.
     */
    public String info(DetachedTimestampFile detachedTimestampFile, boolean verbose) {
        if (detachedTimestampFile == null) {
            return "No ots file";
        }

        String fileHash = Utils.bytesToHex(detachedTimestampFile.timestamp.msg).toLowerCase(Locale.ROOT);
        String hashOp = ((OpCrypto) detachedTimestampFile.fileHashOp)._TAG_NAME();

        String firstLine = "File " + hashOp + " hash: " + fileHash + '\n';

        return firstLine + "Timestamp:\n" + detachedTimestampFile.timestamp.strTree(0, verbose);
    }

    /**
     * Show information on a timestamp.
     *
     * @param timestamp The timestamp buffered.
     * @return the string representation of the timestamp.
     */
    public String info(Timestamp timestamp) {
        if (timestamp == null) {
            return "No timestamp";
        }

        String fileHash = Utils.bytesToHex(timestamp.msg).toLowerCase(Locale.ROOT);
        String firstLine = "Hash: " + fileHash + '\n';

        return firstLine + "Timestamp:\n" + timestamp.strTree(0);
    }

    /**
     * Create timestamp with the aid of a remote calendar. May be specified multiple times.
     *
     * @param fileTimestamp The Detached Timestamp File.
     * @return The plain array buffer of stamped.
     * @throws IOException if fileTimestamp is not valid, or the stamp procedure fails.
     */
    public Timestamp stamp(DetachedTimestampFile fileTimestamp) throws IOException {
        return stamp(fileTimestamp, null, 0);
    }

    /**
     * Create timestamp with the aid of a remote calendar. May be specified multiple times.
     *
     * @param fileTimestamp       The timestamp to stamp.
     * @param calendarsUrl        The list of calendar urls.
     * @param m                   The number of calendar to use.
     * @return The plain array buffer of stamped.
     * @throws IOException if fileTimestamp is not valid, or the stamp procedure fails.
     */
    public Timestamp stamp(DetachedTimestampFile fileTimestamp, List<String> calendarsUrl, Integer m) throws IOException {
        List<DetachedTimestampFile> fileTimestamps = new ArrayList<DetachedTimestampFile>();
        fileTimestamps.add(fileTimestamp);

        return stamp(fileTimestamps, calendarsUrl, m);
    }

    /**
     * Create timestamp with the aid of a remote calendar. May be specified multiple times.
     *
     * @param fileTimestamps      The list of timestamp to stamp.
     * @param calendarsUrl        The list of calendar urls.
     * @param m                   The number of calendar to use.
     * @return The plain array buffer of stamped.
     * @throws IOException if fileTimestamp is not valid, or the stamp procedure fails.
     */
    public Timestamp stamp(List<DetachedTimestampFile> fileTimestamps, List<String> calendarsUrl, Integer m) throws IOException {
        // Parse parameters
        if (fileTimestamps == null || fileTimestamps.size() == 0) {
            throw new IOException();
        }

        if (calendarsUrl == null || calendarsUrl.size() == 0) {
            calendarsUrl = new ArrayList<String>();
            calendarsUrl.add("https://alice.btc.calendar.opentimestamps.org");
            calendarsUrl.add("https://bob.btc.calendar.opentimestamps.org");
            calendarsUrl.add("https://finney.calendar.eternitywall.com");
        }

        if (m == null || m <= 0) {
            if (calendarsUrl.size() == 0) {
                m = 2;
            } else if (calendarsUrl.size() == 1) {
                m = 1;
            } else {
                m = calendarsUrl.size();
            }
        }

        if (m < 0 || m > calendarsUrl.size()) {
            Log.e("OpenTimestamp", "m cannot be greater than available calendar neither less or equal 0");
            throw new IOException();
        }

        // Build markle tree
        Timestamp merkleTip = makeMerkleTree(fileTimestamps);

        if (merkleTip == null) {
            throw new IOException();
        }

        // Stamping
        Timestamp resultTimestamp = create(merkleTip, calendarsUrl, m);

        if (resultTimestamp == null) {
            throw new IOException();
        }

        // Result of timestamp serialization
        if (fileTimestamps.size() == 1) {
            return fileTimestamps.get(0).timestamp;
        } else {
            return merkleTip;
        }
    }

    /**
     * Create a timestamp
     *
     * @param timestamp    The timestamp.
     * @param calendarUrls List of calendar's to use.
     * @param m            Number of calendars to use.
     * @return The created timestamp.
     */
    private Timestamp create(Timestamp timestamp, List<String> calendarUrls, Integer m) {
        int capacity = calendarUrls.size();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        ArrayBlockingQueue<Optional<Timestamp>> queue = new ArrayBlockingQueue<>(capacity);

        // Submit to all public calendars
        for (final String calendarUrl : calendarUrls) {
            Log.i("OpenTimestamps", "Submitting to remote calendar " + calendarUrl);

            try {
                CalendarAsyncSubmit task = new CalendarAsyncSubmit(calendarUrl, timestamp.msg);
                task.setQueue(queue);
                executor.submit(task);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        int count = 0;

        for (count = 0; count < capacity && count < m; count++) {
            try {
                Optional<Timestamp> optionalStamp = queue.take();

                if (optionalStamp.isPresent()) {
                    try {
                        Timestamp time = optionalStamp.get();
                        timestamp.merge(time);
                        Log.i("Open", ""+ timestamp.attestations.size());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (count < m) {
            Log.e("OpenTimestamp", "Failed to create timestamp: requested " + String.valueOf(m) + " attestation" + ((m > 1) ? "s" : "") + " but received only " + String.valueOf(count));
        }

        //shut down the executor service now
        executor.shutdown();

        return timestamp;
    }

    /**
     * Make Merkle Tree of detached timestamps.
     *
     * @param fileTimestamps The list of DetachedTimestampFile.
     * @return merkle tip timestamp.
     */
    public Timestamp makeMerkleTree(List<DetachedTimestampFile> fileTimestamps) {
        List<Timestamp> merkleRoots = new ArrayList<>();

        for (DetachedTimestampFile fileTimestamp : fileTimestamps) {
            byte[] bytesRandom16 = new byte[16];

            try {
                bytesRandom16 = Utils.randBytes(16);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

            // nonce_appended_stamp = file_timestamp.timestamp.ops.add(com.vitorpamplona.quartz.ots.op.OpAppend(os.urandom(16)))
            Timestamp nonceAppendedStamp = fileTimestamp.timestamp.add(new OpAppend(bytesRandom16));
            // merkle_root = nonce_appended_stamp.ops.add(com.vitorpamplona.quartz.ots.op.OpSHA256())
            Timestamp merkleRoot = nonceAppendedStamp.add(new OpSHA256());
            merkleRoots.add(merkleRoot);
        }

        Timestamp merkleTip = Merkle.makeMerkleTree(merkleRoots);
        return merkleTip;
    }

    /**
     * Compare and verify a detached timestamp.
     *
     * @param ots     The DetachedTimestampFile containing the proof to verify.
     * @param diggest The hash of the stamped file, in bytes
     * @return Hashmap of block heights and timestamps indexed by chain: timestamp in seconds from 1 January 1970.
     * @throws Exception if the verification procedure fails.
     */

    public HashMap<VerifyResult.Chains, VerifyResult> verify(DetachedTimestampFile ots, byte[] diggest) throws Exception {
        if (!Arrays.equals(ots.fileDigest(), diggest)) {
            Log.e("OpenTimestamp", "Expected digest " + Hex.encode(ots.fileDigest()).toLowerCase(Locale.ROOT));
            Log.e("OpenTimestamp", "File does not match original!");
            throw new Exception("File does not match original!");
        }

        return verify(ots.timestamp);
    }

    /**
     * Verify a timestamp.
     *
     * @param timestamp The timestamp.
     * @return HashMap of block heights and timestamps indexed by chain: timestamp in seconds from 1 January 1970.
     * @throws Exception if the verification procedure fails.
     */
    public HashMap<VerifyResult.Chains, VerifyResult> verify(Timestamp timestamp) throws Exception {
        HashMap<VerifyResult.Chains, VerifyResult> verifyResults = new HashMap<>();

        for (Map.Entry<byte[], TimeAttestation> item : timestamp.allAttestations().entrySet()) {
            byte[] msg = item.getKey();
            TimeAttestation attestation = item.getValue();
            VerifyResult verifyResult = null;
            VerifyResult.Chains chain = null;

            try {
                if (attestation instanceof BitcoinBlockHeaderAttestation) {
                    chain = VerifyResult.Chains.BITCOIN;
                    Long time = verify((BitcoinBlockHeaderAttestation) attestation, msg);
                    int height = ((BitcoinBlockHeaderAttestation) attestation).getHeight();
                    verifyResult = new VerifyResult(time, height);
                } else if (attestation instanceof LitecoinBlockHeaderAttestation) {
                    chain = VerifyResult.Chains.LITECOIN;
                    Long time = verify((LitecoinBlockHeaderAttestation) attestation, msg);
                    int height = ((LitecoinBlockHeaderAttestation) attestation).getHeight();
                    verifyResult = new VerifyResult(time, height);
                }

                if (verifyResult != null && verifyResults.containsKey(chain)) {
                    if (verifyResult.height < verifyResults.get(chain).height) {
                        verifyResults.put(chain, verifyResult);
                    }
                }

                if (verifyResult != null && !verifyResults.containsKey(chain)) {
                    verifyResults.put(chain, verifyResult);
                }
            } catch (VerificationException e) {
                throw e;
            } catch (Exception e) {
                String text = "";

                if (chain == VerifyResult.Chains.BITCOIN) {
                    text = BitcoinBlockHeaderAttestation.chain;
                } else if (chain == VerifyResult.Chains.LITECOIN) {
                    text = LitecoinBlockHeaderAttestation.chain;
                } else if (chain == VerifyResult.Chains.ETHEREUM) {
                    text = EthereumBlockHeaderAttestation.chain;
                } else {
                    throw e;
                }

                Log.e("OpenTimestamp", Utils.toUpperFirstLetter(text) + " verification failed: " + e.getMessage());
                throw e;
            }
        }
        return verifyResults;
    }

    /**
     * Verify an Bitcoin Block Header Attestation.
     * if the node is not reachable or it fails, uses Lite-client verification.
     *
     * @param attestation The BitcoinBlockHeaderAttestation attestation.
     * @param msg         The digest to verify.
     * @return The unix timestamp in seconds from 1 January 1970.
     * @throws VerificationException if it doesn't check the merkle root of the block.
     * @throws Exception             if the verification procedure fails.
     */
    public Long verify(BitcoinBlockHeaderAttestation attestation, byte[] msg) throws VerificationException, Exception {
        Integer height = attestation.getHeight();
        BlockHeader blockInfo;

        try {
            String blockHash = explorer.blockHash(height);
            blockInfo = explorer.block(blockHash);
            Log.i("OpenTimestamps", "Lite-client verification, assuming block " + blockHash + " is valid");
        } catch (Exception e2) {
            e2.printStackTrace();
            throw e2;
        }

        return attestation.verifyAgainstBlockheader(Utils.arrayReverse(msg), blockInfo);
    }

    /**
     * Verify an Litecoin Block Header Attestation. Litecoin verification uses only lite-client verification.
     *
     * @param attestation The LitecoinBlockHeaderAttestation attestation.
     * @param msg         The digest to verify.
     * @return The unix timestamp in seconds from 1 January 1970.
     * @throws VerificationException if it doesn't check the merkle root of the block.
     * @throws Exception             if the verification procedure fails.
     */
    public Long verify(LitecoinBlockHeaderAttestation attestation, byte[] msg) throws VerificationException, Exception {
        Integer height = attestation.getHeight();
        BlockHeader blockInfo;

        try {
            String blockHash = explorer.blockHash(height);
            Log.i("OpenTimestamps", "Lite-client verification, assuming block " + blockHash + " is valid");
            blockInfo = explorer.block(blockHash);
        } catch (Exception e2) {
            e2.printStackTrace();
            throw e2;
        }

        return attestation.verifyAgainstBlockheader(Utils.arrayReverse(msg), blockInfo);
    }

    /**
     * Upgrade a timestamp.
     *
     * @param detachedTimestamp The DetachedTimestampFile containing the proof to verify.
     * @return a boolean representing if the timestamp has changed.
     * @throws Exception if the upgrading procedure fails.
     */
    public boolean upgrade(DetachedTimestampFile detachedTimestamp) throws Exception {
        // Upgrade timestamp
        boolean changed = upgrade(detachedTimestamp.timestamp);
        return changed;
    }

    /**
     * Attempt to upgrade an incomplete timestamp to make it verifiable.
     * Note that this means if the timestamp that is already complete, False will be returned as nothing has changed.
     *
     * @param timestamp The timestamp to upgrade.
     * @return a boolean representing if the timestamp has changed.
     * @throws Exception if the upgrading procedure fails.
     */
    public boolean upgrade(Timestamp timestamp) throws Exception {
        // Check remote calendars for upgrades.
        // This time we only check PendingAttestations - we can't be as agressive.

        boolean upgraded = false;
        Set<TimeAttestation> existingAttestations = timestamp.getAttestations();

        for (Timestamp subStamp : timestamp.directlyVerified()) {
            for (TimeAttestation attestation : subStamp.attestations) {
                if (attestation instanceof PendingAttestation && !subStamp.isTimestampComplete()) {
                    String calendarUrl = new String(((PendingAttestation) attestation).getUri(), StandardCharsets.UTF_8);
                    // var calendarUrl = calendarUrls[0];
                    byte[] commitment = subStamp.msg;

                    try {
                        Calendar calendar = new Calendar(calendarUrl);
                        Timestamp upgradedStamp = upgrade(subStamp, calendar, commitment, existingAttestations);

                        try {
                            subStamp.merge(upgradedStamp);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        upgraded = true;
                    } catch (Exception e) {
                        Log.i("OpenTimestamps", e.getMessage());
                    }
                }
            }
        }

        return upgraded;
    }

    private Timestamp upgrade(Timestamp subStamp, Calendar calendar, byte[] commitment, Set<TimeAttestation> existingAttestations) throws Exception {
        Timestamp upgradedStamp;

        try {
            upgradedStamp = calendar.getTimestamp(commitment);

            if (upgradedStamp == null) {
                throw new Exception("Invalid stamp");
            }
        } catch (Exception e) {
            Log.i("OpenTimestamps", "Calendar " + calendar.getUrl() + ": " + e.getMessage());
            throw e;
        }

        Set<TimeAttestation> attsFromRemote = upgradedStamp.getAttestations();

        if (attsFromRemote.size() > 0) {
            Log.i("OpenTimestamps", "Got 1 attestation(s) from " + calendar.getUrl());
        }

        // Set difference from remote attestations & existing attestations
        Set<TimeAttestation> newAttestations = attsFromRemote;
        newAttestations.removeAll(existingAttestations);

        // changed & found_new_attestations
        // foundNewAttestations = true;
        // Log.i("OpenTimestamps", attsFromRemote.size + ' attestation(s) from ' + calendar.url);

        // Set union of existingAttestations & newAttestations
        existingAttestations.addAll(newAttestations);

        return upgradedStamp;
        // subStamp.merge(upgradedStamp);
        // args.cache.merge(upgraded_stamp)
        // sub_stamp.merge(upgraded_stamp)
    }
}
