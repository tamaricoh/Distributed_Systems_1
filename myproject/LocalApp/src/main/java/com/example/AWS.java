package com.example;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.model.Tag;
public class AWS {
    private final S3Client s3;
    private final SqsClient sqs;
    private final Ec2Client ec2;

    public static String ami = "ami-00e95a9222311e8ed";

    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;

    private static final AWS instance = new AWS();

    // Constructor initializes S3, SQS, and EC2 clients with the default region.
    private AWS() {
        s3 = S3Client.builder().region(region1).build();
        sqs = SqsClient.builder().region(region1).build();
        ec2 = Ec2Client.builder().region(region1).build();
    }

    /**
     * Provides a singleton instance of the AWS utility class.
     *
     * @return Singleton instance of AWS.
     */
    public static AWS getInstance() {
        return instance;
    }

    public String bucketName = "text_file_bucket";

    /**
     * Creates an S3 bucket if it does not already exist.
     *
     * @param bucketName Name of the bucket to create.
     */
    public void createBucketIfNotExists(String bucketName) {
        try {
            // Create the bucket with the specified configuration
            s3.createBucket(CreateBucketRequest
                    .builder()
                    .bucket(bucketName)
                    .createBucketConfiguration(
                            CreateBucketConfiguration.builder()
                                    .locationConstraint(BucketLocationConstraint.US_WEST_2)
                                    .build())
                    .build());

            // Wait until the bucket exists
            s3.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
        } catch (S3Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Launches an EC2 instance with the specified script, tag, and number of instances.
     *
     * @param script           User-data script for the instance.
     * @param tagName          Name tag for the instance.
     * @param numberOfInstances Number of instances to launch.
     * @return Instance ID of the first launched instance.
     */
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

    /**
     * Creates an SQS queue with the specified name.
     *
     * @param queueName Name of the queue to create.
     */
    public void createSqsQueue(String queueName) {
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(queueName)
                .build();
        sqs.createQueue(createQueueRequest);
    }

    /**
     * Sends a message to the specified SQS queue.
     *
     * @param message   Content of the message.
     * @param queueName Name of the SQS queue.
     */
    public static void sendSQSMessage(String message, String queueName) {
        try {
            // Retrieve the queue URL
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();

            GetQueueUrlResponse queueUrlResponse = getInstance().sqs.getQueueUrl(getQueueUrlRequest);
            String queueUrl = queueUrlResponse.queueUrl();

            // Send the message
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(message)
                    .build();
            getInstance().sqs.sendMessage(sendMessageRequest);

            System.out.println("SQS Message Sent: " + message);
        } catch (SqsException e) {
            System.err.println("Error sending SQS message: " + e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * Checks the specified SQS queue for a message indicating processing completion.
     *
     * @param queueName Name of the SQS queue.
     * @return Number of summary files mentioned in the message, or -1 if no valid message is found.
     */
    public static int checkSQSQueue(String queueName) {
        try {
            // Retrieve the queue URL
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();

            GetQueueUrlResponse queueUrlResponse = getInstance().sqs.getQueueUrl(getQueueUrlRequest);
            String queueUrl = queueUrlResponse.queueUrl();

            // Receive a message
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1)
                    .build();

            ReceiveMessageResponse receiveMessageResponse = getInstance().sqs.receiveMessage(receiveMessageRequest);

            if (!receiveMessageResponse.messages().isEmpty()) {
                String messageBody = receiveMessageResponse.messages().get(0).body();
                System.out.println("Message received: " + messageBody);

                String[] parts = messageBody.split(" ");
                if (parts.length > 5) {
                    try {
                        return Integer.parseInt(parts[5]);
                    } catch (NumberFormatException e) {
                        System.err.println("Error extracting number from message.");
                    }
                }
            }
        } catch (SqsException e) {
            System.err.println("Error checking SQS queue: " + e.awsErrorDetails().errorMessage());
        }
        return -1;
    }

    /**
     * Uploads a file to S3 and returns the object key.
     *
     * @param inputFileName Local path of the file to upload.
     * @param bucketName    Name of the S3 bucket.
     * @return The key of the uploaded file in S3, or null if the upload fails.
     * @throws IOException If an I/O error occurs.
     */
    public static String uploadFileToS3(String inputFileName, String bucketName) throws IOException {
        String s3Key = Paths.get(inputFileName).getFileName().toString();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            Path path = Paths.get(inputFileName);
            getInstance().s3.putObject(putObjectRequest, path);
            System.out.println("File uploaded successfully to S3: " + s3Key);
            return s3Key;
        } catch (S3Exception e) {
            System.err.println("Error uploading file to S3: " + e.awsErrorDetails().errorMessage());
            return null;
        }
    }
}
