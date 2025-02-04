package org.qortal.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class PurchaseBotCreateRequest {

    @Schema(description = "Product ID associated with the purchase", example = "PRODUCT12345")
    public String productId;

    @Schema(description = "Store ID where the product belongs", example = "STORE123")
    public String storeId;

    @Schema(description = "Price of the product in QORT", example = "100.00000000", type = "number")
    @XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
    public long price;

    @Schema(description = "Product key to deliver after successful purchase", example = "LICENSE-KEY-1234-5678")
    public String productKey;

    @Schema(description = "Product description", example = "Limited edition NFT with unique artwork.")
    public String productDescription;

    public PurchaseBotCreateRequest() {
        // Default constructor for deserialization
    }
}
