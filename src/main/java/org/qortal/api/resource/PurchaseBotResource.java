package org.qortal.api.resource;

import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.qortal.api.ApiError;
import org.qortal.api.ApiErrors;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.api.Security;
import org.qortal.api.model.PurchaseBotCreateRequest;
import org.qortal.api.model.PurchaseStoreCreateRequest;
import org.qortal.controller.purchasebot.PurchaseBot;
import org.qortal.data.purchase.PurchaseBotData;
import org.qortal.data.purchase.PurchaseStoreData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Path("/purchasebot")
@Tag(name = "PurchaseBot")
public class PurchaseBotResource {

    @Context
    HttpServletRequest request;

    @GET
    @Operation(
        summary = "List current purchase-bot states",
        responses = {
            @ApiResponse(
                content = @Content(
                    array = @ArraySchema(
                        schema = @Schema(
                            implementation = PurchaseBotData.class
                        )
                    )
                )
            )
        }
    )
    @ApiErrors({ApiError.REPOSITORY_ISSUE})
    @SecurityRequirement(name = "apiKey")
    public List<PurchaseBotData> getPurchaseBotStates(
            @HeaderParam(Security.API_KEY_HEADER) String apiKey,
            @QueryParam("state") String state) {
        Security.checkApiCallAllowed(request);

        try (final Repository repository = RepositoryManager.getRepository()) {
            List<PurchaseBotData> allPurchaseBotData = repository.getPurchaseRepository().getAllPurchaseBotData();

            if (state == null)
                return allPurchaseBotData;

            return allPurchaseBotData.stream()
                    .filter(purchaseBotData -> purchaseBotData.getPurchaseState().equalsIgnoreCase(state))
                    .collect(Collectors.toList());
        } catch (DataException e) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
        }
    }

    @POST
    @Path("/product/create")
    @Operation(
        summary = "Create or update a purchase-bot entry",
        requestBody = @RequestBody(
            required = true,
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(
                    implementation = PurchaseBotCreateRequest.class
                )
            )
        ),
        responses = {
            @ApiResponse(
                content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string"))
            )
        }
    )
    @ApiErrors({ApiError.INVALID_PUBLIC_KEY, ApiError.REPOSITORY_ISSUE, ApiError.INVALID_CRITERIA})
    @SecurityRequirement(name = "apiKey")
    public String createOrUpdatePurchaseBot(@HeaderParam(Security.API_KEY_HEADER) String apiKey, PurchaseBotCreateRequest purchaseRequest) {
        Security.checkApiCallAllowed(request);
    
        try (final Repository repository = RepositoryManager.getRepository()) {
            // 1️⃣ Check if the store exists
            boolean storeExists = repository.getPurchaseRepository().doesStoreExist(purchaseRequest.storeId);
            if (!storeExists) {
                throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA, new Exception("Store ID does not exist: " + purchaseRequest.storeId));
            }
    
            // 2️⃣ Check if a product with the same ID exists
            PurchaseBotData existingPurchaseBot = repository.getPurchaseRepository().getPurchaseBotData(purchaseRequest.productId);
    
            byte[] tradePrivateKey;
            byte[] tradeNativePublicKey;
            int lastPaymentBlockHeight = 0; // Default if no previous record exists
    
            if (existingPurchaseBot != null) {
                // Entry exists, reuse existing keys and last payment block height
                tradePrivateKey = existingPurchaseBot.getPrivateKey();
                tradeNativePublicKey = existingPurchaseBot.getPublicKey();
                lastPaymentBlockHeight = existingPurchaseBot.getLastPaymentBlockHeight(); // Preserve last payment height
            } else {
                // Entry does not exist, generate new keys
                tradePrivateKey = PurchaseBot.generateTradePrivateKey();
                tradeNativePublicKey = PurchaseBot.deriveTradeNativePublicKey(tradePrivateKey);
            }
    
            // 3️⃣ Create or update the purchase-bot data
            PurchaseBotData purchaseBotData = new PurchaseBotData(
                tradePrivateKey,
                tradeNativePublicKey,
                purchaseRequest.productId,
                purchaseRequest.storeId, // Store ID added
                purchaseRequest.sellerAddress,
                purchaseRequest.price,
                purchaseRequest.productKey,
                purchaseRequest.productDescription, // Store description added
                "WAITING_FOR_PAYMENT", // Default initial state
                PurchaseBot.State.resolveStateValue("WAITING_FOR_PAYMENT"),
                lastPaymentBlockHeight // Retain last payment block height if updating
            );
    
            // 4️⃣ Save purchase-bot data (insert if new, update if exists)
            repository.getPurchaseRepository().save(purchaseBotData);
            repository.saveChanges();
    
            return existingPurchaseBot != null ? "PurchaseBot updated successfully" : "PurchaseBot created successfully";
        } catch (DataException e) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
        }
    }
    
    @POST
    @Path("/store/create")
    @Operation(
        summary = "Create or update a purchase store",
        requestBody = @RequestBody(
            required = true,
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(
                    implementation = PurchaseStoreCreateRequest.class
                )
            )
        ),
        responses = {
            @ApiResponse(
                content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string"))
            )
        }
    )
    @ApiErrors({ApiError.INVALID_PUBLIC_KEY, ApiError.REPOSITORY_ISSUE, ApiError.INVALID_CRITERIA})
    @SecurityRequirement(name = "apiKey")
    public String createOrUpdateStore(@HeaderParam(Security.API_KEY_HEADER) String apiKey, PurchaseStoreCreateRequest storeRequest) {
        Security.checkApiCallAllowed(request);
    
        try (final Repository repository = RepositoryManager.getRepository()) {
            // 1️⃣ Check if a store with the same ID exists
            PurchaseStoreData existingStore = repository.getPurchaseRepository().getStoreData(storeRequest.storeId);
    
            if (existingStore != null) {
                // If store exists, update both store name and description
                existingStore.setStoreName(storeRequest.storeName);  // 🆕 Store Name
                existingStore.setStoreDescription(storeRequest.storeDescription);  // 🆕 Store Description
                repository.getPurchaseRepository().saveStore(existingStore);
                repository.saveChanges();
                return "Store updated successfully";
            }
    
            // 2️⃣ Create a new store entry
            PurchaseStoreData newStore = new PurchaseStoreData(
                storeRequest.storeId,
                storeRequest.storeName,  // 🆕 Store Name
                storeRequest.sellerAddress,
                storeRequest.storeDescription
            );
    
            repository.getPurchaseRepository().saveStore(newStore);
            repository.saveChanges();
            return "Store created successfully";
        } catch (DataException e) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
        }
    }
    

    
}
