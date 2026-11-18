package com.example.project_flow.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds sensitive data in text before it leaves the company or reaches an AI tool. Detection is
 * pattern based and deliberately strict on credentials. Limits: it finds shapes of data, not meaning,
 * so a secret written in an unusual format can be missed. Treat it as a guard, not a guarantee.
 */
@Component
public class SensitiveDataDetector {

    public enum Kind {
        CREDENTIAL, PAYMENT_CARD, RESIDENT_ID, EMAIL, PHONE
    }

    public record Finding(Kind kind, String type, int count) {
    }

    private record Rule(Kind kind, String type, Pattern pattern) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Kind.CREDENTIAL, "PRIVATE_KEY", Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----")),
            new Rule(Kind.CREDENTIAL, "AWS_ACCESS_KEY", Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b")),
            new Rule(Kind.CREDENTIAL, "GITHUB_TOKEN", Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{30,}\\b")),
            new Rule(Kind.CREDENTIAL, "API_KEY", Pattern.compile("\\bsk-[A-Za-z0-9_\\-]{16,}\\b")),
            new Rule(Kind.CREDENTIAL, "SECRET_ASSIGNMENT", Pattern.compile("(?i)\\b(?:api[_-]?key|secret|token|password|passwd|pwd|비밀번호|시크릿)\\s*[:=]\\s*\\S{4,}")),
            new Rule(Kind.RESIDENT_ID, "KR_RESIDENT_ID", Pattern.compile("\\b\\d{6}-[1-8]\\d{6}\\b")),
            new Rule(Kind.EMAIL, "EMAIL", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")),
            new Rule(Kind.PHONE, "PHONE", Pattern.compile("\\b01[016789][-. ]?\\d{3,4}[-. ]?\\d{4}\\b"))
    );

    private static final Pattern CARD_CANDIDATE = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

    /** Counts each kind of sensitive data in the text. Null or blank text has no findings. */
    public List<Finding> scan(String text) {
        if (text == null || text.isBlank()) return List.of();
        Map<Kind, Map<String, Integer>> counts = new EnumMap<>(Kind.class);
        for (Rule rule : RULES) {
            int n = count(rule.pattern().matcher(text));
            if (n > 0) counts.computeIfAbsent(rule.kind(), k -> new java.util.LinkedHashMap<>()).merge(rule.type(), n, Integer::sum);
        }
        int cards = 0;
        Matcher m = CARD_CANDIDATE.matcher(text);
        while (m.find()) {
            String digits = m.group().replaceAll("[^0-9]", "");
            if (digits.length() >= 13 && luhn(digits)) cards++;
        }
        if (cards > 0) counts.computeIfAbsent(Kind.PAYMENT_CARD, k -> new java.util.LinkedHashMap<>()).put("PAYMENT_CARD", cards);
        List<Finding> out = new ArrayList<>();
        counts.forEach((kind, byType) -> byType.forEach((type, n) -> out.add(new Finding(kind, type, n))));
        return out;
    }

    /** Replaces every match with a labelled placeholder. Credentials are always replaced. */
    public String redact(String text) {
        if (text == null || text.isBlank()) return text;
        String out = text;
        for (Rule rule : RULES) {
            out = rule.pattern().matcher(out).replaceAll("[REDACTED:" + rule.type() + "]");
        }
        Matcher m = CARD_CANDIDATE.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String digits = m.group().replaceAll("[^0-9]", "");
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(digits.length() >= 13 && luhn(digits) ? "[REDACTED:PAYMENT_CARD]" : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static boolean containsCredential(List<Finding> findings) {
        return findings.stream().anyMatch(f -> f.kind() == Kind.CREDENTIAL);
    }

    public static boolean containsPersonalData(List<Finding> findings) {
        return findings.stream().anyMatch(f -> f.kind() == Kind.RESIDENT_ID || f.kind() == Kind.EMAIL
                || f.kind() == Kind.PHONE || f.kind() == Kind.PAYMENT_CARD);
    }

    private static int count(Matcher m) {
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    /** Luhn checksum, used to tell real card numbers from random digit runs. */
    static boolean luhn(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (alt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            alt = !alt;
        }
        return sum % 10 == 0;
    }
}
