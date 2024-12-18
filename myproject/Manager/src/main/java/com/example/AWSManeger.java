package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

public class AWSManeger {
    private final S3Client s3Client;
    private final SqsClient sqsClient;
    private final Ec2Client ec2Client;
    private int WorkerVisibilityTimeout = 2;
    public static String ami = "ami-00e95a9222311e8ed";

    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;

    private static final AWSManeger instance = new AWSManeger();

    // Constructor initializes S3, SQS, and EC2 clients with the default region.
    private AWSManeger() {
        s3Client = S3Client.builder().region(region1).build();
        sqsClient = SqsClient.builder().region(region1).build();
        ec2Client = Ec2Client.builder().region(region2).build();
    }

    /**
     * Provides a singleton instance of the AWS utility class.
     *
     * @return Singleton instance of AWS.
     */
    public static AWSManeger getInstance() {
        return instance;
    }

    /**
     * Retrieves a message from the specified SQS queue with a visibility timeout.
     *
     * @param queueName The URL of the SQS queue.
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
     * @param queueName     The name of the SQS queue.
     * @param receiptHandle The receipt handle of the message to delete.
     */
    public void deleteMessage(String queueName, String receiptHandle) {
        try {
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(getQueueUrl(queueName))
                    .receiptHandle(receiptHandle)
                    .build();

            sqsClient.deleteMessage(deleteRequest);
        } catch (SqsException e) {
            System.err.println("Error deleting message: " + e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * Downloads a file from an S3 bucket to a local path.
     * 
     * This method retrieves a file from the specified S3 bucket and saves it to the local filesystem.
     * The file is downloaded to a default local path, but the path can be modified as needed.
     * After downloading, the method checks whether the file exists locally and returns the path to the file.
     *
     * @param s3Location The key (path) of the file in the S3 bucket to download.
     * @param BUCKET_NAME The name of the S3 bucket where the file is stored.
     * @return The local file path where the file was downloaded, or null if the download failed or the file was not found.
     */
    public Path downloadFileFromS3(String s3Location, String BUCKET_NAME) {
    try {
        // Create a GetObject request
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Location)
                .build();

        // Specify the local file path to download the file to
        Path destinationPath = Paths.get("downloaded-file");

        // Download the file from S3 to an input stream
        ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);

        // Write the file to the local destination
        Files.copy(s3Object, destinationPath, StandardCopyOption.REPLACE_EXISTING);

        // Ensure the file was successfully downloaded
        if (Files.exists(destinationPath)) {
            System.out.println("File downloaded successfully from S3 to: " + destinationPath);
            return destinationPath;
        } else {
            System.err.println("File download failed, file not found at the destination.");
            return null;
        }
    } catch (S3Exception e) {
        System.err.println("Error downloading file from S3: " + e.getMessage());
        return null;
    } catch (IOException e) {
        System.err.println("Error writing file to local system: " + e.getMessage());
        return null;
    } catch (Exception e) {
        System.err.println("Unexpected error: " + e.getMessage());
        return null;
    }
}

    /**
     * Uploads a file to S3 and returns the object key.
     *
     * @param inputFileName Local path of the file to upload.
     * @param bucketName    Name of the S3 bucket.
     * @return The key of the uploaded file in S3, or null if the upload fails.
     * @throws IOException If an I/O error occurs.
     */
    public String uploadFileToS3(String inputFileName, String bucketName) {
        String s3Key = Paths.get(inputFileName).getFileName().toString();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            Path path = Paths.get(inputFileName);
            getInstance().s3Client.putObject(putObjectRequest, path);
            System.out.println("File uploaded successfully to S3: " + s3Key);
            return s3Key;
        } catch (Exception e) {
            System.err.println("Unexpected error during file upload: " + e.getMessage());
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

    public String createEC2(String script, String tagName, int numberOfInstances) {
        Ec2Client ec2 = Ec2Client.builder().region(region2).build();

        // Configure the instance launch request
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.M4_LARGE)
                .imageId(ami)
                .maxCount(numberOfInstances)
                .minCount(1)
                .keyName("vockey")
                //.keyName("testing")
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build())
                .userData(Base64.getEncoder().encodeToString((script).getBytes()))
                .build();

        // Launch the instances
        RunInstancesResponse response = ec2.runInstances(runRequest);
        String instanceId = response.instances().get(0).instanceId();

        // Tag the instance with the provided tag
        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(Tag.builder()
                        .key("Name")
                        .value(tagName)
                        .build())
                .build();

        try {
            ec2.createTags(tagRequest);
            System.out.printf("[DEBUG] Successfully started EC2 instance %s based on AMI %s\n", instanceId, ami);
        } catch (Ec2Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.exit(1);
        }
        return instanceId;
    }

    public String generateWorkerDataScript(String bucketName, String jarKey, String localAppId) {
        String script = String.join("\n",
            "#!/bin/bash",
            "set -e", // Exit immediately if a command exits with a non-zero status
            "LOG_FILE=/var/log/worker-setup.log",
            "exec > >(tee -a $LOG_FILE) 2>&1", // Redirect all output to log file
            "echo \"Starting EC2 setup for Worker...\"",
    
            // Install necessary software
            "sudo yum update -y",
            "sudo yum install -y java-1.8.0-openjdk",
            "sudo yum install -y aws-cli",
    
            // Prepare the working directory
            "WORK_DIR=/home/ec2-user/worker",
            "mkdir -p $WORK_DIR",
            "cd $WORK_DIR",
    
            // Download the JAR file
            "echo \"Downloading Worker JAR file from S3...\"",
            "aws s3 cp s3://" + bucketName + "/" + jarKey + " ./worker.jar",
    
            // Execute the JAR with LocalAppID as an argument
            "echo \"Running the Worker application...\"",
            "java -jar ./worker.jar " + localAppId + " > $WORK_DIR/app.log 2>&1 &", // Pass LocalAppID to the JAR // TAMAR
    
            "echo \"Worker setup complete\""
        );
        return script;
    }

    public boolean checkIfFileExistsInS3(String BUCKET_NAME, String fileName) {
        // List objects in the bucket and check for a match with the file name
        ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                .bucket(BUCKET_NAME)
                .prefix(fileName) // Search with the file name as a prefix for efficiency
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(listObjectsRequest);
        for (S3Object object : response.contents()) {
            if (object.key().equals(fileName)) {
                return true; // File exists
            }
        }
        return false; // File does not exist
    }

    /**
     * Creates an S3 bucket if it does not already exist.
     *
     * @param bucketName Name of the bucket to create.
     */
    public void createBucketIfNotExists(String bucketName) {
    try {
        // Create the bucket with public access settings
        s3Client.createBucket(CreateBucketRequest
                .builder()
                .bucket(bucketName)
                .createBucketConfiguration(
                        CreateBucketConfiguration.builder()
                                .locationConstraint(BucketLocationConstraint.US_WEST_2)
                                .build())
                .build());

        // Wait until the bucket exists
        s3Client.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                .bucket(bucketName)
                .build());

        // Set bucket policy to allow public read access
        String bucketPolicy = "{\"Version\":\"2012-10-17\"," +
                "\"Statement\":[{" +
                "\"Sid\":\"PublicReadGetObject\"," +
                "\"Effect\":\"Allow\"," +
                "\"Principal\":\"*\"," +
                "\"Action\":\"s3:GetObject\"," +
                "\"Resource\":\"arn:aws:s3:::" + bucketName + "/*\"" +
                "}]}";

        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(bucketName)
                .policy(bucketPolicy)
                .build());

        // Disable block public access settings
        s3Client.putPublicAccessBlock(PutPublicAccessBlockRequest.builder()
                .bucket(bucketName)
                .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder()
                        .blockPublicAcls(false)
                        .blockPublicPolicy(false)
                        .ignorePublicAcls(false)
                        .restrictPublicBuckets(false)
                        .build())
                .build());

    } catch (S3Exception e) {
        System.out.println("Error creating public bucket: " + e.getMessage());
    }
}

    /**
     * Creates an SQS queue with the specified name.
     *
     * @param queueName Name of the queue to create.
     */
    public void createSqsQueue(String queueName) {
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(queueName)
                .build();
        sqsClient.createQueue(createQueueRequest);
    }

    /**
     * Deletes an SQS queue given its name.
     * This method retrieves the queue URL using the provided queue name and sends a delete request.
     * 
     * @param queueName The name of the SQS queue to delete.
     */
    public void deleteQueue(String queueName) {
        try {
            DeleteQueueRequest deleteQueueRequest = DeleteQueueRequest.builder()
                .queueUrl(getQueueUrl(queueName))  // Set the URL of the queue to delete
                .build();
            sqsClient.deleteQueue(deleteQueueRequest);
        } catch (Exception e) {
            System.err.println("Error deleting queue: " + e.getMessage());
        }
    }

    /**
     * Deletes an S3 bucket given its name.
     * This method first checks if the bucket is empty, and if not, it attempts to delete all objects in the bucket before deleting the bucket itself.
     * 
     * @param bucketName The name of the S3 bucket to delete.
     */
    public void deleteBucket(String bucketName) {
        try {
            // Step 1: Check if the bucket exists and is empty
            ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();
            
            ListObjectsV2Response listObjectsResponse = s3Client.listObjectsV2(listObjectsRequest);

            // Step 2: If the bucket is not empty, delete all objects in the bucket
            if (!listObjectsResponse.contents().isEmpty()) {
                for (S3Object object : listObjectsResponse.contents()) {
                    DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(delete -> delete.objects(o -> o.key(object.key())))
                        .build();
                    s3Client.deleteObjects(deleteObjectsRequest);
                }
            }

            // Step 3: Delete the bucket after its objects have been removed (or if it was already empty)
            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder()
                .bucket(bucketName)
                .build();
            
            s3Client.deleteBucket(deleteBucketRequest);
        } catch (Exception e) {
            // Catch any exceptions that occur during the delete process and print the error message
            System.err.println("Error deleting bucket: " + e.getMessage());
        }
    }

    /**
     * Shuts down an EC2 instance by terminating it.
     * This method retrieves the instance ID, creates a termination request, 
     * and executes it. It prints the instance ID and its current state upon 
     * successful termination. If an error occurs during the termination 
     * process, the exception is caught and an error message is logged.
     */
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

    /**
     * Retrieves the instance ID of the current EC2 instance.
     * This method makes an HTTP GET request to the EC2 instance metadata service
     * to fetch the instance ID. If the request is successful, the instance ID is
     * returned. If the request fails or an error occurs, a runtime exception is thrown.
     * 
     * @return The instance ID as a string.
     * @throws RuntimeException if the instance ID cannot be fetched or an error occurs.
     */
    public static String getInstanceId() {
        try {
            URL url = new URL("http://169.254.169.254/latest/meta-data/instance-id");
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
