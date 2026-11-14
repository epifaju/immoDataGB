package com.immodata.gb.application.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.immodata.gb.api.error.ApiException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FieldObservationRequestParserTest {

    private static Map<String, Object> valid() {
        Map<String, Object> body = new HashMap<>();
        body.put("latitude", 11.863);
        body.put("longitude", -15.598);
        body.put("propertyType", "house");
        return body;
    }

    private static void assertInvalid(Map<String, Object> body) {
        assertThatThrownBy(() -> FieldObservationRequestParser.parse(body))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("FIELD_REQUEST_INVALID");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void parsesMinimalValidBody() {
        FieldObservationRequest req = FieldObservationRequestParser.parse(valid());

        assertThat(req.latitude()).isEqualTo(11.863);
        assertThat(req.longitude()).isEqualTo(-15.598);
        assertThat(req.propertyType()).isEqualTo("house");
        assertThat(req.title()).isNull();
        assertThat(req.occupationStatus()).isNull();
        assertThat(req.notes()).isNull();
    }

    @Test
    void parsesFullBodyAndTrimsText() {
        Map<String, Object> body = valid();
        body.put("title", "  Casa azul  ");
        body.put("occupationStatus", "occupied");
        body.put("notes", "  Portão verde  ");

        FieldObservationRequest req = FieldObservationRequestParser.parse(body);

        assertThat(req.title()).isEqualTo("Casa azul");
        assertThat(req.occupationStatus()).isEqualTo("occupied");
        assertThat(req.notes()).isEqualTo("Portão verde");
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        Map<String, Object> body = valid();
        body.put("title", "   ");
        body.put("occupationStatus", "");
        body.put("notes", " ");
        body.put("propertyType", "land");

        FieldObservationRequest req = FieldObservationRequestParser.parse(body);

        assertThat(req.title()).isNull();
        assertThat(req.occupationStatus()).isNull();
        assertThat(req.notes()).isNull();
        assertThat(req.propertyType()).isEqualTo("land");
    }

    @Test
    void nullOptionalFieldsAreAccepted() {
        Map<String, Object> body = valid();
        body.put("title", null);
        body.put("occupationStatus", null);
        body.put("notes", null);

        FieldObservationRequest req = FieldObservationRequestParser.parse(body);

        assertThat(req.title()).isNull();
    }

    @Test
    void acceptsBoundaryCoordinatesAndIntegers() {
        Map<String, Object> body = valid();
        body.put("latitude", 90);
        body.put("longitude", -180);

        FieldObservationRequest req = FieldObservationRequestParser.parse(body);

        assertThat(req.latitude()).isEqualTo(90.0);
        assertThat(req.longitude()).isEqualTo(-180.0);
    }

    @Test
    void acceptsBigDecimalCoordinates() {
        Map<String, Object> body = valid();
        body.put("latitude", new BigDecimal("11.5"));

        assertThat(FieldObservationRequestParser.parse(body).latitude()).isEqualTo(11.5);
    }

    @Test
    void acceptsAllOccupationStatuses() {
        for (String status : new String[] {"occupied", "vacant", "unknown"}) {
            Map<String, Object> body = valid();
            body.put("occupationStatus", status);
            assertThat(FieldObservationRequestParser.parse(body).occupationStatus()).isEqualTo(status);
        }
    }

    @Test
    void acceptsAllPropertyTypes() {
        for (String type : new String[] {"house", "apartment", "land", "commercial"}) {
            Map<String, Object> body = valid();
            body.put("propertyType", type);
            assertThat(FieldObservationRequestParser.parse(body).propertyType()).isEqualTo(type);
        }
    }

    @Test
    void acceptsTitleOfExactly500Characters() {
        Map<String, Object> body = valid();
        body.put("title", "t".repeat(500));

        assertThat(FieldObservationRequestParser.parse(body).title()).hasSize(500);
    }

    @Test
    void rejectsNullBody() {
        assertInvalid(null);
    }

    @Test
    void rejectsMissingLatitude() {
        Map<String, Object> body = valid();
        body.remove("latitude");
        assertInvalid(body);
    }

    @Test
    void rejectsMissingLongitude() {
        Map<String, Object> body = valid();
        body.remove("longitude");
        assertInvalid(body);
    }

    @Test
    void rejectsNonNumericCoordinates() {
        Map<String, Object> body = valid();
        body.put("latitude", "11.8");
        assertInvalid(body);

        Map<String, Object> body2 = valid();
        body2.put("longitude", Boolean.TRUE);
        assertInvalid(body2);
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        for (double lat : new double[] {-90.0001, 90.0001}) {
            Map<String, Object> body = valid();
            body.put("latitude", lat);
            assertInvalid(body);
        }
        for (double lng : new double[] {-180.0001, 180.0001}) {
            Map<String, Object> body = valid();
            body.put("longitude", lng);
            assertInvalid(body);
        }
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        Map<String, Object> body = valid();
        body.put("latitude", Double.NaN);
        assertInvalid(body);

        Map<String, Object> body2 = valid();
        body2.put("longitude", Double.POSITIVE_INFINITY);
        assertInvalid(body2);
    }

    @Test
    void rejectsMissingOrUnknownPropertyType() {
        Map<String, Object> body = valid();
        body.remove("propertyType");
        assertInvalid(body);

        Map<String, Object> body2 = valid();
        body2.put("propertyType", "castle");
        assertInvalid(body2);

        Map<String, Object> body3 = valid();
        body3.put("propertyType", 3);
        assertInvalid(body3);
    }

    @Test
    void rejectsUnknownOccupationStatus() {
        Map<String, Object> body = valid();
        body.put("occupationStatus", "squatted");
        assertInvalid(body);
    }

    @Test
    void rejectsTitleLongerThan500() {
        Map<String, Object> body = valid();
        body.put("title", "t".repeat(501));
        assertInvalid(body);
    }

    @Test
    void rejectsNonStringTextFields() {
        Map<String, Object> body = valid();
        body.put("title", 12);
        assertInvalid(body);

        Map<String, Object> body2 = valid();
        body2.put("notes", Map.of("a", "b"));
        assertInvalid(body2);

        Map<String, Object> body3 = valid();
        body3.put("occupationStatus", 1);
        assertInvalid(body3);
    }
}
