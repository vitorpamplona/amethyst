package com.vitorpamplona.quartz.nip03Timestamp.ots;

import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpAppend;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpPrepend;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpSHA256;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility functions for merkle trees
 */
public class Merkle {

    /**
     * Concatenate left and right, then perform a unary operation on them left and right can be either timestamps or bytes.
     * Appropriate intermediary append/prepend operations will be created as needed for left and right.
     *
     * @param left  the left timestamp parameter
     * @param right the right timestamp parameter
     * @return the concatenation of left and right
     */
    public static Timestamp catThenUnaryOp(Timestamp left, Timestamp right) {
        // rightPrependStamp = right.ops.add(OpPrepend(left.msg))
        Timestamp rightPrependStamp = right.add(new OpPrepend(left.msg));

        // Left and right should produce the same thing, so we can set the timestamp of the left to the right.
        // left.ops[OpAppend(right.msg)] = right_prepend_stamp
        // leftAppendStamp = left.ops.add(OpAppend(right.msg))
        //Timestamp leftPrependStamp = left.add(new OpAppend(right.msg));
        left.ops.put(new OpAppend(right.msg), rightPrependStamp);

        // return rightPrependStamp.ops.add(unaryOpCls())
        Timestamp res = rightPrependStamp.add(new OpSHA256());
        return res;
    }

    public static Timestamp catSha256(Timestamp left, Timestamp right) {
        return Merkle.catThenUnaryOp(left, right);
    }

    public static Timestamp catSha256d(Timestamp left, Timestamp right) {
        Timestamp sha256Timestamp = Merkle.catSha256(left, right);
        // res = sha256Timestamp.ops.add(OpSHA256());
        Timestamp res = sha256Timestamp.add(new OpSHA256());
        return res;
    }

    /**
     * Merkelize a set of timestamps.
     * A merkle tree of all the timestamps is built in-place using binop() to
     * timestamp each pair of timestamps. The exact algorithm used is structurally
     * identical to a merkle-mountain-range, although leaf sums aren't committed.
     * As this function is under the consensus-critical core, it's guaranteed that
     * the algorithm will not be changed in the future.
     *
     * @param timestamps a list of timestamps
     * @return the timestamp for the tip of the tree.
     */
    public static Timestamp makeMerkleTree(List<Timestamp> timestamps) {
        List<Timestamp> stamps = timestamps;
        Timestamp prevStamp = null;
        boolean exit = false;

        while (!exit) {
            if (stamps.size() > 0) {
                prevStamp = stamps.get(0);
            }

            List<Timestamp> subStamps = stamps.subList(1, stamps.size());
            List<Timestamp> nextStamps = new ArrayList<>();

            for (Timestamp stamp : subStamps) {
                if (prevStamp == null) {
                    prevStamp = stamp;
                } else {
                    nextStamps.add(Merkle.catSha256(prevStamp, stamp));
                    prevStamp = null;
                }
            }

            if (nextStamps.size() == 0) {
                exit = true;
            } else {
                if (prevStamp != null) {
                    nextStamps.add(prevStamp);
                }

                stamps = nextStamps;
            }
        }

        return prevStamp;
    }
}
