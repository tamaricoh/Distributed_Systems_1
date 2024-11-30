package com.example;

import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import java.io.*;
import java.util.List;


public class HTMLConverter {

    private int numOfResponses; 
    private S3Client s3Client;
    private String s3BucketName;

    // Constructor to initialize numOfResponses and S3Client
    public HTMLConverter(int numOfResponses, S3Client s3Client, String s3BucketName) {
        this.numOfResponses = numOfResponses;
        this.s3Client = s3Client;
        this.s3BucketName = s3BucketName;
    }

    private static String buildHtmlHeader() {
        return "<html>\n<head><title>PDF Processing Results</title></head>\n<body>\n" +
               "<h1>PDF Processing Results</h1>\n<ul>\n";
    }

    // Function to add a response file to the HTML
    private static String addResponseToHtml(String line) {
        StringBuilder responseHtml = new StringBuilder();

        String[] parts = line.split(",\\s*"); // Split by comma and optional spaces
        if (parts.length < 3) {
            System.err.println("Invalid format: " + line);
            return ""; // Skip invalid lines
        }

        String operation = parts[0];
        String inputFile = parts[1];
        String outputFile = parts[2];

        responseHtml.append("<li>\n");
        responseHtml.append("<strong>").append(operation).append("</strong>: ");
        responseHtml.append("<a href=\"").append(inputFile).append("\" target=\"_blank\">")
                    .append("Input File")
                    .append("</a> ");

        if (outputFile.startsWith("http://") || outputFile.startsWith("https://")) {
            responseHtml.append("-> <a href=\"").append(outputFile).append("\" target=\"_blank\">")
                        .append("Output File")
                        .append("</a>");
        } else {
            responseHtml.append("-> ").append(outputFile);
        }

        responseHtml.append("</li>\n");

        return responseHtml.toString();
    }

    // Method to convert text from S3 to HTML
    private void TxtToHtml(InputStream s3InputStream, StringBuilder htmlContent) throws IOException {
        // Read the content of the S3 file into a string
        BufferedReader reader = new BufferedReader(new InputStreamReader(s3InputStream));
        String line;

        // Convert each line to HTML and append it
        while ((line = reader.readLine()) != null) {
            htmlContent.append(addResponseToHtml(line));
        }

        reader.close();
    }

    private static String buildHtmlFooter() {
        return "</ul>\n</body>\n</html>";
    }

    // Main function to generate HTML from multiple S3 text files
    public void generateHtmlFromMultipleTxtFiles(String prefix, String htmlFilePath) throws IOException {
        StringBuilder htmlContent = new StringBuilder();

        // Build HTML header
        htmlContent.append(buildHtmlHeader());

        // List the objects in the S3 bucket (limit the number of files processed)
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .bucket(s3BucketName)
                .prefix(prefix) // Optional prefix filtering (e.g., if the files are stored under a specific folder)
                .build();

        ListObjectsResponse listObjectsResponse = s3Client.listObjects(listObjectsRequest);
        List<S3Object> objects = listObjectsResponse.contents();

        // Process each file from S3
        int processedFiles = 0;
        for (S3Object object : objects) {
            if (object.key().endsWith(".txt") && processedFiles < numOfResponses) {
                // Get the S3 object content
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(s3BucketName)
                        .key(object.key()) // Use the object's key (file name) directly
                        .build();

                // Fetch the object content as an InputStream
                InputStream s3InputStream = s3Client.getObject(getObjectRequest);
                TxtToHtml(s3InputStream, htmlContent);  // Convert the content to HTML
                processedFiles++;
            }
        }

        // Append HTML footer
        htmlContent.append(buildHtmlFooter());

        // Write the HTML content to the output file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(htmlFilePath))) {
            writer.write(htmlContent.toString());
        }

        System.out.println("HTML file created: " + htmlFilePath);
    }

// public static void generateHtmlFromTxt(String txtFilePath, String htmlFilePath) throws IOException {
//         File txtFile = new File(txtFilePath);
//         BufferedReader reader = new BufferedReader(new FileReader(txtFile));
        
//         StringBuilder htmlContent = new StringBuilder();
//         htmlContent.append("<html>\n<head><title>PDF Processing Results</title></head>\n<body>\n");
//         htmlContent.append("<h1>PDF Processing Results</h1>\n<ul>\n");


//         String line;
//         while ((line = reader.readLine()) != null) {
//             String[] parts = line.split(",\\s*"); // Split by comma and optional spaces
//             if (parts.length < 3) {
//                 System.err.println("Invalid format: " + line);
//                 continue;
//             }

//             String operation = parts[0];
//             String inputFile = parts[1];  
//             String outputFile = parts[2]; 
            
//             htmlContent.append("<li>\n");
//             htmlContent.append("<strong>").append(operation).append("</strong>: ");

//             htmlContent.append("<a href=\"").append(inputFile).append("\" target=\"_blank\">")
//                        .append("Input File")
//                        .append("</a> ");

//             if (outputFile.startsWith("http://") || outputFile.startsWith("https://")) {
//                 htmlContent.append("-> <a href=\"").append(outputFile).append("\" target=\"_blank\">")
//                            .append("Output File")
//                            .append("</a>");
//             } else {
//                 htmlContent.append("-> ").append(outputFile);
//             }

//             htmlContent.append("</li>\n");
//         }

//         reader.close();

//         // Complete the HTML content
//         htmlContent.append("</ul>\n</body>\n</html>");

//         // Write the HTML content to the output file
//         BufferedWriter writer = new BufferedWriter(new FileWriter(htmlFilePath));
//         writer.write(htmlContent.toString());
//         writer.close();

//         System.out.println("HTML file created: " + htmlFilePath);
//     }
}
