package org.qortal.network.message;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.qortal.data.chat.ChatMessage;
import org.qortal.data.chat.ChatMessage.Encoding;
import org.qortal.transform.Transformer;

public class ChatMessagesMessage extends Message {

    private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;
    private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;
    private static final int PUBLIC_KEY_LENGTH = Transformer.PUBLIC_KEY_LENGTH;

    private List<ChatMessage> chatMessages;
    private Encoding encoding;

    public ChatMessagesMessage(List<ChatMessage> chatMessages, Encoding encoding) {
        super(MessageType.CHAT_MESSAGES);

        this.chatMessages = chatMessages;
        this.encoding = encoding;

        ByteBuffer buffer = serializeChatMessages(chatMessages, encoding);

        this.dataBytes = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(this.dataBytes);

        this.checksumBytes = Message.generateChecksum(this.dataBytes);
    }

    public ChatMessagesMessage(int id, List<ChatMessage> chatMessages, Encoding encoding) {
        this(chatMessages, encoding);
        this.setId(id);
    }

    public List<ChatMessage> getChatMessages() {
        return this.chatMessages;
    }

    public Encoding getEncoding() {
        return this.encoding;
    }

    private static ByteBuffer serializeChatMessages(List<ChatMessage> chatMessages, Encoding encoding) {
        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1 MB buffer, adjust if needed

        // Serialize encoding globally (1 byte)
        buffer.put((byte) encoding.ordinal());

        // Number of messages
        buffer.putInt(chatMessages.size());

        for (ChatMessage chatMessage : chatMessages) {
            buffer.putLong(chatMessage.getTimestamp());
            buffer.putInt(chatMessage.getTxGroupId());

            buffer.put(chatMessage.getReference());
            buffer.put(chatMessage.getSenderPublicKey());

            putSizedString(buffer, chatMessage.getSender());
            putSizedString(buffer, chatMessage.getSenderName());
            putSizedString(buffer, chatMessage.getRecipient());
            putSizedString(buffer, chatMessage.getRecipientName());

            byte[] chatReference = chatMessage.getChatReference();
            if (chatReference != null) {
                buffer.putInt(1);
                buffer.put(chatReference);
            } else {
                buffer.putInt(0);
            }

            // Serialize data (already encoded string)
            putSizedString(buffer, chatMessage.getData());

            buffer.putInt(chatMessage.isText() ? 1 : 0);
            buffer.putInt(chatMessage.isEncrypted() ? 1 : 0);

            buffer.put(chatMessage.getSignature());
        }

        return buffer;
    }

    private static void putSizedString(ByteBuffer buffer, String value) {
        if (value == null) {
            buffer.putInt(0);
        } else {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            buffer.putInt(bytes.length);
            buffer.put(bytes);
        }
    }

    public static Message fromByteBuffer(int id, ByteBuffer buffer) {
        // Read global encoding
        Encoding encoding = Encoding.values()[buffer.get()];

        int messageCount = buffer.getInt();
        List<ChatMessage> chatMessages = new ArrayList<>(messageCount);

        for (int i = 0; i < messageCount; i++) {
            long timestamp = buffer.getLong();
            int txGroupId = buffer.getInt();

            byte[] reference = new byte[SIGNATURE_LENGTH];
            buffer.get(reference);

            byte[] senderPublicKey = new byte[PUBLIC_KEY_LENGTH];
            buffer.get(senderPublicKey);

            String sender = readSizedString(buffer);
            String senderName = readSizedString(buffer);
            String recipient = readSizedString(buffer);
            String recipientName = readSizedString(buffer);

            int hasChatReference = buffer.getInt();
            byte[] chatReference = null;
            if (hasChatReference == 1) {
                chatReference = new byte[SIGNATURE_LENGTH];
                buffer.get(chatReference);
            }

            String data = readSizedString(buffer);

            boolean isText = buffer.getInt() == 1;
            boolean isEncrypted = buffer.getInt() == 1;

            byte[] signature = new byte[SIGNATURE_LENGTH];
            buffer.get(signature);

            // Construct ChatMessage (data as null to skip re-encoding)
            ChatMessage chatMessage = new ChatMessage(timestamp, txGroupId, reference, senderPublicKey, sender,
                    senderName, recipient, recipientName, chatReference, encoding,
                    null, isText, isEncrypted, signature);

            // No need to set data manually; getData() returns correct string set during construction.

            chatMessages.add(chatMessage);
        }

        return new ChatMessagesMessage(id, chatMessages, encoding);
    }

    private static String readSizedString(ByteBuffer buffer) {
        int length = buffer.getInt();
        if (length == 0) {
            return null;
        }
        byte[] strBytes = new byte[length];
        buffer.get(strBytes);
        return new String(strBytes, StandardCharsets.UTF_8);
    }

    public ChatMessagesMessage cloneWithNewId(int newId) {
        ChatMessagesMessage clone = new ChatMessagesMessage(this.chatMessages, this.encoding);
        clone.setId(newId);
        return clone;
    }
}
