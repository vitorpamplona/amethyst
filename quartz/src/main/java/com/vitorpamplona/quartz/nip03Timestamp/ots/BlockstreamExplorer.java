package com.vitorpamplona.quartz.nip03Timestamp.ots;

import android.util.Log;

import com.fasterxml.jackson.databind.JsonNode;
import com.vitorpamplona.quartz.nip03Timestamp.ots.http.Request;
import com.vitorpamplona.quartz.nip03Timestamp.ots.http.Response;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BlockstreamExplorer implements BitcoinExplorer {
    private static final String esploraUrl = "https://blockstream.info/api";

    /**
     * Retrieve the block information from the block hash.
     *
     * @param hash Hash of the block.
     * @return the blockheader of the hash
     * @throws Exception desc
     */
    public BlockHeader block(final String hash) throws Exception {
        final URL url = new URL(esploraUrl + "/block/" + hash);
        final Request task = new Request(url);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<Response> future = executor.submit(task);
        final Response take = future.get();
        executor.shutdown();
        if (!take.isOk())
            throw new Exception();

        final JsonNode jsonObject = take.getJson();
        final String merkleroot = jsonObject.get("merkle_root").asText();
        final String time = String.valueOf(jsonObject.get("timestamp").asInt());
        final BlockHeader blockHeader = new BlockHeader();
        blockHeader.setMerkleroot(merkleroot);
        blockHeader.setTime(time);
        blockHeader.setBlockHash(hash);
        Log.i("BlockstreamExplorer", take.getFromUrl() + " " + blockHeader);
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
    public String blockHash(final Integer height) throws Exception {
        final URL url = new URL(esploraUrl + "/block-height/" + height);
        final Request task = new Request(url);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<Response> future = executor.submit(task);
        final Response take = future.get();
        executor.shutdown();
        if (!take.isOk())
            throw new Exception();
        final String blockHash = take.getString();
        Log.i("BlockstreamExplorer", take.getFromUrl() + " " + blockHash);
        return blockHash;
    }
}