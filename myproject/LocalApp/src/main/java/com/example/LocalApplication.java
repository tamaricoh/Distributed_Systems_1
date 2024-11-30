package com.example;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.io.IOException;
// import java.nio.file.Files;
import java.nio.file.Paths;
// import java.nio.file.StandardCopyOption;
import java.nio.file.Path;

public class LocalApplication {

    // Replace your-bucket-name, your-access-key, and your-secret-key with your actual S3 bucket name and credentials.
    private static final String BUCKET_NAME = "your-bucket-name";  // Replace with your S3 bucket name
    private static final String REGION = "us-east-1";  // Replace with the desired AWS region
    private static final String ACCESS_KEY = "your-access-key";  // Replace with your AWS Access Key
    private static final String SECRET_KEY = "your-secret-key";  // Replace with your AWS Secret Key
    private static final String SQS_QUEUE_NAME = "your-queue-name";  // Replace with your SQS queue name

     // Create a single SQS client instance to be used across methods
     private static final SqsClient sqsClient = SqsClient.builder()
        // Manager will delete this message from the sqs
        .region(Region.of(REGION))
        .credentialsProvider(StaticCredentialsProvider.create(
             AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
        .build();

    private static final S3Client s3Client = S3Client.builder()
        .region(Region.of(REGION))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
        .build();

    // Sends a message to an SQS queue, stating the location of the file on S3
    public static void sendSQSMessage(String message) {
        try {
            // Get the queue URL
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(SQS_QUEUE_NAME)
                    .build();

            GetQueueUrlResponse queueUrlResponse = sqsClient.getQueueUrl(getQueueUrlRequest);
            String queueUrl = queueUrlResponse.queueUrl();

            // Send the message with the file location
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(message)
                    .build();

            sqsClient.sendMessage(sendMessageRequest);
            // Location of file or terminate
            System.out.println("SQS Message Sent:" + message);

        } catch (SqsException e) {
            System.err.println("Error sending SQS message: " + e.awsErrorDetails().errorMessage());
        }
    }

    // Checks an SQS queue for a message indicating the process is done and the response (the summary file) is available on S3.
    // Extracts the number of summary files from the message body.
    public static int checkSQSQueue() {
        try {
            // Get the queue URL
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(SQS_QUEUE_NAME)
                    .build();

            GetQueueUrlResponse queueUrlResponse = sqsClient.getQueueUrl(getQueueUrlRequest);
            String queueUrl = queueUrlResponse.queueUrl();

            // Receive messages from the queue
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1)  // Only retrieve one message at a time
                    .build();

            ReceiveMessageResponse receiveMessageResponse = sqsClient.receiveMessage(receiveMessageRequest);

            if (!receiveMessageResponse.messages().isEmpty()) {
                // Get the message body
                String messageBody = receiveMessageResponse.messages().get(0).body();
                System.out.println("Message received: " + messageBody);

                // Assuming the message contains the number of summary files in the format:
                // "Process is complete. There are NUM summary files ready for you."
                String[] parts = messageBody.split(" ");
                if (parts.length > 5) {
                    try {
                        return Integer.parseInt(parts[5]); // Extract the NUM part of the message
                    } catch (NumberFormatException e) {
                        System.err.println("Error extracting number from message.");
                    }
                }
            }

        } catch (SqsException e) {
            System.err.println("Error checking SQS queue: " + e.awsErrorDetails().errorMessage());
        }
        return -1;  // Return -1 if no valid message is found
    }

    // Uploads the file to S3 - returns the file path if the upload succeeds or null if it fails.
    public static String uploadFileToS3(String inputFileName) throws IOException {
        // Create the S3 object key (you can use any naming strategy)
        String s3Key = Paths.get(inputFileName).getFileName().toString();
        
        // Upload the file
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .build();

            Path path = Paths.get(inputFileName);
            s3Client.putObject(putObjectRequest, path);
            System.out.println("File uploaded successfully to S3: " + s3Key);
            return s3Key;
        } catch (S3Exception e) {
            System.err.println("Error uploading file to S3: " + e.awsErrorDetails().errorMessage());
            return null;
        }
    }

    // Method to process the file
    public static void processFile(String inputFileName, String outputFileName, int n) {
        try {

            // Checks if a Manager node is active on the EC2 cloud.
            // If it is not, the application will start the manager node.
            // Manager manager = new Manager(inputFileName, n, 50000);

            // Uploads the file to S3.
            String uploadedFilePath = uploadFileToS3(inputFileName);
            if (uploadedFilePath == null) {
                return;
            }

            // Sends a message to an SQS queue, stating the location of the file on S3
            sendSQSMessage(uploadedFilePath);

            // Checks an SQS queue for a message indicating the process is done and the response
            // (the summary file) is available on S3
            // int summaryFileNum = checkSQSQueue(); // Get the num of summary files-------------------
            int summaryFileNum = -1;

            // Loop until a valid number is retrieved
            while (summaryFileNum <= 0) {
                summaryFileNum = checkSQSQueue();
                if (summaryFileNum <= 0) {
                    System.out.println("No valid number found in the queue. Retrying...");
                    try {
                        Thread.sleep(1000); // Wait for 1 second before retrying
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Restore interrupted status
                        throw new RuntimeException("Thread was interrupted while waiting", e);
                    }
                }
            }

            // Pass the S3 bucket name to the HTMLConverter
            HTMLConverter htmlConverter = new HTMLConverter(summaryFileNum, s3Client, BUCKET_NAME);
            htmlConverter.generateHtmlFromMultipleTxtFiles(BUCKET_NAME, outputFileName);
    
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
        }
    }
    

    public static void terminateManager() {
        // Sends a termination message to the Manager via SQS.
        String terminationMessage = "Termination request received. Please stop the manager.";
        sendSQSMessage(terminationMessage);
        System.out.println("Termination message sent to the Manager.");
    }

    // Main method
    public static void main(String[] args) {

        if (args.length < 3) {
            System.out.println("No args: LocalApplication <inputFileName> <outputFileName> <n> [--terminate]");
            return;
        }

        String inputFileName = args[0];
        String outputFileName = args[1];
        int n = Integer.parseInt(args[2]);
        boolean terminate = args.length > 3 && args[3].equals("--terminate");

        // If terminate flag is passed, simulate manager termination
        if (terminate) {
            terminateManager();
        }

        processFile(inputFileName, outputFileName, n);
    }
}
