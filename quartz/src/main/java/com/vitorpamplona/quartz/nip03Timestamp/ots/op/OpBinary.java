package com.vitorpamplona.quartz.nip03Timestamp.ots.op;

import android.util.Log;

import com.vitorpamplona.quartz.utils.Hex;
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException;

import java.util.Arrays;

/**
 * Operations that act on a message and a single argument.
 *
 * @see OpUnary
 */
public abstract class OpBinary extends Op implements Comparable<Op> {

    public byte[] arg;

    @Override
    public String _TAG_NAME() {
        return "";
    }

    public OpBinary() {
        super();
        this.arg = new byte[]{};
    }

    public OpBinary(byte[] arg_) {
        super();
        this.arg = arg_;
    }

    public static Op deserializeFromTag(StreamDeserializationContext ctx, byte tag)
        throws DeserializationException {
        byte[] arg = ctx.readVarbytes(_MAX_RESULT_LENGTH, 1);

        if (tag == OpAppend._TAG) {
            return new OpAppend(arg);
        } else if (tag == OpPrepend._TAG) {
            return new OpPrepend(arg);
        } else {
            Log.e("OpenTimestamp", "Unknown operation tag: " + tag + " 0x" + String.format("%02x", tag));
            return null;     // TODO: Is this OK? Won't it blow up later? Better to throw?
        }
    }

    @Override
    public void serialize(StreamSerializationContext ctx) {
        super.serialize(ctx);
        ctx.writeVarbytes(this.arg);
    }

    @Override
    public String toString() {
        return this._TAG_NAME() + ' ' + Hex.encode(this.arg).toLowerCase();
    }

    @Override
    public int compareTo(Op o) {
        if (o instanceof OpBinary && this._TAG() == o._TAG()) {
            return Utils.compare(this.arg, ((OpBinary) o).arg);
        }

        return this._TAG() - o._TAG();
    }

    @Override
    public int hashCode() {
        return _TAG ^ Arrays.hashCode(this.arg);
    }
}
