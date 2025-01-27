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

import org.qortal.account.PublicKeyAccount;
import org.qortal.api.ApiError;
import org.qortal.api.ApiErrors;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.api.Security;
import org.qortal.api.model.PurchaseBotCreateRequest;
import org.qortal.controller.purchasebot.PurchaseBot;
import org.qortal.crypto.Crypto;
import org.qortal.data.purchase.PurchaseBotData;
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
@Path("/create")
@Operation(
    summary = "Create a purchase-bot entry",
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
@ApiErrors({ApiError.INVALID_PUBLIC_KEY, ApiError.REPOSITORY_ISSUE})
@SecurityRequirement(name = "apiKey")
public String createPurchaseBot(@HeaderParam(Security.API_KEY_HEADER) String apiKey, PurchaseBotCreateRequest purchaseRequest) {
    Security.checkApiCallAllowed(request);

    try (final Repository repository = RepositoryManager.getRepository()) {
        // Basic validation
        PublicKeyAccount creatorAccount = new PublicKeyAccount(repository, purchaseRequest.creatorPublicKey);
        if (creatorAccount.getAddress() == null)
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_PUBLIC_KEY);

        byte[] tradePrivateKey = PurchaseBot.generateTradePrivateKey();

		byte[] tradeNativePublicKey = PurchaseBot.deriveTradeNativePublicKey(tradePrivateKey);

		String tradeNativeAddress = Crypto.toAddress(tradeNativePublicKey);
        // Map request to database object
        PurchaseBotData purchaseBotData = new PurchaseBotData(
            tradePrivateKey,
            tradeNativePublicKey,
            purchaseRequest.productId,
            purchaseRequest.sellerAddress,
            purchaseRequest.price,
            purchaseRequest.productKey,
            "WAITING_FOR_PAYMENT", // Default initial state
            PurchaseBot.State.resolveStateValue("WAITING_FOR_PAYMENT"),
            0
        );

        // Save purchase-bot data
        repository.getPurchaseRepository().save(purchaseBotData);
        repository.saveChanges();
        return "PurchaseBot created successfully";
    } catch (DataException e) {
        throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
    }
}
}
