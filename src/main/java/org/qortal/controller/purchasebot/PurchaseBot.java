package org.qortal.controller.purchasebot;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitcoinj.core.ECKey;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.controller.Controller;
import org.qortal.controller.Synchronizer;
import org.qortal.crypto.Crypto;
import org.qortal.data.purchase.PurchaseBotData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.ChatTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.event.Event;
import org.qortal.event.EventBus;
import org.qortal.event.Listener;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.transaction.ChatTransaction;
import org.qortal.transaction.PaymentTransaction;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.ValidationResult;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.ChatTransactionTransformer;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;

import com.google.common.primitives.Bytes;

/**
 * PurchaseBot listens for payment transactions and delivers product keys to
 * buyers.
 */
public class PurchaseBot implements Listener {

    private static final Logger LOGGER = LogManager.getLogger(PurchaseBot.class);
    private static PurchaseBot instance;

    // Singleton instance
    private PurchaseBot() {
        EventBus.INSTANCE.addListener(event -> PurchaseBot.getInstance().listen(event));
    }

    public static synchronized PurchaseBot getInstance() {
        if (instance == null) {
            instance = new PurchaseBot();
        }
        return instance;
    }

 
    

    @Override
    public void listen(Event event) {

        // Only process new chain tip events
        if (!(event instanceof Synchronizer.NewChainTipEvent)) {
            return;
        }

        // Ensure blockchain is up to date
        final Long minLatestBlockTimestamp = NTP.getTime() - (60 * 60 * 1000L); // 60 minutes
        if (!Controller.getInstance().isUpToDate(minLatestBlockTimestamp)) {
            return;
        }

        synchronized (this) {
    //         int latestBlockHeight = Controller.getInstance().getChainHeight();
    // System.out.println("New block height detected: " + latestBlockHeight);
            List<PurchaseBotData> allPurchaseBotData;

            try (final Repository repository = RepositoryManager.getRepository()) {
                allPurchaseBotData = repository.getPurchaseRepository().getAllPurchaseBotData();
                
             
            } catch (DataException e) {
                LOGGER.error("Couldn't run purchase bot due to repository issue", e);
                return;
            }
            LOGGER.info("PurchaseBotData: {}", allPurchaseBotData);

            for (PurchaseBotData purchaseBotData : allPurchaseBotData) {
                try (final Repository repository = RepositoryManager.getRepository()) {
                    progress(repository, purchaseBotData);
                } catch (DataException e) {
                    LOGGER.error("Couldn't process purchase bot entry", e);
                }
            }
        }
    }

    public void progress(Repository repository, PurchaseBotData purchaseBotData) throws DataException {
        State currentState = State.valueOf(purchaseBotData.getPurchaseStateValue());
     
        switch (currentState) {
            case WAITING_FOR_PAYMENT:
                handleWaitingForPayment(repository, purchaseBotData);
                break;

            case COMPLETED:
            case CANCELLED:
                // No action needed for completed or canceled purchases
                break;

            default:
                LOGGER.warn("Unknown state for PurchaseBot: {}", currentState);
        }
    }
    private final Set<String> deliveredTransactionSignatures = Collections.synchronizedSet(new HashSet<>());

    private void handleWaitingForPayment(Repository repository, PurchaseBotData purchaseBotData) throws DataException {
        String sellerAddress = purchaseBotData.getSellerAddress();
        long price = purchaseBotData.getPrice();
        String address = Crypto.toAddress(purchaseBotData.getPublicKey());
  


      
        // Fetch payment transactions addressed to the seller
        // List<TransactionData> paymentTransactions = repository.getTransactionRepository()
        //         .getTransactionsByRecipient(sellerAddress);
        // List<byte[]> signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(null, null, null,
        //         Collections.singletonList(TransactionType.PAYMENT), null, null, sellerAddress, TransactionsResource.ConfirmationStatus.CONFIRMED, 1, 0, true);
                int latestBlockHeight = Controller.getInstance().getChainHeight();
                int lastSaveBlockHeight = repository.getPurchaseRepository().getSavedBlockHeight(purchaseBotData.getProductId());
                if (lastSaveBlockHeight == 0) {
                    lastSaveBlockHeight = latestBlockHeight - 10080;
                }
                System.out.println("Saved blockheight: " + lastSaveBlockHeight);
                System.out.println("latest blockheight: " + latestBlockHeight);
                List<PaymentTransaction> paymentTransactions = repository.getTransactionRepository().findPaymentTransactions(sellerAddress, price,  lastSaveBlockHeight, latestBlockHeight);

              

        // // // Expand signatures to transactions
        // List<TransactionData> paymentTransactions = new ArrayList<>(signatures.size());
        // for (byte[] signature : signatures) {
        //     paymentTransactions.add(repository.getTransactionRepository().fromSignature(signature));
        // }

        for (PaymentTransaction paymentTransaction : paymentTransactions) {

            String txSignature = Base58.encode(paymentTransaction.getPaymentTransactionData().getSignature());

            LOGGER.info("PaymentTransaction: signature={}",
                    Base58.encode(paymentTransaction.getPaymentTransactionData().getSignature()));

            // Check if this transaction has already been delivered
            synchronized (deliveredTransactionSignatures) {
                // if (deliveredTransactionSignatures.contains(txSignature)) {
                //     // LOGGER.info("Transaction already delivered: signature={}", txSignature);
                //     continue;
                // }
                byte[] reference = new byte[64];
                new Random().nextBytes(reference);

              
                // System.out.println("reference: " + Base58.encode(reference));
                System.out.println("reference (Base64): " + Base64.getEncoder().encodeToString(reference));

                // Mock values for the required fields
                byte[] senderPublicKey = purchaseBotData.getPublicKey(); // Replace with actual public key
                String sender = address; // Replace with actual sender address
                int nonce = 0; // Replace with actual nonce
                String recipient = paymentTransaction.getSender().getAddress(); // Replace with actual recipient address
                byte[] chatReference = null; // Optional, replace if needed
                byte[] data = purchaseBotData.getProductKey().getBytes(); // Your message data
                byte[] recipientPublicKey = paymentTransaction.getTransactionData().getCreatorPublicKey();
                // 1) Compute shared secret
                byte[] sharedSecret = Crypto.getSharedSecret(purchaseBotData.getPrivateKey(), recipientPublicKey);

                System.out.println("secretHash1: " + Base64.getEncoder().encodeToString(sharedSecret));

                // 2) Hash the shared secret -> 32-byte AES-256 key
                byte[] chatEncryptionSeed = Crypto.digest(sharedSecret);

                System.out.println("secretHash2: " + Base64.getEncoder().encodeToString(chatEncryptionSeed));

                // 3) Extract a 12-byte nonce for AES-GCM
                byte[] nonce2 = Arrays.copyOfRange(reference, 0, 12);

                // 4) Encrypt using AES-GCM
                byte[] encryptedMessage = Crypto.encryptAESGCM(chatEncryptionSeed, nonce2, data);
                boolean isText = true; // Indicates if the data is text
                boolean isEncrypted = true; // Indicates if the data is encrypted

                LOGGER.info("sender: {}", sender);

                // BaseTransactionData (mock or real values)
                long timestamp = System.currentTimeMillis(); // Replace with actual timestamp

                long fee = 1000L; // Transaction fee
         

                BaseTransactionData baseTransactionData = new BaseTransactionData(
                        timestamp,
                        Group.NO_GROUP,
                        reference,
                        senderPublicKey,
                        fee,
                        null
                );

// Construct ChatTransactionData
                ChatTransactionData chatTransactionData = new ChatTransactionData(
                        baseTransactionData,
                        sender,
                        nonce,
                        recipient,
                        chatReference,
                        encryptedMessage,
                        isText,
                        isEncrypted
                );

                System.out.println("reference2: " + Base64.getEncoder().encodeToString(chatTransactionData.getReference()));

// Convert to bytes
try {
    byte[] bytes = ChatTransactionTransformer.toBytes(chatTransactionData);
    bytes = Bytes.concat(bytes, new byte[TransactionTransformer.SIGNATURE_LENGTH]);

    TransactionData transactionData = TransactionTransformer.fromBytes(bytes);
    ChatTransaction chatTransaction = (ChatTransaction) Transaction.fromData(repository, transactionData);

    chatTransaction.computeNonce();

			
			// Strip zeroed signature
			transactionData.setSignature(null);

			bytes = ChatTransactionTransformer.toBytes(transactionData);

            //new
            byte[] rawBytes = Bytes.concat(bytes, new byte[TransactionTransformer.SIGNATURE_LENGTH]);

			 transactionData = TransactionTransformer.fromBytes(rawBytes);
			if (transactionData == null)
				return;

			PrivateKeyAccount signer = new PrivateKeyAccount(null, purchaseBotData.getPrivateKey());

			Transaction transaction = Transaction.fromData(null, transactionData);
			transaction.sign(signer);

			byte[] signedBytes = TransactionTransformer.toBytes(transactionData);

            //
            transactionData = TransactionTransformer.fromBytes(signedBytes);

             transaction = Transaction.fromData(repository, transactionData);

			if (!transaction.isSignatureValid())
				return;

            ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();


			try {
                if (!blockchainLock.tryLock(60, TimeUnit.SECONDS)) {
                    LOGGER.warn("Failed to acquire blockchain lock within timeout");
                    return; // Exit if the lock couldn't be acquired
                }
            
                try {
                    // Critical section: your protected code here
                    ValidationResult result = transaction.importAsUnconfirmed();
                    if (result != ValidationResult.OK)
                        return;
                } finally {
                    blockchainLock.unlock(); // Always unlock in the finally block
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
                LOGGER.warn("Failed to acquire blockchain lock due to interruption", e);
            }
            // deliveredTransactionSignatures.add(txSignature);
            
    System.out.println("Serialized ChatTransactionData: " + Base58.encode(bytes));
} catch (TransformationException e) {
    System.err.println("Failed to transform ChatTransactionData to bytes: " + e.getMessage());
}

                // Add the transaction to the delivered set to avoid reprocessing
                
            }
            // Validate payment
            // if (validatePayment(paymentTransaction, price)) {
            //     deliverProduct(repository, purchaseBotData, paymentTransaction);
            //     return;
            // }
        }
        repository.getPurchaseRepository().updateSavedBlockHeight(purchaseBotData.getProductId(), latestBlockHeight);
        LOGGER.debug("No valid payment transactions found for seller: {}", sellerAddress);
    }

    // private boolean validatePayment(TransactionData paymentTransaction, long expectedAmount) {
    //     if (paymentTransaction.getAmount() < expectedAmount) {
    //         LOGGER.warn("Payment amount too low: {}", paymentTransaction.getAmount());
    //         return false;
    //     }
    //     LOGGER.info("Valid payment detected: {}", paymentTransaction.getTransactionId());
    //     return true;
    // }
    // private void deliverProduct(Repository repository, PurchaseBotData purchaseBotData, TransactionData paymentTransaction) throws DataException {
    //     String buyerAddress = paymentTransaction.getSenderAddress();
    //     String productKey = purchaseBotData.getProductKey();
    //     // Encrypt the product key using the buyer's public key
    //     byte[] encryptedKey = encryptProductKey(productKey, buyerAddress);
    //     // Send the encrypted key to the buyer
    //     PrivateKeyAccount sender = new PrivateKeyAccount(repository, purchaseBotData.getPrivateKey());
    //     MessageTransaction message = MessageTransaction.build(repository, sender, Group.NO_GROUP, buyerAddress, encryptedKey, false, false);
    //     message.computeNonce();
    //     message.sign(sender);
    //     repository.discardChanges();
    //     ValidationResult result = message.importAsUnconfirmed();
    //     if (result != ValidationResult.OK) {
    //         LOGGER.warn("Failed to deliver product key to buyer: {}", result.name());
    //         return;
    //     }
    //     LOGGER.info("Product key successfully delivered to buyer: {}", buyerAddress);
    //     updateState(repository, purchaseBotData, State.COMPLETED);
    // }
    // private byte[] encryptProductKey(String productKey, String buyerAddress) {
    //     try {
    //         byte[] buyerPublicKey = Crypto.toPublicKey(buyerAddress);
    //         return Crypto.encrypt(buyerPublicKey, productKey.getBytes());
    //     } catch (Exception e) {
    //         LOGGER.error("Failed to encrypt product key for buyer: {}", buyerAddress, e);
    //         return null;
    //     }
    // }
    // private void updateState(Repository repository, PurchaseBotData purchaseBotData, State newState) throws DataException {
    //     // Update the purchaseStateValue with the integer value from the State enum
    //     purchaseBotData.setPurchaseStateValue(newState.getValue());
    //     // Optionally update the string representation of the state, if needed
    //     purchaseBotData.setPurchaseState(newState.name());
    //     // Save the updated state in the repository
    //     repository.getPurchaseRepository().save(purchaseBotData);
    //     LOGGER.info("Purchase state updated to {} for {}", newState, purchaseBotData.getPurchaseId());
    // }
    public enum State {
        WAITING_FOR_PAYMENT(1),
        COMPLETED(2),
        CANCELLED(3);

        private final int value;

        State(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static State valueOf(int stateValue) {
            for (State state : values()) {
                if (state.value == stateValue) {
                    return state;
                }
            }
            throw new IllegalArgumentException("Unknown state value: " + stateValue);
        }

        public static int resolveStateValue(String stateName) {
            try {
                return State.valueOf(stateName).getValue();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid state name: " + stateName, e);
            }
        }
    }

    public static byte[] generateTradePrivateKey() {
        // The private key is used for both Curve25519 and secp256k1 so needs to be valid for both.
        // Curve25519 accepts any seed, so generate a valid secp256k1 key and use that.
        return new ECKey().getPrivKeyBytes();
    }

    public static byte[] deriveTradeNativePublicKey(byte[] privateKey) {
        return Crypto.toPublicKey(privateKey);
    }

  
    
}
