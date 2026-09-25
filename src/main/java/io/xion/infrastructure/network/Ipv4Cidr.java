package io.xion.infrastructure.network;

import java.util.Objects;

/**
 * IPv4 CIDR helper (e.g. {@code 10.89.0.0/24}) for bridge namespace subnets.
 */
public final class Ipv4Cidr {

    private final int networkInt;
    private final int prefixLength;
    private final int hostBits;
    private final int hostCount;

    private Ipv4Cidr(int networkInt, int prefixLength) {
        this.networkInt = networkInt;
        this.prefixLength = prefixLength;
        this.hostBits = 32 - prefixLength;
        this.hostCount = 1 << hostBits;
    }

    public static Ipv4Cidr parse(String cidr) {
        Objects.requireNonNull(cidr, "cidr");
        String[] parts = cidr.trim().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR (expected a.b.c.d/prefix): " + cidr);
        }
        int prefix = Integer.parseInt(parts[1]);
        if (prefix < 8 || prefix > 30) {
            throw new IllegalArgumentException("CIDR prefix must be between 8 and 30: " + cidr);
        }
        int addr = toInt(parts[0]);
        int mask = prefix == 0 ? 0 : (-1 << (32 - prefix));
        int network = addr & mask;
        return new Ipv4Cidr(network, prefix);
    }

    public String cidr() {
        return toDotted(networkInt) + "/" + prefixLength;
    }

    public int prefixLength() {
        return prefixLength;
    }

    public String networkAddress() {
        return toDotted(networkInt);
    }

    /** Gateway is the first usable host (.1). */
    public String gateway() {
        return hostAddress(1);
    }

    public String broadcast() {
        return toDotted(networkInt | (hostCount - 1));
    }

    public int usableHostCount() {
        // exclude network (0), gateway (1), broadcast (last)
        return Math.max(0, hostCount - 3);
    }

    public String hostAddress(int hostIndex) {
        if (hostIndex < 0 || hostIndex >= hostCount) {
            throw new IllegalArgumentException("host index out of range: " + hostIndex);
        }
        return toDotted(networkInt + hostIndex);
    }

    public String netmaskDotted() {
        int mask = prefixLength == 0 ? 0 : (-1 << (32 - prefixLength));
        return toDotted(mask);
    }

    public boolean contains(String ip) {
        int addr = toInt(ip);
        int mask = prefixLength == 0 ? 0 : (-1 << (32 - prefixLength));
        return (addr & mask) == networkInt;
    }

    public static int toInt(String dotted) {
        String[] o = dotted.trim().split("\\.");
        if (o.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + dotted);
        }
        int a = Integer.parseInt(o[0]);
        int b = Integer.parseInt(o[1]);
        int c = Integer.parseInt(o[2]);
        int d = Integer.parseInt(o[3]);
        for (int n : new int[]{a, b, c, d}) {
            if (n < 0 || n > 255) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + dotted);
            }
        }
        return (a << 24) | (b << 16) | (c << 8) | d;
    }

    public static String toDotted(int value) {
        return ((value >>> 24) & 0xff) + "."
                + ((value >>> 16) & 0xff) + "."
                + ((value >>> 8) & 0xff) + "."
                + (value & 0xff);
    }
}
