package com.vitorpamplona.quartz.ots;

import com.vitorpamplona.quartz.ots.http.Request;
import com.vitorpamplona.quartz.ots.http.Response;

import org.json.JSONObject;

import java.net.URL;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class Esplora {

    private static final Logger log = Utils.getLogger(Esplora.class.getName());
    private static final String esploraUrl = "https://blockstream.info/api";

    /**
     * Retrieve the block information from the block hash.
     *
     * @param hash Hash of the block.
     * @return the blockheader of the hash
     * @throws Exception desc
     */
    public static BlockHeader block(final String hash) throws Exception {
        final URL url = new URL(esploraUrl + "/block/" + hash);
        final Request task = new Request(url);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<Response> future = executor.submit(task);
        final Response take = future.get();
        executor.shutdown();
        if (!take.isOk())
            throw new Exception();

        final JSONObject jsonObject = take.getJson();
        final String merkleroot = jsonObject.getString("merkle_root");
        final String time = String.valueOf(jsonObject.getInt("timestamp"));
        final BlockHeader blockHeader = new BlockHeader();
        blockHeader.setMerkleroot(merkleroot);
        blockHeader.setTime(time);
        blockHeader.setBlockHash(hash);
        log.info(take.getFromUrl() + " " + blockHeader);
        return blockHeader;
        //log.warning("Cannot parse merkleroot from body: " + jsonObject + ": " + e.getMessage());
    }

    /**
     * Retrieve the block hash from the block height.
     *
     * @param height Height of the block.
     * @return the hash of the block at height height
     * @throws Exception desc
     */
    public static String blockHash(final Integer height) throws Exception {
        final URL url = new URL(esploraUrl + "/block-height/" + height);
        final Request task = new Request(url);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<Response> future = executor.submit(task);
        final Response take = future.get();
        executor.shutdown();
        if (!take.isOk())
            throw new Exception();
        final String blockHash = take.getString();
        log.info(take.getFromUrl() + " " + blockHash);
        return blockHash;
    }
}