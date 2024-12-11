package com.example;

import java.io.*;
import java.net.URL;

/**
 * A utility class to convert a text file containing PDF processing results into an HTML file.
 * Each line of the input file specifies an operation, an input URL, and either an output URL or an error message.
 */
public class HTMLConverter {

    private String inputFilePath;
    private String outputFilePath;

    /**
     * Constructor to initialize the file paths for input and output.
     *
     * @param inputFilePath  the path to the input text file
     * @param outputFilePath the path to the output HTML file
     */
    public HTMLConverter(String inputFilePath, String outputFilePath) {
        this.inputFilePath = inputFilePath;
        this.outputFilePath = outputFilePath;
    }

    /**
     * Builds the HTML header for the output file.
     * The header includes the opening tags for the HTML structure and a title.
     *
     * @return the HTML header as a string
     */
    private static String buildHtmlHeader() {
        return "<html>\n<head><title>PDF Processing Results</title></head>\n<body>\n" +
               "<h1>PDF Processing Results</h1>\n<ul>\n";
    }

    /**
     * Builds the HTML footer for the output file.
     * The footer includes the closing tags for the HTML structure.
     *
     * @return the HTML footer as a string
     */
    private static String buildHtmlFooter() {
        return "</ul>\n</body>\n</html>";
    }

    /**
     * Converts a line of input into an HTML list item.
     * Each line is expected to be a string array containing an operation name, an input URL, and either an output URL or an error message.
     * If the output URL is valid, it is included as a clickable link. Otherwise, the error message is displayed.
     *
     * @param line a string array of format [operation, inputUrl, outputUrl/error]
     * @return the HTML list item as a string
     */
    private static String addResponseToHtml(String[] line) {
        System.err.println(line[0] + "-------------");
        System.err.println(line[1] + "-------------");
        System.err.println(line[2] + "-------------");
        StringBuilder responseHtml = new StringBuilder();
        responseHtml.append("<li>\n");
        responseHtml.append("<strong>").append(line[0]).append("</strong>: ");
        responseHtml.append("<a href=\"").append(line[1]).append("\" target=\"_blank\">")
                    .append("Original PDF")
                    .append("</a> ");

        if (isValidUrl(line[2])) {
            responseHtml.append("-> <a href=\"").append(line[2]).append("\" target=\"_blank\">")
                        .append("Processed PDF")
                        .append("</a>");
        } else {
            String err = line[2].replaceAll("_", " ");
            responseHtml.append(err);
        }
        responseHtml.append("</li>\n");
        return responseHtml.toString();
    }

    /**
     * Helper method to check if a URL is valid.
     * Attempts to create a URL object and a URI to verify syntax correctness.
     *
     * @param url the URL string to validate
     * @return true if the URL is valid, false otherwise
     */
    private static boolean isValidUrl(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Converts the content of the input text file into an HTML file.
     * Reads each line from the input file, formats it into an HTML list item, and writes it to the output file.
     * If an error occurs, the partially written output file is deleted.
     *
     * @return true if the conversion is successful, false otherwise
     */
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
            System.err.println("Error occurred: " + e.getMessage());
            if (outputFile.exists()) {
                outputFile.delete();
            }
            return false;

        } finally {
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
