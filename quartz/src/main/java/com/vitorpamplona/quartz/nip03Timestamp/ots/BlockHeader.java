package com.vitorpamplona.quartz.nip03Timestamp.ots;

public class BlockHeader {

    private String merkleroot;
    private String blockHash;
    private String time;

    public void setTime(String time) {
        this.time = time;
    }

    public Long getTime() {
        return Long.valueOf(time);
    }

    public String getMerkleroot() {
        return merkleroot;
    }

    public void setMerkleroot(String merkleroot) {
        this.merkleroot = merkleroot;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BlockHeader that = (BlockHeader) o;

        if (merkleroot != null ? !merkleroot.equals(that.merkleroot) : that.merkleroot != null) {
            return false;
        }

        if (blockHash != null ? !blockHash.equals(that.blockHash) : that.blockHash != null) {
            return false;
        }

        return time != null ? time.equals(that.time) : that.time == null;
    }

    @Override
    public int hashCode() {
        int result = merkleroot != null ? merkleroot.hashCode() : 0;
        result = 31 * result + (blockHash != null ? blockHash.hashCode() : 0);
        result = 31 * result + (time != null ? time.hashCode() : 0);

        return result;
    }

    @Override
    public String toString() {
        return "BlockHeader{" +
                "merkleroot='" + merkleroot + '\'' +
                ", blockHash='" + blockHash + '\'' +
                ", time='" + time + '\'' +
                '}';
    }
}