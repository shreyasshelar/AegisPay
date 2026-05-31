package com.aegispay.risk.rules;

import lombok.Value;

import java.util.List;

@Value
public class RuleResult {
    int totalScore;
    List<String> flaggedRules;
}
