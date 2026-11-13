package com.immodata.gb.application.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.immodata.gb.api.error.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class DocumentFileValidatorTest {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n"
            .getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R',
        0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89
    };
    private static final byte[] JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
        (byte) 0xFF, (byte) 0xD9
    };
    private static final byte[] WEBP = {
        'R', 'I', 'F', 'F', 0x1A, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ', 0x0E, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    private final DocumentFileValidator validator = new DocumentFileValidator();

    private static MultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private static byte[] heic(String brand) {
        byte[] data = new byte[32];
        data[3] = 0x18;
        byte[] ftyp = ("ftyp" + brand).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(ftyp, 0, data, 4, ftyp.length);
        byte[] compat = ("mif1" + brand).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(compat, 0, data, 16, compat.length);
        return data;
    }

    private static byte[] ooxml(String contentTypesOverride, String partName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"" + partName + "\" ContentType=\"" + contentTypesOverride + "\"/>"
                    + "</Types>";
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\""
                    + partName.substring(1) + "\"/></Relationships>";
            zip.write(rels.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(partName.substring(1)));
            zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] docx() throws IOException {
        return ooxml(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
                "/word/document.xml");
    }

    private static byte[] xlsx() throws IOException {
        return ooxml(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml", "/xl/workbook.xml");
    }

    private static String codeOf(MultipartFile file, DocumentFileValidator validator) {
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        return ex.getCode();
    }

    @Test
    void validPdfIsAccepted() throws IOException {
        ValidatedFile result = validator.validate(file("Contrat Final.PDF", PDF));
        assertEquals("Contrat Final.PDF", result.originalFilename());
        assertEquals("pdf", result.extension());
        assertEquals("application/pdf", result.contentType());
        assertEquals(PDF.length, result.sizeBytes());
        assertArrayEquals(PDF, result.openStream().readAllBytes());
    }

    @Test
    void textSpoofedAsPdfIsRejected() {
        MultipartFile spoof = file("evil.pdf", "just some plain text, not a pdf".getBytes(StandardCharsets.UTF_8));
        assertEquals("DOCUMENT_FILE_TYPE_REJECTED", codeOf(spoof, validator));
    }

    @Test
    void pngSpoofedAsPdfIsRejected() {
        assertEquals("DOCUMENT_FILE_TYPE_REJECTED", codeOf(file("image.pdf", PNG), validator));
    }

    @Test
    void jpegAcceptsBothJpgAndJpegExtensions() throws IOException {
        assertEquals("image/jpeg", validator.validate(file("a.jpg", JPEG)).contentType());
        ValidatedFile jpeg = validator.validate(file("a.JPEG", JPEG));
        assertEquals("jpeg", jpeg.extension());
        assertEquals("image/jpeg", jpeg.contentType());
    }

    @Test
    void pngAndWebpAreAccepted() throws IOException {
        assertEquals("image/png", validator.validate(file("a.png", PNG)).contentType());
        assertEquals("image/webp", validator.validate(file("a.webp", WEBP)).contentType());
    }

    @Test
    void heicMapsToHeicOrHeifMime() throws IOException {
        ValidatedFile heic = validator.validate(file("photo.heic", heic("heic")));
        assertEquals("heic", heic.extension());
        assertEquals("image/heic", heic.contentType());
        ValidatedFile heif = validator.validate(file("photo.HEIC", heic("mif1")));
        assertEquals("image/heif", heif.contentType());
    }

    @Test
    void docxAndXlsxMapToOpenXmlMime() throws IOException {
        assertEquals(DOCX_MIME, validator.validate(file("acte.docx", docx())).contentType());
        assertEquals(XLSX_MIME, validator.validate(file("tabela.xlsx", xlsx())).contentType());
    }

    @Test
    void docxContentUnderXlsxExtensionIsRejected() throws IOException {
        assertEquals("DOCUMENT_FILE_TYPE_REJECTED", codeOf(file("tabela.xlsx", docx()), validator));
    }

    @Test
    void plainZipRenamedDocxIsRejected() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("hello".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertEquals("DOCUMENT_FILE_TYPE_REJECTED", codeOf(file("fake.docx", out.toByteArray()), validator));
    }

    @Test
    void nullFileIsMissing() {
        assertEquals("DOCUMENT_FILE_MISSING", codeOf(null, validator));
    }

    @Test
    void emptyFileIsMissing() {
        assertEquals("DOCUMENT_FILE_MISSING", codeOf(file("a.pdf", new byte[0]), validator));
    }

    @Test
    void zeroByteFileWithDisallowedExtensionIsStillMissingFirst() {
        assertEquals("DOCUMENT_FILE_MISSING", codeOf(file("a.exe", new byte[0]), validator));
    }

    @Test
    void oversizedFileIsTooLargeBeforeTypeChecks() {
        MultipartFile big = new SizedMultipartFile("huge.exe", DocumentFileValidator.MAX_SIZE_BYTES + 1);
        assertEquals("DOCUMENT_FILE_TOO_LARGE", codeOf(big, validator));
    }

    @Test
    void fileExactlyAtLimitPassesSizeCheck() {
        MultipartFile atLimit = new SizedMultipartFile("exact.exe", DocumentFileValidator.MAX_SIZE_BYTES);
        // size ok -> proceeds to extension check, which rejects .exe
        assertEquals("DOCUMENT_FILE_TYPE_REJECTED", codeOf(atLimit, validator));
    }

    @Test
    void filenameLongerThan500IsRejected() {
        String tooLong = "a".repeat(DocumentFileValidator.MAX_FILENAME_LENGTH - 3) + ".pdf";
        assertEquals(DocumentFileValidator.MAX_FILENAME_LENGTH + 1, tooLong.length());
        assertEquals("DOCUMENT_FILE_TYPE_REJECTED", codeOf(file(tooLong, PDF), validator));
    }

    @Test
    void filenameOfExactly500IsAccepted() {
        String atLimit = "a".repeat(DocumentFileValidator.MAX_FILENAME_LENGTH - 4) + ".pdf";
        assertEquals(DocumentFileValidator.MAX_FILENAME_LENGTH, atLimit.length());
        assertEquals(atLimit, validator.validate(file(atLimit, PDF)).originalFilename());
    }

    @Test
    void maxSizeConstantIsFiftyMebibytes() {
        assertEquals(52_428_800L, DocumentFileValidator.MAX_SIZE_BYTES);
    }

    @Test
    void disallowedExtensionIsRejected() {
        assertEquals("DOCUMENT_FILE_TYPE_REJECTED", codeOf(file("run.exe", PDF), validator));
        assertEquals("DOCUMENT_FILE_TYPE_REJECTED", codeOf(file("noextension", PDF), validator));
        assertEquals("DOCUMENT_FILE_TYPE_REJECTED", codeOf(file(null, PDF), validator));
        assertEquals("DOCUMENT_FILE_TYPE_REJECTED", codeOf(file("archive.pdf.zip", PDF), validator));
    }

    /** Fake multipart exposing a size without allocating the bytes. */
    private record SizedMultipartFile(String filename, long size) implements MultipartFile {
        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return "application/octet-stream";
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public byte[] getBytes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.io.InputStream getInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void transferTo(java.io.File dest) {
            throw new UnsupportedOperationException();
        }
    }
}
