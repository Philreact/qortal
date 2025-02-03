package org.qortal.data.purchase;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.json.JSONObject;
import org.qortal.crypto.Crypto;
import org.qortal.utils.Base58;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class PurchaseBotData {

    private byte[] privateKey;
    private byte[] publicKey;

    private String productId;
    private String storeId; // Added store ID
    private String sellerAddress;

    @XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
    private long price;

    private String productKey;
    private String productDescription; // Added product description
    private String purchaseState;

    @XmlTransient
    @Schema(hidden = true)
    private int purchaseStateValue;

    private int lastPaymentBlockHeight;
    private String address;

    protected PurchaseBotData() {
        /* JAXB */
    }

    public PurchaseBotData(byte[] privateKey, byte[] publicKey, String productId, String storeId, String sellerAddress,
                           long price, String productKey, String productDescription, String purchaseState,
                           int purchaseStateValue, int lastPaymentBlockHeight) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.productId = productId;
        this.storeId = storeId; // Store ID added
        this.sellerAddress = sellerAddress;
        this.price = price;
        this.productKey = productKey;
        this.productDescription = productDescription; // Product description added
        this.purchaseState = purchaseState;
        this.purchaseStateValue = purchaseStateValue;
        this.lastPaymentBlockHeight = lastPaymentBlockHeight;
        this.address = Crypto.toAddress(publicKey); // Set address during initialization
    }

    // Getters and Setters
    public byte[] getPrivateKey() {
        return privateKey;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public String getProductId() {
        return productId;
    }

    public String getStoreId() { // Getter for Store ID
        return storeId;
    }

    public void setStoreId(String storeId) { // Setter for Store ID
        this.storeId = storeId;
    }

    public String getSellerAddress() {
        return sellerAddress;
    }

    public long getPrice() {
        return price;
    }

    public String getProductKey() {
        return productKey;
    }

    public String getProductDescription() { // Getter for Product Description
        return productDescription;
    }

    public void setProductDescription(String productDescription) { // Setter for Product Description
        this.productDescription = productDescription;
    }

    public String getPurchaseState() {
        return purchaseState;
    }

    public void setPurchaseState(String purchaseState) {
        this.purchaseState = purchaseState;
    }

    public int getPurchaseStateValue() {
        return purchaseStateValue;
    }

    public void setPurchaseStateValue(int purchaseStateValue) {
        this.purchaseStateValue = purchaseStateValue;
    }

    public int getLastPaymentBlockHeight() {
        return lastPaymentBlockHeight;
    }

    public void setLastPaymentBlockHeight(int lastPaymentBlockHeight) {
        this.lastPaymentBlockHeight = lastPaymentBlockHeight;
    }

    // New getter for address
    public String getAddress() {
        if (this.address == null && this.publicKey != null) {
            this.address = Crypto.toAddress(this.publicKey); // Calculate dynamically
        }
        return this.address;
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("privateKey", Base58.encode(this.getPrivateKey()));
        jsonObject.put("publicKey", Base58.encode(this.getPublicKey()));
        jsonObject.put("productId", this.getProductId());
        jsonObject.put("storeId", this.getStoreId()); // Added storeId
        jsonObject.put("sellerAddress", this.getSellerAddress());
        jsonObject.put("price", this.getPrice());
        jsonObject.put("productKey", this.getProductKey());
        jsonObject.put("productDescription", this.getProductDescription()); // Added product description
        jsonObject.put("purchaseState", this.getPurchaseState());
        jsonObject.put("purchaseStateValue", this.getPurchaseStateValue());
        jsonObject.put("lastPaymentBlockHeight", this.getLastPaymentBlockHeight());
        jsonObject.put("address", this.getAddress()); // Include address in JSON
        return jsonObject;
    }

    public static PurchaseBotData fromJson(JSONObject json) {
        PurchaseBotData purchaseBotData = new PurchaseBotData(
                json.isNull("privateKey") ? null : Base58.decode(json.getString("privateKey")),
                json.isNull("publicKey") ? null : Base58.decode(json.getString("publicKey")),
                json.getString("productId"),
                json.getString("storeId"), // Added storeId
                json.getString("sellerAddress"),
                json.getLong("price"),
                json.getString("productKey"),
                json.getString("productDescription"), // Added product description
                json.getString("purchaseState"),
                json.getInt("purchaseStateValue"),
                json.optInt("lastPaymentBlockHeight", 0)
        );

        // Manually set address if available
        if (json.has("address")) {
            purchaseBotData.address = json.getString("address");
        }

        return purchaseBotData;
    }

    @Override
    public String toString() {
        return String.format("Product ID: %s, Store ID: %s, State: %s (%d), Address: %s, Last Payment Block: %d",
                this.productId, this.storeId, this.purchaseState, this.purchaseStateValue, this.getAddress(), this.lastPaymentBlockHeight);
    }
}
