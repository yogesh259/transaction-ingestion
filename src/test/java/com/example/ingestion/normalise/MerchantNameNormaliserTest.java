package com.example.ingestion.normalise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MerchantNameNormaliserTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            // SBI-style UPI
            "UPI/DR/412345678901/SWIGGY/YESB/swiggy@ybl/Payment | SWIGGY",
            "TO TRANSFER-UPI/DR/412345678902/ZEPTO MARKETPLACE PRIVATE LIMITED/ICIC/zepto@icici/UPI | ZEPTO MARKETPLACE",
            // HDFC-style UPI
            "UPI-ZOMATO LIMITED-zomato@hdfcbank-HDFC0000001-412345678903-PAYMENT | ZOMATO",
            // ICICI-style UPI: remark first, merchant only in the VPA
            "UPI/412345678904/Payment from Ph/swiggy@ybl/YES BANK LIMITED | SWIGGY",
            // Person-to-person transfer keeps the person's name
            "UPI/DR/412345678905/RAHUL KUMAR/SBIN/rahul.k@oksbi/Rent | RAHUL KUMAR",
            // Card, NEFT, ACH
            "POS 512345XXXXXX1234 AMAZON PAY INDIA | AMAZON INDIA",
            "NEFT-HDFCN52023123456-ACME CORP-SALARY | ACME CORP",
            "ACH D- BAJAJ FINANCE LTD-0000123456 | BAJAJ FINANCE",
            // ATM
            "ATM WDL/12345/MG ROAD BANGALORE | ATM CASH WITHDRAWAL",
            "NWD-412345XXXXXX9876-S1ANBG12-BANGALORE | ATM CASH WITHDRAWAL",
    })
    void normalisesBankNarrations(String narration, String expected) {
        assertThat(MerchantNameNormaliser.normalise(narration)).isEqualTo(expected);
    }

    @Test
    void plainMerchantNamesPassThroughUnchanged() {
        assertThat(MerchantNameNormaliser.normalise("STARBUCKS NYC")).isEqualTo("STARBUCKS NYC");
        assertThat(MerchantNameNormaliser.normalise("UBER TRIP 88")).isEqualTo("UBER TRIP 88");
        assertThat(MerchantNameNormaliser.normalise("Atmosphere Cafe")).isEqualTo("Atmosphere Cafe");
        assertThat(MerchantNameNormaliser.normalise("  Posh   Salon ")).isEqualTo("Posh Salon");
    }

    @Test
    void nullAndBlankAreSafe() {
        assertThat(MerchantNameNormaliser.normalise(null)).isNull();
        assertThat(MerchantNameNormaliser.normalise("   ")).isEmpty();
    }
}
