package com.bet;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import com.fasterxml.jackson.databind.ObjectMapper;



import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TriggerOtpLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private final String tableName = "users";

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {

        String bodyJson = (String) input.get("body");
        if (bodyJson == null || bodyJson.isEmpty()) {
            return Map.of("statusCode", 400, "message", "Missing request body");
        }

        Map<String, Object> body;
        try {
            ObjectMapper mapper = new ObjectMapper();
            body = mapper.readValue(bodyJson, Map.class);
        } catch (Exception e) {
            return Map.of("statusCode", 400, "message", "Invalid JSON: " + e.getMessage());
        }

        String email = (String) body.get("email");
        if (email == null || email.isEmpty()) {
            return Map.of("statusCode", 400, "message", "Missing email");
        }

        String pk = "USER#" + email;
        String now = Instant.now().toString();
        String otp = String.format("%06d", new Random().nextInt(999999));
        String expiry = Instant.now().plusSeconds(300).toString();

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", AttributeValue.fromS(pk));

        try {
            // Check if user exists
            GetItemResponse getItem = dynamoDb.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build());

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("PK", AttributeValue.fromS(pk));
            item.put("email", AttributeValue.fromS(email));
            item.put("otp", AttributeValue.fromS(otp));
            item.put("otpExpiry", AttributeValue.fromS(expiry));
            item.put("updatedAt", AttributeValue.fromS(now));

            if (!getItem.hasItem()) {
                item.put("createdAt", AttributeValue.fromS(now));
            }

            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build());

            // (Optionally call sendEmailLambda here via SNS or invoke directly)

            SesClient ses = SesClient.create();
            SendEmailRequest request = SendEmailRequest.builder()
                    .destination(Destination.builder().toAddresses(email).build())
                    .message(Message.builder()
                            .subject(Content.builder().data("Your OTP").build())
                            .body(Body.builder()
                                    .text(Content.builder().data("Your OTP is " + otp).build())
                                    .build())
                            .build())
                    .source("mail4bharane@gmail.com")  // Must be verified in SES
                    .build();

            ses.sendEmail(request);


            return Map.of("statusCode", 200, "message", "OTP generated and stored successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("statusCode", 500, "message", "Error: " + e.getMessage());

        }
    }
}
