package com.yourname.aicommerce.payment;

import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.common.response.ApiResponse;
import com.yourname.aicommerce.payment.dto.RazorpayOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final RazorpayService razorpayService;

    @PostMapping("/create-order/{orderId}")
    public ResponseEntity<ApiResponse<RazorpayOrderResponse>> createPaymentOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User user) {

        RazorpayOrderResponse response = razorpayService.createPaymentOrder(orderId, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<String>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        if (signature == null || !razorpayService.verifyWebhookSignature(payload, signature)) {
            throw new IllegalArgumentException("Invalid or missing Razorpay webhook signature");
        }

        razorpayService.processWebhook(payload, signature);
        return ResponseEntity.ok(ApiResponse.success("Webhook processed successfully", null));
    }
}
