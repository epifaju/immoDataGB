package com.immodata.gb.api.valuation;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.valuation.EstimateResult;
import com.immodata.gb.application.valuation.ValuationService;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValuationController {

    private final ValuationService valuationService;

    public ValuationController(ValuationService valuationService) {
        this.valuationService = valuationService;
    }

    @PostMapping("/api/v1/valuations/estimate")
    public ResponseEntity<ApiResponse<EstimateResult>> estimate(@RequestBody Map<String, Object> body) {
        EstimateResult result = valuationService.estimate(ValuationRequestParser.parse(body));
        return ResponseEntity.ok().body(new ApiResponse<>(true, result, null, Map.of()));
    }

    /** JSON absent, malformé ou non-objet : même code métier que les bornes invalides. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> unreadableBody(HttpMessageNotReadableException exception) {
        ApiException invalid = new ApiException("VALUATION_REQUEST_INVALID", HttpStatus.BAD_REQUEST);
        ApiResponse<Object> body = new ApiResponse<>(false, null, invalid.getCode(), Map.of());
        return ResponseEntity.status(invalid.getStatus())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
