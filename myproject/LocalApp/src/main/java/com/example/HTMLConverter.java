package com.example;

import java.io.*;

public class HTMLConverter {
public static void generateHtmlFromTxt(String txtFilePath, String htmlFilePath) throws IOException {
        File txtFile = new File(txtFilePath);
        BufferedReader reader = new BufferedReader(new FileReader(txtFile));
        
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<html>\n<head><title>PDF Processing Results</title></head>\n<body>\n");
        htmlContent.append("<h1>PDF Processing Results</h1>\n<ul>\n");


        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",\\s*"); // Split by comma and optional spaces
            if (parts.length < 3) {
                System.err.println("Invalid format: " + line);
                continue;
            }

            String operation = parts[0];
            String inputFile = parts[1];  
            String outputFile = parts[2]; 
            
            // Create an HTML list item
            htmlContent.append("<li>\n");
            htmlContent.append("<strong>").append(operation).append("</strong>: ");

            // Add input file as a link
            htmlContent.append("<a href=\"").append(inputFile).append("\" target=\"_blank\">")
                       .append("Input File")
                       .append("</a> ");

            // Add output file or exception description
            if (outputFile.startsWith("http://") || outputFile.startsWith("https://")) {
                htmlContent.append("-> <a href=\"").append(outputFile).append("\" target=\"_blank\">")
                           .append("Output File")
                           .append("</a>");
            } else {
                htmlContent.append("-> ").append(outputFile);
            }

            htmlContent.append("</li>\n");
        }

        reader.close();

        // Complete the HTML content
        htmlContent.append("</ul>\n</body>\n</html>");

        // Write the HTML content to the output file
        BufferedWriter writer = new BufferedWriter(new FileWriter(htmlFilePath));
        writer.write(htmlContent.toString());
        writer.close();

        System.out.println("HTML file created: " + htmlFilePath);
    }

    // Example usage
    public static void main(String[] args) {
        try {
            // Calling the function with "t.txt" as input and "output.html" as output file path
            generateHtmlFromTxt("t.txt", "output.html");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
