package com.example;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.List;

public class SQSReceiver {

    public static void receiveMessage(String queueUrl) {
        // Create an SQS client
        try (SqsClient sqsClient = SqsClient.builder()
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build()) {

            // Create a request to receive messages
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1) // Get one message at a time
                    .waitTimeSeconds(10)    // Long polling for up to 10 seconds
                    .build();

            // Receive messages
            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

            if (!messages.isEmpty()) {
                // Process each message
                for (Message message : messages) {
                    System.out.println("Message Received: " + message.body());

                    // Delete the message after processing
                    deleteMessage(sqsClient, queueUrl, message.receiptHandle());
                }
            } else {
                System.out.println("No messages available in the queue.");
            }
        } catch (SqsException e) {
            System.err.println("SQS error: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void deleteMessage(SqsClient sqsClient, String queueUrl, String receiptHandle) {
        try {
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build();
            sqsClient.deleteMessage(deleteRequest);
            System.out.println("Message deleted successfully.");
        } catch (SqsException e) {
            System.err.println("Failed to delete message: " + e.awsErrorDetails().errorMessage());
        }
    }

    public static void main(String[] args) {
        // Replace with your SQS queue URL
        String queueUrl = "https://sqs.your-region.amazonaws.com/your-account-id/your-queue-name";
        receiveMessage(queueUrl);
    }
}

    
}
