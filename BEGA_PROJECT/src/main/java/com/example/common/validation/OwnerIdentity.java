package com.example.common.validation;

/**
 * 소유자 식별자 비교 도우미.
 *
 * <p>{@link java.util.Objects#equals} 는 null 과 null 을 같다고 본다. 소유자 컬럼이 null 을 허용하게 된 뒤
 * 그 비교는 익명화된 소유자와 식별되지 않은 사용자를 같은 소유자로 잘못 판정할 수 있다.
 * 이 도우미는 두 값이 모두 있고 같을 때만 같은 소유자로 본다.
 */
public final class OwnerIdentity {

    private OwnerIdentity() {
    }

    public static boolean isSameOwner(Long ownerId, Long userId) {
        return ownerId != null && userId != null && ownerId.equals(userId);
    }
}
