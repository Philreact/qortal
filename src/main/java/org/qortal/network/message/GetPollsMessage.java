package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class GetPollsMessage extends Message {

	private int limit;
	private int offset;
	private boolean reverse;

	public GetPollsMessage(int limit, int offset, boolean reverse) {
		super(MessageType.GET_POLLS);

		this.limit = limit;
		this.offset = offset;
		this.reverse = reverse;

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(intToBytes(limit));
			bytes.write(intToBytes(offset));
			bytes.write(intToBytes(reverse ? 1 : 0));
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetPollsMessage(int id, int limit, int offset, boolean reverse) {
		super(id, MessageType.GET_POLLS);

		this.limit = limit;
		this.offset = offset;
		this.reverse = reverse;
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

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		int limit = bytes.getInt();
		int offset = bytes.getInt();
		boolean reverse = bytes.getInt() == 1;

		return new GetPollsMessage(id, limit, offset, reverse);
	}

	private byte[] intToBytes(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
	}
}
