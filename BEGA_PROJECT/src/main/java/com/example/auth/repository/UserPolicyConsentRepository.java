package com.example.auth.repository;

import com.example.auth.entity.PolicyType;
import com.example.auth.entity.UserPolicyConsent;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPolicyConsentRepository extends JpaRepository<UserPolicyConsent, Long> {

    boolean existsByUser_IdAndPolicyTypeAndPolicyVersion(Long userId, PolicyType policyType, String policyVersion);

    List<UserPolicyConsent> findByUser_IdAndPolicyTypeIn(Long userId, Collection<PolicyType> policyTypes);
}

