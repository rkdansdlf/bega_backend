package com.example.common.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageUtilTest {

    @Mock
    private ImageOptimizationMetricsService metricsService;

    @InjectMocks
    private ImageUtil imageUtil;

    @Test
    void getImageDimension_readsPngMetadataWithoutFullDecode() throws IOException {
        byte[] hugeDimensionPng = minimalPng(8192, 8192);

        ImageUtil.ImageDimension dimension = imageUtil.getImageDimension(hugeDimensionPng);

        assertThat(dimension.width()).isEqualTo(8192);
        assertThat(dimension.height()).isEqualTo(8192);
    }

    private byte[] minimalPng(int width, int height) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream dataOutput = new DataOutputStream(output);
        dataOutput.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});

        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream ihdrData = new DataOutputStream(ihdr);
        ihdrData.writeInt(width);
        ihdrData.writeInt(height);
        ihdrData.writeByte(8);
        ihdrData.writeByte(2);
        ihdrData.writeByte(0);
        ihdrData.writeByte(0);
        ihdrData.writeByte(0);

        writeChunk(dataOutput, "IHDR", ihdr.toByteArray());
        writeChunk(dataOutput, "IEND", new byte[0]);
        return output.toByteArray();
    }

    private void writeChunk(DataOutputStream output, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc32 = new CRC32();
        crc32.update(typeBytes);
        crc32.update(data);

        output.writeInt(data.length);
        output.write(typeBytes);
        output.write(data);
        output.writeInt((int) crc32.getValue());
    }
}
