package com.immodata.gb.application.admin;

import java.util.UUID;

public record PendingProperty(UUID id, String reference, String validationStatus) {}
