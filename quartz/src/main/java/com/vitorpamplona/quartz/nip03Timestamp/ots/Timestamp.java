package com.vitorpamplona.quartz.nip03Timestamp.ots;

import com.vitorpamplona.quartz.crypto.Hex;
import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.BitcoinBlockHeaderAttestation;
import com.vitorpamplona.quartz.nip03Timestamp.ots.attestation.TimeAttestation;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.Op;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpBinary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 * Proof that one or more attestations commit to a message.
 * The proof is in the form of a tree, with each node being a message, and the
 * edges being operations acting on those messages. The leafs of the tree are
 * attestations that attest to the time that messages in the tree existed prior.
 */
public class Timestamp {

    public byte[] msg;
    public List<TimeAttestation> attestations;
    public HashMap<Op, Timestamp> ops;

    /**
     * Create a com.vitorpamplona.quartz.ots.Timestamp object.
     *
     * @param msg - Desc
     */
    public Timestamp(byte[] msg) {
        this.msg = msg;
        this.attestations = new ArrayList<>();
        this.ops = new HashMap<>();
    }

    /**
     * Deserialize a Timestamp.
     *
     * @param ots        - The serialized byte array.
     * @param initialMsg - The initial message.
     * @return The deserialized Timestamp.
     */
    public static Timestamp deserialize(byte[] ots, byte[] initialMsg) throws DeserializationException {
        StreamDeserializationContext ctx = new StreamDeserializationContext(ots);

        return Timestamp.deserialize(ctx, initialMsg);
    }

    /**
     * Deserialize a Timestamp.
     * Because the serialization format doesn't include the message that the
     * timestamp operates on, you have to provide it so that the correct
     * operation results can be calculated.
     * The message you provide is assumed to be correct; if it causes a op to
     * raise MsgValueError when the results are being calculated (done
     * immediately, not lazily) DeserializationError is raised instead.
     *
     * @param ctx        - The stream deserialization context.
     * @param initialMsg - The initial message.
     * @return The deserialized Timestamp.
     */
    public static Timestamp deserialize(StreamDeserializationContext ctx, byte[] initialMsg)
        throws DeserializationException {
        Timestamp self = new Timestamp(initialMsg);
        byte tag = ctx.readBytes(1)[0];

        while ((tag & 0xff) == 0xff) {
            byte current = ctx.readBytes(1)[0];
            doTagOrAttestation(self, ctx, current, initialMsg);
            tag = ctx.readBytes(1)[0];
        }

        doTagOrAttestation(self, ctx, tag, initialMsg);

        return self;
    }

    private static void doTagOrAttestation(Timestamp self, StreamDeserializationContext ctx, byte tag, byte[] initialMsg)
        throws DeserializationException {
        if ((tag & 0xff) == 0x00) {
            TimeAttestation attestation = TimeAttestation.deserialize(ctx);
            self.attestations.add(attestation);
        } else {
            Op op = Op.deserializeFromTag(ctx, tag);
            byte[] result = op.call(initialMsg);

            Timestamp stamp = Timestamp.deserialize(ctx, result);
            self.ops.put(op, stamp);
        }
    }

    /**
     * Create a Serialize object.
     *
     * @return The byte array of the serialized timestamp
     */
    public byte[] serialize() {
        StreamSerializationContext ctx = new StreamSerializationContext();
        serialize(ctx);

        return ctx.getOutput();
    }

    /**
     * Create a Serialize object.
     *
     * @param ctx - The stream serialization context.
     */
    public void serialize(StreamSerializationContext ctx) {
        List<TimeAttestation> sortedAttestations = this.attestations;   // TODO: Hm, this is just a reference copy...
        Collections.sort(sortedAttestations);

        if (sortedAttestations.size() > 1) {
            for (int i = 0; i < sortedAttestations.size() - 1; i++) {
                ctx.writeBytes(new byte[]{(byte) 0xff, (byte) 0x00});
                sortedAttestations.get(i).serialize(ctx);
            }
        }

        if (this.ops.isEmpty()) {
            ctx.writeByte((byte) 0x00);

            if (!sortedAttestations.isEmpty()) {
                sortedAttestations.get(sortedAttestations.size() - 1).serialize(ctx);
            }
        } else if (!this.ops.isEmpty()) {
            if (!sortedAttestations.isEmpty()) {
                ctx.writeBytes(new byte[]{(byte) 0xff, (byte) 0x00});
                sortedAttestations.get(sortedAttestations.size() - 1).serialize(ctx);
            }

            int counter = 0;
            List<Map.Entry<Op, Timestamp>> list = sortToList(this.ops.entrySet());

            for (Map.Entry<Op, Timestamp> entry : list) {
                Timestamp stamp = entry.getValue();
                Op op = entry.getKey();

                if (counter < this.ops.size() - 1) {
                    ctx.writeBytes(new byte[]{(byte) 0xff});
                    counter++;
                }

                op.serialize(ctx);
                stamp.serialize(ctx);
            }
        }
    }

    /**
     * Add all operations and attestations from another timestamp to this one.
     *
     * @param other - Initial other com.vitorpamplona.quartz.ots.Timestamp to merge.
     * @throws Exception different timestamps messages
     */
    public void merge(Timestamp other) throws Exception {
        if (!Arrays.equals(this.msg, other.msg)) {
            //Log.e("OpenTimestamp", "Can\'t merge timestamps for different messages together");
            throw new Exception("Can\'t merge timestamps for different messages together");
        }

        for (final TimeAttestation attestation : other.attestations) {
            this.attestations.add(attestation);
        }

        for (Map.Entry<Op, Timestamp> entry : other.ops.entrySet()) {
            Timestamp otherOpStamp = entry.getValue();
            Op otherOp = entry.getKey();

            Timestamp ourOpStamp = this.ops.get(otherOp);

            if (ourOpStamp == null) {
                ourOpStamp = new Timestamp(otherOp.call(this.msg));
                this.ops.put(otherOp, ourOpStamp);
            }

            ourOpStamp.merge(otherOpStamp);
        }
    }

    /**
     * Shrink Timestamp.
     * Remove useless pending attestions if exist a full bitcoin attestation.
     *
     * @return TimeAttestation - the minimal attestation.
     * @throws Exception no attestion founds.
     */
    public TimeAttestation shrink() throws Exception {
        // Get all attestations
        HashMap<byte[], TimeAttestation> allAttestations = this.allAttestations();

        if (allAttestations.size() == 0) {
            throw new Exception();
        } else if (allAttestations.size() == 1) {
            return allAttestations.values().iterator().next();
        } else if (this.ops.size() == 0) {
            throw new Exception();     // TODO: Need a descriptive exception string here
        }

        // Fore >1 attestations :
        // Search first BitcoinBlockHeaderAttestation
        TimeAttestation minAttestation = null;

        for (Map.Entry<Op, Timestamp> entry : this.ops.entrySet()) {
            Timestamp timestamp = entry.getValue();
            //Op op = entry.getKey();

            for (TimeAttestation attestation : timestamp.getAttestations()) {
                if (attestation instanceof BitcoinBlockHeaderAttestation) {
                    if (minAttestation == null) {
                        minAttestation = attestation;
                    } else {
                        if (minAttestation instanceof BitcoinBlockHeaderAttestation
                                && attestation instanceof BitcoinBlockHeaderAttestation
                                && ((BitcoinBlockHeaderAttestation) minAttestation).getHeight()
                                > ((BitcoinBlockHeaderAttestation) attestation).getHeight()) {
                            minAttestation = attestation;
                        }
                    }
                }
            }
        }

        // Only pending attestations : return the first
        if (minAttestation == null) {
            return allAttestations.values().iterator().next();
        }

        // Remove attestation if not min attestation
        boolean shrinked = false;

        for (Iterator<Entry<Op, Timestamp>> it = this.ops.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Op, Timestamp> entry = it.next();
            Timestamp timestamp = entry.getValue();
            Op op = entry.getKey();
            Set<TimeAttestation> attestations = timestamp.getAttestations();

            if (attestations.size() > 0 && attestations.contains(minAttestation) && shrinked == false) {
                timestamp.shrink();
                shrinked = true;
            } else {
                it.remove();
            }
        }

        return minAttestation;
    }

    /**
     * Return the digest of the timestamp.
     *
     * @return The byte[] digest string.
     */
    public byte[] getDigest() {
        return this.msg;
    }

    /**
     * Return as memory hierarchical object.
     *
     * @param indent - Initial hierarchical indention.
     * @return The output string.
     */
    public String toString(int indent) {
        StringBuilder builder = new StringBuilder();
        builder.append(Timestamp.indention(indent) + "msg: " + Hex.encode(this.msg) + "\n");
        builder.append(Timestamp.indention(indent) + this.attestations.size() + " attestations: \n");
        int i = 0;

        for (final TimeAttestation attestation : this.attestations) {
            builder.append(Timestamp.indention(indent) + "[" + i + "] " + attestation.toString() + "\n");
            i++;
        }

        i = 0;
        builder.append(Timestamp.indention(indent) + this.ops.size() + " ops: \n");

        for (Map.Entry<Op, Timestamp> entry : this.ops.entrySet()) {
            Timestamp stamp = entry.getValue();
            Op op = entry.getKey();

            builder.append(Timestamp.indention(indent) + "[" + i + "] op: " + op.toString() + "\n");
            builder.append(Timestamp.indention(indent) + "[" + i + "] timestamp: \n");
            builder.append(stamp.toString(indent + 1));
            i++;
        }

        builder.append('\n');

        return builder.toString();
    }

    /**
     * Indention function for printing tree.
     *
     * @param pos - Initial hierarchical indention.
     * @return The output space string.
     */
    public static String indention(int pos) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < pos; i++) {
            builder.append("    ");
        }

        return builder.toString();
    }

    public String strTree(int indent) {
        return strTree(indent, false);
    }

    private String strResult(boolean verbosity, byte[] parameter, byte[] result) {
        final String ANSI_HEADER = "\u001B[95m";
        final String ANSI_OKBLUE = "\u001B[94m";
        final String ANSI_OKGREEN = "\u001B[92m";
        final String ANSI_WARNING = "\u001B[93m";
        final String ANSI_FAIL = "\u001B[91m";
        final String ANSI_ENDC = "\u001B[0m";
        final String ANSI_BOLD = "\u001B[1m";
        final String ANSI_UNDERLINE = "\u001B[4m";

        String rr = "";

        if (verbosity == true && result != null) {
            rr += " == ";
            String resultHex = Utils.bytesToHex(result);

            if (parameter == null) {
                rr += resultHex;
            } else {
                String parameterHex = Utils.bytesToHex(parameter);

                try {
                    int index = resultHex.indexOf(parameterHex);
                    String parameterHexHighlight = ANSI_BOLD + parameterHex + ANSI_ENDC;

                    if (index == 0) {
                        rr += parameterHexHighlight + resultHex.substring(index + parameterHex.length(), resultHex.length());
                    } else {
                        rr += resultHex.substring(0, index) + parameterHexHighlight;
                    }
                } catch (Exception err) {
                    rr += resultHex;
                }
            }
        }

        return rr;
    }

    /**
     * Return as tree hierarchical object.
     *
     * @param indent    - Initial hierarchical indention.
     * @param verbosity - Verbose option.
     * @return The output string.
     */
    public String strTree(int indent, boolean verbosity) {
        StringBuilder builder = new StringBuilder();

        if (!this.attestations.isEmpty()) {
            for (final TimeAttestation attestation : this.attestations) {
                builder.append(Timestamp.indention(indent));
                builder.append("verify " + attestation.toString() + strResult(verbosity, this.msg, null) + "\n");

                if (attestation instanceof BitcoinBlockHeaderAttestation) {
                    String tx = Utils.bytesToHex(Utils.arrayReverse(this.msg));
                    builder.append(Timestamp.indention(indent) + "# Bitcoin block merkle root " + tx.toLowerCase() + "\n");
                }
            }
        }

        if (this.ops.size() > 1) {
            TreeMap<Op, Timestamp> ordered = new TreeMap<>(this.ops);

            for (Map.Entry<Op, Timestamp> entry : ordered.entrySet()) {
                Timestamp timestamp = entry.getValue();
                Op op = entry.getKey();

                byte[] curRes = op.call(this.msg);
                byte[] curPar = null;

                if (op instanceof OpBinary) {
                    curPar = ((OpBinary) op).arg;
                }

                builder.append(Timestamp.indention(indent) + " -> " + op.toString().toLowerCase() + strResult(verbosity, curPar, curRes).toLowerCase() + "\n");
                builder.append(timestamp.strTree(indent + 1, verbosity));
            }
        } else if (this.ops.size() > 0) {
            // output += com.eternitywall.ots.Timestamp.indention(indent);
            for (Map.Entry<Op, Timestamp> entry : this.ops.entrySet()) {
                Timestamp timestamp = entry.getValue();
                Op op = entry.getKey();

                byte[] curRes = op.call(this.msg);
                byte[] curPar = null;

                if (op instanceof OpBinary) {
                    curPar = ((OpBinary) op).arg;
                }

                builder.append(Timestamp.indention(indent) + op.toString().toLowerCase() + strResult(verbosity, curPar, curRes).toLowerCase() + "\n");
                builder.append(timestamp.strTree(indent, verbosity));
            }
        }

        return builder.toString();
    }

    /**
     * Returns a list of all sub timestamps with attestations.
     *
     * @return List of all sub timestamps with attestations.
     */
    public List<Timestamp> directlyVerified() {
        if (!this.attestations.isEmpty()) {
            List<Timestamp> list = new ArrayList<>();
            list.add(this);
            return list;
        }

        List<Timestamp> list = new ArrayList<>();

        for (Map.Entry<Op, Timestamp> entry : this.ops.entrySet()) {
            Timestamp ts = entry.getValue();
            //Op op = entry.getKey();

            List<Timestamp> result = ts.directlyVerified();
            list.addAll(result);
        }

        return list;
    }

    /**
     * Returns a set of all Attestations.
     *
     * @return Set of all timestamp attestations.
     */
    public Set<TimeAttestation> getAttestations() {
        Set set = new HashSet<TimeAttestation>();

        for (Map.Entry<byte[], TimeAttestation> item : this.allAttestations().entrySet()) {
            //byte[] msg = item.getKey();
            TimeAttestation attestation = item.getValue();
            set.add(attestation);
        }

        return set;
    }

    /**
     * Determine if timestamp is complete and can be verified.
     *
     * @return True if the timestamp is complete, False otherwise.
     */
    public Boolean isTimestampComplete() {
        for (Map.Entry<byte[], TimeAttestation> item : this.allAttestations().entrySet()) {
            //byte[] msg = item.getKey();
            TimeAttestation attestation = item.getValue();

            if (attestation instanceof BitcoinBlockHeaderAttestation) {
                return true;
            }
        }

        return false;
    }

    /**
     * Iterate over all attestations recursively
     *
     * @return Returns iterable of (msg, attestation)
     */
    public HashMap<byte[], TimeAttestation> allAttestations() {
        HashMap<byte[], TimeAttestation> map = new HashMap<>();

        for (TimeAttestation attestation : this.attestations) {
            map.put(this.msg, attestation);
        }

        for (Map.Entry<Op, Timestamp> entry : this.ops.entrySet()) {
            Timestamp ts = entry.getValue();
            //Op op = entry.getKey();

            HashMap<byte[], TimeAttestation> subMap = ts.allAttestations();

            for (Map.Entry<byte[], TimeAttestation> item : subMap.entrySet()) {
                byte[] msg = item.getKey();
                TimeAttestation attestation = item.getValue();
                map.put(msg, attestation);
            }
        }

        return map;
    }

    /**
     * Iterate over all tips recursively
     *
     * @return Returns iterable of (msg, attestation)
     */
    public Set<byte[]> allTips() {
        Set<byte[]> set = new HashSet<>();

        if (this.ops.size() == 0) {
            set.add(this.msg);
        }

        for (Map.Entry<Op, Timestamp> entry : this.ops.entrySet()) {
            Timestamp ts = entry.getValue();
            //Op op = entry.getKey();

            Set<byte[]> subSet = ts.allTips();

            for (byte[] msg : subSet) {
                set.add(msg);
            }
        }

        return set;
    }

    /**
     * Compare timestamps.
     *
     * @param timestamp the timestamp to compare with
     * @return Returns true if timestamps are equals
     */
    public boolean equals(Timestamp timestamp) {
        if (Arrays.equals(this.getDigest(), timestamp.getDigest()) == false) {
            return false;
        }

        // Check attestations
        if (this.attestations.size() != timestamp.attestations.size()) {
            return false;
        }

        for (int i = 0; i < this.attestations.size(); i++) {
            TimeAttestation ta1 = this.attestations.get(i);
            TimeAttestation ta2 = timestamp.attestations.get(i);

            if (!(ta1.equals(ta2))) {
                return false;
            }
        }

        // Check operations
        if (this.ops.size() != timestamp.ops.size()) {
            return false;
        }

        // Order list of operations
        List<Map.Entry<Op, Timestamp>> list1 = sortToList(this.ops.entrySet());
        List<Map.Entry<Op, Timestamp>> list2 = sortToList(timestamp.ops.entrySet());

        for (int i = 0; i < list1.size(); i++) {
            Op op1 = list1.get(i).getKey();
            Op op2 = list2.get(i).getKey();

            if (!op1.equals(op2)) {
                return false;
            }

            Timestamp t1 = list1.get(i).getValue();
            Timestamp t2 = list2.get(i).getValue();

            if (!t1.equals(t2)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Add Op to current timestamp and return the sub stamp
     *
     * @param op - The operation to insert
     * @return Returns the sub timestamp
     */
    public Timestamp add(Op op) {
        // nonce_appended_stamp = timestamp.ops.add(com.vitorpamplona.quartz.ots.op.OpAppend(os.urandom(16)))
        //Op opAppend = new OpAppend(bytes);

        if (this.ops.containsKey(op)) {
            return this.ops.get(op);
        }

        Timestamp stamp = new Timestamp(op.call(this.msg));
        this.ops.put(op, stamp);

        return stamp;
    }

    /**
     * Retrieve a sorted list of all map entries.
     *
     * @param setEntries - The entries set of ops hashmap
     * @return Returns the sorted list of map entries
     */
    public List<Map.Entry<Op, Timestamp>> sortToList(Set<Entry<Op, Timestamp>> setEntries) {
        List<Map.Entry<Op, Timestamp>> entries = new ArrayList<>(setEntries);
        Collections.sort(entries, new Comparator<Map.Entry<Op, Timestamp>>() {
            @Override
            public int compare(Entry<Op, Timestamp> a, Entry<Op, Timestamp> b) {
                return a.getKey().compareTo(b.getKey());
            }
        });

        return entries;
    }
}
