package com.immodata.gb.application.imports;

public record ImportRowErrorItem(int rowNumber, String code, String message, String rawExcerpt) {}
