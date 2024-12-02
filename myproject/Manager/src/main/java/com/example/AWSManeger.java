package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
// import java.util.List;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification;
// import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

public class AWSManeger {
    private final S3Client s3Client;
    private final SqsClient sqsClient;

    public static String ami = "ami-00e95a9222311e8ed";

    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;

    private static final AWSManeger instance = new AWSManeger();

    // Constructor initializes S3, SQS, and EC2 clients with the default region.
    private AWSManeger() {
        s3Client = S3Client.builder().region(region1).build();
        sqsClient = SqsClient.builder().region(region1).build();
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
     * Retrieves a message from the specified SQS queue and processes it.
     * The message is expected to contain an operation and a URL, separated by a space.
     * After processing, the message is deleted from the queue.
     *
     * @param QUEUE_NAME The name of the SQS queue to retrieve the message from.
     * @return An array of strings where the first element is the operation and the second is the URL extracted from the message.
     *         Returns null if no message is found or an error occurs during processing.
     */
    public String[] getMessage(String QUEUE_NAME) {
        try {
            // Create a request to receive messages
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(getQueueUrl(QUEUE_NAME))
                    .maxNumberOfMessages(1) // Process one message at a time
                    .waitTimeSeconds(10) // Long polling
                    .build();

            // Receive messages from the queue
            var messages = sqsClient.receiveMessage(receiveRequest).messages();

            if (messages.isEmpty()) {
                System.out.println("No messages in the queue.");
                return null; // No message was processed
            }

            // Process the first message
            Message message = messages.get(0);
            String body = message.body();

            // Parse the message body (assumes the body format is fixed)
            String[] parts = body.split(" ");

            // Delete the message from the queue after processing
            deleteMessage(QUEUE_NAME, message.receiptHandle());

            return parts;
        } catch (Exception e) {
            System.err.println("Error while processing SQS message: " + e.getMessage());
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

    public String createEC2(String script, String tagName, int numberOfInstances) {
        Ec2Client ec2 = Ec2Client.builder().region(region2).build();

        // Configure the instance launch request
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.M4_LARGE)
                .imageId(ami)
                .maxCount(numberOfInstances)
                .minCount(1)
                .keyName("vockey")
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

    public String generateWorkerDataScript(String BUCKET_NAME, String jarFilePath, String LocalAppID) {
        String script = String.join("\n",
            "#!/bin/bash",
            "sudo yum update -y",
            "sudo yum install -y java-1.8.0-openjdk", // Install Java if needed
            "aws s3 cp s3://" + BUCKET_NAME + "/" + Paths.get(jarFilePath).getFileName() + " /home/ec2-user/manager.jar",
            "export LOCAL_APP_ID=" + LocalAppID // Set LOCAL_APP_ID environment variable
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
            // Create the bucket with the specified configuration
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
        } catch (S3Exception e) {
            System.out.println(e.getMessage());
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
}
