package com.vitorpamplona.quartz.nip03Timestamp.ots.crypto;

/**
 * Interface for Memoable objects. Memoable objects allow the taking of a snapshot of their internal state
 * via the {@link #copy copy()} method and then resetting the object back to that state later using the
 * {@link #reset reset()} method.
 */
public interface Memoable {
    /**
     * Produce a copy of this object with its configuration and in its current state.
     * <p>
     * The returned object may be used simply to store the state, or may be used as a similar object
     * starting from the copied state.
     *
     * @return Memoable object
     */
    Memoable copy();

    /**
     * Restore a copied object state into this object.
     * <p>
     * Implementations of this method <em>should</em> try to avoid or minimise memory allocation to perform the reset.
     *
     * @param other an object originally {@link #copy() copied} from an object of the same type as this instance.
     * @throws ClassCastException if the provided object is not of the correct type.
     */
    void reset(Memoable other);
}
