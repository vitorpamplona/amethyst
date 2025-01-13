package com.vitorpamplona.quartz.nip03Timestamp.ots.op;

import android.util.Log;

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException;

/**
 * Operations are the edges in the timestamp tree, with each operation taking a message and zero or more arguments to produce a result.
 */
public abstract class Op implements Comparable<Op> {

    /**
     * Maximum length of an com.vitorpamplona.quartz.ots.op.Op result
     * <p>
     * For a verifier, this limit is what limits the maximum amount of memory you
     * need at any one time to verify a particular timestamp path; while verifying
     * a particular commitment operation path previously calculated results can be
     * discarded.
     * <p>
     * Of course, if everything was a merkle tree you never need to append/prepend
     * anything near 4KiB of data; 64 bytes would be plenty even with SHA512. The
     * main need for this is compatibility with existing systems like Bitcoin
     * timestamps and Certificate Transparency servers. While the pathological
     * limits required by both are quite large - 1MB and 16MiB respectively - 4KiB
     * is perfectly adequate in both cases for more reasonable usage.
     * <p>
     * @see Op subclasses should set this limit even lower if doing so is appropriate
     * for them.
     */
    public static int _MAX_RESULT_LENGTH = 4096;

    /**
     * Maximum length of the message an com.vitorpamplona.quartz.ots.op.Op can be applied too.
     * <p>
     * Similar to the result length limit, this limit gives implementations a sane
     * constraint to work with; the maximum result-length limit implicitly
     * constrains maximum message length anyway.
     * <p>
     * com.vitorpamplona.quartz.ots.op.Op subclasses should set this limit even lower if doing so is appropriate
     * for them.
     */
    public static int _MAX_MSG_LENGTH = 4096;

    public static byte _TAG = (byte) 0x00;

    public String _TAG_NAME() {
        return "";
    }

    public byte _TAG() {
        return Op._TAG;
    }

    /**
     * Deserialize operation from a buffer.
     *
     * @param ctx The stream deserialization context.
     * @return The subclass Operation.
     */
    public static Op deserialize(StreamDeserializationContext ctx) throws DeserializationException {
        byte tag = ctx.readBytes(1)[0];

        return Op.deserializeFromTag(ctx, tag);
    }

    /**
     * Deserialize operation from a buffer.
     *
     * @param ctx The stream deserialization context.
     * @param tag The tag of the operation.
     * @return The subclass Operation.
     */
    public static Op deserializeFromTag(StreamDeserializationContext ctx, byte tag)
        throws DeserializationException {
        if (tag == OpAppend._TAG) {
            return OpAppend.deserializeFromTag(ctx, tag);
        } else if (tag == OpPrepend._TAG) {
            return OpPrepend.deserializeFromTag(ctx, tag);
        } else if (tag == OpSHA1._TAG) {
            return OpSHA1.deserializeFromTag(ctx, tag);
        } else if (tag == OpSHA256._TAG) {
            return OpSHA256.deserializeFromTag(ctx, tag);
        } else if (tag == OpRIPEMD160._TAG) {
            return OpRIPEMD160.deserializeFromTag(ctx, tag);
        } else if (tag == OpKECCAK256._TAG) {
            return OpKECCAK256.deserializeFromTag(ctx, tag);
        } else {
            Log.e("OpenTimestamp", "Unknown operation tag: " + tag + " 0x" + String.format("%02x", tag));
            return null;     // TODO: Is this OK? Won't it blow up later? Better to throw?
        }
    }

    /**
     * Serialize operation.
     *
     * @param ctx The stream serialization context.
     */
    public void serialize(StreamSerializationContext ctx) {
        if (this._TAG() == 0x00) {
            Log.e("OpenTimestamp", "No valid serialized Op");
            // TODO: Is it OK to just log and carry on? Won't it blow up later? Better to throw?
        }

        ctx.writeByte(this._TAG());
    }

    /**
     * Apply the operation to a message.
     * Raises MsgValueError if the message value is invalid, such as it being
     * too long, or it causing the result to be too long.
     *
     * @param msg The message.
     * @return the msg after the operation has been applied
     */
    public byte[] call(byte[] msg) {
        if (msg.length > _MAX_MSG_LENGTH) {
            Log.e("OpenTimestamp", "Error : Message too long;");
            return new byte[]{};     // TODO: Is this OK? Won't it blow up later? Better to throw?
        }

        byte[] r = this.call(msg);

        if (r.length > _MAX_RESULT_LENGTH) {
            Log.e("OpenTimestamp", "Error : Result too long;");
            // TODO: Is it OK to just log and carry on? Won't it blow up later? Better to throw?
        }

        return r;
    }

    @Override
    public int compareTo(Op o) {
        return this._TAG() - o._TAG();
    }
}
