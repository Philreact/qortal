package org.qortal.controller.purchasebot;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitcoinj.core.ECKey;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.arbitrary.ArbitraryDataTransactionBuilder;
import org.qortal.arbitrary.misc.Service;
import org.qortal.controller.Controller;
import org.qortal.controller.Synchronizer;
import org.qortal.crypto.Crypto;
import org.qortal.data.purchase.PurchaseBotData;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.event.Event;
import org.qortal.event.EventBus;
import org.qortal.event.Listener;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.transaction.PaymentTransaction;
import org.qortal.transaction.Transaction;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;

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
                allPurchaseBotData = repository.getPurchaseRepository().getAllPurchaseBotData(null);
                
             
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
                } catch (IOException | TransformationException ex) {
                }
            }
        }
    }

    public void progress(Repository repository, PurchaseBotData purchaseBotData) throws DataException, IOException, TransformationException {
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
    private static final Set<String> deliveredTransactionSignatures = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Integer> transactionBlockHeights = new ConcurrentHashMap<>();
    private void handleWaitingForPayment(Repository repository, PurchaseBotData purchaseBotData) throws DataException, IOException, TransformationException {
        String sellerAddress = purchaseBotData.getSellerAddress();
        long price = purchaseBotData.getPrice();
        String address = Crypto.toAddress(purchaseBotData.getPublicKey());
    
        int latestBlockHeight = Controller.getInstance().getChainHeight();
        int lastSaveBlockHeight = repository.getPurchaseRepository().getSavedBlockHeight(purchaseBotData.getProductId());
        if (lastSaveBlockHeight == 0) {
            lastSaveBlockHeight = latestBlockHeight - 10080;
        }
    
        System.out.println("Saved blockheight: " + lastSaveBlockHeight);
        System.out.println("latest blockheight: " + latestBlockHeight);
        int offset = 10;
        if(deliveredTransactionSignatures.isEmpty()){
            offset = 0;
        }
        List<PaymentTransaction> paymentTransactions = repository.getTransactionRepository().findPaymentTransactions(
            address, price, lastSaveBlockHeight - offset, latestBlockHeight
        );
        int currentBlockHeight = Controller.getInstance().getChainHeight();

        for (PaymentTransaction paymentTransaction : paymentTransactions) {
            String txSignature = Base58.encode(paymentTransaction.getTransactionData().getSignature());
            LOGGER.info("PaymentTransaction: signature={}",
                    Base58.encode(paymentTransaction.getPaymentTransactionData().getSignature()));
            synchronized (deliveredTransactionSignatures) {
                if (deliveredTransactionSignatures.contains(txSignature)) {
                    LOGGER.info("Skipping already processed transaction: {}", txSignature);
                    continue;
                }
                byte[] reference = new byte[64];
                new Random().nextBytes(reference);
                System.out.println("reference (Base64): " + Base64.getEncoder().encodeToString(reference));
                byte[] senderPublicKey = purchaseBotData.getPublicKey();
                String sender = address;
                String recipient = paymentTransaction.getSender().getAddress();
                byte[] data = purchaseBotData.getProductKey().getBytes();
                byte[] recipientPublicKey = paymentTransaction.getTransactionData().getCreatorPublicKey();
                // 1) Compute shared secret
                byte[] sharedSecret = Crypto.getSharedSecret(purchaseBotData.getPrivateKey(), recipientPublicKey);
                System.out.println("secretHash1: " + Base64.getEncoder().encodeToString(sharedSecret));
                // 2) Hash the shared secret
                byte[] chatEncryptionSeed = Crypto.digest(sharedSecret);
                System.out.println("secretHash2: " + Base64.getEncoder().encodeToString(chatEncryptionSeed));
                // 3) Extract a 12-byte nonce
                byte[] nonce2 = Arrays.copyOfRange(reference, 0, 12);
                // 4) Encrypt using AES-GCM
                byte[] encryptedMessage = Crypto.encryptAESGCM(chatEncryptionSeed, nonce2, data);
                byte[] testText = "hello".getBytes();
                byte[] combined = new byte[nonce2.length + encryptedMessage.length];
                System.arraycopy(nonce2, 0, combined, 0, nonce2.length);
                System.arraycopy(encryptedMessage, 0, combined, nonce2.length, encryptedMessage.length);
                System.out.println("Original data length: " + data.length);
                System.out.println("BASE64 encryptedMessage: " + Base64.getEncoder().encodeToString(encryptedMessage));
                System.out.println("BASE64 nonce2: " + Base64.getEncoder().encodeToString(nonce2));

System.out.println("Encrypted data length: " + encryptedMessage.length);
System.out.println("Nonce length: " + nonce2.length);
System.out.println("Final combined length: " + combined.length);

                LOGGER.info("sender: {}", sender);
                long timestamp = NTP.getTime();
                Transaction.TransactionType txType = Transaction.TransactionType.ARBITRARY;
                Constructor<?> constructor = txType.constructor;
                Transaction transaction ;
                try {
                    transaction = (Transaction) constructor.newInstance(null, null);
                } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
                    LOGGER.error("Error creating transaction instance", e);
                    continue;
                }
                long fee = transaction.getUnitFee(timestamp);
                String filename = String.format("qortal-%d", NTP.getTime());
                Service service = Service.CHAIN_DATA;
                java.nio.file.Path tempDirectory;
                try {
                    tempDirectory = Files.createTempDirectory("qortal-");
                } catch (IOException e) {
                    LOGGER.error("Error creating temp directory", e);
                    continue;
                }
                File tempFile = Paths.get(tempDirectory.toString(), filename).toFile();
                tempFile.deleteOnExit();
                try {
                    Files.write(tempFile.toPath(), combined);
                } catch (IOException e) {
                    LOGGER.error("Error writing encrypted message to file", e);
                    continue;
                }
                String path = tempFile.toPath().toString();
                try {
                    ArbitraryDataTransactionBuilder transactionBuilder = new ArbitraryDataTransactionBuilder(
                            repository, Base58.encode(senderPublicKey), fee, Paths.get(path), null, null, service, recipient,
                            null, null, null, null
                    );
                    
                    transactionBuilder.build();
                    ArbitraryTransactionData arbitraryTransactionData = transactionBuilder.getArbitraryTransactionData();

                    // ArbitraryTransaction arbitraryTransaction = (ArbitraryTransaction) Transaction.fromData(repository, transactionData);
                      //new
            // byte[] rawBytes = ArbitraryTransactionTransformer.toBytes(transactionData);

            
            // transactionData = TransactionTransformer.fromBytes(rawBytes);
           if (arbitraryTransactionData == null)
               return;

           PrivateKeyAccount signer = new PrivateKeyAccount(null, purchaseBotData.getPrivateKey());

            transaction = Transaction.fromData(null, arbitraryTransactionData);
           transaction.sign(signer);

           byte[] signedBytes = TransactionTransformer.toBytes(arbitraryTransactionData);

           //
          TransactionData transactionData = TransactionTransformer.fromBytes(signedBytes);

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
                    Transaction.ValidationResult result = transaction.importAsUnconfirmed();
                    if (result != Transaction.ValidationResult.OK)
                        return;

                   
                    deliveredTransactionSignatures.add(txSignature);
                    transactionBlockHeights.put(txSignature, currentBlockHeight);
                    

                } finally {
                    blockchainLock.unlock(); // Always unlock in the finally block
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
                LOGGER.warn("Failed to acquire blockchain lock due to interruption", e);
            }
                } catch (DataException e) {
                    LOGGER.error("Error building transaction", e);
                }
            }
        }
    
        repository.getPurchaseRepository().updateSavedBlockHeight(purchaseBotData.getProductId(), latestBlockHeight);
        int minValidBlockHeight = currentBlockHeight - 10;
        transactionBlockHeights.entrySet().removeIf(entry -> entry.getValue() < minValidBlockHeight);
        deliveredTransactionSignatures.removeIf(sig -> transactionBlockHeights.getOrDefault(sig, 0) < minValidBlockHeight);
        LOGGER.debug("No valid payment transactions found for seller: {}", address);
    }
    

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
