package com.immodata.gb.application.imports;

/** Erreur de validation d'une ligne CSV (code stable + message PT), non liée à une réponse HTTP. */
public class ImportRowException extends RuntimeException {
    private final String code;

    public ImportRowException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
