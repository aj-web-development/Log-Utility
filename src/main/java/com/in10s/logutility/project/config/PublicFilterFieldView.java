package com.in10s.logutility.project.config;

/** A filter field as shown on the public search form — no match-type or line-prefix leaked. */
public record PublicFilterFieldView(String key, String label) {
}
