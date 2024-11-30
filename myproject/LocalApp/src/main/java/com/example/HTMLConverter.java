package com.example;

import java.io.*;

public class HTMLConverter {

    private int numOfResponses; 

    // Constructor to initialize numOfResponses
    public HTMLConverter(int numOfResponses) {
        this.numOfResponses = numOfResponses;
        
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

    private void TxtToHtml(BufferedReader reader, StringBuilder htmlContent) throws IOException {
        String line;

        while ((line = reader.readLine()) != null) {
            htmlContent.append(addResponseToHtml(line));
        }
    }

    private static String buildHtmlFooter() {
        return "</ul>\n</body>\n</html>";
    }

    // Main function to generate the HTML from a text file
    public void generateHtmlFromMultipleTxtFiles(String baseFilePath, String htmlFilePath) throws IOException {
        StringBuilder htmlContent = new StringBuilder();
    
        htmlContent.append(buildHtmlHeader());
    
        // Loop through `numOfResponses` and process each file
        for (int i = 0; i < numOfResponses; i++) {
            // here it will get the txt file from s3 or sqs in the future.
            String txtFilePath = baseFilePath + (i + 1) + ".txt";
            File txtFile = new File(txtFilePath);
    
            if (txtFile.exists()) {
                // Add responses from the current file
                BufferedReader reader = new BufferedReader(new FileReader(txtFile));
                TxtToHtml(reader, htmlContent);
                reader.close();
            } else {
                System.err.println("File not found: " + txtFilePath);
            }
        }
    
        htmlContent.append(buildHtmlFooter());
    
        // Write the HTML content to the output file
        BufferedWriter writer = new BufferedWriter(new FileWriter(htmlFilePath));
        writer.write(htmlContent.toString());
        writer.close();
    
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
