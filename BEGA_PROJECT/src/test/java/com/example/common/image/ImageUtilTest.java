package com.example.common.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImageUtilTest {

    private final ImageOptimizationMetricsService metricsService = mock(ImageOptimizationMetricsService.class);
    private final ImageUtil imageUtil = new ImageUtil(metricsService);

    @Test
    @DisplayName("cheer feed 파생 이미지는 WebP writer 없이도 불투명 JPG로 재인코딩된다")
    void processFeedProfileImage_reencodesPngAsOpaqueJpegWithoutWebpWriter() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                createTransparentPngBytes());

        ImageUtil.ProcessedImage processed = imageUtil.processFeedProfileImage(file);

        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(processed.getBytes()));

        assertThat(processed.getContentType()).isEqualTo("image/jpeg");
        assertThat(processed.getExtension()).isEqualTo("jpg");
        assertThat(rendered).isNotNull();
        assertThat(rendered.getColorModel().hasAlpha()).isFalse();
        assertThat(rendered.getWidth()).isLessThanOrEqualTo(320);
        assertThat(rendered.getHeight()).isLessThanOrEqualTo(320);
        verify(metricsService).record("profile_feed", "optimized");
    }

    private byte[] createTransparentPngBytes() throws Exception {
        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(java.awt.AlphaComposite.Clear);
            graphics.fillRect(0, 0, 640, 640);
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            graphics.setColor(new Color(31, 41, 55, 255));
            graphics.fillOval(48, 48, 544, 544);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }
}
