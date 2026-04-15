package com.example.common.config;

final class OracleConnectionDiagnostics {

    private OracleConnectionDiagnostics() {
    }

    static boolean isListenerAclFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("ORA-12506")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static String buildListenerAclFailureMessage(String datasourceUrl, String username, String tnsAdmin) {
        String target = extractOracleTarget(datasourceUrl);
        String walletPath = isBlank(tnsAdmin) ? "<unset>" : tnsAdmin;
        String user = isBlank(username) ? "<unset>" : username;
        return "Oracle listener rejected the connection (ORA-12506). "
                + "Check the Autonomous DB network/service ACL for target ["
                + target
                + "], username ["
                + user
                + "], TNS_ADMIN ["
                + walletPath
                + "].";
    }

    static String extractOracleTarget(String datasourceUrl) {
        if (isBlank(datasourceUrl)) {
            return "<unknown>";
        }
        int atIndex = datasourceUrl.indexOf('@');
        if (atIndex < 0 || atIndex + 1 >= datasourceUrl.length()) {
            return datasourceUrl;
        }
        String target = datasourceUrl.substring(atIndex + 1);
        int queryIndex = target.indexOf('?');
        if (queryIndex >= 0) {
            target = target.substring(0, queryIndex);
        }
        return isBlank(target) ? "<unknown>" : target;
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
