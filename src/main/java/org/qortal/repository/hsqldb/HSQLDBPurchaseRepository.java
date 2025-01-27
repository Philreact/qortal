package org.qortal.repository.hsqldb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.qortal.data.purchase.PurchaseBotData;
import org.qortal.repository.DataException;
import org.qortal.repository.PurchaseRepository;

public class HSQLDBPurchaseRepository implements PurchaseRepository {

    protected HSQLDBRepository repository;

    public HSQLDBPurchaseRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public PurchaseBotData getPurchaseBotData(String purchaseId) throws DataException {
        String sql = "SELECT private_key, public_key, product_id, seller_address, "
                   + "price, product_key, state, state_value, last_payment_block_height "
                   + "FROM PurchaseBotStates WHERE product_id = ?";
    
        try (ResultSet resultSet = this.repository.checkedExecute(sql, purchaseId)) {
            if (resultSet == null)
                return null;
    
            byte[] privateKey = resultSet.getBytes(1);
            byte[] publicKey = resultSet.getBytes(2);
            String productId = resultSet.getString(3);
            String sellerAddress = resultSet.getString(4);
            long price = resultSet.getLong(5);
            String productKey = resultSet.getString(6);
            String state = resultSet.getString(7);
            int stateValue = resultSet.getInt(8);
            int lastPaymentBlockHeight = resultSet.getInt(9);
    
            return new PurchaseBotData(privateKey, publicKey, productId, sellerAddress,
                                       price, productKey, state, stateValue, lastPaymentBlockHeight);
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
            return this.repository.exists("PurchaseBotStates", whereClause.toString(), bindParams);
        } catch (SQLException e) {
            throw new DataException("Unable to check for purchase-bot state in repository", e);
        }
    }

    @Override
    public List<PurchaseBotData> getAllPurchaseBotData() throws DataException {
        String sql = "SELECT private_key, public_key, product_id, seller_address, "
                   + "price, product_key, state, state_value, last_payment_block_height FROM PurchaseBotStates";
    
        List<PurchaseBotData> allPurchaseBotData = new ArrayList<>();
    
        try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
            if (resultSet == null)
                return allPurchaseBotData;
    
            do {
                byte[] privateKey = resultSet.getBytes(1);
                byte[] publicKey = resultSet.getBytes(2);
                String productId = resultSet.getString(3);
                String sellerAddress = resultSet.getString(4);
                long price = resultSet.getLong(5);
                String productKey = resultSet.getString(6);
                String state = resultSet.getString(7);
                int stateValue = resultSet.getInt(8);
                int lastPaymentBlockHeight = resultSet.getInt(9);
    
                PurchaseBotData purchaseBotData = new PurchaseBotData(
                    privateKey, publicKey, productId, sellerAddress, price,
                    productKey, state, stateValue, lastPaymentBlockHeight
                );
                allPurchaseBotData.add(purchaseBotData);
            } while (resultSet.next());
    
            return allPurchaseBotData;
        } catch (SQLException e) {
            throw new DataException("Unable to fetch all purchase-bot data from repository", e);
        }
    }

    @Override
    public void save(PurchaseBotData purchaseBotData) throws DataException {
        HSQLDBSaver saveHelper = new HSQLDBSaver("PurchaseBotStates");

        saveHelper.bind("private_key", purchaseBotData.getPrivateKey())
                  .bind("public_key", purchaseBotData.getPublicKey())
                  .bind("product_id", purchaseBotData.getProductId())
                  .bind("seller_address", purchaseBotData.getSellerAddress())
                  .bind("price", purchaseBotData.getPrice())
                  .bind("product_key", purchaseBotData.getProductKey())
                  .bind("state", purchaseBotData.getPurchaseState())
                  .bind("state_value", purchaseBotData.getPurchaseStateValue());

        try {
            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save purchase-bot data into repository", e);
        }
    }

    @Override
    public int delete(String productId) throws DataException {
        try {
            return this.repository.delete("PurchaseBotStates", "product_id = ?", productId);
        } catch (SQLException e) {
            throw new DataException("Unable to delete purchase-bot data from repository", e);
        }
    }
}
