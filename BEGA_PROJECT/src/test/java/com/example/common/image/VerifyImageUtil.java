package com.example.common.image;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class VerifyImageUtil {

    private static boolean isWebpStrictMode() {
        return Boolean.parseBoolean(System.getProperty("webp.strict", "false"));
    }

    @Test
    void verifyImageProcessing() throws Exception {
        boolean strictMode = isWebpStrictMode();
        System.out.println("Starting ImageUtil Processing Verification...");
        System.out.println("webp.strict=" + strictMode);
        System.out.println("Available Image Writers: " + java.util.Arrays.toString(ImageIO.getWriterFormatNames()));

        // 1. Create a dummy high-res image (1000x1000, valid PNG)
        BufferedImage image = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 1000, 1000);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        MultipartFile originalFile = new MockMultipartFile(
                "test-image.png",
                "test-image.png",
                "image/png",
                imageBytes);

        // 2. Process with ImageUtil
        ImageUtil imageUtil = new ImageUtil();
        ImageUtil.ProcessedImage processed = imageUtil.process(originalFile);

        // 3. Verify results
        assertNotNull(processed, "Processed image should not be null");
        assertTrue(processed.getSize() > 0, "Processed size should be > 0");

        System.out.println("Original Size: " + imageBytes.length + " bytes");
        System.out.println("Processed Size: " + processed.getSize() + " bytes");
        System.out.println("Verified: ImageUtil processed the image.");

        if (strictMode) {
            assertEquals("webp", processed.getExtension(),
                    "webp.strict=true 환경에서는 WebP 변환이 반드시 성공해야 합니다.");
            assertEquals("image/webp", processed.getContentType());
            return;
        }

        // Non-strict: runtime fail-open 허용 (원본 또는 WebP)
        if ("webp".equalsIgnoreCase(processed.getExtension())) {
            System.out.println("Success: WebP conversion active.");
            assertEquals("image/webp", processed.getContentType());
        } else {
            System.out.println("Warning: WebP conversion unavailable. Fallback extension=" + processed.getExtension());
            assertEquals("png", processed.getExtension());
        }
    }

    @Test
    void verifyDirectImageIOWrite() throws Exception {
        boolean strictMode = isWebpStrictMode();
        System.out.println("Testing Direct ImageIO Write for WebP...");
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean writerFound = ImageIO.write(image, "webp", baos);

        System.out.println("ImageIO.write('webp') returned: " + writerFound);
        if (strictMode) {
            assertTrue(writerFound, "webp.strict=true 환경에서는 WebP ImageWriter가 필요합니다.");
        } else if (!writerFound) {
            System.out.println("Warning: No WebP ImageWriter found in classpath. Non-strict mode allows fallback.");
        }
    }
}
