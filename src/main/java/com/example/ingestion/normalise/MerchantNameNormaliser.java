package com.example.ingestion.normalise;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a raw bank-statement narration into a stable merchant key, so the same
 * merchant is categorised once no matter how many reference numbers, VPAs or
 * bank codes the bank wraps around it.
 *
 * <pre>
 *   UPI/DR/412345678901/SWIGGY/YESB/swiggy@ybl/Payment         -> SWIGGY
 *   UPI-ZOMATO LIMITED-zomato@hdfcbank-HDFC0000001-4123-PAY     -> ZOMATO
 *   POS 512345XXXXXX1234 AMAZON PAY INDIA                      -> AMAZON INDIA
 *   ATM WDL/12345/MG ROAD BANGALORE                            -> ATM CASH WITHDRAWAL
 *   STARBUCKS NYC                                              -> STARBUCKS NYC (unchanged)
 * </pre>
 *
 * Narrations that do not start with a known payment-rail prefix are only
 * whitespace-collapsed, so plain merchant names pass through unchanged.
 */
public final class MerchantNameNormaliser {

    public static final String ATM_WITHDRAWAL = "ATM CASH WITHDRAWAL";
    private static final int MAX_LENGTH = 255;

    private static final Pattern RAIL_PREFIX = Pattern.compile(
            "^(?:TO TRANSFER-|BY TRANSFER-)?(UPI|POS|NEFT|IMPS|RTGS|ACH|NACH|ECS|MMT|BIL|ONL|ECOM)\\b.*");
    private static final Pattern ATM_PREFIX = Pattern.compile("^(?:ATM|ATW|NWD|EAW|CASH WDL)\\b.*|.*\\bATM WDL\\b.*");
    private static final Pattern SEPARATORS = Pattern.compile("[/\\-:|*]+");
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern HAS_DIGITS = Pattern.compile(".*\\d.*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
            "\\s+(?:PVT\\.?\\s*LTD\\.?|PRIVATE\\s+LIMITED|LIMITED|LTD\\.?|INDIA\\s+PVT|TECHNOLOGIES)$");

    /** Rail markers, direction flags and generic remarks that are never the merchant. */
    private static final Set<String> NOISE = Set.of(
            "UPI", "POS", "NEFT", "IMPS", "RTGS", "ACH", "NACH", "ECS", "MMT", "BIL", "ONL", "ECOM",
            "DR", "CR", "D", "C", "P2M", "P2A", "P2P", "TO", "BY", "TRANSFER", "TRF", "TXN", "REF",
            "PAYMENT", "PAY", "PAID", "SENT", "FROM", "PH", "PHONE", "COLLECT", "REQUEST", "UPI PAYMENT", "PAYMENT FROM PH",
            "PAYMENT FROM PHONE", "PAY TO", "NA", "NULL", "OTHERS", "OTHER", "NO REMARKS", "UPIINTENT",
            "YES BANK LIMITED", "YESB", "HDFC", "HDFC BANK", "ICIC", "ICICI BANK", "SBIN", "UTIB",
            "AXIS BANK", "KKBK", "PYTM", "PAYTM PAYMENTS BANK", "IDFB", "INDB", "BARB", "PUNB", "CNRB");

    private MerchantNameNormaliser() {
    }

    public static String normalise(String narration) {
        if (narration == null) {
            return null;
        }
        String collapsed = WHITESPACE.matcher(narration.trim()).replaceAll(" ");
        if (collapsed.isEmpty()) {
            return collapsed;
        }
        String upper = collapsed.toUpperCase(Locale.ROOT);

        if (ATM_PREFIX.matcher(upper).matches()) {
            return ATM_WITHDRAWAL;
        }
        if (!RAIL_PREFIX.matcher(upper).matches()) {
            return truncate(collapsed);
        }

        List<String> tokens = Arrays.stream(SEPARATORS.split(upper))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();

        // 1) First token that looks like a business/person name.
        for (String token : tokens) {
            String candidate = cleanToken(token);
            if (candidate != null) {
                return truncate(stripLegalSuffix(candidate));
            }
        }
        // 2) Fall back to the handle part of a VPA, e.g. "swiggy@ybl" -> "SWIGGY".
        for (String token : tokens) {
            int at = token.indexOf('@');
            if (at > 0) {
                String handle = token.substring(0, at).replaceAll("[._]", " ").trim();
                if (!HAS_DIGITS.matcher(handle).matches() && handle.length() >= 3) {
                    return truncate(handle);
                }
            }
        }
        return truncate(collapsed);
    }

    /** Returns the token if it can be a merchant name, otherwise null. */
    private static String cleanToken(String token) {
        // Masked card numbers ("512345XXXXXX1234") and reference numbers are noise;
        // strip any digit-bearing words, keep the rest ("POS 5123XX AMAZON" -> "AMAZON").
        String words = Arrays.stream(token.split(" "))
                .filter(w -> !HAS_DIGITS.matcher(w).matches())
                .filter(w -> !NOISE.contains(w))
                .reduce((a, b) -> a + " " + b)
                .orElse("")
                .trim();
        if (words.length() < 3 || token.contains("@") || IFSC.matcher(token).matches()
                || NOISE.contains(words)) {
            return null;
        }
        return words;
    }

    private static String stripLegalSuffix(String name) {
        String stripped = LEGAL_SUFFIX.matcher(name).replaceAll("").trim();
        return stripped.length() >= 3 ? stripped : name;
    }

    private static String truncate(String s) {
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH) : s;
    }
}
