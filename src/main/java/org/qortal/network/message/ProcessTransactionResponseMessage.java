package org.qortal.network.message;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.qortal.api.ApiRequest;
import org.qortal.data.transaction.TransactionData;

public class ProcessTransactionResponseMessage extends Message {

	private final TransactionData transactionData;

	// Constructor for sending side (Core)
	public ProcessTransactionResponseMessage(TransactionData transactionData) throws IOException {
	super(MessageType.PROCESS_TRANSACTION_RESPONSE);
	this.transactionData = transactionData;
	serializeTransactionData();
}

	// Constructor for receiving side (Lite node)
	private ProcessTransactionResponseMessage(int id, String xml) {
		super(id, MessageType.PROCESS_TRANSACTION_RESPONSE);
		this.transactionData = null; // Lite node will deserialize separately
		this.dataBytes = xml.getBytes(StandardCharsets.UTF_8);
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public TransactionData getTransactionData() {
		return this.transactionData;
	}

private void serializeTransactionData() throws IOException {
	StringWriter writer = new StringWriter();
	ApiRequest.marshall(writer, this.transactionData);
	this.dataBytes = writer.toString().getBytes(StandardCharsets.UTF_8);
	this.checksumBytes = Message.generateChecksum(this.dataBytes);
}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
		try {
			byte[] xmlBytes = new byte[byteBuffer.remaining()];
			byteBuffer.get(xmlBytes);
			String xml = new String(xmlBytes, StandardCharsets.UTF_8);
			return new ProcessTransactionResponseMessage(id, xml);
		} catch (Exception e) {
			throw new RuntimeException("Failed to parse PROCESS_TRANSACTION_RESPONSE message", e);
		}
	}
}
