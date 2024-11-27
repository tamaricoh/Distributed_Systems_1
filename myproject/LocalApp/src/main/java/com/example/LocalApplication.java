package com.example;
import java.io.*;
import java.util.Scanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


public class LocalApplication {

    // Sends a message to an SQS queue, stating the location of the file on S3
    public static void sendSQSMessage(String message) {
        System.out.println("Manager message: " + message);
    }

    public static String checkSQSQueue() {
        //Checks an SQS queue for a message indicating the process is done and the response (the summary file) is available on S3.
        return "Processing complete. Summary file available.";
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
            String uploadedFilePath = uploadFileToS3(inputFileName);
            if (uploadedFilePath == null) {
                return;
            }
    
            sendSQSMessage("File uploaded to: " + uploadedFilePath);
    
            String managerResponse = checkSQSQueue();
            System.out.println(managerResponse);
    
            generateHtmlSummary(outputFileName);
    
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
        }
    }
    

    public static void generateHtmlSummary(String outputFileName) throws IOException {
        // Read the output file content
        File outputFile = new File(outputFileName);
        Scanner scanner = new Scanner(outputFile);
        StringBuilder content = new StringBuilder();
        while (scanner.hasNextLine()) {
            content.append(scanner.nextLine()).append("\n");
        }
        scanner.close();
    
        // Create HTML file from the content
        String htmlFileName = outputFileName.replace(".txt", ".html");
        BufferedWriter writer = new BufferedWriter(new FileWriter(htmlFileName));
        writer.write("<html><body><h1>Processed File Summary</h1><pre>" + content.toString() + "</pre></body></html>");
        writer.close();
    
        System.out.println("HTML summary created: " + htmlFileName);
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
