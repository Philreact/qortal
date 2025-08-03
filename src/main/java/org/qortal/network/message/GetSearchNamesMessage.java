package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class GetSearchNamesMessage extends Message {

	private int limit;
	private int offset;
	private boolean reverse;
	private boolean prefix;
	private String query;

	public GetSearchNamesMessage(int limit, int offset, boolean reverse, boolean prefix, String query) {
		super(MessageType.GET_SEARCH_NAMES);

		this.limit = limit;
		this.offset = offset;
		this.reverse = reverse;
		this.prefix = prefix;
		this.query = query;

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(intToBytes(limit));
			bytes.write(intToBytes(offset));
			bytes.write(intToBytes(reverse ? 1 : 0));
			bytes.write(intToBytes(prefix ? 1 : 0));

			// Serialize query string (sized)
			if (query != null) {
				byte[] queryBytes = query.getBytes(StandardCharsets.UTF_8);
				bytes.write(intToBytes(queryBytes.length));
				bytes.write(queryBytes);
			} else {
				bytes.write(intToBytes(0));
			}

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetSearchNamesMessage(int id, int limit, int offset, boolean reverse, boolean prefix, String query) {
		super(id, MessageType.GET_SEARCH_NAMES);

		this.limit = limit;
		this.offset = offset;
		this.reverse = reverse;
		this.prefix = prefix;
		this.query = query;
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

	public boolean isPrefix() {
		return this.prefix;
	}

	public String getQuery() {
		return this.query;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		int limit = bytes.getInt();
		int offset = bytes.getInt();
		boolean reverse = bytes.getInt() == 1;
		boolean prefix = bytes.getInt() == 1;

		// Deserialize query (sized string)
		int queryLength = bytes.getInt();
		String query = null;
		if (queryLength > 0) {
			byte[] queryBytes = new byte[queryLength];
			bytes.get(queryBytes);
			query = new String(queryBytes, StandardCharsets.UTF_8);
		}

		return new GetSearchNamesMessage(id, limit, offset, reverse, prefix, query);
	}

	private byte[] intToBytes(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
	}
}
