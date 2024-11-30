package com.example;
// import java.io.BufferedReader;
// import java.io.BufferedWriter;
// import java.io.FileOutputStream;
// import java.io.FileReader;
// import java.io.FileWriter;
// // import java.io.BufferedReader;
// // import java.io.FileReader;
// import java.io.IOException;
// import java.io.InputStream;
// import java.net.MalformedURLException;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.nio.file.StandardOpenOption;
// import java.nio.file.FileAlreadyExistsException;
// import java.nio.file.Files;
// import java.util.Base64;
// import java.util.List;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;

import java.io.*;
import java.net.*;

import software.amazon.awssdk.services.sqs.*;
import software.amazon.awssdk.services.sqs.model.*;
// import software.amazon.awssdk.services.ec2.Ec2Client;
// import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
// import software.amazon.awssdk.services.ec2.model.Ec2Exception;
// import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification;
// import software.amazon.awssdk.services.ec2.model.Instance;
// import software.amazon.awssdk.services.ec2.model.InstanceType;
// import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
// import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.regions.Region;
// import software.amazon.awssdk.services.ec2.model.Tag;

public class Worker implements Runnable {

    private static final String MANAGER_TO_WORKERS_QUEUE_NAME = "Manager-To-Workers";
    private static final String WORKERS_TO_MANAGER_QUEUE_NAME = "Workers-To-Manager";
    private static final String WORKERS_TO_MANAGER_BUCKET_NAME = "Workers-To-Manager";

    String linesPerWorker = System.getenv("LINES_PER_WORKER");
    String localAppID = System.getenv("LOCAL_APP_ID");

    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;

    private static SqsClient sqsClient;
    private static S3Client s3Client;

    // private final String managerSQS;
    // private String operation;
    // private String pdfFilePath; // Path to the input file                     // Number of lines to process
    // private String outputFilePath;
    private Boolean terminate;

    // Constructor
    public Worker(String sqsURL) {
        sqsClient = SqsClient.builder().region(region1).build();
        s3Client = S3Client.builder().region(region1).build();
        // this.managerSQS = sqsURL;
        this.terminate = false;
    }

    @Override
    public void run() {
        while(!terminate){
            String msg = getMessage();
            if (msg != ""){
                try{
                    String[] parts = msg.split(" ");
                    String operation = parts[0]; // Extract operation
                    String url = parts[1];      // Extract URL
                    downloadFile(url);
                    String newURL = processFile(operation);

                    // If newURL is empty, handle the error - write it!!!!!!!!!!1
    
                    uploadFile(url, newURL, operation);
                    // check if terminate??
    
                } catch (Exception e) {
                    System.err.println("Error while processing the task: " + e.getMessage());
                    // handle the error - write it!!!!!!!!!!1 to sqs
                    
                }
            }
        }
    }
    
    private String getMessage() {
        try {
            // Create a request to receive messages
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(getQueueUrl(MANAGER_TO_WORKERS_QUEUE_NAME + "/" + localAppID))
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
            String operation = parts[1]; // Extract operation
            String url = parts[3];      // Extract URL

            System.out.println("Received message:");
            System.out.println("Operation: " + operation);
            System.out.println("URL: " + url);

            // Delete the message from the queue after processing
            deleteMessage(MANAGER_TO_WORKERS_QUEUE_NAME + "/" + localAppID, message.receiptHandle());

            return operation + " " + url;
        } catch (Exception e) {
            System.err.println("Error while processing SQS message: " + e.getMessage());
            return "";
        }
    }

    private String getQueueUrl(String QueueName) {
        // Get the URL of the SQS queue by name
        GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                .queueName(QueueName)
                .build();
        GetQueueUrlResponse getQueueUrlResponse = sqsClient.getQueueUrl(getQueueUrlRequest);
        return getQueueUrlResponse.queueUrl();
    }
    private void deleteMessage(String queueUrl, String receiptHandle) {
        // Delete the message after processing
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(getQueueUrl(queueUrl))
                .receiptHandle(receiptHandle)
                .build();

        sqsClient.deleteMessage(deleteRequest);
    }


    private void downloadFile(String url) {
        // Define the destination where the file will be saved locally
        String destinationFile = "downloaded_file.pdf";  // Modify this as needed
        
        // Check if the file exists and delete it if needed
        File file = new File(destinationFile);
        if (file.exists()) {
            System.out.println("File already exists, it will be overwritten.");
            // Optionally, delete the existing file if you want to overwrite it manually before downloading
            file.delete();
        }

        try {
            // Create a URL object from the provided URL string
            URL fileUrl = new URL(url);

            // Open an input stream from the URL (i.e., downloading the file)
            try (InputStream inputStream = fileUrl.openStream();
                 FileOutputStream fileOutputStream = new FileOutputStream(destinationFile)) {
                
                // Create a buffer for reading data from the URL
                byte[] buffer = new byte[4096];
                int bytesRead;

                // Read from the input stream and write to the output stream (local file)
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
                System.out.println("File downloaded successfully to: " + destinationFile);
            }

        } catch (MalformedURLException e) {
            System.err.println("Invalid URL format: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error downloading file: " + e.getMessage());
        }
    }

    private String processFile(String operation) throws IOException {
        String pdfPath = "downloaded_file.pdf";  // This is the path to the file you just downloaded

        // Check if the file exists
        File file = new File(pdfPath);
        if (!file.exists()) {
            System.err.println("The file " + pdfPath + " does not exist.");
            return "";
        }
        return PDFConverter.convertFromUrl(pdfPath, operation);
    }

    private void uploadFile(String url, String newURL, String operation) {
        try {
            // Step 1: Upload the output file to S3
            File outputFile = new File(newURL); // Assuming newURL is the path to the output file
            String key = outputFile.getName(); // Use the file name as the S3 key

            // Create the S3 PutObjectRequest
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(WORKERS_TO_MANAGER_BUCKET_NAME + "/" + localAppID)
                    .key(key)
                    .build();

            // Upload the file to S3
            s3Client.putObject(putObjectRequest, outputFile.toPath());
            System.out.println("File uploaded to S3 with key: " + key);

            // Step 2: Create the S3 URL
            String s3Url = "https://" + WORKERS_TO_MANAGER_BUCKET_NAME + "/" + localAppID + ".s3.amazonaws.com/" + key;

            // Step 3: Send a message to SQS with the details
            sendSqsMessage(url, s3Url, operation);

        } catch (Exception e) {
            // Handle any exception that occurs during the file upload or SQS message sending
            System.err.println("Error uploading file to S3 or sending message to SQS: " + e.getMessage());
        }
    }

    private void sendSqsMessage(String url, String s3Url, String operation) {
        try {
            // Construct the message body
            String messageBody = "Operation: " + operation + " URL: " + url + " S3 URL: " + s3Url;

            // Send the message to the SQS queue
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(getQueueUrl(WORKERS_TO_MANAGER_QUEUE_NAME + "/" + localAppID)) // Assuming you have this method defined
                    .messageBody(messageBody)
                    .build();

            sqsClient.sendMessage(sendMessageRequest);
            System.out.println("Message sent to SQS with URL: " + s3Url);
        } catch (Exception e) {
            // Handle any exception that occurs while sending the message to SQS
            System.err.println("Error sending message to SQS: " + e.getMessage());
        }
    }
    private void terminate() {}
}