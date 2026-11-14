package com.immodata.gb.infrastructure.batch.imports;

/** Ligne CSV brute lue par le reader : numéro 1-based (corps, hors en-tête), colonnes et ligne d'origine. */
public record ImportRawRow(int rowNumber, String[] cols, String rawLine) {}
