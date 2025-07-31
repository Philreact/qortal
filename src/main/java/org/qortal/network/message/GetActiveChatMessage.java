package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.qortal.data.chat.ChatMessage.Encoding;
import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

public class GetActiveChatMessage extends Message {

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;

	private final String address;
	private final Encoding encoding;
	private final Boolean hasChatReference;

	public GetActiveChatMessage(String address, Encoding encoding, Boolean hasChatReference) {
		super(MessageType.GET_ACTIVE_CHAT);

		this.address = address;
		this.encoding = encoding;
		this.hasChatReference = hasChatReference;

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			// Decode base58 address and write raw bytes
			byte[] addressBytes = Base58.decode(address);
			if (addressBytes.length != ADDRESS_LENGTH)
				throw new IllegalArgumentException("Invalid address length");

			bytes.write(addressBytes);
			bytes.write((byte) encoding.ordinal()); // Enum to byte
			bytes.write((byte) (hasChatReference ? 1 : 0)); // Boolean to byte
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetActiveChatMessage(int id, String address, Encoding encoding, Boolean hasChatReference) {
		super(id, MessageType.GET_ACTIVE_CHAT);

		this.address = address;
		this.encoding = encoding;
		this.hasChatReference = hasChatReference;
	}

	public String getAddress() {
		return this.address;
	}

	public Encoding getEncoding() {
		return this.encoding;
	}

	public Boolean getHasChatReference() {
		return this.hasChatReference;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		// Read 25-byte address
		byte[] addressBytes = new byte[ADDRESS_LENGTH];
		bytes.get(addressBytes);
		String address = Base58.encode(addressBytes);

		// Read 1-byte encoding enum
		byte encodingByte = bytes.get();
		Encoding encoding = Encoding.values()[encodingByte];

		// Read 1-byte boolean
		byte chatRefByte = bytes.get();
		Boolean hasChatReference = chatRefByte != 0;

		return new GetActiveChatMessage(id, address, encoding, hasChatReference);
	}
}
