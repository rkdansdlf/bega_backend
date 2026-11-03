# ADR 0001: 게시글·이미지 purge 가드 두 건 처리

- 상태: 제안 (로컬 검증 완료, 커밋 전)
- 날짜: 2026-11-02
- 관련: 계정 삭제 PR 분리 작업 (`feat/account-deletion-moderation-hold`)

## 배경

통합 브랜치의 전체 테스트에서 purge 안전장치 검증 두 건이 실패했다.

1. `CheerPurgedAssetReuseProbeTest.individuallyDeletedManagedImageCannotBeLinkedAgain`
2. `AdminReportAppealDecisionIntegrationTest.concurrentRestoreWaitsForPurgeClaimThenRejects`

## 1. 삭제된 관리 이미지의 재연결 방지 (프로브 테스트)

### 원인
- `ImageService.deleteImage`는 `PostImage` 행과 저장소 객체만 삭제한다. 대응하는 `MediaAsset`의 상태는 바꾸지 않는다.
- 연결 경로의 가드(`MediaLinkService.resolveReadyAssets`)는 `MediaAssetStatus.READY`가 아닌 자산만 거부한다.
- 따라서 삭제된 객체의 키를 다시 연결하면 자산이 여전히 `READY`이므로 예외가 나지 않는다.

### 설계 현황
- 다른 세션이 주 체크아웃에 미커밋 상태로 `CheerIndividualMediaDeletion`(lockOwners, reserve)을 만들고 있다. `reserve`는 자산을 `DELETED`로 바꾼다.
- 이 클래스와 `ImageService` 변경은 커밋되지 않았다. 해당 프로브의 생성자 호출(14개 인자)도 현재 커밋된 `ImageService`에 맞춰져 있다.

### 결정
- 이 브랜치에서 가드를 독립적으로 구현하지 않는다. 다른 세션의 설계와 충돌하고, 소유 파일을 덮어쓰게 되기 때문이다.
- 프로브 테스트는 그대로 둔다. 실패 상태를 숨기지 않는다.
- 해결 경로는 둘 중 하나다. 소유 세션이 해당 파일을 커밋한 뒤 PR에 포함하거나, 소유자와 합의해 프로브를 PR 범위에서 뺀다. 둘 다 승인이 필요하다.

### 영향 범위와 위험
- 삭제된 관리 이미지가 다른 게시글에 다시 연결될 수 있다. 이는 데이터 보존과 무관한 재사용 위험이다.
- 실제 저장소 객체는 이미 삭제된 상태이므로, 연결 후 조회 시 깨진 이미지가 노출될 수 있다.

## 2. 시작 claim과 복구의 동시성 (관리자 테스트)

### 원인
- 픽스처가 `REVIEWING` 상태의 이의제기를 남긴 채 purge claim을 기대했다.
- `CheerPostRepo.claimPurge`는 열린 moderation 사건이 있으면 claim을 거부한다. 이는 의도된 정책이다. 열린 사건이 있는 콘텐츠를 영구 삭제하면 안 된다.
- 따라서 테스트 기대값과 정책이 충돌했다. 가드 자체는 잘못되지 않았다.

### 결정
- 정책을 유지한다. claim 쿼리는 변경하지 않는다.
- 테스트 픽스처를 고쳐, claim 전에 이의제기 상태를 `NONE`으로 닫는다. 동시성 검증 의도는 그대로 유지된다.
- 경계 테스트 `purgeClaimIsRefusedWhileAppealIsReviewingAndAllowedOnceClosed`를 추가해, 열린 이의제기는 claim을 거부하고 종료 후에는 허용됨을 고정한다.

### 변경 파일
- `src/test/java/com/example/admin/service/AdminReportAppealDecisionIntegrationTest.java` (픽스처 1줄, 경계 테스트 1개)

### 검증 결과
- `./gradlew test --rerun-tasks --tests "*AdminReportAppealDecisionIntegrationTest*"`
- 결과: 18개 테스트, 실패 0, 건너뜀 0. 수정 전 실패하던 동시성 테스트 포함.

## 승인 필요 항목

- 커밋 (로컬 worktree 변경 전부: 테스트 수정, 계약 재생성, 홈 WIP 제거, ADR)
- 원격 푸시, PR 생성
- 프로브 가드 해결 방식 결정 (소유 세션 커밋 후 포함, 또는 PR 범위에서 제외)
- 다른 세션 소유 파일을 PR에 포함하는 것

## 남은 위험

- 프로브 1건은 여전히 실패한다. 가드 구현이 다른 세션의 미커밋 작업에 있다.
- 로컬에서는 관련 클래스만 검증했다. 전체 스위트 재실행은 아직 하지 않았다.
