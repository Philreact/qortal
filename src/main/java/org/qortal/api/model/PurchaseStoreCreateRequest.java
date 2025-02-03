package org.qortal.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

public class PurchaseStoreCreateRequest {

    @Schema(description = "Unique Store ID", example = "STORE12345")
    public String storeId;

    @Schema(description = "Seller's Qortal Address", example = "QWERTYUIOP1234567890ASDFGHJKLZXCVBNM")
    public String sellerAddress;

    @Schema(description = "Seller's store name", example = "My first store")
    public String storeName;

    @Schema(description = "Description of the store", example = "This is my Qortal store selling digital products.")
    public String storeDescription;

    public PurchaseStoreCreateRequest() {
        // Default constructor for deserialization
    }
}
