package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.GroupInviteTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class GroupInviteTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int ADMIN_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int INVITEE_LENGTH = ADDRESS_LENGTH;
	private static final int TTL_LENGTH = INT_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + ADMIN_LENGTH + NAME_SIZE_LENGTH + INVITEE_LENGTH + TTL_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] adminPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String groupName = Serialization.deserializeSizedString(byteBuffer, Group.MAX_NAME_SIZE);

		String invitee = Serialization.deserializeAddress(byteBuffer);
		
		int timeToLive = byteBuffer.getInt();

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new GroupInviteTransactionData(adminPublicKey, groupName, invitee, timeToLive, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		GroupInviteTransactionData groupInviteTransactionData = (GroupInviteTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(groupInviteTransactionData.getGroupName());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			GroupInviteTransactionData groupInviteTransactionData = (GroupInviteTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(groupInviteTransactionData.getType().value));
			bytes.write(Longs.toByteArray(groupInviteTransactionData.getTimestamp()));
			bytes.write(groupInviteTransactionData.getReference());

			bytes.write(groupInviteTransactionData.getCreatorPublicKey());
			Serialization.serializeSizedString(bytes, groupInviteTransactionData.getGroupName());
			Serialization.serializeAddress(bytes, groupInviteTransactionData.getInvitee());
			bytes.write(Ints.toByteArray(groupInviteTransactionData.getTimeToLive()));

			Serialization.serializeBigDecimal(bytes, groupInviteTransactionData.getFee());

			if (groupInviteTransactionData.getSignature() != null)
				bytes.write(groupInviteTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			GroupInviteTransactionData groupInviteTransactionData = (GroupInviteTransactionData) transactionData;

			byte[] adminPublicKey = groupInviteTransactionData.getAdminPublicKey();

			json.put("admin", PublicKeyAccount.getAddress(adminPublicKey));
			json.put("adminPublicKey", HashCode.fromBytes(adminPublicKey).toString());

			json.put("groupName", groupInviteTransactionData.getGroupName());
			json.put("invitee", groupInviteTransactionData.getInvitee());
			json.put("timeToLive", groupInviteTransactionData.getTimeToLive());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}