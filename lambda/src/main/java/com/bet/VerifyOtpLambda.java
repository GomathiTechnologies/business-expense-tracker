package com.bet;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.Map;

public class VerifyOtpLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = "users";

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            context.getLogger().log("🔍 Incoming event: " + event);

            String bodyJson = (String) event.get("body");
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> body = mapper.readValue(bodyJson, Map.class);

            String email = body.get("emailid");
            String inputOtp = body.get("verifyOTP");

            if (email == null || inputOtp == null) {
                return response(400, "Missing email or OTP");
            }

            String pk = "USER#" + email;

            GetItemRequest getRequest = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("PK", AttributeValue.fromS(pk)))
                .build();

            Map<String, AttributeValue> item = dynamoDb.getItem(getRequest).item();

            if (item == null || item.isEmpty()) {
                return response(404, "User not found");
            }

            String storedOtp = item.get("otp").s();
            String expiryStr = item.get("otpExpiry").s();
            Instant expiry = Instant.parse(expiryStr);
            Instant now = Instant.now();

            if (!inputOtp.equals(storedOtp)) {
                return response(400, "Incorrect OTP");
            }

            if (now.isAfter(expiry)) {
                return response(400, "OTP expired");
            }

            return response(200, "OTP verified successfully");

        } catch (Exception e) {
            context.getLogger().log("💥 Exception: " + e.getMessage());
            return response(500, "Internal server error");
        }
    }

    private Map<String, Object> response(int statusCode, String message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String body = mapper.writeValueAsString(Map.of("message", message));
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", body
            );
        } catch (Exception e) {
            return Map.of("statusCode", 500, "body", "{\"message\":\"Response error\"}");
        }
    }
}
