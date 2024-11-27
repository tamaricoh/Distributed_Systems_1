package com.example;
import java.io.*;
import java.util.Scanner;
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
            // Uploads the file to S3.
            String uploadedFilePath = uploadFileToS3(inputFileName);
            if (uploadedFilePath == null) {
                return;
            }

            // Checks if a Manager node is active on the EC2 cloud.
            // If it is not, the application will start the manager node.
            Manager manager = new Manager(inputFileName, n, 50000);

            // Sends a message to an SQS queue, stating the location of the file on S3
            sendSQSMessage("File uploaded to: " + uploadedFilePath, manager);

            // Checks an SQS queue for a message indicating the process is done and the response
            // (the summary file) is available on S3
            String summaryFileName = checkSQSQueue(); // Get the summary file name
            System.out.println("Manager Response: " + summaryFileName);

            // Creates an html file representing the results.
            generateHtmlSummary(outputFileName, summaryFileName);
    
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
        }
    }
    

    public static void generateHtmlSummary(String outputFileName, String summaryFileName) throws IOException {
        // Read the summary file content
        File summaryFile = new File(summaryFileName);
        if (!summaryFile.exists()) {
            throw new FileNotFoundException("Summary file not found: " + summaryFileName);
        }
        
        Scanner scanner = new Scanner(summaryFile);
        StringBuilder content = new StringBuilder();
    
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
    
            try {
                // Parse the line for operation, input file, and output file or exception
                String[] parts = line.split(",");
                String operation = parts[0].trim();
                String inputFile = parts[1].trim();
                String outputFileOrError = parts.length > 2 ? parts[2].trim() : "Unknown error";
    
                // Create HTML link tags for input and output files
                String inputLink = "<a href=\"" + inputFile + "\">" + inputFile + "</a>";
                String outputLink = outputFileOrError.contains("error") ? outputFileOrError
                        : "<a href=\"" + outputFileOrError + "\">" + outputFileOrError + "</a>";
    
                // Append the formatted line to the content
                content.append("<p>")
                       .append(operation).append(": ")
                       .append(inputLink).append(" ")
                       .append(outputLink)
                       .append("</p>\n");
            } catch (Exception e) {
                // If an error occurs while processing the line, handle gracefully
                content.append("<p>Error processing line: ").append(line).append("</p>\n");
            }
        }
        scanner.close();
    
        // Generate the HTML file
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName));
        writer.write("<html><body><h1>Processed File Summary</h1>");
        writer.write(content.toString());
        writer.write("</body></html>");
        writer.close();
    
        System.out.println("HTML summary created: " + outputFileName);
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
