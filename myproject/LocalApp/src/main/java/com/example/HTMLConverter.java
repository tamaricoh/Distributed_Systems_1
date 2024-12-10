package com.example;

import java.io.*;
import java.net.URL;

public class HTMLConverter {

    private String inputFilePath;
    private String outputFilePath;

    // Constructor to initialize the input and output file paths
    public HTMLConverter(String inputFilePath, String outputFilePath) {
        this.inputFilePath = inputFilePath;
        this.outputFilePath = outputFilePath;
    }

    /*Builds the HTML header for the output file
    */
    private static String buildHtmlHeader() {
        return "<html>\n<head><title>PDF Processing Results</title></head>\n<body>\n" +
               "<h1>PDF Processing Results</h1>\n<ul>\n";
    }

    /*Builds the HTML footer for the output file*/
    private static String buildHtmlFooter() {
        return "</ul>\n</body>\n</html>";
    }

    /** Converts a line (operation, input URL, and output URL or error) into an HTML list item
    * If the new URL is valid, it creates a clickable link, otherwise it write the error message
    * @param line - a string array of format [operation, url, newUrl]
    */
    private static String addResponseToHtml(String[] line) {
        System.err.println(line[0] +"-------------");
        System.err.println(line[1] +"-------------");
        System.err.println(line[2] +"-------------");
        StringBuilder responseHtml = new StringBuilder();
        responseHtml.append("<li>\n");
        responseHtml.append("<strong>").append(line[0]).append("</strong>: ");
        responseHtml.append("<a href=\"").append(line[1]).append("\" target=\"_blank\">")
                    .append("Original PDF")
                    .append("</a> ");

        // Check if the new URL is valid or add an error message
        if (isValidUrl(line[2])) {
            responseHtml.append("-> <a href=\"").append(line[2]).append("\" target=\"_blank\">")
                        .append("Processed PDF")
                        .append("</a>");
        } else {
            responseHtml.append("Error: ").append(line[2]);
        }
        responseHtml.append("</li>\n");
        return responseHtml.toString();
    }

    /* Helper method to check if a URL is valid
    * It attempts to create a URL object and URI to check for syntax correctness
    */
    private static boolean isValidUrl(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Converts the content of the input text file to an HTML file
    // Reads each line from the input file, converts it to an HTML list item, 
    // and writes it to the output HTML file. If an error occurs during the process, 
    // the HTML file is deleted and the method returns false.
    public boolean convertToHTML() {
        BufferedReader reader = null;
        BufferedWriter writer = null;
        File outputFile = new File(outputFilePath);

        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            writer.write(buildHtmlHeader());
            File inputFile = new File(inputFilePath);
            reader = new BufferedReader(new FileReader(inputFile));
            String line;
            // Iterate over each line of the input file
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                System.out.println(line);
                writer.write(addResponseToHtml(parts));
            }
            writer.write(buildHtmlFooter());
            return true;

        } catch (IOException e) {
            // Handle errors: delete the .html file if something goes wrong
            System.err.println("Error occurred: " + e.getMessage());
            if (outputFile.exists()) {
                outputFile.delete();
            }
            return false;

        } finally { // Close the reader and writer
            try {
                if (reader != null) {
                    reader.close();
                }
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
