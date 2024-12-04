package com.example;

import java.io.BufferedReader;
import java.io.File;
// import java.io.IOException;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.util.Base64;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesResponse;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.model.Message;


public class AWS {
    private final S3Client s3Client;
    private final SqsClient sqsClient;
    private final Ec2Client ec2Client;

    public static String ami = "ami-00e95a9222311e8ed";
    private int WorkerVisibilityTimeout = 10;
    private String dilimeter = " ";

    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;

    private static final AWS instance = new AWS();

    // Constructor initializes S3, SQS, and EC2 clients with the default region.
    private AWS() {
        String[] credentials = aws_credentials_loader();
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(credentials[0], credentials[1]);
        s3Client = S3Client.builder().credentialsProvider(StaticCredentialsProvider.create(awsCreds)).region(region1).build();
        sqsClient = SqsClient.builder().credentialsProvider(StaticCredentialsProvider.create(awsCreds)).region(region1).build();
        ec2Client = Ec2Client.builder().credentialsProvider(StaticCredentialsProvider.create(awsCreds)).region(region1).build();
    }

    /**
     * Provides a singleton instance of the AWS utility class.
     *
     * @return Singleton instance of AWS.
     */
    public static AWS getInstance() {
        return instance;
    }

    private static String[] aws_credentials_loader() {
        // Specify the file path
        String credentialsFilePath = "aws_credinatials.txt";

        // Load the properties file
         // Initialize variables to store credentials
        String[] creds = new String[2];

        // Read the file line by line
        try (BufferedReader reader = new BufferedReader(new FileReader(credentialsFilePath))) {
            String line;
            int i = 0;
            while ((line = reader.readLine()) != null) {
                // Split each line at the '=' character
                String[] parts = line.split("=", 2);
                creds[i] = parts[1];
                i++;
            }
        } catch (IOException e) {
            System.err.println("Error reading credentials file: " + e.getMessage());
        }
        return creds;
    }

    /**
     * Retrieves a message from the specified SQS queue with a visibility timeout.
     *
     * @param queueUrl The URL of the SQS queue.
     * @return The message, or null if no message is found.
     */
    public Message getMessage(String queueName) {
        try {
            // Receive a single message with a visibility timeout
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(getQueueUrl(queueName))
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(10) // Long polling for 10 seconds
                    .visibilityTimeout(WorkerVisibilityTimeout) // Make the message invisible to others for 10 seconds
                    .build();

            // Receive messages from the queue
            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

            if (messages.isEmpty()) {
                System.out.println("No messages available in the queue.");
                return null; // No message received
            }

            return messages.get(0); // Return the first message
        } catch (SqsException e) {
            System.err.println("Error receiving message: " + e.awsErrorDetails().errorMessage());
            return null;
        }
    }

    /**
     * Deletes a message from the specified SQS queue.
     *
     * @param queueUrl     The URL of the SQS queue.
     * @param receiptHandle The receipt handle of the message to delete.
     */
    public void deleteMessage(String queueUrl, String receiptHandle) {
        try {
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build();

            sqsClient.deleteMessage(deleteRequest);;
        } catch (SqsException e) {
            System.err.println("Error deleting message: " + e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * Sends a new message to the specified SQS queue.
     *
     * @param queueUrl The URL of the SQS queue.
     * @param messageBody The content of the new message.
     */
    public void sendMessage(String QueueName, String messageBody) {
        try {
            SendMessageRequest sendRequest = SendMessageRequest.builder()
                    .queueUrl(getQueueUrl(QueueName))
                    .messageBody(messageBody)
                    .build();

            sqsClient.sendMessage(sendRequest);
            System.out.println("Message sent successfully.");
        } catch (SqsException e) {
            System.err.println("Error sending message: " + e.awsErrorDetails().errorMessage());
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
     * Uploads a file to S3 and returns the object key.
     *
     * @param input_file_path Local path of the file to upload.
     * @param bucketName    Name of the S3 bucket.
     * @return The key of the uploaded file in S3, or null if the upload fails.
     * @throws IOException If an I/O error occurs.
     */
    public String uploadFileToS3(String input_file_path, String bucketName) {
        String s3Key = Paths.get(input_file_path).getFileName().toString();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            Path path = Paths.get(input_file_path);
            getInstance().s3Client.putObject(putObjectRequest, path);
            System.out.println("File uploaded successfully to S3: " + s3Key);
            return "s3://" + bucketName + "/" + s3Key;
        } catch (Exception e) {
            System.err.println("Unexpected error during file upload: " + e.getMessage());
            return null;
        }
    }
    //TODO::implement shutdown() which is supposed to shut off the ec2 machine....
    public static void shutdown() {
        
    }

}
