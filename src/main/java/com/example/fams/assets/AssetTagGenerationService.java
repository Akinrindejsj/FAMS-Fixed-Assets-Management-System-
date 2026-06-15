package com.example.fams.assets;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Service
public class AssetTagGenerationService {

    public AssetTags generate(String assetCode) {
        return new AssetTags(
                assetCode,
                imageDataUri(qrCode(assetCode)),
                imageDataUri(barcode(assetCode))
        );
    }

    private BitMatrix qrCode(String assetCode) {
        try {
            return new QRCodeWriter().encode(assetCode, BarcodeFormat.QR_CODE, 220, 220);
        } catch (WriterException e) {
            throw new IllegalStateException("Unable to generate asset QR code", e);
        }
    }

    private BitMatrix barcode(String assetCode) {
        return new Code128Writer().encode(assetCode, BarcodeFormat.CODE_128, 360, 96);
    }

    private String imageDataUri(BitMatrix matrix) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to render asset tag image", e);
        }
    }
}
