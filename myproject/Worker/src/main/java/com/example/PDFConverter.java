package com.example;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.io.FileWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

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

    public static String convertFromUrl(String url, String operation, String localAppID, String file_location) throws IOException {
        // Generate a destination file name based on URL and localAppID
        String originalPdfPath = file_location + "/" + generateFileName(url, localAppID);
    
        // First, download the file
        String downloadResult = downloadFile(url, originalPdfPath);
        if (downloadResult != null) {
            // If the download fails, return the error message
            return downloadResult;
        }
    
        // Proceed with the conversion after downloading the file
        String baseFileName = originalPdfPath.replace(".pdf", "");
        String outputPath;
    
        try {
            switch (operation.toUpperCase()) {
                case "TOIMAGE":
                    outputPath = baseFileName + ".png";
                    convertToImage(originalPdfPath, outputPath);
                    break;
                case "TOTEXT":
                    outputPath = baseFileName + ".txt";
                    convertToText(originalPdfPath, outputPath);
                    break;
                case "TOHTML":
                    outputPath = baseFileName + ".html";
                    convertToHTML(originalPdfPath, outputPath);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation: " + operation);
            }

            File originalPdfFile = new File(originalPdfPath);
            if (originalPdfFile.exists()) {
                originalPdfFile.delete();
            }
    
            // Return the path of the converted file
            return outputPath;
    
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
        try {
            // Use URI to handle URL encoding and special characters
            URI uri = new URI(url);
            String path = uri.getPath();
            String baseName = Paths.get(path).getFileName().toString();
            
            // Sanitize filename to only contain lowercase letters, digits, and hyphens
            String sanitizedName = baseName.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")  // Replace non-matching characters with hyphen
                .replaceAll("-+", "-")         // Replace multiple consecutive hyphens with single hyphen
                .replaceAll("(^-|-$)", "");    // Remove leading or trailing hyphens
            
            // Ensure .pdf extension
            if (!sanitizedName.toLowerCase().endsWith(".pdf")) {
                sanitizedName += ".pdf";
            }
            
            // Append localAppID
            return sanitizedName.replace(".pdf", "_" + localAppID + ".pdf");
        } catch (URISyntaxException e) {
            // Fallback to a safe random filename if URI parsing fails
            return UUID.randomUUID().toString() + "_" + localAppID + ".pdf";
        }
    }

    // Download function which returns a string indicating the result
    private static String downloadFile(String url, String destinationFile) {
        try {
            URL fileUrl = new URL(url);
            Path destination = Paths.get(destinationFile);
            
            // Use Files.copy with StandardCopyOption for better file handling
            try (InputStream inputStream = fileUrl.openStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
                return null;
            }
        } catch (MalformedURLException e) {
            return "Error: Invalid URL format. " + e.getMessage();
        } catch (IOException e) {
            return "Error: Unable to download file. " + e.getMessage();
        }
    }
}
