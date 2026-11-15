package com.example.common.web;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * 레이트리밋과 로그인 잠금 키에 쓰는 클라이언트 식별자를 만든다.
 *
 * <p>IPv6 는 한 사용자가 /64 안의 주소를 바꿔 가며 매번 새 버킷을 얻을 수 있으므로 /64 단위로 묶는다.
 * IPv4, IPv4 매핑 주소, IP 가 아닌 값(예: unknown)은 그대로 둔다.
 * 로그와 세션 메타데이터에 찍히는 IP 는 바꾸지 않고, 키를 만드는 지점에서만 쓴다.
 */
public final class ClientIpBucket {

    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f:]+");

    private ClientIpBucket() {
    }

    public static String of(String clientIp) {
        if (clientIp == null || !clientIp.contains(":") || !IPV6_LITERAL.matcher(clientIp).matches()) {
            return clientIp;
        }
        try {
            // 리터럴 IPv6 만 통과하므로 DNS 조회가 일어나지 않는다.
            InetAddress address = InetAddress.getByName(clientIp);
            if (!(address instanceof Inet6Address)) {
                return clientIp;
            }
            byte[] bytes = address.getAddress();
            for (int i = 8; i < bytes.length; i++) {
                bytes[i] = 0;
            }
            return InetAddress.getByAddress(bytes).getHostAddress() + "/64";
        } catch (UnknownHostException e) {
            return clientIp;
        }
    }
}
