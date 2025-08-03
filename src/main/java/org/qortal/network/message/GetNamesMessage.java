package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class GetNamesMessage extends Message {

	private int limit;
	private int offset;
	private boolean reverse;
	private Long after; // nullable timestamp in milliseconds

	public GetNamesMessage(int limit, int offset, boolean reverse, Long after) {
		super(MessageType.GET_NAMES);

		this.limit = limit;
		this.offset = offset;
		this.reverse = reverse;
		this.after = after;

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(intToBytes(limit));
			bytes.write(intToBytes(offset));
			bytes.write(intToBytes(reverse ? 1 : 0));

			// after presence flag and value (nullable long)
			if (after != null) {
				bytes.write(intToBytes(1)); // hasAfter = true
				bytes.write(longToBytes(after));
			} else {
				bytes.write(intToBytes(0)); // hasAfter = false
			}

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetNamesMessage(int id, int limit, int offset, boolean reverse, Long after) {
		super(id, MessageType.GET_NAMES);

		this.limit = limit;
		this.offset = offset;
		this.reverse = reverse;
		this.after = after;
	}

	public int getLimit() {
		return this.limit;
	}

	public int getOffset() {
		return this.offset;
	}

	public boolean isReverse() {
		return this.reverse;
	}

	public Long getAfter() {
		return this.after;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		int limit = bytes.getInt();
		int offset = bytes.getInt();
		boolean reverse = bytes.getInt() == 1;

		Long after = null;
		int hasAfter = bytes.getInt();
		if (hasAfter == 1) {
			after = bytes.getLong();
		}

		return new GetNamesMessage(id, limit, offset, reverse, after);
	}

	private byte[] intToBytes(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
	}

	private byte[] longToBytes(long value) {
		return ByteBuffer.allocate(8).putLong(value).array();
	}
}
