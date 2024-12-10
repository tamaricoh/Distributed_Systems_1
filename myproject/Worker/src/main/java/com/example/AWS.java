package com.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.net.URL;


import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.model.Message;


public class AWS {
    private final S3Client s3Client;
    private final SqsClient sqsClient;
    private final Ec2Client ec2Client;
    private static final String INSTANCE_METADATA_URL = "http://169.254.169.254/latest/meta-data/instance-id";
    public static String ami = "ami-00e95a9222311e8ed";
    private int WorkerVisibilityTimeout = 2;
    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;
    private static final AWS instance = new AWS();

    // Constructor initializes S3, SQS, and EC2 clients with the default region.
    private AWS() {
        s3Client = S3Client.builder().region(region1).build();
        sqsClient = SqsClient.builder().region(region1).build();
        ec2Client =  Ec2Client.builder().region(region2).build();
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

    public void sendSQSMessage(String message, String queueName) {
        try{
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(getQueueUrl(queueName))
                .messageBody(message)
                .build();
                getInstance().sqsClient.sendMessage(sendMessageRequest);
            System.err.println("Message from LocalApp sent to " + queueName + " queue: " + message);
        }catch (SqsException e){
                System.err.println("[DEBUG]: Error trying to send message to queue " + queueName + ", Error Message: " + e.awsErrorDetails().errorMessage());
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

            sqsClient.deleteMessage(deleteRequest);
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
    public void sendMessage(String queueName, String message) {
        try{
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(getQueueUrl(queueName))
                .messageBody(message)
                .build();
                getInstance().sqsClient.sendMessage(sendMessageRequest);
            System.err.println("Message from Worker sent to " + queueName + " queue: " + message);
        }catch (SqsException e){
                System.err.println("[DEBUG]: Error trying to send message to queue " + queueName + ", Error Message: " + e.awsErrorDetails().errorMessage());
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

    public String uploadFileToS3(String input_file_path, String bucketName) {
        String s3Key = Paths.get(input_file_path).getFileName().toString();
    
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    //.acl(ObjectCannedACL.PUBLIC_READ) // Explicitly set public read access
                    .build();
    
            Path path = Paths.get(input_file_path);
            getInstance().s3Client.putObject(putObjectRequest, RequestBody.fromFile(path));
            System.out.println("File uploaded successfully to S3: " + s3Key);
    
            // Return the public URL of the uploaded file
            return "https://" + bucketName + ".s3." + region1 + ".amazonaws.com/" + s3Key;
        } catch (Exception e) {
            return "Error: Unexpected error during file upload " + e.getMessage();
        }
    }
    
    public void shutdown() {
        String instanceId = getInstanceId();
        try {
            // Create a termination request
            TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();

            // Execute the termination
            TerminateInstancesResponse response = ec2Client.terminateInstances(terminateRequest);
            response.terminatingInstances().forEach(instance -> 
                System.out.println("Instance " + instance.instanceId() + " is now " + instance.currentState().name())
            );
        } catch (Exception e) {
            System.err.println("Failed to terminate instance: " + e.getMessage());
            
        }
    }

    public static String getInstanceId() {
        try {
            URL url = new URL(INSTANCE_METADATA_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    return reader.readLine();
                }
            } else {
                throw new RuntimeException("Failed to fetch instance ID. HTTP response code: " + responseCode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error fetching instance ID: " + e.getMessage(), e);
        }
    }

}
