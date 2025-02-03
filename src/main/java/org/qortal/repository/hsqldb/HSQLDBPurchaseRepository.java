package org.qortal.repository.hsqldb;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.controller.Controller;
import org.qortal.data.purchase.PurchaseBotData;
import org.qortal.data.purchase.PurchaseStoreData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.PaymentTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.PurchaseRepository;
import org.qortal.transaction.Transaction;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.TransactionTransformer;

public class HSQLDBPurchaseRepository implements PurchaseRepository {

    protected HSQLDBRepository repository;

    public HSQLDBPurchaseRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }
    
    @Override
    public PurchaseBotData getPurchaseBotData(String purchaseId) throws DataException {
        String sql = "SELECT private_key, public_key, product_id, store_id, seller_address, "
                   + "price, product_key, product_description, state, state_value, last_payment_block_height "
                   + "FROM PurchaseBotProducts WHERE product_id = ?";
    
        try (ResultSet resultSet = this.repository.checkedExecute(sql, purchaseId)) {
            if (resultSet == null || !resultSet.next()) // Ensure result is fetched properly
                return null;
    
            byte[] privateKey = resultSet.getBytes(1);
            byte[] publicKey = resultSet.getBytes(2);
            String productId = resultSet.getString(3);
            String storeId = resultSet.getString(4);
            String sellerAddress = resultSet.getString(5);
            long price = resultSet.getLong(6);
            String productKey = resultSet.getString(7);
            String description = resultSet.getString(8);
            String state = resultSet.getString(9);
            int stateValue = resultSet.getInt(10);
            int lastPaymentBlockHeight = resultSet.getInt(11);
    
            return new PurchaseBotData(privateKey, publicKey, productId, storeId, sellerAddress,
                                       price, productKey, description, state, stateValue, lastPaymentBlockHeight);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch purchase-bot data from repository", e);
        }
    }
    

    @Override
    public boolean existsPurchaseForProductExcludingStates(String productId, List<String> excludeStates) throws DataException {
        if (excludeStates == null)
            excludeStates = Collections.emptyList();

        StringBuilder whereClause = new StringBuilder("product_id = ?");
        Object[] bindParams = new Object[1 + excludeStates.size()];
        bindParams[0] = productId;

        if (!excludeStates.isEmpty()) {
            whereClause.append(" AND state NOT IN (?");
            bindParams[1] = excludeStates.get(0);

            for (int i = 1; i < excludeStates.size(); ++i) {
                whereClause.append(", ?");
                bindParams[1 + i] = excludeStates.get(i);
            }
            whereClause.append(")");
        }

        try {
            return this.repository.exists("PurchaseBotProducts", whereClause.toString(), bindParams);
        } catch (SQLException e) {
            throw new DataException("Unable to check for purchase-bot state in repository", e);
        }
    }

    @Override
    public List<PurchaseBotData> getAllPurchaseBotData(String storeId) throws DataException {
        String sql;
        List<Object> params = new ArrayList<>();
    
        // 🟢 If storeId is provided, filter by store
        if (storeId != null) {
            sql = "SELECT private_key, public_key, product_id, store_id, seller_address, "
                + "price, product_key, product_description, state, state_value, last_payment_block_height "
                + "FROM PurchaseBotProducts WHERE store_id = ?";
            params.add(storeId);
        } else {
            // 🔵 If storeId is null, fetch all products
            sql = "SELECT private_key, public_key, product_id, store_id, seller_address, "
                + "price, product_key, product_description, state, state_value, last_payment_block_height FROM PurchaseBotProducts";
        }
    
        List<PurchaseBotData> purchaseBotDataList = new ArrayList<>();
    
        try (ResultSet resultSet = this.repository.checkedExecute(sql, params.toArray())) {
            if (resultSet == null)
                return purchaseBotDataList;
    
          do {
                byte[] privateKey = resultSet.getBytes(1);
                byte[] publicKey = resultSet.getBytes(2);
                String productId = resultSet.getString(3);
                String fetchedStoreId = resultSet.getString(4);
                String sellerAddress = resultSet.getString(5);
                long price = resultSet.getLong(6);
                String productKey = resultSet.getString(7);
                String description = resultSet.getString(8);
                String state = resultSet.getString(9);
                int stateValue = resultSet.getInt(10);
                int lastPaymentBlockHeight = resultSet.getInt(11);
    
                PurchaseBotData purchaseBotData = new PurchaseBotData(
                    privateKey, publicKey, productId, fetchedStoreId, sellerAddress, price,
                    productKey, description, state, stateValue, lastPaymentBlockHeight
                );
                purchaseBotDataList.add(purchaseBotData);
            } while (resultSet.next());
    
            return purchaseBotDataList;
        } catch (SQLException e) {
            System.out.println("unable to fetch: " + e);
            throw new DataException("Unable to fetch purchase-bot data from repository", e);
        }
    }
    
    

    @Override
    public void save(PurchaseBotData purchaseBotData) throws DataException {
        HSQLDBSaver saveHelper = new HSQLDBSaver("PurchaseBotProducts");
    
        saveHelper.bind("private_key", purchaseBotData.getPrivateKey())
                  .bind("public_key", purchaseBotData.getPublicKey())
                  .bind("product_id", purchaseBotData.getProductId())
                  .bind("store_id", purchaseBotData.getStoreId())  // 🆕 Added store ID
                  .bind("seller_address", purchaseBotData.getSellerAddress())
                  .bind("price", purchaseBotData.getPrice())
                  .bind("product_key", purchaseBotData.getProductKey())
                  .bind("product_description", purchaseBotData.getProductDescription())  // 🆕 Added product description
                  .bind("state", purchaseBotData.getPurchaseState())
                  .bind("state_value", purchaseBotData.getPurchaseStateValue());
    
        try {
            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save purchase-bot data into repository", e);
        }
    }

    @Override
public void saveStore(PurchaseStoreData purchaseStoreData) throws DataException {
    HSQLDBSaver saveHelper = new HSQLDBSaver("PurchaseBotStores"); // Table for storing stores

    saveHelper.bind("store_id", purchaseStoreData.getStoreId())
              .bind("store_name", purchaseStoreData.getStoreName()) // 🆕 Store name
              .bind("seller_address", purchaseStoreData.getSellerAddress())
              .bind("store_description", purchaseStoreData.getStoreDescription()); // Store description

    try {
        saveHelper.execute(this.repository);
    } catch (SQLException e) {
        throw new DataException("Unable to save purchase store data into repository", e);
    }
}

    

    @Override
    public void updateSavedBlockHeight(String productId, int blockHeight) throws DataException {
        String sql = "UPDATE PurchaseBotProducts SET last_payment_block_height = ? WHERE product_id = ?";
    
        try {
            System.out.println("update height: " + blockHeight );
            System.out.println("productId: " + productId );
            this.repository.executeCheckedUpdate(sql, blockHeight, productId);
            this.repository.saveChanges();
        } catch (SQLException ex) {
            throw new DataException("Failed to update saved block height", ex);
        }
    }

    
    @Override
    public int getSavedBlockHeight(String productId) throws DataException {
        String sql = "SELECT last_payment_block_height FROM PurchaseBotProducts WHERE product_id = ?";
    
        try (ResultSet resultSet = this.repository.checkedExecute(sql, productId)) {
            System.out.println("Executing SQL Query: " + sql);
            System.out.println("Product ID: " + productId);
    
            if (resultSet == null) {
                System.out.println("ResultSet is NULL!");
                return 0;
            }
    
          
                int blockHeight = resultSet.getInt("last_payment_block_height");
                System.out.println("Retrieved Block Height: " + blockHeight);
                return blockHeight;
           
        } catch (SQLException e) {
            return 0;
        }
    
       
    }
    
    @Override
    public int delete(String productId) throws DataException {
        try {
            return this.repository.delete("PurchaseBotProducts", "product_id = ?", productId);
        } catch (SQLException e) {
            throw new DataException("Unable to delete purchase-bot data from repository", e);
        }
    }

    @Override
    public boolean doesStoreExist(String storeId) throws DataException {
        String sql = "SELECT COUNT(*) AS count FROM PurchaseBotStores WHERE store_id = ?";
    
        try (ResultSet resultSet = this.repository.checkedExecute(sql, storeId)) {
            System.out.println("Executing SQL Query: " + sql);
            System.out.println("Store ID: " + storeId);
    
            if (resultSet == null) {
                System.out.println("ResultSet is NULL!");
                return false;
            }
    
            int count = resultSet.getInt("count");
            System.out.println("Store Count Retrieved: " + count);
            return count > 0; // If count > 0, store exists; otherwise, it does not
    
        } catch (SQLException e) {
            System.err.println("❌ SQL Exception while checking store existence: " + e.getMessage());
            return false;
        }
    }

    @Override
public PurchaseStoreData getStoreData(String storeId) throws DataException {
    String sql = "SELECT store_id, store_name, seller_address, store_description "
               + "FROM PurchaseBotStores WHERE store_id = ?";

    try (ResultSet resultSet = this.repository.checkedExecute(sql, storeId)) {
        if (resultSet == null || !resultSet.next()) // Ensure result is fetched properly
            return null;

        String fetchedStoreId = resultSet.getString(1);
        String storeName = resultSet.getString(2);
        String sellerAddress = resultSet.getString(3);
        String storeDescription = resultSet.getString(4);

        return new PurchaseStoreData(fetchedStoreId, storeName, sellerAddress, storeDescription);
    } catch (SQLException e) {
        throw new DataException("Unable to fetch store data from repository", e);
    }
}

@Override
public List<PurchaseStoreData> getAllStores(String sellerAddressParam) throws DataException {
    String sql;
    List<Object> params = new ArrayList<>();

    if (sellerAddressParam != null) {
        sql = "SELECT store_id, store_name, seller_address, store_description "
            + "FROM PurchaseBotStores WHERE seller_address = ?";
        params.add(sellerAddressParam);
    } else {
        // 🔵 If sellerAddressParam is null, fetch all stores
         sql = "SELECT store_id, store_name, seller_address, store_description FROM PurchaseBotStores";
    }
    

    List<PurchaseStoreData> allStores = new ArrayList<>();

    try (ResultSet resultSet = this.repository.checkedExecute(sql, params.toArray())) {
        if (resultSet == null)
            return allStores;

        do { // Correct loop structure
            String storeId = resultSet.getString(1);
            String storeName = resultSet.getString(2);
            String sellerAddress = resultSet.getString(3);
            String storeDescription = resultSet.getString(4);

            PurchaseStoreData storeData = new PurchaseStoreData(storeId, storeName, sellerAddress, storeDescription);
            allStores.add(storeData);
        } while (resultSet.next());

        return allStores;
    } catch (SQLException e) {
        throw new DataException("Unable to fetch all store data from repository", e);
    }
}

@Override
public long cashOut() throws DataException {
    List<PurchaseBotData> allPurchaseBotData = getAllPurchaseBotData(null);
    long totalAmount = 0; // Initialize totalAmount

    for (PurchaseBotData purchaseBotData : allPurchaseBotData) {
        try  {
            Account account = new Account(this.repository, purchaseBotData.getAddress());
            long assetId = Asset.QORT;
            long timestamp = System.currentTimeMillis();
            long balance = account.getConfirmedBalance(assetId);
            System.out.println("balance: " + balance);
            Transaction.TransactionType txType = Transaction.TransactionType.PAYMENT;
            Constructor<?> constructor = txType.constructor;
            Transaction transaction;

            try {
                transaction = (Transaction) constructor.newInstance(null, null);
            } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
                continue;
            }

            long fee = transaction.getUnitFee(timestamp);
            long balanceToSend = balance - fee;
            System.out.println("balanceToSend: " + balanceToSend);
            if (balanceToSend <= 0) {
                continue; // Skip if no balance to cash out
            }

           
            byte[] reference = new byte[64];
            new Random().nextBytes(reference);

            BaseTransactionData baseTransactionData = new BaseTransactionData(
                timestamp,
                Group.NO_GROUP,
                reference,
                purchaseBotData.getPublicKey(),
                fee,
                null
            );

            TransactionData paymentTransactionData = new PaymentTransactionData(
                baseTransactionData,
                purchaseBotData.getSellerAddress(),
                balanceToSend
            );

            PrivateKeyAccount signer = new PrivateKeyAccount(null, purchaseBotData.getPrivateKey());

            transaction = Transaction.fromData(null, paymentTransactionData);
            transaction.sign(signer);

            byte[] signedBytes = TransactionTransformer.toBytes(paymentTransactionData);
            TransactionData transactionData = TransactionTransformer.fromBytes(signedBytes);
            transaction = Transaction.fromData(this.repository, transactionData);

            if (!transaction.isSignatureValid()) {
                continue;
            }

            ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();

            try {
                if (!blockchainLock.tryLock(60, TimeUnit.SECONDS)) {
                    continue;
                }

                try {
                    Transaction.ValidationResult result = transaction.importAsUnconfirmed();
                    if (result != Transaction.ValidationResult.OK) {
                        continue;
                    }

                    // Add the balance to totalAmount if successful
                    totalAmount += balanceToSend;

                } finally {
                    blockchainLock.unlock(); // Always unlock in the finally block
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
            }
        } catch (DataException | TransformationException e) {
            throw new DataException("Unable to perform cashout", e);
        }
    }

    return totalAmount; // Return the total amount cashed out
}

    
}
