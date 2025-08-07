package org.qortal.network.message;

import com.google.common.primitives.Ints;

import org.qortal.transform.TransformationException;
import org.qortal.transform.Transformer;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.qortal.naming.Name;
import org.qortal.transaction.ArbitraryTransaction;

public class GetArbitraryLatestTransactionMessage extends Message {

	private final int service;
	private final String name;
	private final String identifier; // Nullable

	public GetArbitraryLatestTransactionMessage(int service, String name, String identifier) {
		super(MessageType.GET_ARBITRARY_LATEST_TRANSACTION);

		this.service = service;
		this.name = name;
		this.identifier = identifier;

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			// Write service as int
			bytes.write(Ints.toByteArray(service));

			// Write name with size prefix
			Serialization.serializeSizedStringV2(bytes, name);

			// Write hasIdentifier as int (0 or 1)
			if (identifier != null) {
				bytes.write(Ints.toByteArray(1));
				Serialization.serializeSizedStringV2(bytes, identifier);
			} else {
				bytes.write(Ints.toByteArray(0));
			}

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream", e);
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetArbitraryLatestTransactionMessage(int id, int service, String name, String identifier) {
		super(id, MessageType.GET_ARBITRARY_LATEST_TRANSACTION);
		this.service = service;
		this.name = name;
		this.identifier = identifier;
	}

	public int getService() {
		return service;
	}

	public String getName() {
		return name;
	}

	public String getIdentifier() {
		return identifier;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws MessageException {
		try {
			int service = bytes.getInt();

			String name = Serialization.deserializeSizedStringV2(bytes, Name.MAX_NAME_SIZE);

			int hasIdentifier = bytes.getInt();

			String identifier = null;
			if (hasIdentifier == 1) {
				identifier = Serialization.deserializeSizedStringV2(bytes, ArbitraryTransaction.MAX_IDENTIFIER_LENGTH);
			}

			return new GetArbitraryLatestTransactionMessage(id, service, name, identifier);

		} catch (TransformationException | IllegalArgumentException e) {
			throw new MessageException("Failed to deserialize GetArbitraryLatestTransactionMessage", e);
		}
	}
}
