package com.example;

import java.util.UUID;

public class App {
    final static AWS aws = AWS.getInstance(); // Get the AWS instance.

    public static void main(String[] args) { 
        // args =  [ yourjar.jar, inputFileName, outputFileName, n, (terminate - optional) ]
        final String localApplicationID = UUID.randomUUID().toString();
        System.out.println("Local Application ID: " + localApplicationID);

        // Calculating the n = number of messages per worker
        int n = Integer.parseInt(args[args.length == 4 ? 3 : args.length - 2]);

        // Creat the S3 bucket for the local application
        String bucketS3Name = localApplicationID + "bucket";
        setup(bucketS3Name);

        // Creating the SQS queues to communicate with the manager
        // This queue is for all the local applications, only one like that exists.
        String toManagerQueue = AWS.TO_MANAGER_QUEUE_NAME;
        aws.createSQSQueue(toManagerQueue);  
        // 
        String fromManagerQueue = AWS.FROM_MANAGER_QUEUE_NAME + localApplicationID; // + localApplicationID to make the name singular
        aws.createSQSQueue(fromManagerQueue);

        // try {
        //     setup();
        //     createEC2();
        // } catch (Exception e) {
        //     e.printStackTrace();
        // }
    }

    // Create Buckets, Create Queues, Upload JARs to S3
    private static void setup(String bucketName) {
        System.out.println("[DEBUG] Create bucket if not exist.");
        aws.createBucketIfNotExists(aws.bucketName); // Creating the S3 bucket.
    }

    // Create an EC2 instance
    private static void createEC2() {
        String ec2Script = "#!/bin/bash\n" + "echo Hello World\n";
        String managerInstanceID = aws.createEC2(ec2Script, "thisIsJustAString", 1);
    }
}
