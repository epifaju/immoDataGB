package com.immodata.gb.application.confidence;

public record AppliedRule(String ruleName, String ruleType, int scoreDelta) {

    public static AppliedRule fromDomain(com.immodata.gb.domain.confidence.AppliedRule rule) {
        return new AppliedRule(rule.ruleName(), rule.ruleType(), rule.scoreDelta());
    }
}
