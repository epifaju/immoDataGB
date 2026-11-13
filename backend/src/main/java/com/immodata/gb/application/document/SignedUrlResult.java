package com.immodata.gb.application.document;

import java.time.Instant;

/** URL signée à durée limitée. */
public record SignedUrlResult(String url, Instant expiresAt) {}
