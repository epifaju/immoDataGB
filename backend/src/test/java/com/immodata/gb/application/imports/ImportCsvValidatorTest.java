package com.immodata.gb.application.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.domain.imports.ImportCsvRow;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ImportCsvValidatorTest {

    private static final String[] HEADER = {
        "reference", "title", "property_type", "address_text", "latitude", "longitude"
    };

    private final ImportCsvValidator validator = new ImportCsvValidator();

    private static String[] row(String ref, String title, String type, String addr, String lat, String lng) {
        return new String[] {ref, title, type, addr, lat, lng};
    }

    private ImportRowException assertRowError(String code, String[] cols) {
        ImportRowException ex = assertThrows(ImportRowException.class, () -> validator.parseAndValidate(2, cols));
        assertEquals(code, ex.getCode());
        return ex;
    }

    @Test
    void acceptsExactHeader() {
        validator.assertValidHeader(HEADER.clone());
    }

    @Test
    void rejectsWrongHeaderOrder() {
        String[] bad = {"title", "reference", "property_type", "address_text", "latitude", "longitude"};
        ApiException ex = assertThrows(ApiException.class, () -> validator.assertValidHeader(bad));
        assertEquals("IMPORT_INVALID_HEADER", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void rejectsHeaderWithWrongCase() {
        String[] bad = {"Reference", "title", "property_type", "address_text", "latitude", "longitude"};
        ApiException ex = assertThrows(ApiException.class, () -> validator.assertValidHeader(bad));
        assertEquals("IMPORT_INVALID_HEADER", ex.getCode());
    }

    @Test
    void rejectsHeaderWithMissingColumn() {
        String[] bad = {"reference", "title", "property_type", "address_text", "latitude"};
        ApiException ex = assertThrows(ApiException.class, () -> validator.assertValidHeader(bad));
        assertEquals("IMPORT_INVALID_HEADER", ex.getCode());
    }

    @Test
    void rejectsNullHeader() {
        ApiException ex = assertThrows(ApiException.class, () -> validator.assertValidHeader(null));
        assertEquals("IMPORT_INVALID_HEADER", ex.getCode());
    }

    @Test
    void parsesValidRow() {
        ImportCsvRow r = validator.parseAndValidate(
                3, row("REF-1", "Casa T3", "house", "Rua X, Bissau", "11.8637", "-15.5977"));
        assertEquals(3, r.rowNumber());
        assertEquals("REF-1", r.reference());
        assertEquals("Casa T3", r.title());
        assertEquals("house", r.propertyType());
        assertEquals("Rua X, Bissau", r.addressText());
        assertEquals(11.8637, r.latitude(), 1e-9);
        assertEquals(-15.5977, r.longitude(), 1e-9);
    }

    @Test
    void emptyAddressBecomesNull() {
        ImportCsvRow r = validator.parseAndValidate(2, row("R", "T", "land", "  ", "0", "0"));
        assertNull(r.addressText());
    }

    @Test
    void acceptsBoundaryCoordinates() {
        ImportCsvRow r = validator.parseAndValidate(2, row("R", "T", "apartment", "", "-90", "180"));
        assertEquals(-90.0, r.latitude());
        assertEquals(180.0, r.longitude());
    }

    @Test
    void rejectsInvalidPropertyType() {
        assertRowError("IMPORT_INVALID_PROPERTY_TYPE", row("R", "T", "villa", "", "11", "-15"));
        assertRowError("IMPORT_INVALID_PROPERTY_TYPE", row("R", "T", "House", "", "11", "-15"));
    }

    @Test
    void rejectsLatitudeOutOfRange() {
        assertRowError("IMPORT_INVALID_COORDINATE", row("R", "T", "house", "", "90.1", "-15"));
        assertRowError("IMPORT_INVALID_COORDINATE", row("R", "T", "house", "", "-91", "-15"));
    }

    @Test
    void rejectsLongitudeOutOfRange() {
        assertRowError("IMPORT_INVALID_COORDINATE", row("R", "T", "house", "", "11", "180.5"));
        assertRowError("IMPORT_INVALID_COORDINATE", row("R", "T", "house", "", "11", "-181"));
    }

    @Test
    void rejectsNonNumericOrNonFiniteCoordinates() {
        assertRowError("IMPORT_INVALID_COORDINATE", row("R", "T", "house", "", "abc", "-15"));
        assertRowError("IMPORT_INVALID_COORDINATE", row("R", "T", "house", "", "NaN", "-15"));
        assertRowError("IMPORT_INVALID_COORDINATE", row("R", "T", "house", "", "11", "Infinity"));
        assertRowError("IMPORT_INVALID_COORDINATE", row("R", "T", "house", "", "11d", "-15"));
        assertRowError("IMPORT_INVALID_COORDINATE", row("R", "T", "house", "", "1e1", "-15"));
    }

    @Test
    void rejectsMissingCoordinate() {
        assertRowError("IMPORT_MISSING_FIELD", row("R", "T", "house", "", "", "-15"));
        assertRowError("IMPORT_MISSING_FIELD", row("R", "T", "house", "", "11", null));
    }

    @Test
    void rejectsMissingReferenceTitleAndType() {
        assertRowError("IMPORT_MISSING_FIELD", row("", "T", "house", "", "11", "-15"));
        assertRowError("IMPORT_MISSING_FIELD", row("  ", "T", "house", "", "11", "-15"));
        assertRowError("IMPORT_MISSING_FIELD", row("R", "", "house", "", "11", "-15"));
        assertRowError("IMPORT_MISSING_FIELD", row("R", "T", "", "", "11", "-15"));
    }

    @Test
    void rejectsWrongColumnCount() {
        assertRowError("IMPORT_MISSING_FIELD", new String[] {"R", "T", "house"});
    }

    @Test
    void rejectsTooLongReferenceAndTitle() {
        assertRowError("IMPORT_MISSING_FIELD", row("R".repeat(51), "T", "house", "", "11", "-15"));
        assertRowError("IMPORT_MISSING_FIELD", row("R", "T".repeat(501), "house", "", "11", "-15"));
    }

    @Test
    void acceptsMaxLengthReferenceAndTitle() {
        ImportCsvRow r =
                validator.parseAndValidate(2, row("R".repeat(50), "T".repeat(500), "house", "", "11", "-15"));
        assertEquals(50, r.reference().length());
        assertEquals(500, r.title().length());
    }

    @Test
    void rowErrorCarriesCodeAndMessage() {
        ImportRowException ex = assertRowError("IMPORT_MISSING_FIELD", row("", "T", "house", "", "11", "-15"));
        assertEquals("Campo obrigatório em falta: reference", ex.getMessage());
    }
}
