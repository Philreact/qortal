package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class GetLastBlockHeightMessage extends Message {

	private boolean includeOnlineSignatures;


	public GetLastBlockHeightMessage(boolean includeOnlineSignatures) {
		super(MessageType.GET_LAST_BLOCK_HEIGHT);

		this.includeOnlineSignatures = includeOnlineSignatures;


		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(intToBytes(includeOnlineSignatures ? 1 : 0));

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetLastBlockHeightMessage(int id,  boolean includeOnlineSignatures) {
		super(id, MessageType.GET_LAST_BLOCK_HEIGHT);


		this.includeOnlineSignatures = includeOnlineSignatures;

	}



	public boolean isIncludeOnlineSignatures() {
		return this.includeOnlineSignatures;
	}



	public static Message fromByteBuffer(int id, ByteBuffer bytes) {

		boolean includeOnlineSignatures = bytes.getInt() == 1;


		return new GetLastBlockHeightMessage(id, includeOnlineSignatures);
	}

	private byte[] intToBytes(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
	}
}
