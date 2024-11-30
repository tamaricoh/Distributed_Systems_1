package com.example;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.io.FileWriter;
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

    public static String convertFromUrl(String PDFfile, String operation) throws IOException {
        // URL url = new URL(urlString);
        // File tempPdf = File.createTempFile("downloaded", ".pdf");
        // tempPdf.deleteOnExit();
    
        // try {
        //     // Download PDF
        //     try (java.io.InputStream in = url.openStream()) {
        //         java.nio.file.Files.copy(in, tempPdf.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        //     }
    
        //     String baseFileName = tempPdf.getAbsolutePath().replace(".pdf", "");
        //     String outputPath;
    
            // Handle different operations
            String baseFileName = PDFfile.replace(".pdf", "");
            String outputPath;
            switch (operation.toUpperCase()) {
                case "TOIMAGE":
                    outputPath = baseFileName + ".png";
                    convertToImage(PDFfile, outputPath);
                    break;
                case "TOTEXT":
                    outputPath = baseFileName + ".txt";
                    convertToText(PDFfile, outputPath);
                    break;
                case "TOHTML":
                    outputPath = baseFileName + ".html";
                    try { // --------------------------------------------
                        convertToHTML(PDFfile, outputPath);
                    } catch (IOException e) {
                        // Return an error message when HTML conversion fails
                        return "Error: Unable to convert PDF to HTML. Reason: " + e.getMessage();
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation: " + operation);
            }
    
            // Return the URL of the output file
            return new File(outputPath).toURI().toString();
    
        // } catch (IOException e) {
        //     // Catch any IOException, including file download or conversion issues
        //     return "Error: Unable to download or process PDF. Reason: " + e.getMessage();
        // }
    }
}
