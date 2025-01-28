package com.vitorpamplona.quartz.nip03Timestamp.ots.op;

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException;

import java.util.Arrays;

/**
 * Append a suffix to a message.
 *
 * @see OpBinary
 */
public class OpAppend extends OpBinary {
    byte[] arg;

    public static byte _TAG = (byte) 0xf0;

    @Override
    public byte _TAG() {
        return OpAppend._TAG;
    }

    @Override
    public String _TAG_NAME() {
        return "append";
    }

    public OpAppend() {
        super();
        this.arg = new byte[]{};
    }

    public OpAppend(byte[] arg_) {
        super(arg_);
        this.arg = arg_;
    }

    @Override
    public byte[] call(byte[] msg) {
        return Utils.arraysConcat(msg, this.arg);
    }

    public static Op deserializeFromTag(StreamDeserializationContext ctx, byte tag)
        throws DeserializationException {
        return OpBinary.deserializeFromTag(ctx, tag);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OpAppend)) {
            return false;
        }

        return Arrays.equals(this.arg, ((OpAppend) obj).arg);
    }

    @Override
    public int hashCode() {
        return _TAG ^ Arrays.hashCode(this.arg);
    }
}
