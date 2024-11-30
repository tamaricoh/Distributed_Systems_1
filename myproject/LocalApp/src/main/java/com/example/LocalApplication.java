package com.example;
import java.io.*;
// import java.util.Scanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
// import com.example.Manager;


public class LocalApplication {

    // Sends a message to an SQS queue, stating the location of the file on S3
    public static void sendSQSMessage(String message, Manager manager) {
        manager.run();
        System.out.println("Manager message: " + message);
    }

    public static int checkSQSQueue() {
        // Checks an SQS queue for a message indicating the process is done and the response (the summary file) is available on S3.
        // In the manager's message, we will receive the number of responses in S3 that contain our outputs, and from them, we will assemble the HTML.
        return 5;  
    }

    // Uploads the file to S3
    // >>>>  For now, just returns the file path
    public static String uploadFileToS3(String inputFileName) throws IOException {
        File inputFile = new File(inputFileName);
        Path destination = Paths.get("./uploads", inputFile.getName());
        Files.copy(inputFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
        return destination.toString();
    }

    // Method to process the file
    public static void processFile(String inputFileName, String outputFileName, int n) {
        try {

            // Checks if a Manager node is active on the EC2 cloud.
            // If it is not, the application will start the manager node.
            Manager manager = new Manager(inputFileName, n, 50000);

            // Uploads the file to S3.
            String uploadedFilePath = uploadFileToS3(inputFileName);
            if (uploadedFilePath == null) {
                return;
            }

            // Sends a message to an SQS queue, stating the location of the file on S3
            sendSQSMessage(uploadedFilePath, manager);

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

            HTMLConverter htmlConverter = new HTMLConverter(summaryFileNum);
            htmlConverter.generateHtmlFromMultipleTxtFiles("", outputFileName);
    
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
        }
    }
    

    // Method to handle the termination (simulated)
    public static void terminateManager() {
        // Sends a termination message to the Manager.
        System.out.println("Manager terminated.");
    }

    // Main method
    public static void main(String[] args) {

        if (args.length < 3) {
            System.out.println("No args: LocalApplication <inputFileName> <outputFileName> <n> [--terminate]");
            return;
        }

        String inputFileName = args[0];
        String outputFileName = args[1];
        int n = Integer.parseInt(args[2]);
        boolean terminate = args.length > 3 && args[3].equals("--terminate");

        // If terminate flag is passed, simulate manager termination
        if (terminate) {
            terminateManager();
        }

        processFile(inputFileName, outputFileName, n);
    }
}
