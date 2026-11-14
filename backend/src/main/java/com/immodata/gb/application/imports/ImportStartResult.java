package com.immodata.gb.application.imports;

import java.util.UUID;

public record ImportStartResult(UUID jobId, String status) {}
