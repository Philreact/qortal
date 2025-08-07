package org.qortal.network.message;

import java.nio.ByteBuffer;

public class GetSupplyMessage extends Message {

	public GetSupplyMessage() {
		super(MessageType.GET_SUPPLY);

		this.dataBytes = EMPTY_DATA_BYTES;
	}

	private GetSupplyMessage(int id) {
		super(id, MessageType.GET_SUPPLY);
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		return new GetSupplyMessage(id);
	}

}
