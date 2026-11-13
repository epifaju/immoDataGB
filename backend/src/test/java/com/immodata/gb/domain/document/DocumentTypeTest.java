package com.immodata.gb.domain.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentTypeTest {

    private static final List<String> PRD_VALUES = List.of(
            "titre_foncier",
            "attestation_possession",
            "contrat_vente",
            "contrat_location",
            "plan_parcelle",
            "photo_terrain",
            "piece_identite_masquee",
            "acte_notarie",
            "document_administratif",
            "autre");

    @Test
    void exposesExactlyTheTenPrdLiteralsInOrder() {
        List<String> values = java.util.Arrays.stream(DocumentType.values()).map(DocumentType::value).toList();
        assertEquals(PRD_VALUES, values);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "titre_foncier", "attestation_possession", "contrat_vente", "contrat_location", "plan_parcelle",
            "photo_terrain", "piece_identite_masquee", "acte_notarie", "document_administratif", "autre"})
    void fromApiResolvesEachLiteral(String raw) {
        Optional<DocumentType> type = DocumentType.fromApi(raw);
        assertTrue(type.isPresent());
        assertEquals(raw, type.get().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"inconnu", "TITRE_FONCIER", "Titre_Foncier", "titre foncier", " autre", "", "  "})
    void fromApiRejectsUnknownOrDifferentlyCasedValues(String raw) {
        assertTrue(DocumentType.fromApi(raw).isEmpty());
    }

    @Test
    void fromApiRejectsNull() {
        assertTrue(DocumentType.fromApi(null).isEmpty());
    }
}
