package com.example.admin.exception;

import com.example.common.exception.ConflictBusinessException;

public class MateDeletionBlockedException extends ConflictBusinessException {

    public MateDeletionBlockedException(Long mateId) {
        super("MATE_DELETE_HAS_LINKED_RECORDS",
                "연결된 신청·결제·대화·체크인·리뷰 기록이 있어 메이트 모임을 삭제할 수 없습니다. "
                        + "연결 기록을 보존한 뒤 별도 정리 절차를 진행해주세요. (모임 ID: " + mateId + ")");
    }
}
