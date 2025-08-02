package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

public class GetAddressGroupInvitesMessage extends Message {

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;

	private String address;

	public GetAddressGroupInvitesMessage(String address) {
		super(MessageType.GET_ADDRESS_GROUP_INVITES);

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

	private GetAddressGroupInvitesMessage(int id, String address) {
		super(id, MessageType.GET_ADDRESS_GROUP_INVITES);

		this.address = address;

	}

	public String getAddress() {
		return this.address;
	}



	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		byte[] addressBytes = new byte[ADDRESS_LENGTH];
		bytes.get(addressBytes);
		String address = Base58.encode(addressBytes);

		return new GetAddressGroupInvitesMessage(id, address);
	}

}
