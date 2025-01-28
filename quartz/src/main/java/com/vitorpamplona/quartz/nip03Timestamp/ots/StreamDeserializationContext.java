package com.vitorpamplona.quartz.nip03Timestamp.ots;

import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException;

import java.util.Arrays;

public class StreamDeserializationContext {

    byte[] buffer;
    int counter = 0;

    public StreamDeserializationContext(byte[] stream) {
        this.buffer = stream;
        this.counter = 0;
    }

    public byte[] getOutput() {
        return this.buffer;
    }

    public int getCounter() {
        return this.counter;
    }

    public byte[] read(int l) {
        if (this.counter == this.buffer.length) {
            return null;
        }

        if (l+this.counter > this.buffer.length) {
            l = this.buffer.length-this.counter;
        }

        // const uint8Array = new Uint8Array(this.buffer,this.counter,l);
        byte[] uint8Array = Arrays.copyOfRange(this.buffer, this.counter, this.counter + l);
        this.counter += l;

        return uint8Array;
    }

    public boolean readBool() {
        byte b = this.read(1)[0];

        if (b == 0xff) {
            return true;
        } else if (b == 0x00) {
            return false;
        }

        return false;
    }

    public int readVaruint() {
        int value = 0;
        byte shift = 0;
        byte b;

        do {
            b = this.read(1)[0];
            value |= (b & 0b01111111) << shift;
            shift += 7;
        } while ((b & 0b10000000) != 0b00000000);

        return value;
    }

    public byte[] readBytes(int expectedLength) throws DeserializationException {


        if (expectedLength == 0) {
            return this.readVarbytes(1024, 0);
        }

        return this.read(expectedLength);
    }

    public byte[] readVarbytes(int maxLen) throws DeserializationException {
        return readVarbytes(maxLen, 0);
    }

    public byte[] readVarbytes(int maxLen, int minLen) throws DeserializationException {
        int l = this.readVaruint();

        if (l  > maxLen) {
            throw new DeserializationException("varbytes max length exceeded;");
        } else if (l < minLen) {
            throw new DeserializationException("varbytes min length not met;");
        }

        return this.read(l);
    }

    public boolean assertMagic(byte[] expectedMagic) {
        byte[] actualMagic = this.read(expectedMagic.length);

        return Arrays.equals(expectedMagic, actualMagic);
    }

    public boolean assertEof() {
        byte[] excess = this.read(1);
        return excess != null;
    }

    public String toString() {
        return Arrays.toString(this.buffer);
    }
}
