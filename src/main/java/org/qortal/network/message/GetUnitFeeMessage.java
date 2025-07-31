package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.google.common.primitives.Longs;

public class GetUnitFeeMessage extends Message {

	private final String txType;
	private final Long timestamp; // optional

	public GetUnitFeeMessage(String txType, Long timestamp) {
		super(MessageType.GET_UNIT_FEE);

		if (txType == null)
			throw new IllegalArgumentException("txType cannot be null");

		this.txType = txType;
		this.timestamp = timestamp;

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			byte[] txTypeBytes = txType.getBytes(StandardCharsets.UTF_8);
			bytes.write(intToBytes(txTypeBytes.length));
			bytes.write(txTypeBytes);

			if (timestamp != null) {
				bytes.write(Longs.toByteArray(timestamp));
			}
		} catch (IOException e) {
			throw new RuntimeException("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetUnitFeeMessage(int id, String txType, Long timestamp) {
		super(id, MessageType.GET_UNIT_FEE);
		this.txType = txType;
		this.timestamp = timestamp;
	}

	public String txType() {
		return this.txType;
	}

	public Long timestamp() {
		return this.timestamp;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
		int typeLength = byteBuffer.getInt();
		byte[] typeBytes = new byte[typeLength];
		byteBuffer.get(typeBytes);
		String txType = new String(typeBytes, StandardCharsets.UTF_8);

		Long timestamp = null;
		if (byteBuffer.remaining() >= 8) {
			timestamp = byteBuffer.getLong();
		}

		return new GetUnitFeeMessage(id, txType, timestamp);
	}

	private static byte[] intToBytes(int value) {
		return new byte[] {
			(byte)(value >>> 24),
			(byte)(value >>> 16),
			(byte)(value >>> 8),
			(byte)value
		};
	}
}
