package com.vitorpamplona.quartz.nip03Timestamp.ots.op;

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException;

import java.util.Arrays;

/**
 * Prepend a prefix to a message.
 *
 * @see OpBinary
 */
public class OpPrepend extends OpBinary {

    byte[] arg;

    public static byte _TAG = (byte) 0xf1;

    @Override
    public byte _TAG() {
        return OpPrepend._TAG;
    }

    @Override
    public String _TAG_NAME() {
        return "prepend";
    }

    public OpPrepend() {
        super();
        this.arg = new byte[]{};
    }

    public OpPrepend(byte[] arg_) {
        super(arg_);
        this.arg = arg_;
    }

    @Override
    public byte[] call(byte[] msg) {
        return Utils.arraysConcat(this.arg, msg);
    }

    public static Op deserializeFromTag(StreamDeserializationContext ctx, byte tag)
        throws DeserializationException {
        return OpBinary.deserializeFromTag(ctx, tag);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OpPrepend)) {
            return false;
        }

        return Arrays.equals(this.arg, ((OpPrepend) obj).arg);
    }

    @Override
    public int hashCode() {
        return _TAG ^ Arrays.hashCode(this.arg);
    }
}
