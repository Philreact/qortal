package org.qortal.network.message;

import java.nio.ByteBuffer;

import com.google.common.primitives.Longs;

public class UnitFeeMessage extends Message {

	private final long unitFee;

	public UnitFeeMessage(long unitFee) {
		super(MessageType.UNIT_FEE);

		this.unitFee = unitFee;

		this.dataBytes = Longs.toByteArray(unitFee);
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public UnitFeeMessage(int id, long unitFee) {
		super(id, MessageType.UNIT_FEE);

		this.unitFee = unitFee;

		this.dataBytes = Longs.toByteArray(unitFee);
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public long getUnitFee() {
		return this.unitFee;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
		long unitFee = byteBuffer.getLong();
		return new UnitFeeMessage(id, unitFee);
	}

	public UnitFeeMessage cloneWithNewId(int newId) {
		UnitFeeMessage clone = new UnitFeeMessage(this.unitFee);
		clone.setId(newId);
		return clone;
	}
}
