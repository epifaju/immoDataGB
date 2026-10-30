package com.immodata.gb.api;

import java.util.Map;

public record ApiResponse<T>(boolean success, T data, String message, Map<String, Object> meta) {
}
