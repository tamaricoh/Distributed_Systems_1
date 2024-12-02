package com.example;

import java.io.File;
// import java.io.IOException;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.util.Base64;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.model.Message;


public class AWS {
    private final S3Client s3Client;
    private final SqsClient sqsClient;

    public static String ami = "ami-00e95a9222311e8ed";

    private static final String WORKERS_TO_MANAGER_QUEUE_NAME = "Workers-To-Manager";

    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;

    private static final AWS instance = new AWS();

    // Constructor initializes S3, SQS, and EC2 clients with the default region.
    private AWS() {
        s3Client = S3Client.builder().region(region1).build();
        sqsClient = SqsClient.builder().region(region1).build();
    }

    /**
     * Provides a singleton instance of the AWS utility class.
     *
     * @return Singleton instance of AWS.
     */
    public static AWS getInstance() {
        return instance;
    }

    /**
     * Sends a message to the specified SQS queue.
     *
     * @param message   The content of the message to send.
     * @param queueName The name of the SQS queue.
     */
    public void sendSQSMessage(String message, String queueName) {
        try {
            // Retrieve the queue URL
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();

            GetQueueUrlResponse queueUrlResponse = getInstance().sqsClient.getQueueUrl(getQueueUrlRequest);
            String queueUrl = queueUrlResponse.queueUrl();

            // Send the message
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(message)
                    .build();
            getInstance().sqsClient.sendMessage(sendMessageRequest);

            System.out.println("SQS Message Sent: " + message);
        } catch (SqsException e) {
            System.err.println("Error sending SQS message: " + e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * Retrieves a message from the specified SQS queue.
     *
     * @param QUEUE_NAME The name of the SQS queue.
     * @param localAppID The application ID for scoping the queue.
     * @return The operation and URL extracted from the message, or an empty string if no messages were found.
     */ 
    public String getMessage(String QUEUE_NAME, String localAppID) {
        //TODO: try to figure how two workers are not working on the same message
        //TODO: update worker so it won't delete the message and not handle it
        try {
            // Create a request to receive messages
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(getQueueUrl(QUEUE_NAME + "_" + localAppID))
                    .maxNumberOfMessages(1) // Process one message at a time
                    .waitTimeSeconds(10) // Long polling
                    .build();

            // Receive messages from the queue
            var messages = sqsClient.receiveMessage(receiveRequest).messages();

            if (messages.isEmpty()) {
                System.out.println("No messages in the queue.");
                return ""; // No message was processed
            }

            // Process the first message
            Message message = messages.get(0);
            String body = message.body();

            // Parse the message body (assumes the body format is fixed)
            String[] parts = body.split(" ");
            String operation = parts[0]; // Extract operation
            String url = parts[1];      // Extract URL

            System.out.println("Received message:");
            System.out.println("Operation: " + operation);
            System.out.println("URL: " + url);

            // Delete the message from the queue after processing 
            //problemmm
            deleteMessage(QUEUE_NAME + "_" + localAppID, message.receiptHandle());

            return operation + " " + url;
        } catch (Exception e) {
            System.err.println("Error while processing SQS message: " + e.getMessage());
            return "";
        }
    }

    /**
     * Retrieves the URL of an SQS queue by its name.
     *
     * @param QueueName The name of the SQS queue.
     * @return The URL of the specified SQS queue.
     */
    public String getQueueUrl(String QueueName) {
        // Get the URL of the SQS queue by name
        GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                .queueName(QueueName)
                .build();
        GetQueueUrlResponse getQueueUrlResponse = sqsClient.getQueueUrl(getQueueUrlRequest);
        return getQueueUrlResponse.queueUrl();
    }

    /**
     * Deletes a message from the specified SQS queue.
     *
     * @param queueUrl      The URL of the SQS queue.
     * @param receiptHandle The receipt handle of the message to delete.
     */
    public void deleteMessage(String queueUrl, String receiptHandle) {
        // Delete the message after processing
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(getQueueUrl(queueUrl))
                .receiptHandle(receiptHandle)
                .build();

        sqsClient.deleteMessage(deleteRequest);
    }

    /**
     * Uploads a file to an S3 bucket and sends a message to an SQS queue.
     *
     * @param url         The original URL associated with the file.
     * @param newURL      The path to the output file to upload.
     * @param operation   The operation to perform.
     * @param BUCKET_NAME The name of the S3 bucket.
     * @param localAppID  The application ID for scoping.
     */
    public void uploadFileToS3(String url, String newURL, String operation, String BUCKET_NAME, String localAppID) {
        try {
            // Step 1: Check if newURL is a valid URL or an error message
            if (newURL.contains("Error:")) {
                // If it's an error message, skip the S3 upload and just send the message to SQS
                System.out.println("Error message detected: " + newURL);
                sendSqsMessage(url, newURL, operation, WORKERS_TO_MANAGER_QUEUE_NAME, localAppID);
            } else {
                // If it's a valid URL, proceed with uploading to S3
                File outputFile = new File(newURL); // Assuming newURL is the path to the output file
                String key = outputFile.getName(); // Use the file name as the S3 key
    
                // Create the S3 PutObjectRequest
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(key)
                        .build();
    
                // Upload the file to S3
                s3Client.putObject(putObjectRequest, outputFile.toPath());
                System.out.println("File uploaded to S3 with key: " + key);
    
                // Step 2: Create the S3 URL
                String s3Url = "https://" + BUCKET_NAME + ".s3.amazonaws.com/" + key;
    
                // Step 3: Send a message to SQS with the details
                sendSqsMessage(url, s3Url, operation, WORKERS_TO_MANAGER_QUEUE_NAME, localAppID);
            }
        } catch (Exception e) {
            // Handle any exception that occurs during the file upload or SQS message sending
            System.err.println("Error uploading file to S3 or sending message to SQS: " + e.getMessage());
        }
    }

    /**
     * Sends a message to the specified SQS queue with details about an operation and its associated URLs.
     *
     * @param url         The original URL.
     * @param s3Url       The S3 URL of the uploaded file.
     * @param operation   The operation to perform.
     * @param QUEUE_NAME  The name of the SQS queue.
     * @param localAppID  The application ID for scoping.
     */
    private void sendSqsMessage(String url, String s3Url, String operation, String QUEUE_NAME, String localAppID) {
        try {
            // Construct the message body
            String messageBody = "Operation: " + operation + " URL: " + url + " S3 URL: " + s3Url;

            // Send the message to the SQS queue
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(getQueueUrl(QUEUE_NAME + "_" + localAppID))
                    .messageBody(messageBody)
                    .build();

            sqsClient.sendMessage(sendMessageRequest);
            System.out.println("Message sent to SQS with URL: " + s3Url);
        } catch (Exception e) {
            // Handle any exception that occurs while sending the message to SQS
            System.err.println("Error sending message to SQS: " + e.getMessage());
        }
    }
}
