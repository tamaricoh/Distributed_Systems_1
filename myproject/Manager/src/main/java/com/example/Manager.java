package com.example;
import java.io.BufferedWriter;
// import java.io.BufferedReader;
// import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;

import software.amazon.awssdk.services.sqs.*;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.model.Tag;

public class Manager implements Runnable {
    private static final String LOCALAPP_TO_MANAGER_QUEUE_NAME = "LocalApp-To-Manager";
    private static final String MANAGER_TO_LOCALAPP_QUEUE_NAME = "Manager_To_LocalApp";
    private static final String MANAGER_TO_WORKER_QUEUE_NAME = "Manager-To-Workers";
    private static final String WORKER_TO_MANAGER_QUEUE_NAME = "Workers-To-Manager";
    private static final String LOCALAPP_TO_MANAGER_BUCKET_NAME = "LocalApp-To-Manager";
    private static final String WORKER_TO_MANAGER_BUCKET_NAME = "Workers-To-Manager";
    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;
    private static final InstanceType workerType = InstanceType.T2_LARGE;
    private static final String workerScript = "";
    // "#!/bin/bash\n" + 
    // "sudo yum install -y java-1.8.0-openjdk\n" + // Install OpenJDK 8
    // "aws s3 cp s3://" + BUCKET_JAR_NAME + "/" + workerJarName + " /home/ec2-user/" + workerJarName + "\n"+ // Copy the jar from an S3 bucket to the local /home/ec2-user/ directory TODO: so it will be ok if a LA deletes the jar and it's bucket?
    // "java -jar /home/ec2-user/"+ workerJarName +"\n";

    private final static String ami = ""; //"ami-00e95a9222311e8ed";
    private static final String WORKER_TAG = "Worker";


    private static SqsClient sqsClient;
    private static S3Client s3Client;
    private static Ec2Client ec2Client;
    // private int linesPerWorker;
    // private int numOfTasks;
    // private String LocalAppID;

    public Manager() {
        sqsClient = SqsClient.builder().region(region1).build();
        s3Client = S3Client.builder().region(region1).build();
        ec2Client = Ec2Client.builder().region(region2).build();
        // this.numOfTasks = -1;
        // this.LocalAppID = "";
    }

    // Constructor
    // public Manager(String inputFilePath, int linesPerWorker, long workerTimeout) {
    //     this.inputFilePath = inputFilePath;
    //     this.linesPerWorker = linesPerWorker;
    //     this.workerTimeout = workerTimeout;
    //     this.threadPool = Executors.newCachedThreadPool(); // Dynamically allocate threads
    // }

    @Override
    public void run() {
        String LocalAppID = "";
        int numOfTasks = 0;
        // Step 1: Receive message from the LOCALAPP_TO_MANAGER queue
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(getQueueUrl(LOCALAPP_TO_MANAGER_QUEUE_NAME))
                .maxNumberOfMessages(1)
                .waitTimeSeconds(20) // Long polling to wait for a message
                .build();

        ReceiveMessageResponse receiveMessageResponse = sqsClient.receiveMessage(receiveMessageRequest);

        List<Message> messages = receiveMessageResponse.messages();
        if (messages.isEmpty()) {
            System.out.println("No messages available in the queue.");
            return;
        }

        // Step 2: Process the received message
        for (Message message : messages) {
            String messageBody = message.body(); // The entire message body

            // Parse the message body into components
            if (messageBody != null && !messageBody.isEmpty()) {
                String[] parts = messageBody.split(" "); // Assuming space-separated values
                if (parts.length == 3) {
                    String s3Location = parts[0];  // Extract S3 location
                    String linesPerWorkerStr = parts[1]; // Extract LinesPerWorker
                    
                    // Convert LinesPerWorker to an integer
                    int linesPerWorker = Integer.parseInt(linesPerWorkerStr);
                    // Step 3: Download the file from S3
                    if (s3Location != null && !s3Location.isEmpty()) {
                        // Call the downloadFileFromS3 method and capture the returned Path
                        Path downloadedFilePath = downloadFileFromS3(s3Location);
                        // 
                        // Check if the file was downloaded successfully
                        if (downloadedFilePath != null) {
                            System.out.println("File downloaded successfully to: " + downloadedFilePath);
                            LocalAppID = downloadedFilePath.toString();
                            // Process the file and create SQS messages for workers
                            numOfTasks = processFileAndCreateSQSMessages(downloadedFilePath, linesPerWorker, LocalAppID);
                        } else {
                            System.err.println("Failed to download file from S3 for location: " + s3Location);
                        }
                    }
                } else {
                    System.err.println("Malformed message: " + messageBody);
                }
            }

            // Delete the message from the LOCALAPP_TO_MANAGER queue after processing
            deleteMessage(message, LOCALAPP_TO_MANAGER_QUEUE_NAME);
        }

        if (LocalAppID != ""){
            String summaryFileName = "Summary" + LocalAppID + ".txt";
            Path summaryFilePath = Paths.get(summaryFileName);

            try {
                Files.createFile(summaryFilePath); // Create the file if it doesn't already exist
                System.out.println("Summary file created: " + summaryFileName);
            } catch (FileAlreadyExistsException e) {
                System.out.println("Summary file already exists: " + summaryFileName);
            } catch (IOException e) {
                System.err.println("Error creating summary file: " + e.getMessage());
                return;
            }

            int taskDone = 0;
            // Loop until all tasks are done
            while (taskDone < numOfTasks) {
                // Receive a message from the WORKER_TO_MANAGER_QUEUE_NAME
                ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                        .queueUrl(getQueueUrl(WORKER_TO_MANAGER_QUEUE_NAME))
                        .maxNumberOfMessages(1) // Read one message at a time
                        .waitTimeSeconds(10) // Long-polling for efficiency
                        .build();

                List<Message> messagesFromWorkers = sqsClient.receiveMessage(receiveRequest).messages();

                if (!messagesFromWorkers.isEmpty()) {
                    // Process the message
                    Message message = messagesFromWorkers.get(0);
                    String messageBody = message.body(); // Assuming body format: <operation> <oldurl> <newurl>
                    System.out.println("Received message: " + messageBody);

                    // Append the message body to the summary file
                    try (BufferedWriter writer = Files.newBufferedWriter(summaryFilePath, StandardOpenOption.APPEND)) {
                        writer.write(messageBody);
                        writer.newLine(); // Add a newline after each entry
                        System.out.println("Appended to summary file: " + messageBody);
                    } catch (IOException e) {
                        System.err.println("Error writing to summary file: " + e.getMessage());
                    }

                    // Delete the message from the queue
                    DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                            .queueUrl(getQueueUrl(WORKER_TO_MANAGER_QUEUE_NAME))
                            .receiptHandle(message.receiptHandle())
                            .build();

                    sqsClient.deleteMessage(deleteRequest);
                    System.out.println("Deleted message from queue.");

                    // Increment the task done counter
                    taskDone++;
                } else {
                    System.out.println("No messages received. Waiting...");
                }
            }

            System.out.println("All tasks completed. Summary file updated: " + summaryFileName);
            uploadToS3(summaryFilePath);
            postSQSMessage(summaryFileName);
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

    public Path downloadFileFromS3(String s3Location) {
        try {
            // Create a GetObject request
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(LOCALAPP_TO_MANAGER_BUCKET_NAME)
                    .key(s3Location)
                    .build();

            // Specify the local file path to download the file to
            Path destinationPath = Paths.get("downloaded-file"); // Modify this to specify the local download path

            // Download the file from S3 to the local path
            s3Client.getObject(getObjectRequest, destinationPath);

            // Check if the file was successfully downloaded by checking its existence
            if (Files.exists(destinationPath)) {
                System.out.println("File downloaded successfully from S3 to: " + destinationPath);
                return destinationPath; // Return the path if the file exists
            } else {
                System.err.println("File download failed, file not found at the destination.");
                return null;
            }
        } catch (S3Exception e) {
            System.err.println("Error downloading file from S3: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return null;
        }
    }

    private void deleteMessage(Message message, String QueueName) {
        // Delete the message from queue after processing
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(getQueueUrl(QueueName))
                .receiptHandle(message.receiptHandle())
                .build();
        sqsClient.deleteMessage(deleteMessageRequest);
        System.out.println("Message deleted from SQS queue.");
    }

    private int processFileAndCreateSQSMessages(Path filePath, int linesPerWorker, String LocalAppID) {
        // this.LocalAppID = filePath.toString();
        int count = 0;
        int numOfTasks = -1;
        String msg = "";
        try {
            // Read the file line by line
            numOfTasks = 0;
            List<String> lines = Files.readAllLines(filePath);
            
            for (String line : lines) {
                // Split the line into operation and URL based on tab character
                String[] parts = line.split("\t");
                if (parts.length == 2) {
                    String operation = parts[0]; // Operation
                    String url = parts[1]; // URL
                    numOfTasks++;
                    // Create and send the SQS message
                    msg = createSQSMessage(operation, url, LocalAppID);
                    sendSQSMessageToWorker(msg, LocalAppID);
                    count++;
                    if (count == linesPerWorker){
                        count = 0;
                        createWorkerInstances(linesPerWorker, LocalAppID);
                    }
                } else {
                    System.err.println("Invalid line format: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        }
        return numOfTasks;
    }
    
    private String createSQSMessage(String operation, String url, String LocalAppID) {
        return "Operation: " + operation + " URL: " + url + " LocalAppID: " + LocalAppID;
    }

    private void createWorkerInstances(int linesPerWorker, String LocalAppID) {
        System.out.println("Creating a worker instance.");
        String modifiedScript = workerScript + "\n" + "LINES_PER_WORKER=" + linesPerWorker + "\n" + "LOCAL_APP_ID="+ LocalAppID;
        // Create a single worker EC2 instance
        createEC2(modifiedScript, WORKER_TAG, workerType);
    }

    public static void createEC2(String script, String tagName, InstanceType insType) {
        // Create the request to launch a single EC2 instance
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(insType) // The EC2 instance type 
                .imageId(ami) // The AMI ID for your EC2 instance
                .maxCount(1) // Always create 1 instance
                .minCount(1) // Always create 1 instance
                .keyName("vockey") // Your EC2 key pair for SSH access
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build()) // IAM role
                .userData(Base64.getEncoder().encodeToString(script.getBytes())) // Base64 encode the bootstrap script
                .build();
        
        // Send the request to launch the EC2 instance
        RunInstancesResponse response = ec2Client.runInstances(runRequest);
        
        // Extract the instance ID and tag the launched instance
        List<Instance> instances = response.instances();
        String instanceId = null; // Default value to handle case if no instances are returned
        for (Instance instance : instances) {
            instanceId = instance.instanceId(); // Capture the instance ID of the first instance created
        
            // Create a tag to identify the EC2 instance
            software.amazon.awssdk.services.ec2.model.Tag tag = Tag.builder()
                    .key("Name")
                    .value(tagName) // The tag name you want for the EC2 instance
                    .build();
            
            // Create the request to apply the tag
            CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                    .resources(instanceId) // The EC2 instance to tag
                    .tags(tag) // The tags to apply
                    .build();
    
            try {
                ec2Client.createTags(tagRequest); // Apply the tag
                System.out.printf("[DEBUG] Successfully started EC2 instance %s with tag %s\n", instanceId, tagName);
            } catch (Ec2Exception e) {
                System.err.println("[ERROR] " + e.getMessage());
                System.exit(1); // Exit on failure (you might want to handle this more gracefully)
            }
        }
    }

    private void sendSQSMessageToWorker(String msg, String LocalAppID) {
        // Create the SQS message to send to the worker instance
        String message = "New task for worker: " + msg;
    
        // Assuming the queue URL is already set (replace with actual queue URL)
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(getQueueUrl(MANAGER_TO_WORKER_QUEUE_NAME + " " + LocalAppID)) 
                .messageBody(message)     // Message body with worker instance ID and task
                .build();

        // Send the message to SQS
        sqsClient.sendMessage(sendMessageRequest);
        System.out.println("SQS message sent to worker: " + msg);
    }

    private static void uploadToS3(Path filePath) {
        try {
            // Create a PutObjectRequest to specify the file and destination bucket/key
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(LOCALAPP_TO_MANAGER_BUCKET_NAME) 
                .key("summary.txt")  // The S3 key (filename) to use
                .build();

            // Upload the file to S3
            s3Client.putObject(putObjectRequest, filePath);

            System.out.println("Summary file successfully uploaded to S3.");
        } catch (S3Exception e) {
            System.err.println("Error uploading file to S3: " + e.getMessage());
        }
    }

    private void postSQSMessage(String summaryS3Url) {
        String messageBody = "Summary file uploaded to S3 at: " + summaryS3Url;

        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
            .queueUrl(getQueueUrl(MANAGER_TO_LOCALAPP_QUEUE_NAME))
            .messageBody(messageBody)
            .build();

        try {
            sqsClient.sendMessage(sendMessageRequest);
            System.out.println("Message posted to SQS with summary S3 URL.");
        } catch (SqsException e) {
            System.err.println("Error posting message to SQS: " + e.getMessage());
        }
    }

    // Main method for local execution
    public static void main(String[] args) {
    
    }
}