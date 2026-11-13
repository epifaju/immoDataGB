package com.immodata.gb.domain.document;

import java.util.Arrays;
import java.util.Optional;

/** Type métier fermé d'un document (10 littéraux du PRD, stockés tels quels en base). */
public enum DocumentType {
    TITRE_FONCIER("titre_foncier"),
    ATTESTATION_POSSESSION("attestation_possession"),
    CONTRAT_VENTE("contrat_vente"),
    CONTRAT_LOCATION("contrat_location"),
    PLAN_PARCELLE("plan_parcelle"),
    PHOTO_TERRAIN("photo_terrain"),
    PIECE_IDENTITE_MASQUEE("piece_identite_masquee"),
    ACTE_NOTARIE("acte_notarie"),
    DOCUMENT_ADMINISTRATIF("document_administratif"),
    AUTRE("autre");

    private final String value;

    DocumentType(String value) {
        this.value = value;
    }

    /** Littéral SQL / API. */
    public String value() {
        return value;
    }

    /** Résolution stricte (sensible à la casse) ; vide si inconnu, nul ou vide. */
    public static Optional<DocumentType> fromApi(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(type -> type.value.equals(raw)).findFirst();
    }
}
