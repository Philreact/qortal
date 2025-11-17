package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.transform.Transformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class GetBlocksMessage extends Message {

	private final List<byte[]> signatures;

	public GetBlocksMessage(List<byte[]> signatures) throws MessageException {
		super(MessageType.GET_BLOCKS);

		if (signatures == null || signatures.isEmpty()) {
			throw new MessageException("No block signatures supplied");
		}

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(Ints.toByteArray(signatures.size()));

			for (byte[] signature : signatures) {
				if (signature.length != Transformer.SIGNATURE_LENGTH) {
					throw new MessageException("Invalid signature length in GET_BLOCKS request");
				}

				bytes.write(signature);
			}
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
		this.signatures = signatures;
	}

	private GetBlocksMessage(int id, List<byte[]> signatures) {
		super(id, MessageType.GET_BLOCKS);

		this.signatures = signatures;
	}

	public List<byte[]> getSignatures() {
		return this.signatures;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws MessageException {
		try {
			int signaturesCount = byteBuffer.getInt();

			if (signaturesCount <= 0) {
				throw new MessageException("Invalid block signature count");
			}

			List<byte[]> signatures = new ArrayList<>(signaturesCount);

			for (int i = 0; i < signaturesCount; ++i) {
				byte[] signature = new byte[Transformer.SIGNATURE_LENGTH];
				byteBuffer.get(signature);
				signatures.add(signature);
			}

			return new GetBlocksMessage(id, signatures);
		} catch (BufferUnderflowException e) {
			throw new MessageException("Unable to decode GetBlocks message", e);
		}
	}

}
