package org.qortal.data.purchase;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.json.JSONObject;
import org.qortal.utils.Base58;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class PurchaseBotData {

    private byte[] privateKey;
    private byte[] publicKey;

    private String productId;
    private String sellerAddress;

    @XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
    private long price;

    private String productKey;
    private String purchaseState;

    @XmlTransient
    @Schema(hidden = true)
    private int purchaseStateValue;

    private int lastPaymentBlockHeight; // Added field for last payment block height

    protected PurchaseBotData() {
        /* JAXB */
    }

    public PurchaseBotData(byte[] privateKey, byte[] publicKey, String productId, String sellerAddress,
                            long price, String productKey, String purchaseState,
                           int purchaseStateValue, int lastPaymentBlockHeight) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.productId = productId;
        this.sellerAddress = sellerAddress;
        this.price = price;
        this.productKey = productKey;
        this.purchaseState = purchaseState;
        this.purchaseStateValue = purchaseStateValue;
        this.lastPaymentBlockHeight = lastPaymentBlockHeight;
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

    public String getSellerAddress() {
        return sellerAddress;
    }

    public long getPrice() {
        return price;
    }

    public String getProductKey() {
        return productKey;
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

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("privateKey", Base58.encode(this.getPrivateKey()));
        jsonObject.put("publicKey", Base58.encode(this.getPublicKey()));
        jsonObject.put("productId", this.getProductId());
        jsonObject.put("sellerAddress", this.getSellerAddress());
        jsonObject.put("price", this.getPrice());
        jsonObject.put("productKey", this.getProductKey());
        jsonObject.put("purchaseState", this.getPurchaseState());
        jsonObject.put("purchaseStateValue", this.getPurchaseStateValue());
        jsonObject.put("lastPaymentBlockHeight", this.getLastPaymentBlockHeight());
        return jsonObject;
    }

    public static PurchaseBotData fromJson(JSONObject json) {
        return new PurchaseBotData(
                json.isNull("privateKey") ? null : Base58.decode(json.getString("privateKey")),
                json.isNull("publicKey") ? null : Base58.decode(json.getString("publicKey")),
                json.getString("productId"),
                json.getString("sellerAddress"),
                json.getLong("price"),
                json.getString("productKey"),
                json.getString("purchaseState"),
                json.getInt("purchaseStateValue"),
                json.optInt("lastPaymentBlockHeight", 0) // Handle optional field with default
        );
    }

    // Mostly for debugging
    @Override
    public String toString() {
        return String.format("Product ID: %s, State: %s (%d), Last Payment Block: %d",
                this.productId, this.purchaseState, this.purchaseStateValue, this.lastPaymentBlockHeight);
    }
}
