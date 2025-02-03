package org.qortal.repository;

import java.util.List;

import org.qortal.data.purchase.PurchaseBotData;
import org.qortal.data.purchase.PurchaseStoreData;

public interface PurchaseRepository {

    /**
     * Retrieves the purchase data for a given purchase ID.
     *
     * @param purchaseId Unique identifier for the purchase.
     * @return PurchaseBotData object containing purchase details, or null if not found.
     * @throws DataException if a data access error occurs.
     */
    PurchaseBotData getPurchaseBotData(String productId) throws DataException;

    /**
     * Returns true if there is an existing purchase-bot entry relating to the given product ID,
     * excluding purchases with specific states.
     *
     * @param productId Unique identifier for the product.
     * @param excludeStates List of states to exclude.
     * @return True if a matching purchase exists, false otherwise.
     * @throws DataException if a data access error occurs.
     */
    boolean existsPurchaseForProductExcludingStates(String productId, List<String> excludeStates) throws DataException;

    /**
     * Retrieves all purchase-bot data stored in the repository.
     * If a storeId is provided, retrieves only products from that store.
     *
     * @param storeId (Optional) Store ID to filter products by.
     * @return List of PurchaseBotData objects.
     * @throws DataException if a data access error occurs.
     */
    List<PurchaseBotData> getAllPurchaseBotData(String storeId) throws DataException;


    /**
     * Saves the given purchase-bot data to the repository.
     *
     * @param purchaseBotData The purchase data to save.
     * @throws DataException if a data access error occurs.
     */
    void save(PurchaseBotData purchaseBotData) throws DataException;

    /**
     * Deletes a purchase-bot entry using the provided purchase ID.
     *
     * @param purchaseId The unique ID of the purchase to delete.
     * @return Number of rows deleted.
     * @throws DataException if a data access error occurs.
     */
    int delete(String purchaseId) throws DataException;

    void updateSavedBlockHeight(String productId, int blockHeight) throws DataException;

    int getSavedBlockHeight(String productId) throws DataException;

    boolean doesStoreExist(String storeId) throws DataException;

    void saveStore(PurchaseStoreData purchaseStoreData) throws DataException;

    PurchaseStoreData getStoreData(String storeId) throws DataException;

    List<PurchaseStoreData> getAllStores(String sellerAddress) throws DataException;
}
