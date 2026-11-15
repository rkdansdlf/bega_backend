package com.example.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientIpBucketTest {

    @Test
    void ipv4AddressIsUnchanged() {
        assertThat(ClientIpBucket.of("203.0.113.10")).isEqualTo("203.0.113.10");
    }

    @Test
    void ipv6AddressesInTheSameSlash64ShareOneBucket() {
        String first = ClientIpBucket.of("2001:db8:1:2::10");
        String second = ClientIpBucket.of("2001:db8:1:2:abcd:ef01:2345:6789");

        assertThat(first).isEqualTo("2001:db8:1:2:0:0:0:0/64");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void ipv6AddressesInDifferentSlash64sGetDifferentBuckets() {
        assertThat(ClientIpBucket.of("2001:db8:1:2::10"))
                .isNotEqualTo(ClientIpBucket.of("2001:db8:1:3::10"));
    }

    @Test
    void ipv4MappedIpv6IsTreatedAsIpv4() {
        assertThat(ClientIpBucket.of("::ffff:203.0.113.10")).isEqualTo("::ffff:203.0.113.10");
    }

    @Test
    void nonIpValuesAreUnchanged() {
        assertThat(ClientIpBucket.of("unknown")).isEqualTo("unknown");
        assertThat(ClientIpBucket.of(null)).isNull();
    }
}
