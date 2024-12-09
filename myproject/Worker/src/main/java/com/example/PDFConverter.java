package com.example;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.io.FileWriter;
import java.net.MalformedURLException;
import java.net.URL;

public class PDFConverter {

    public static void convertToImage(String pdfPath, String outputImagePath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            BufferedImage image = pdfRenderer.renderImageWithDPI(0, 300);
            ImageIO.write(image, "png", new File(outputImagePath));
        }
    }

    public static void convertToText(String pdfPath, String outputTextPath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            
            try (Writer writer = new FileWriter(outputTextPath)) {
                writer.write(text);
            }
        }
    }

    // Add this new method for HTML conversion
    private static void convertToHTML(String pdfPath, String outputHTMLPath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            
            // Simple HTML conversion
            try (Writer writer = new FileWriter(outputHTMLPath)) {
                writer.write("<!DOCTYPE html>\n<html>\n<body>\n");
                writer.write("<pre>" + text + "</pre>\n");
                writer.write("</body>\n</html>");
            }
        }
    }

    public static String convertFromUrl(String url, String operation, String localAppID) throws IOException {
        // Generate a destination file name based on URL and localAppID
        String destinationFile = generateFileName(url, localAppID);
    
        // First, download the file
        String downloadResult = downloadFile(url, destinationFile);
        if (downloadResult != null) {
            // If the download fails, return the error message
            return downloadResult;
        }
    
        // Proceed with the conversion after downloading the file
        String baseFileName = destinationFile.replace(".pdf", "");
        String outputPath;
    
        try {
            switch (operation.toUpperCase()) {
                case "TOIMAGE":
                    outputPath = baseFileName + ".png";
                    convertToImage(destinationFile, outputPath);
                    break;
                case "TOTEXT":
                    outputPath = baseFileName + ".txt";
                    convertToText(destinationFile, outputPath);
                    break;
                case "TOHTML":
                    outputPath = baseFileName + ".html";
                    convertToHTML(destinationFile, outputPath);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation: " + operation);
            }
    
            // Return the URL of the output file (successful case)
            return new File(outputPath).toURI().toString();
    
        } catch (IOException e) {
            // Return an error message if conversion fails
            return "Error: Unable to convert PDF to " + operation + ". Reason: " + e.getMessage();
        }
    }
    
    /**
     * Generates a file name using the base name of the URL and localAppID.
     *
     * @param url       The URL of the file to be downloaded.
     * @param localAppID A unique identifier for the application.
     * @return A string representing the generated file name.
     */
    private static String generateFileName(String url, String localAppID) {
        String baseName = new File(url).getName(); // Extract the file name from URL
        if (!baseName.endsWith(".pdf")) {
            baseName += ".pdf"; // Ensure the extension is .pdf
        }
        return baseName.replace(".pdf", "_" + localAppID + ".pdf");
    }

    // Download function which returns a string indicating the result
    private static String downloadFile(String url, String destinationFile) {
        // Check if the file exists and delete it if needed
        File file = new File(destinationFile);
        if (file.exists()) {
            file.delete();
        }

        try {
            // Create a URL object from the provided URL string
            URL fileUrl = new URL(url);

            // Open an input stream from the URL (i.e., downloading the file)
            try (InputStream inputStream = fileUrl.openStream();
                FileOutputStream fileOutputStream = new FileOutputStream(destinationFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
                System.out.println("File downloaded successfully to: " + destinationFile);
                return null; // Indicate successful download
            }
        } catch (MalformedURLException e) {
            return "Error: Invalid URL format. " + e.getMessage();
        } catch (IOException e) {
            return "Error: Unable to download file. " + url + " === " + destinationFile + "====" +e.getMessage();
        }
    }
}
