package com.example.auth.service;

import com.example.auth.dto.PolicyConsentItemDto;
import com.example.auth.dto.PolicyRequiredResponseDto;
import com.example.auth.dto.PolicyRequirementItemDto;
import com.example.auth.entity.PolicyType;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserPolicyConsent;
import com.example.auth.repository.UserPolicyConsentRepository;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.PolicyConsentException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyConsentService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CURRENT_POLICY_VERSION = "2026-02-26";
    private static final LocalDate POLICY_EFFECTIVE_DATE = LocalDate.of(2026, 2, 26);
    private static final int GRACE_PERIOD_DAYS = 14;
    private static final String CODE_POLICY_CONSENT_REQUIRED = "POLICY_CONSENT_REQUIRED";
    private static final String CODE_POLICY_VERSION_MISMATCH = "POLICY_VERSION_MISMATCH";

    private static final List<PolicyDefinition> REQUIRED_POLICY_DEFINITIONS = List.of(
            new PolicyDefinition(PolicyType.TERMS, CURRENT_POLICY_VERSION, "/terms", true, POLICY_EFFECTIVE_DATE),
            new PolicyDefinition(PolicyType.PRIVACY, CURRENT_POLICY_VERSION, "/privacy", true, POLICY_EFFECTIVE_DATE),
            new PolicyDefinition(PolicyType.DATA_DISCLAIMER, CURRENT_POLICY_VERSION, "/data-disclaimer", true,
                    POLICY_EFFECTIVE_DATE));

    private final UserPolicyConsentRepository userPolicyConsentRepository;
    private final UserRepository userRepository;
    @Value("${app.policy.hard-gate-approved-date:}")
    private String hardGateApprovedDateRaw;

    public record PolicyDefinition(
            PolicyType policyType,
            String version,
            String path,
            boolean required,
            LocalDate effectiveDate) {
    }

    public record PolicyConsentStatus(
            boolean policyConsentRequired,
            boolean policyConsentNoticeRequired,
            List<String> missingPolicyTypes,
            String effectiveDate,
            String hardGateDate) {
    }

    public PolicyRequiredResponseDto getRequiredPolicyResponse() {
        List<PolicyRequirementItemDto> items = REQUIRED_POLICY_DEFINITIONS.stream()
                .map(definition -> PolicyRequirementItemDto.builder()
                        .policyType(definition.policyType().name())
                        .version(definition.version())
                        .path(definition.path())
                        .required(definition.required())
                        .effectiveDate(definition.effectiveDate().toString())
                        .build())
                .toList();

        return PolicyRequiredResponseDto.builder()
                .policies(items)
                .gracePeriodDays(GRACE_PERIOD_DAYS)
                .effectiveDate(POLICY_EFFECTIVE_DATE.toString())
                .hardGateDate(POLICY_EFFECTIVE_DATE.plusDays(GRACE_PERIOD_DAYS).toString())
                .build();
    }

    public List<PolicyDefinition> getRequiredPolicies() {
        return REQUIRED_POLICY_DEFINITIONS;
    }

    public void validateRequiredConsents(List<PolicyConsentItemDto> policyConsents) {
        if (policyConsents == null || policyConsents.isEmpty()) {
            throw new PolicyConsentException(
                    CODE_POLICY_CONSENT_REQUIRED,
                    "필수 정책 동의가 필요합니다.",
                    REQUIRED_POLICY_DEFINITIONS.stream()
                            .map(item -> item.policyType().name())
                            .toList());
        }

        Map<PolicyType, PolicyConsentItemDto> consentByType = policyConsents.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        PolicyConsentItemDto::getPolicyType,
                        item -> item,
                        (first, second) -> second,
                        () -> new EnumMap<>(PolicyType.class)));

        List<String> missingPolicyTypes = new ArrayList<>();
        List<String> mismatchedPolicyTypes = new ArrayList<>();

        for (PolicyDefinition definition : REQUIRED_POLICY_DEFINITIONS) {
            PolicyConsentItemDto consentItem = consentByType.get(definition.policyType());
            if (consentItem == null || !Boolean.TRUE.equals(consentItem.getAgreed())) {
                missingPolicyTypes.add(definition.policyType().name());
                continue;
            }

            if (!definition.version().equals(consentItem.getVersion())) {
                mismatchedPolicyTypes.add(definition.policyType().name());
            }
        }

        if (!mismatchedPolicyTypes.isEmpty()) {
            throw new PolicyConsentException(
                    CODE_POLICY_VERSION_MISMATCH,
                    "정책 버전이 최신이 아닙니다. 페이지를 새로고침 후 다시 동의해 주세요.",
                    mismatchedPolicyTypes);
        }

        if (!missingPolicyTypes.isEmpty()) {
            throw new PolicyConsentException(
                    CODE_POLICY_CONSENT_REQUIRED,
                    "필수 정책 동의가 필요합니다.",
                    missingPolicyTypes);
        }
    }

    @Transactional
    public void recordRequiredConsents(
            Long userId,
            List<PolicyConsentItemDto> policyConsents,
            String consentMethod,
            String clientIp,
            String userAgent) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 정보가 없습니다.");
        }

        Map<PolicyType, PolicyConsentItemDto> consentByType = policyConsents.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        PolicyConsentItemDto::getPolicyType,
                        item -> item,
                        (first, second) -> second,
                        () -> new EnumMap<>(PolicyType.class)));

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        for (PolicyDefinition definition : REQUIRED_POLICY_DEFINITIONS) {
            PolicyConsentItemDto consentItem = consentByType.get(definition.policyType());
            if (consentItem == null || !Boolean.TRUE.equals(consentItem.getAgreed())) {
                continue;
            }

            boolean exists = userPolicyConsentRepository.existsByUser_IdAndPolicyTypeAndPolicyVersion(
                    userId,
                    definition.policyType(),
                    definition.version());
            if (exists) {
                continue;
            }

            UserPolicyConsent consent = UserPolicyConsent.builder()
                    .user(user)
                    .policyType(definition.policyType())
                    .policyVersion(definition.version())
                    .consentMethod(consentMethod)
                    .consentIp(clientIp)
                    .consentUserAgent(userAgent)
                    .build();
            userPolicyConsentRepository.save(consent);
        }
    }

    @Transactional(readOnly = true)
    public PolicyConsentStatus evaluatePolicyConsentStatus(Long userId) {
        List<String> missingPolicyTypes = getMissingPolicyTypes(userId);
        LocalDate today = LocalDate.now(KST);
        LocalDate softGateEndDate = POLICY_EFFECTIVE_DATE.plusDays(GRACE_PERIOD_DAYS);
        LocalDate approvedDate = resolveHardGateApprovedDate();
        LocalDate hardGateDate = approvedDate == null
                ? softGateEndDate
                : (approvedDate.isAfter(softGateEndDate) ? approvedDate : softGateEndDate);

        boolean consentNoticeRequired = !missingPolicyTypes.isEmpty() && !today.isBefore(POLICY_EFFECTIVE_DATE);
        boolean consentRequired = !missingPolicyTypes.isEmpty() && approvedDate != null && !today.isBefore(hardGateDate);

        return new PolicyConsentStatus(
                consentRequired,
                consentNoticeRequired,
                missingPolicyTypes,
                POLICY_EFFECTIVE_DATE.toString(),
                hardGateDate.toString());
    }

    private LocalDate resolveHardGateApprovedDate() {
        if (hardGateApprovedDateRaw == null || hardGateApprovedDateRaw.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(hardGateApprovedDateRaw.trim());
        } catch (DateTimeParseException e) {
            log.warn("Invalid app.policy.hard-gate-approved-date value '{}'. Expected YYYY-MM-DD", hardGateApprovedDateRaw);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<String> getMissingPolicyTypes(Long userId) {
        if (userId == null) {
            return REQUIRED_POLICY_DEFINITIONS.stream()
                    .map(item -> item.policyType().name())
                    .toList();
        }

        Set<PolicyType> requiredPolicyTypes = REQUIRED_POLICY_DEFINITIONS.stream()
                .map(PolicyDefinition::policyType)
                .collect(Collectors.toSet());

        Set<String> consentedKeys = userPolicyConsentRepository.findByUser_IdAndPolicyTypeIn(userId, requiredPolicyTypes)
                .stream()
                .map(consent -> consent.getPolicyType().name() + "::" + consent.getPolicyVersion())
                .collect(Collectors.toSet());

        return REQUIRED_POLICY_DEFINITIONS.stream()
                .filter(definition -> !consentedKeys
                        .contains(definition.policyType().name() + "::" + definition.version()))
                .map(item -> item.policyType().name())
                .toList();
    }
}
