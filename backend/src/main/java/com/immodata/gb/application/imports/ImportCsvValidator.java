package com.immodata.gb.application.imports;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.domain.imports.ImportCsvRow;
import com.immodata.gb.domain.property.PropertyType;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ImportCsvValidator {

    public static final List<String> EXPECTED_HEADER =
            List.of("reference", "title", "property_type", "address_text", "latitude", "longitude");

    static final int MAX_REFERENCE_LENGTH = 50;
    static final int MAX_TITLE_LENGTH = 500;

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");

    private static final int COL_REFERENCE = 0;
    private static final int COL_TITLE = 1;
    private static final int COL_PROPERTY_TYPE = 2;
    private static final int COL_ADDRESS = 3;
    private static final int COL_LATITUDE = 4;
    private static final int COL_LONGITUDE = 5;

    public void assertValidHeader(String[] header) {
        if (header == null || !EXPECTED_HEADER.equals(Arrays.asList(header))) {
            throw new ApiException("IMPORT_INVALID_HEADER", HttpStatus.BAD_REQUEST);
        }
    }

    public ImportCsvRow parseAndValidate(int rowNumber, String[] cols) {
        String reference = required(cols, COL_REFERENCE, "reference");
        if (reference.length() > MAX_REFERENCE_LENGTH) {
            throw new ImportRowException(
                    "IMPORT_MISSING_FIELD",
                    "Campo reference excede " + MAX_REFERENCE_LENGTH + " caracteres");
        }
        String title = required(cols, COL_TITLE, "title");
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ImportRowException(
                    "IMPORT_MISSING_FIELD", "Campo title excede " + MAX_TITLE_LENGTH + " caracteres");
        }
        String rawType = required(cols, COL_PROPERTY_TYPE, "property_type");
        String propertyType = PropertyType.fromParam(rawType)
                .map(PropertyType::getSqlValue)
                .orElseThrow(() -> new ImportRowException(
                        "IMPORT_INVALID_PROPERTY_TYPE",
                        "Tipo de imóvel inválido: " + rawType
                                + " (house, apartment, land, commercial)"));

        String address = optional(cols, COL_ADDRESS);
        double latitude = coordinate(required(cols, COL_LATITUDE, "latitude"), "latitude", -90, 90);
        double longitude = coordinate(required(cols, COL_LONGITUDE, "longitude"), "longitude", -180, 180);

        return new ImportCsvRow(rowNumber, reference, title, propertyType, address, latitude, longitude);
    }

    private static String optional(String[] cols, int index) {
        if (cols == null || index >= cols.length || cols[index] == null) {
            return null;
        }
        String value = cols[index].trim();
        return value.isEmpty() ? null : value;
    }

    private static String required(String[] cols, int index, String name) {
        String value = optional(cols, index);
        if (value == null) {
            throw new ImportRowException("IMPORT_MISSING_FIELD", "Campo obrigatório em falta: " + name);
        }
        return value;
    }

    private static double coordinate(String raw, String name, double min, double max) {
        if (!DECIMAL.matcher(raw).matches()) {
            throw new ImportRowException("IMPORT_INVALID_COORDINATE", "Coordenada inválida em " + name + ": " + raw);
        }
        double value = Double.parseDouble(raw);
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new ImportRowException(
                    "IMPORT_INVALID_COORDINATE",
                    "Coordenada fora dos limites em " + name + ": " + raw + " (" + min + " a " + max + ")");
        }
        return value;
    }
}
