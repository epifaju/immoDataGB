package com.immodata.gb.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorageKeyFactoryTest {

    private static final UUID ORG = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROPERTY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DOC = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String PREFIX = ORG + "/" + PROPERTY + "/" + DOC + "/";

    private final StorageKeyFactory factory = new StorageKeyFactory();

    @Test
    void spaceBecomesUnderscoreAndExtensionIsLowercasedFromWhitelist() {
        String key = factory.build(ORG, PROPERTY, DOC, "contrat final.PDF", "pdf");
        assertEquals(PREFIX + "contrat_final.pdf", key);
    }

    @Test
    void extensionComesFromValidatedExtensionNotFromFilename() {
        String key = factory.build(ORG, PROPERTY, DOC, "photo.exe", "JPG");
        assertEquals(PREFIX + "photo.jpg", key);
    }

    @Test
    void unsafeCharactersAreReplacedByUnderscore() {
        String key = factory.build(ORG, PROPERTY, DOC, "../../etc/passwd é#?.pdf", "pdf");
        String filename = key.substring(PREFIX.length());
        assertTrue(key.startsWith(PREFIX));
        assertTrue(filename.matches("[A-Za-z0-9._-]+"), filename);
        assertTrue(filename.endsWith(".pdf"));
        assertEquals(-1, filename.indexOf('/'));
    }

    @Test
    void stemIsTruncatedToOneHundredCharacters() {
        String longName = "a".repeat(300) + ".png";
        String key = factory.build(ORG, PROPERTY, DOC, longName, "png");
        assertEquals(PREFIX + "a".repeat(100) + ".png", key);
    }

    @Test
    void onlyLastDotSegmentIsStrippedAsExtension() {
        String key = factory.build(ORG, PROPERTY, DOC, "plan.v2.final.pdf", "pdf");
        assertEquals(PREFIX + "plan.v2.final.pdf", key);
    }

    @Test
    void emptyStemFallsBackToDocument() {
        assertEquals(PREFIX + "document.pdf", factory.build(ORG, PROPERTY, DOC, ".pdf", "pdf"));
        assertEquals(PREFIX + "document.pdf", factory.build(ORG, PROPERTY, DOC, "   ", "pdf"));
        assertEquals(PREFIX + "document.pdf", factory.build(ORG, PROPERTY, DOC, null, "pdf"));
    }

    @Test
    void filenameWithoutExtensionKeepsWholeNameAsStem() {
        assertEquals(PREFIX + "scan.jpeg", factory.build(ORG, PROPERTY, DOC, "scan", "jpeg"));
    }
}
