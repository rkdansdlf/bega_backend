package com.example.common.readonly;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class ReadOnlyVerificationPolicy {

    public static final String PROFILE = "local-readonly-verification";

    private final boolean readOnly;

    public ReadOnlyVerificationPolicy(Environment environment) {
        this.readOnly = environment.acceptsProfiles(Profiles.of(PROFILE));
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void requireWritable() {
        if (readOnly) {
            throw new ReadOnlyVerificationUnavailableException();
        }
    }
}
