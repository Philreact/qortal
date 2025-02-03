package org.qortal.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class DecryptDataRequest {

    @Schema(description = "Raw data in base58", example = "B229FLq8VAT8KVgt6zs5h2YpEWeaVgCjGq592FUta96r")
    public String data;

    @Schema(description = "Secret in base58", example = "BqP6VZgBzVAoSqiX89J6SDdvs7nSu7HjXsyMvvLCgXa7")
    public String secret;

    public DecryptDataRequest() {
        // Default constructor for deserialization
    }
}