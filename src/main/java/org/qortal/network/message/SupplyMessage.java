package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.google.common.primitives.Longs;

public class SupplyMessage extends Message {

    private long supply;

    public SupplyMessage(long supply) {
        super(MessageType.SUPPLY);

        this.supply = supply;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try {
            // Just write one long (8 bytes)
            bytes.write(Longs.toByteArray(supply));
        } catch (IOException e) {
            throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
        }

        this.dataBytes = bytes.toByteArray();
        this.checksumBytes = Message.generateChecksum(this.dataBytes);
    }

    public SupplyMessage(int id, long supply) {
        super(id, MessageType.SUPPLY);
        this.supply = supply;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try {
            bytes.write(Longs.toByteArray(supply));
        } catch (IOException e) {
            throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
        }

        this.dataBytes = bytes.toByteArray();
        this.checksumBytes = Message.generateChecksum(this.dataBytes);
    }

    public long getSupply() {
        return this.supply;
    }

    public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
        long supply = byteBuffer.getLong();
        return new SupplyMessage(id, supply);
    }

    public SupplyMessage cloneWithNewId(int newId) {
        SupplyMessage clone = new SupplyMessage(this.supply);
        clone.setId(newId);
        return clone;
    }
}
