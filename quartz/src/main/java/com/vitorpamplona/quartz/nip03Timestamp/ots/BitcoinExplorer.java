package com.vitorpamplona.quartz.nip03Timestamp.ots;

public interface BitcoinExplorer {
    /**
     * Retrieve the block information from the block hash.
     *
     * @param hash Hash of the block.
     * @return the blockheader of the hash
     * @throws Exception desc
     */
    public BlockHeader block(final String hash) throws Exception;

    /**
     * Retrieve the block hash from the block height.
     *
     * @param height Height of the block.
     * @return the hash of the block at height height
     * @throws Exception desc
     */
    public String blockHash(final Integer height) throws Exception;
}
