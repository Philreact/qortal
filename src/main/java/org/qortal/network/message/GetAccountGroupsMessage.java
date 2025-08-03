package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

public class GetAccountGroupsMessage extends Message {

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;

	private String address;

	public GetAccountGroupsMessage(String address) {
		super(MessageType.GET_ACCOUNT_GROUPS);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			// Send raw address instead of base58 encoded
			byte[] addressBytes = Base58.decode(address);
			bytes.write(addressBytes);

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetAccountGroupsMessage(int id, String address) {
		super(id, MessageType.GET_ACCOUNT_GROUPS);

		this.address = address;

	}

	public String getAddress() {
		return this.address;
	}



	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		byte[] addressBytes = new byte[ADDRESS_LENGTH];
		bytes.get(addressBytes);
		String address = Base58.encode(addressBytes);

		return new GetAccountGroupsMessage(id, address);
	}

}
