package org.qortal.data.purchase;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.json.JSONObject;


@XmlAccessorType(XmlAccessType.FIELD)
public class PurchaseStoreData {

    private String storeId;
    private String storeName; // 🆕 Added store name
    private String sellerAddress;
    private String storeDescription;
    

    protected PurchaseStoreData() {
        /* JAXB */
    }

    public PurchaseStoreData(String storeId, String storeName, String sellerAddress, String storeDescription) {
        this.storeId = storeId;
        this.storeName = storeName; // 🆕 Assign store name
        this.sellerAddress = sellerAddress;
        this.storeDescription = storeDescription;
    }

    // Getters and Setters
    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getStoreName() { // 🆕 Getter for store name
        return storeName;
    }

    public void setStoreName(String storeName) { // 🆕 Setter for store name
        this.storeName = storeName;
    }

    public String getSellerAddress() {
        return sellerAddress;
    }

    public void setSellerAddress(String sellerAddress) {
        this.sellerAddress = sellerAddress;
    }

    public String getStoreDescription() {
        return storeDescription;
    }

    public void setStoreDescription(String storeDescription) {
        this.storeDescription = storeDescription;
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("storeId", this.getStoreId());
        jsonObject.put("storeName", this.getStoreName()); // 🆕 Include store name in JSON
        jsonObject.put("sellerAddress", this.getSellerAddress());
        jsonObject.put("storeDescription", this.getStoreDescription());
        return jsonObject;
    }

    public static PurchaseStoreData fromJson(JSONObject json) {
        return new PurchaseStoreData(
                json.getString("storeId"),
                json.getString("storeName"), // 🆕 Parse store name
                json.getString("sellerAddress"),
                json.getString("storeDescription")
        );
    }

    @Override
    public String toString() {
        return String.format("Store ID: %s, Store Name: %s, Seller Address: %s, Description: %s",
                this.storeId, this.storeName, this.sellerAddress, this.storeDescription);
    }
}
