package com.immodata.gb.api.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.property.PropertyQuery;
import com.immodata.gb.domain.property.ConfidenceLevel;
import com.immodata.gb.domain.property.PropertyType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class PropertyRequestParserTest {

    @Test
    void parsesValidBboxAndDefaults() {
        PropertyQuery query = PropertyRequestParser.parseList(params("bbox", "-15.70,11.80,-15.50,11.95"));
        assertEquals(-15.70, query.bbox().west());
        assertEquals(11.80, query.bbox().south());
        assertEquals(-15.50, query.bbox().east());
        assertEquals(11.95, query.bbox().north());
        assertNull(query.type());
        assertNull(query.confidence());
        assertEquals(0, query.page());
        assertEquals(20, query.size());
    }

    @Test
    void rejectsMissingBbox() {
        ApiException ex = assertThrows(ApiException.class, () -> PropertyRequestParser.parseList(params()));
        assertEquals("PROPERTY_BBOX_INVALID", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void rejectsNonNumericBbox() {
        ApiException ex = assertThrows(
                ApiException.class, () -> PropertyRequestParser.parseList(params("bbox", "a,b,c,d")));
        assertEquals("PROPERTY_BBOX_INVALID", ex.getCode());
    }

    @Test
    void rejectsWrongArityBbox() {
        ApiException tooFew = assertThrows(
                ApiException.class, () -> PropertyRequestParser.parseList(params("bbox", "1,2,3")));
        ApiException tooMany = assertThrows(
                ApiException.class, () -> PropertyRequestParser.parseList(params("bbox", "1,2,3,4,5")));
        assertEquals("PROPERTY_BBOX_INVALID", tooFew.getCode());
        assertEquals("PROPERTY_BBOX_INVALID", tooMany.getCode());
    }

    @Test
    void rejectsNonFiniteBbox() {
        ApiException nan = assertThrows(
                ApiException.class,
                () -> PropertyRequestParser.parseList(params("bbox", "NaN,11.80,-15.50,11.95")));
        ApiException inf = assertThrows(
                ApiException.class,
                () -> PropertyRequestParser.parseList(params("bbox", "Infinity,11.80,-15.50,11.95")));
        assertEquals("PROPERTY_BBOX_INVALID", nan.getCode());
        assertEquals("PROPERTY_BBOX_INVALID", inf.getCode());
    }

    @Test
    void rejectsOutOfRangeAndUnorderedBbox() {
        assertEquals(
                "PROPERTY_BBOX_INVALID",
                assertThrows(
                                ApiException.class,
                                () -> PropertyRequestParser.parseList(params("bbox", "-200,11.80,-15.50,11.95")))
                        .getCode());
        assertEquals(
                "PROPERTY_BBOX_INVALID",
                assertThrows(
                                ApiException.class,
                                () -> PropertyRequestParser.parseList(params("bbox", "-15.50,11.80,-15.70,11.95")))
                        .getCode());
        assertEquals(
                "PROPERTY_BBOX_INVALID",
                assertThrows(
                                ApiException.class,
                                () -> PropertyRequestParser.parseList(params("bbox", "-15.70,11.95,-15.50,11.80")))
                        .getCode());
    }

    @Test
    void parsesTypeAndConfidence() {
        MultiValueMap<String, String> map = params("bbox", "-15.70,11.80,-15.50,11.95");
        map.add("type", "house");
        map.add("confidence", "high");
        PropertyQuery query = PropertyRequestParser.parseList(map);
        assertEquals(PropertyType.HOUSE, query.type());
        assertEquals(ConfidenceLevel.HIGH, query.confidence());
    }

    @Test
    void ignoresEmptyTypeAndConfidence() {
        MultiValueMap<String, String> map = params("bbox", "-15.70,11.80,-15.50,11.95");
        map.add("type", "");
        map.add("confidence", "  ");
        PropertyQuery query = PropertyRequestParser.parseList(map);
        assertNull(query.type());
        assertNull(query.confidence());
    }

    @Test
    void rejectsInvalidOrRepeatedFilters() {
        MultiValueMap<String, String> badType = params("bbox", "-15.70,11.80,-15.50,11.95");
        badType.add("type", "villa");
        assertEquals(
                "PROPERTY_FILTER_INVALID",
                assertThrows(ApiException.class, () -> PropertyRequestParser.parseList(badType)).getCode());

        MultiValueMap<String, String> repeated = params("bbox", "-15.70,11.80,-15.50,11.95");
        repeated.add("type", "house");
        repeated.add("type", "land");
        assertEquals(
                "PROPERTY_FILTER_INVALID",
                assertThrows(ApiException.class, () -> PropertyRequestParser.parseList(repeated)).getCode());
    }

    @Test
    void parsesPageAndSize() {
        MultiValueMap<String, String> map = params("bbox", "-15.70,11.80,-15.50,11.95");
        map.add("page", "1");
        map.add("size", "50");
        PropertyQuery query = PropertyRequestParser.parseList(map);
        assertEquals(1, query.page());
        assertEquals(50, query.size());
    }

    @Test
    void rejectsInvalidPageAndSize() {
        assertEquals(
                "PROPERTY_PAGE_INVALID",
                assertThrows(
                                ApiException.class,
                                () -> {
                                    MultiValueMap<String, String> map =
                                            params("bbox", "-15.70,11.80,-15.50,11.95");
                                    map.add("page", "-1");
                                    PropertyRequestParser.parseList(map);
                                })
                        .getCode());
        assertEquals(
                "PROPERTY_PAGE_INVALID",
                assertThrows(
                                ApiException.class,
                                () -> {
                                    MultiValueMap<String, String> map =
                                            params("bbox", "-15.70,11.80,-15.50,11.95");
                                    map.add("size", "0");
                                    PropertyRequestParser.parseList(map);
                                })
                        .getCode());
        assertEquals(
                "PROPERTY_PAGE_INVALID",
                assertThrows(
                                ApiException.class,
                                () -> {
                                    MultiValueMap<String, String> map =
                                            params("bbox", "-15.70,11.80,-15.50,11.95");
                                    map.add("size", "101");
                                    PropertyRequestParser.parseList(map);
                                })
                        .getCode());
        assertEquals(
                "PROPERTY_PAGE_INVALID",
                assertThrows(
                                ApiException.class,
                                () -> {
                                    MultiValueMap<String, String> map =
                                            params("bbox", "-15.70,11.80,-15.50,11.95");
                                    map.add("page", "x");
                                    PropertyRequestParser.parseList(map);
                                })
                        .getCode());
    }

    @Test
    void parsesValidAndInvalidIds() {
        UUID id = UUID.fromString("00000000-0000-0000-0001-000000000001");
        assertEquals(id, PropertyRequestParser.parseId(id.toString()));
        assertEquals(
                "PROPERTY_ID_INVALID",
                assertThrows(ApiException.class, () -> PropertyRequestParser.parseId("not-a-uuid")).getCode());
    }

    private static MultiValueMap<String, String> params(String... keyValues) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.add(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
