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

public class LocalApplication{

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
        String BUCKET_NAME = "Text_File_Bucket";
        String SQS_QUEUE_NAME = "Client2Manager";
        AWS awsTool = AWS.getInstance();
        if (args.length < 3) {
            System.out.println("No args: LocalApplication <inputFileName> <outputFileName> <n> [--terminate]");
            return;
        }

        String inputFileName = args[0];
        String outputFileName = args[1];
        int n = Integer.parseInt(args[2]);
        boolean terminate = args.length > 3 && args[3].equals("terminate");

        initalizeManager();
        AWS.uploadFileToS3(inputFileName, BUCKET_NAME);
        Aws

        // If terminate flag is passed, simulate manager termination
        if (terminate) {
            terminateManager();
        }

        processFile(inputFileName, outputFileName, n);
    }
}
