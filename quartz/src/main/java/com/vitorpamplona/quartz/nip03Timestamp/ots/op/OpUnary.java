package com.vitorpamplona.quartz.nip03Timestamp.ots.op;

import android.util.Log;

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;

/**
 * Operations that act on a single message.
 *
 * @see Op
 */
public abstract class OpUnary extends Op {

    @Override
    public String _TAG_NAME() {
        return "";
    }

    public OpUnary() {
        super();
    }

    public static Op deserializeFromTag(StreamDeserializationContext ctx, byte tag) {
        if (tag == OpSHA1._TAG) {
            return new OpSHA1();
        } else if (tag == OpSHA256._TAG) {
            return new OpSHA256();
        } else if (tag == OpRIPEMD160._TAG) {
            return new OpRIPEMD160();
        } else if (tag == OpKECCAK256._TAG) {
            return new OpKECCAK256();
        } else {
            Log.e("OpenTimestamp", "Unknown operation tag: " + tag);

            return null;     // TODO: Is this OK? Won't it blow up later? Better to throw?
        }
    }

    @Override
    public String toString() {
        return this._TAG_NAME();
    }
}
