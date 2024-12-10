package com.example;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
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
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Filter;
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
        ec2 =  Ec2Client.builder().region(region2).build();
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

    public String createEC2(String script, String tagName, int numberOfInstances) {
        // Configure the instance launch request
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.M4_LARGE)
                .imageId(ami)
                .maxCount(numberOfInstances)
                .minCount(1)
                .keyName("vockey")
                .keyName("Test")
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
     * Checks if there is any running EC2 instance with the tag "Name" = "Manager".
     *
     * @return true if such an instance exists, false otherwise.
     */
    public boolean isManagerInstanceRunning() {
        try {
                Thread.sleep(15000);
        } catch (InterruptedException e) {
                e.printStackTrace();
        }
        // Define a filter for the tag "Name=Manager"
        Filter tagFilter = Filter.builder()
                .name("tag:Name")
                .values("Manager") // We are looking for instances with the "Name" tag set to "Manager"
                .build();
    
        // Define a filter for the instance state "running"
        Filter stateFilter = Filter.builder()
                .name("instance-state-name")
                .values("running") // We want to find instances that are "running"
                .build();
    
        // Build the describe instances request with the filters
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(tagFilter, stateFilter)
                .build();
    
        // Retrieve the instances
        DescribeInstancesResponse response = getInstance().ec2.describeInstances(request);
        List<Reservation> reservations = response.reservations();

        // Check if there are any instances matching the filters
        for (Reservation reservation : reservations) {
            for (Instance instance : reservation.instances()) {
                // Log instance ID and its state for better debugging
                System.out.println("[DEBUG] Found instance: " + instance.instanceId() + " with state: " + instance.state().name());

                // Check if the instance is in the RUNNING state
                if (instance.state().name().equals(InstanceStateName.RUNNING)) {
                    System.out.println("[DEBUG] Manager instance is already running with Instance ID: " + instance.instanceId());
                    return true;
                    }
                }
            }
        return false;
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
    public void sendSQSMessage(String queueName, String message) {
        try{
                SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                        .queueUrl(getQueueUrl(queueName))
                        .messageBody(message)
                        .build();
                        getInstance().sqs.sendMessage(sendMessageRequest);
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
    public static String getQueueUrl(String QueueName) {
        // Get the URL of the SQS queue by name
        GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                .queueName(QueueName)
                .build();
        GetQueueUrlResponse getQueueUrlResponse = getInstance().sqs.getQueueUrl(getQueueUrlRequest);
        return getQueueUrlResponse.queueUrl();
    }

    /**
     * Checks the specified SQS queue for a message indicating processing completion.
     * The message format should be a JSON string in the form <inputfile>:::<file_location_in_S3>.
     *
     * @param queueName Name of the SQS queue.
     * @param inputfile Name of the uploaded file.
     * @return the processed file key in s3 as a String, or "FileNotFound" if no matching message is found.
     */
    public String checkSQSQueue(String queueName, String input_file_name) {
        try {
            // Retrieve the queue URL
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();

            GetQueueUrlResponse queueUrlResponse = getInstance().sqs.getQueueUrl(getQueueUrlRequest);
            String queueUrl = queueUrlResponse.queueUrl();

            // Receive up to 10 messages
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .build();

            ReceiveMessageResponse receiveMessageResponse = getInstance().sqs.receiveMessage(receiveMessageRequest);

            // Loop through the received messages
            for (Message message : receiveMessageResponse.messages()) {
                String messageBody = message.body();
                String[] parts = messageBody.split(" ");
                if (parts[0].contentEquals(input_file_name)) {
                    return parts[1];
                }
                else if (parts[0].contentEquals("terminate")){
                    return "terminate";
                }
            }
        } catch (SqsException e) {
            System.err.println("Error checking SQS queue: " + e.awsErrorDetails().errorMessage());
        }
        return "FileNotFound"; // Return this if no matching message is found
    }




    /**
     * Uploads a file to S3 and returns the object key.
     *
     * @param inputFileName Local path of the file to upload.
     * @param bucketName    Name of the S3 bucket.
     * @return The key of the uploaded file in S3, or null if the upload fails.
     * @throws IOException If an I/O error occurs.
     */
    public String uploadFileToS3(String input_file_path, String bucketName) {
        String s3Key = Paths.get(input_file_path).getFileName().toString();
        if (s3Key.contentEquals("terminate") || s3Key.contentEquals("FileNoTFound")){
            return null;
        }

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            Path path = Paths.get(input_file_path);
            getInstance().s3.putObject(putObjectRequest, path);
            System.out.println("File uploaded successfully to S3: " + s3Key);
            return s3Key;
        } catch (Exception e) {
            System.err.println("Unexpected error during file upload: " + e.getMessage());
            return null;
        }
    }


    /**
     * Downloads a file from an S3 bucket to the local system.
     *
     * @param bucketName The name of the S3 bucket containing the file.
     * @param fileKey    The key (name) of the file in the S3 bucket.
     * @param outputFilePath The local file path where the downloaded file will be saved.
     * @return true if the download is successful, false otherwise.
     * @throws IOException If an I/O error occurs during the file save process.
     */
    public boolean downloadFileFromS3(String bucketName, String fileKey, String outputFilePath) {
        try {
            // Create a GetObjectRequest to specify the bucket and key
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            // Perform the file download
            getInstance().s3.getObject(getObjectRequest, Paths.get(outputFilePath));

            System.out.println("File downloaded successfully from S3: " + fileKey + " to " + outputFilePath);
            return true;
        } catch (Exception e) {
            System.err.println("Unexpected error during file download: " + e.getMessage());
            return false;
        }
    }

    /**
 * Uploads a JAR file to an S3 bucket if it does not already exist in the bucket.
 * Returns the full S3 file path of the uploaded file.
 *
 * @param filePath   Path to the JAR file on the local machine.
 * @param bucketName Name of the S3 bucket.
 * @return The full S3 file path of the uploaded JAR file.
 */
public String uploadJar(String filePath, String bucketName) {
    // Extract the file name from the file path
    Path path = Paths.get(filePath);
    String fileName = path.getFileName().toString();

    try {
        // Check if the file already exists in the bucket
        boolean fileExists = checkIfFileExistsInS3(bucketName, fileName);

        if (fileExists) {
            System.out.println("File already exists in the bucket. fileName: " + fileName);
        } else {
            // If the file does not exist, upload it
            String fileKey = uploadFileToS3(filePath, bucketName);
            if (fileKey != null) {
                System.out.println("File successfully uploaded to the bucket. fileName: " + fileName);
                return fileKey;
            }
            else {
                System.out.println("Unexpected error during file upload: " + fileName);
                return "";
            }
        }
        // Return the S3 path if the file already exists
        return fileName;
    } catch (S3Exception e) {
        throw new RuntimeException("Error during file upload process: " + e.awsErrorDetails().errorMessage(), e);
    }
}

    /**
     * Checks if a file with the specified name exists in the given S3 bucket.
     *
     * @param bucketName Name of the S3 bucket.
     * @param fileName   Name of the file to check.
     * @return True if the file exists, false otherwise.
     */
    private boolean checkIfFileExistsInS3(String bucketName, String fileName) {
        // List objects in the bucket and check for a match with the file name
        ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(fileName) // Search with the file name as a prefix for efficiency
                .build();

        ListObjectsV2Response response = s3.listObjectsV2(listObjectsRequest);
        for (S3Object object : response.contents()) {
            if (object.key().equals(fileName)) {
                return true; // File exists
            }
        }
        return false; // File does not exist
    }

    public String generateManagerUserDataScript(String bucketName, String jar_key, String worker_jar_key) {
        String script = String.join("\n",
        "#!/bin/bash",
        "set -e", // Exit immediately if a command exits with a non-zero status
        "LOG_FILE=/var/log/manager-setup.log",
        "exec > >(tee -a $LOG_FILE) 2>&1", // Redirect all output to log file
        "echo \"Starting EC2 setup\"",

        // Install necessary software
        "sudo yum update -y",
        "sudo yum install -y java-1.8.0-openjdk",
        "sudo yum install -y aws-cli",

        // Prepare the working directory
        "WORK_DIR=/home/ec2-user/manager",
        "mkdir -p $WORK_DIR",
        "cd $WORK_DIR",

        // Download the JAR file
        "echo \"Downloading JAR file from S3...\"",
        "aws s3 cp s3://" + bucketName + "/" + jar_key + " ./manager.jar",

        // Execute the JAR
        "echo \"Running the Manager application...\"",
        "java -jar ./manager.jar " + worker_jar_key + " > $WORK_DIR/app.log 2>&1 &", // Run the shaded JAR ///TAMAR
        "echo \"Manager setup complete\""
    );
    System.out.println("worker jar key: " + worker_jar_key);
        return script;
    }

    
    

}
