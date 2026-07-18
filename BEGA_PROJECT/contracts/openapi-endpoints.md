# KBO Platform API Endpoints

> This file is generated. Do not edit directly.
> Source: `contracts/openapi.json`
> Regenerate with: `./gradlew updateOpenApiContract`

Version: `1.0`
Paths: **260**
Operations: **285**

## account-security-controller

### GET `/api/auth/account/deletion/recovery`
- Operation ID: `getDeletionRecoveryInfo`
- Tags: `account-security-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `token` | query | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseAccountDeletionRecoveryInfoDto](openapi-schemas.md#apiresponseaccountdeletionrecoveryinfodto)

### POST `/api/auth/account/deletion/recovery`
- Operation ID: `recoverDeletedAccount`
- Tags: `account-security-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [AccountDeletionRecoveryRequestDto](openapi-schemas.md#accountdeletionrecoveryrequestdto)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseVoid](openapi-schemas.md#apiresponsevoid)

### GET `/api/auth/security-events`
- Operation ID: `getSecurityEvents`
- Tags: `account-security-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseListAccountSecurityEventDto](openapi-schemas.md#apiresponselistaccountsecurityeventdto)

### GET `/api/auth/trusted-devices`
- Operation ID: `getTrustedDevices`
- Tags: `account-security-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseListTrustedDeviceDto](openapi-schemas.md#apiresponselisttrusteddevicedto)

### DELETE `/api/auth/trusted-devices/{deviceId}`
- Operation ID: `deleteTrustedDevice`
- Tags: `account-security-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `deviceId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseVoid](openapi-schemas.md#apiresponsevoid)

## admin-controller

### GET `/api/admin/cache-stats`
- Operation ID: `getCacheStats`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMapStringObject](openapi-schemas.md#apiresponsemapstringobject)

### GET `/api/admin/games/non-canonical-cleanup-trackers`
- Operation ID: `getNonCanonicalCleanupTrackers`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseListAdminNonCanonicalCleanupTrackerDto](openapi-schemas.md#apiresponselistadminnoncanonicalcleanuptrackerdto)

### PUT `/api/admin/games/non-canonical-cleanup-trackers`
- Operation ID: `upsertNonCanonicalCleanupTracker`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `endDate` | query | no | `string (date)` | — | — |
| `startDate` | query | yes | `string (date)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [AdminNonCanonicalCleanupTrackerUpsertRequest](openapi-schemas.md#adminnoncanonicalcleanuptrackerupsertrequest)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseAdminNonCanonicalCleanupTrackerDto](openapi-schemas.md#apiresponseadminnoncanonicalcleanuptrackerdto)

### DELETE `/api/admin/games/non-canonical-cleanup-trackers`
- Operation ID: `deleteNonCanonicalCleanupTracker`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `endDate` | query | no | `string (date)` | — | — |
| `startDate` | query | yes | `string (date)` | — | — |

### Response `200`
OK

### POST `/api/admin/games/repair-status-mismatches`
- Operation ID: `repairGameStatusMismatches`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `dryRun` | query | no | `boolean` | — | — |
| `endDate` | query | no | `string (date)` | — | — |
| `startDate` | query | yes | `string (date)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseGameStatusRepairBatchResultDto](openapi-schemas.md#apiresponsegamestatusrepairbatchresultdto)

### GET `/api/admin/games/status-mismatches`
- Operation ID: `getGameStatusMismatches`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `endDate` | query | no | `string (date)` | — | — |
| `startDate` | query | yes | `string (date)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseGameStatusMismatchBatchResultDto](openapi-schemas.md#apiresponsegamestatusmismatchbatchresultdto)

### POST `/api/admin/games/sync-snapshots`
- Operation ID: `syncGameSnapshotsByDateRange`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `endDate` | query | no | `string (date)` | — | — |
| `startDate` | query | yes | `string (date)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseGameScoreSyncBatchResultDto](openapi-schemas.md#apiresponsegamescoresyncbatchresultdto)

### PUT `/api/admin/games/{gameId}/inning-scores`
- Operation ID: `upsertInningScores`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `gameId` | path | yes | `string` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: array<[GameInningScoreRequestDto](openapi-schemas.md#gameinningscorerequestdto)>

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMapStringObject](openapi-schemas.md#apiresponsemapstringobject)

### POST `/api/admin/games/{gameId}/sync-snapshot`
- Operation ID: `syncGameSnapshot`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `gameId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseGameScoreSyncResultDto](openapi-schemas.md#apiresponsegamescoresyncresultdto)

### GET `/api/admin/mates`
- Operation ID: `getMates`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseListAdminMateDto](openapi-schemas.md#apiresponselistadminmatedto)

### DELETE `/api/admin/mates/{mateId}`
- Operation ID: `deleteMate`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `mateId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseVoid](openapi-schemas.md#apiresponsevoid)

### GET `/api/admin/posts`
- Operation ID: `getPosts`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseListAdminPostDto](openapi-schemas.md#apiresponselistadminpostdto)

### DELETE `/api/admin/posts/{postId}`
- Operation ID: `deletePost`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `postId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseVoid](openapi-schemas.md#apiresponsevoid)

### GET `/api/admin/reports`
- Operation ID: `getReports`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `fromDate` | query | no | `string (date)` | — | — |
| `page` | query | no | `integer (int32)` | — | — |
| `reason` | query | no | `string` | — | — |
| `size` | query | no | `integer (int32)` | — | — |
| `status` | query | no | `string` | — | — |
| `toDate` | query | no | `string (date)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponsePageAdminReportDto](openapi-schemas.md#apiresponsepageadminreportdto)

### GET `/api/admin/reports/{reportId}`
- Operation ID: `getReport`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `reportId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseAdminReportDto](openapi-schemas.md#apiresponseadminreportdto)

### PATCH `/api/admin/reports/{reportId}`
- Operation ID: `handleReport`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `reportId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [AdminReportActionReq](openapi-schemas.md#adminreportactionreq)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseAdminReportDto](openapi-schemas.md#apiresponseadminreportdto)

### POST `/api/admin/reports/{reportId}/appeal`
- Operation ID: `requestAppeal`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `reportId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **no**

Media type: `application/json`
Schema: [AdminReportAppealReq](openapi-schemas.md#adminreportappealreq)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseAdminReportDto](openapi-schemas.md#apiresponseadminreportdto)

### GET `/api/admin/seat-views`
- Operation ID: `getSeatViews`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `adminLabel` | query | no | `string` | — | — |
| `aiSuggestedLabel` | query | no | `string` | — | — |
| `moderationStatus` | query | no | `string` | — | — |
| `stadium` | query | no | `string` | — | — |
| `ticketVerified` | query | no | `boolean` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseListAdminSeatViewDto](openapi-schemas.md#apiresponselistadminseatviewdto)

### GET `/api/admin/seat-views/{seatViewId}`
- Operation ID: `getSeatView`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `seatViewId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseAdminSeatViewDto](openapi-schemas.md#apiresponseadminseatviewdto)

### PATCH `/api/admin/seat-views/{seatViewId}`
- Operation ID: `handleSeatView`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `seatViewId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [AdminSeatViewActionReq](openapi-schemas.md#adminseatviewactionreq)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseAdminSeatViewDto](openapi-schemas.md#apiresponseadminseatviewdto)

### GET `/api/admin/stats`
- Operation ID: `getStats`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseAdminStatsDto](openapi-schemas.md#apiresponseadminstatsdto)

### GET `/api/admin/users`
- Operation ID: `getUsers`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `search` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseListAdminUserDto](openapi-schemas.md#apiresponselistadminuserdto)

### DELETE `/api/admin/users/{userId}`
- Operation ID: `deleteUser`
- Tags: `admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `userId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseVoid](openapi-schemas.md#apiresponsevoid)

## admin-maintenance-controller

### POST `/api/admin/maintenance/cheer-posts/cleanup`
- Operation ID: `cleanupSoftDeletedCheerPosts`
- Tags: `admin-maintenance-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseVoid](openapi-schemas.md#apiresponsevoid)

## admin-media-maintenance-controller

### POST `/api/admin/maintenance/media/backfill`
- Operation ID: `backfillExistingData`
- Tags: `admin-media-maintenance-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `apply` | query | no | `boolean` | — | — |
| `batchSize` | query | no | `integer (int32)` | — | — |
| `clearBrokenChatImages` | query | no | `boolean` | — | — |
| `domains` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMediaBackfillReport](openapi-schemas.md#apiresponsemediabackfillreport)

### POST `/api/admin/maintenance/media/cleanup`
- Operation ID: `runCleanup`
- Tags: `admin-media-maintenance-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `targets` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMediaCleanupReport](openapi-schemas.md#apiresponsemediacleanupreport)

### POST `/api/admin/maintenance/media/smoke`
- Operation ID: `runSmoke`
- Tags: `admin-media-maintenance-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `domains` | query | no | `string` | — | — |
| `sampleLimit` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMediaSmokeReport](openapi-schemas.md#apiresponsemediasmokereport)

## admin-role-controller

### GET `/api/admin/roles/audit-logs`
- Operation ID: `getAuditLogs`
- Tags: `admin-role-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponsePageAuditLogDto](openapi-schemas.md#apiresponsepageauditlogdto)

### POST `/api/admin/roles/users/{userId}/demote`
- Operation ID: `demoteToUser`
- Tags: `admin-role-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `userId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **no**

Media type: `application/json`
Schema: [RoleChangeRequestDto](openapi-schemas.md#rolechangerequestdto)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseRoleChangeResponseDto](openapi-schemas.md#apiresponserolechangeresponsedto)

### POST `/api/admin/roles/users/{userId}/promote`
- Operation ID: `promoteToAdmin`
- Tags: `admin-role-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `userId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **no**

Media type: `application/json`
Schema: [RoleChangeRequestDto](openapi-schemas.md#rolechangerequestdto)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseRoleChangeResponseDto](openapi-schemas.md#apiresponserolechangeresponsedto)

## ai-chat-persistence-controller

### GET `/api/ai/chat/favorites`
- Operation ID: `listFavorites`
- Tags: `ai-chat-persistence-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseListChatFavoriteItem](openapi-schemas.md#apiresponselistchatfavoriteitem)

### POST `/api/ai/chat/favorites/{messageId}`
- Operation ID: `addFavorite_2`
- Tags: `ai-chat-persistence-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `messageId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseChatFavoriteItem](openapi-schemas.md#apiresponsechatfavoriteitem)

### DELETE `/api/ai/chat/favorites/{messageId}`
- Operation ID: `removeFavorite_2`
- Tags: `ai-chat-persistence-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `messageId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseVoid](openapi-schemas.md#apiresponsevoid)

### GET `/api/ai/chat/sessions`
- Operation ID: `listSessions`
- Tags: `ai-chat-persistence-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseListChatSessionSummary](openapi-schemas.md#apiresponselistchatsessionsummary)

### POST `/api/ai/chat/sessions`
- Operation ID: `createSession`
- Tags: `ai-chat-persistence-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseChatSessionSummary](openapi-schemas.md#apiresponsechatsessionsummary)

### DELETE `/api/ai/chat/sessions/{sessionId}`
- Operation ID: `deleteSession_1`
- Tags: `ai-chat-persistence-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `sessionId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseVoid](openapi-schemas.md#apiresponsevoid)

### GET `/api/ai/chat/sessions/{sessionId}/messages`
- Operation ID: `listMessages`
- Tags: `ai-chat-persistence-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `sessionId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseListStoredChatMessage](openapi-schemas.md#apiresponseliststoredchatmessage)

### POST `/api/ai/chat/sessions/{sessionId}/messages/assistant`
- Operation ID: `addAssistantMessage`
- Tags: `ai-chat-persistence-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `sessionId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [CreateAssistantChatMessageRequest](openapi-schemas.md#createassistantchatmessagerequest)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseStoredChatMessage](openapi-schemas.md#apiresponsestoredchatmessage)

### POST `/api/ai/chat/sessions/{sessionId}/messages/user`
- Operation ID: `addUserMessage`
- Tags: `ai-chat-persistence-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `sessionId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [CreateUserChatMessageRequest](openapi-schemas.md#createuserchatmessagerequest)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseStoredChatMessage](openapi-schemas.md#apiresponsestoredchatmessage)

## ai-proxy-controller

### POST `/api/ai/chat/completion`
- Operation ID: `chatCompletion`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: `string`

### Response `200`
OK

Media type: `*/*`
Schema: `string (byte)`

### POST `/api/ai/chat/stream`
- Operation ID: `chatStream`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `X-AI-Event-Version` | header | no | `string` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: `string`

### Response `200`
OK

Media type: `*/*`
Schema: [StreamingResponseBody](openapi-schemas.md#streamingresponsebody)

### POST `/api/ai/chat/voice`
- Operation ID: `chatVoice`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **no**

Media type: `application/json`
Schema: `{<br>  "properties" : {<br>    "file" : {<br>      "format" : "binary",<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "file" ],<br>  "type" : "object"<br>}`

### Response `200`
OK

Media type: `*/*`
Schema: `string (byte)`

### POST `/api/ai/coach/analyze`
- Operation ID: `coachAnalyze`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `X-AI-Event-Version` | header | no | `string` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: `string`

### Response `200`
OK

Media type: `*/*`
Schema: [StreamingResponseBody](openapi-schemas.md#streamingresponsebody)

### GET `/api/ai/coach/auto-brief/ops/health`
- Operation ID: `coachAutoBriefOpsHealth`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: `string (byte)`

### GET `/api/ai/release-decision/artifacts`
- Operation ID: `releaseDecisionArtifacts`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: `string (byte)`

### GET `/api/ai/release-decision/artifacts/{artifactId}`
- Operation ID: `releaseDecisionArtifactDetail`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `artifactId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `string (byte)`

### POST `/api/ai/release-decision/draft`
- Operation ID: `releaseDecisionDraft`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: `string`

### Response `200`
OK

Media type: `*/*`
Schema: `string (byte)`

### GET `/api/ai/release-decision/eval-cases`
- Operation ID: `releaseDecisionEvalCases`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: `string (byte)`

### POST `/api/ai/release-decision/evaluate`
- Operation ID: `releaseDecisionEvaluate`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: `string`

### Response `200`
OK

Media type: `*/*`
Schema: `string (byte)`

### GET `/api/ai/release-decision/presets`
- Operation ID: `releaseDecisionPresets`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: `string (byte)`

### POST `/api/ai/release-decision/save`
- Operation ID: `releaseDecisionSave`
- Tags: `ai-proxy-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: `string`

### Response `200`
OK

Media type: `*/*`
Schema: `string (byte)`

## block-controller

### GET `/api/users/me/blocked`
- Operation ID: `getBlockedUsers`
- Tags: `block-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelUserFollowSummaryDto](openapi-schemas.md#pagedmodeluserfollowsummarydto)

### POST `/api/users/profile/{handle}/block`
- Operation ID: `toggleBlockByHandle`
- Tags: `block-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [BlockToggleResponse](openapi-schemas.md#blocktoggleresponse)

## chat-image-controller

### POST `/api/storage/image`
Mate chat image upload
- Operation ID: `uploadChatImage`
- Tags: `chat-image-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **no**

Media type: `multipart/form-data`
Schema: `{<br>  "properties" : {<br>    "file" : {<br>      "format" : "binary",<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "file" ],<br>  "type" : "object"<br>}`

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMateChatImageUploadResponse](openapi-schemas.md#apiresponsematechatimageuploadresponse)

## chat-message-controller

### POST `/api/chat/messages`
Mate chat message send
- Operation ID: `sendMessage_1`
- Tags: `chat-message-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [MateChatMessageRequest](openapi-schemas.md#matechatmessagerequest)

### Response `201`
Created

Media type: `*/*`
Schema: [MateChatMessageResponse](openapi-schemas.md#matechatmessageresponse)

### GET `/api/chat/party/{partyId}`
Mate chat messages by party
- Operation ID: `getMessagesByPartyId`
- Tags: `chat-message-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | path | yes | `integer (int64)` | — | — |
| `beforeId` | query | no | `integer (int64)` | — | — |
| `limit` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MateChatMessageResponse](openapi-schemas.md#matechatmessageresponse)>

### GET `/api/chat/party/{partyId}/latest`
Mate latest chat message by party
- Operation ID: `getLatestMessage`
- Tags: `chat-message-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [MateChatMessageResponse](openapi-schemas.md#matechatmessageresponse)

### Response `204`
No Content

## chat-read-controller

### GET `/api/chat/my/unread-counts`
- Operation ID: `getTotalUnreadCount`
- Tags: `chat-read-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [MateChatUnreadCountResponse](openapi-schemas.md#matechatunreadcountresponse)

### POST `/api/chat/party/{partyId}/read`
- Operation ID: `updateReadTimestamp`
- Tags: `chat-read-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [MateChatReadResponse](openapi-schemas.md#matechatreadresponse)

## check-in-record-controller

### POST `/api/checkin`
- Operation ID: `checkIn`
- Tags: `check-in-record-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [MateCheckInRequest](openapi-schemas.md#matecheckinrequest)

### Response `200`
OK

Media type: `*/*`
Schema: [MateCheckInResponse](openapi-schemas.md#matecheckinresponse)

### GET `/api/checkin/check`
- Operation ID: `isCheckedIn`
- Tags: `check-in-record-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | query | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `boolean`

### GET `/api/checkin/party/{partyId}`
- Operation ID: `getCheckInsByPartyId`
- Tags: `check-in-record-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MateCheckInResponse](openapi-schemas.md#matecheckinresponse)>

### GET `/api/checkin/party/{partyId}/count`
- Operation ID: `getCheckInCount`
- Tags: `check-in-record-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `integer (int64)`

### POST `/api/checkin/qr-session`
- Operation ID: `createQrSession`
- Tags: `check-in-record-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [MateCheckInQrSessionRequest](openapi-schemas.md#matecheckinqrsessionrequest)

### Response `200`
OK

Media type: `*/*`
Schema: [MateCheckInQrSessionResponse](openapi-schemas.md#matecheckinqrsessionresponse)

### GET `/api/checkin/user/{userId}`
- Operation ID: `getCheckInsByUserId`
- Tags: `check-in-record-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `userId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MateCheckInResponse](openapi-schemas.md#matecheckinresponse)>

## check-oracle-controller

### GET `/api/test/counts`
- Operation ID: `getCounts`
- Tags: `check-oracle-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: `{<br>  "additionalProperties" : { },<br>  "type" : "object"<br>}`

### GET `/api/test/games-range`
- Operation ID: `getGamesInRange`
- Tags: `check-oracle-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `end` | query | yes | `string` | — | — |
| `start` | query | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `{<br>  "additionalProperties" : { },<br>  "type" : "object"<br>}`

## client-error-admin-controller

### GET `/api/admin/client-errors/dashboard`
- Operation ID: `getDashboard`
- Tags: `client-error-admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `from` | query | no | `string (date-time)` | — | — |
| `to` | query | no | `string (date-time)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseClientErrorDashboardDto](openapi-schemas.md#apiresponseclienterrordashboarddto)

### GET `/api/admin/client-errors/events`
- Operation ID: `getEvents`
- Tags: `client-error-admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `bucket` | query | no | `string` | — | — |
| `fingerprint` | query | no | `string` | — | — |
| `from` | query | no | `string (date-time)` | — | — |
| `page` | query | no | `integer (int32)` | — | — |
| `route` | query | no | `string` | — | — |
| `search` | query | no | `string` | — | — |
| `size` | query | no | `integer (int32)` | — | — |
| `source` | query | no | `string` | — | — |
| `statusGroup` | query | no | `string` | — | — |
| `to` | query | no | `string (date-time)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseClientErrorEventPageDto](openapi-schemas.md#apiresponseclienterroreventpagedto)

### GET `/api/admin/client-errors/events/{eventId}`
- Operation ID: `getEventDetail`
- Tags: `client-error-admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `eventId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseClientErrorEventDetailDto](openapi-schemas.md#apiresponseclienterroreventdetaildto)

## client-error-controller

### POST `/api/client-errors`
- Operation ID: `ingestClientError`
- Tags: `client-error-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [ClientErrorEventRequest](openapi-schemas.md#clienterroreventrequest)

### Response `200`
OK

### POST `/api/client-errors/feedback`
- Operation ID: `ingestClientErrorFeedback`
- Tags: `client-error-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [ClientErrorFeedbackRequest](openapi-schemas.md#clienterrorfeedbackrequest)

### Response `200`
OK

## diary-controller

### GET `/api/diary/entries`
- Operation ID: `getDiary_1`
- Tags: `diary-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: array<[DiaryResponseDto](openapi-schemas.md#diaryresponsedto)>

### GET `/api/diary/games`
- Operation ID: `getGamesByDate_1`
- Tags: `diary-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `date` | query | yes | `string (date)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[GameResponseDto](openapi-schemas.md#gameresponsedto)>

### POST `/api/diary/save`
- Operation ID: `saveDiary`
- Tags: `diary-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [DiaryRequestDto](openapi-schemas.md#diaryrequestdto)

### Response `200`
OK

Media type: `*/*`
Schema: `object`

### GET `/api/diary/seat-views`
- Operation ID: `getSeatViewPhotos`
- Tags: `diary-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `limit` | query | no | `integer (int32)` | — | — |
| `section` | query | no | `string` | — | — |
| `stadium` | query | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[SeatViewPhotoDto](openapi-schemas.md#seatviewphotodto)>

### GET `/api/diary/statistics`
- Operation ID: `showStatistics`
- Tags: `diary-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [DiaryStatisticsDto](openapi-schemas.md#diarystatisticsdto)

### GET `/api/diary/{id}`
- Operation ID: `getDiary`
- Tags: `diary-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [DiaryResponseDto](openapi-schemas.md#diaryresponsedto)

### POST `/api/diary/{id}/delete`
- Operation ID: `deleteDiary`
- Tags: `diary-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

### POST `/api/diary/{id}/images`
- Operation ID: `uploadImages`
- Tags: `diary-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |
| `sourceTypes` | query | no | `array<string>` | — | — |

#### Request body
Required: **no**

Media type: `application/json`
Schema: `{<br>  "properties" : {<br>    "images" : {<br>      "items" : {<br>        "format" : "binary",<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "required" : [ "images" ],<br>  "type" : "object"<br>}`

### Response `200`
OK

Media type: `*/*`
Schema: `object`

### POST `/api/diary/{id}/modify`
- Operation ID: `updateDiary`
- Tags: `diary-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [DiaryRequestDto](openapi-schemas.md#diaryrequestdto)

### Response `200`
OK

Media type: `*/*`
Schema: [DiaryResponseDto](openapi-schemas.md#diaryresponsedto)

### POST `/api/diary/{id}/seat-view-candidates`
- Operation ID: `createSeatViewCandidates`
- Tags: `diary-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [SeatViewCandidateCreateRequest](openapi-schemas.md#seatviewcandidatecreaterequest)

### Response `200`
OK

Media type: `*/*`
Schema: `object`

### POST `/api/diary/{id}/seat-view-selections`
- Operation ID: `submitSeatViewSelections`
- Tags: `diary-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [SeatViewSelectionRequest](openapi-schemas.md#seatviewselectionrequest)

### Response `200`
OK

Media type: `*/*`
Schema: `object`

## dm-controller

### POST `/api/dm/messages`
- Operation ID: `sendMessage`
- Tags: `dm-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [Request](openapi-schemas.md#request)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### DELETE `/api/dm/messages/{messageId}`
- Operation ID: `deleteMessage`
- Tags: `dm-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `messageId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### POST `/api/dm/rooms`
- Operation ID: `bootstrapRoom`
- Tags: `dm-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [BootstrapRequest](openapi-schemas.md#bootstraprequest)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### GET `/api/dm/rooms/my`
- Operation ID: `getMyRooms`
- Tags: `dm-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### GET `/api/dm/rooms/{roomId}/messages`
- Operation ID: `getMessages`
- Tags: `dm-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `roomId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

## follow-controller

### GET `/api/users/me/follow-counts`
- Operation ID: `getMyFollowCounts`
- Tags: `follow-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [FollowCountResponse](openapi-schemas.md#followcountresponse)

### GET `/api/users/me/followers`
- Operation ID: `getMyFollowers`
- Tags: `follow-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelUserFollowSummaryDto](openapi-schemas.md#pagedmodeluserfollowsummarydto)

### DELETE `/api/users/me/followers/{followerId}`
- Operation ID: `removeFollower`
- Tags: `follow-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `followerId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

### GET `/api/users/me/following`
- Operation ID: `getMyFollowing`
- Tags: `follow-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelUserFollowSummaryDto](openapi-schemas.md#pagedmodeluserfollowsummarydto)

### POST `/api/users/profile/{handle}/follow`
- Operation ID: `toggleFollowByHandle`
- Tags: `follow-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [FollowToggleResponse](openapi-schemas.md#followtoggleresponse)

### GET `/api/users/profile/{handle}/follow-counts`
- Operation ID: `getPublicFollowCounts`
- Tags: `follow-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [FollowCountResponse](openapi-schemas.md#followcountresponse)

### PUT `/api/users/profile/{handle}/follow/notify`
- Operation ID: `updateNotifySettingByHandle`
- Tags: `follow-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |
| `notify` | query | yes | `boolean` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [FollowToggleResponse](openapi-schemas.md#followtoggleresponse)

### GET `/api/users/profile/{handle}/followers`
- Operation ID: `getPublicFollowers`
- Tags: `follow-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelUserFollowSummaryDto](openapi-schemas.md#pagedmodeluserfollowsummarydto)

### GET `/api/users/profile/{handle}/following`
- Operation ID: `getPublicFollowing`
- Tags: `follow-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelUserFollowSummaryDto](openapi-schemas.md#pagedmodeluserfollowsummarydto)

## game-live-controller

### GET `/api/matches/live`
- Operation ID: `getLiveSummaries`
- Tags: `game-live-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `gameIds` | query | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[GameLiveSummaryDto](openapi-schemas.md#gamelivesummarydto)>

### GET `/api/matches/{gameId}/live`
- Operation ID: `getLiveSnapshot`
- Tags: `game-live-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `gameId` | path | yes | `string` | — | — |
| `afterSeq` | query | no | `integer (int32)` | — | — |
| `limit` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [GameLiveSnapshotDto](openapi-schemas.md#gamelivesnapshotdto)

### GET `/api/matches/{gameId}/live-relay`
- Operation ID: `getLiveRelaySnapshot`
- Tags: `game-live-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `gameId` | path | yes | `string` | — | — |
| `afterId` | query | no | `integer (int32)` | — | — |
| `limit` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [GameRelaySnapshotDto](openapi-schemas.md#gamerelaysnapshotdto)

## home-controller

### GET `/api/home/bootstrap`
- Operation ID: `getBootstrap`
- Tags: `home-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `date` | query | no | `string (date)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [HomeBootstrapResponseDto](openapi-schemas.md#homebootstrapresponsedto)

### GET `/api/home/navigation`
- Operation ID: `getNavigation`
- Tags: `home-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `date` | query | no | `string (date)` | — | — |
| `scope` | query | no | `string` | — | — |
| `seasonYear` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [HomeScopedNavigationDto](openapi-schemas.md#homescopednavigationdto)

### GET `/api/home/widgets`
- Operation ID: `getWidgets`
- Tags: `home-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `date` | query | no | `string (date)` | — | — |
| `seasonYear` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [HomeWidgetsResponseDto](openapi-schemas.md#homewidgetsresponsedto)

## home-page-controller

### GET `/api/kbo/league-start-dates`
- Operation ID: `getLeagueStartDates`
- Tags: `home-page-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [LeagueStartDatesDto](openapi-schemas.md#leaguestartdatesdto)

### GET `/api/kbo/rankings/snapshot`
- Operation ID: `getRankingSnapshot`
- Tags: `home-page-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `date` | query | no | `string (date)` | — | — |
| `seasonYear` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [HomeRankingSnapshotDto](openapi-schemas.md#homerankingsnapshotdto)

### GET `/api/kbo/rankings/{seasonYear}`
- Operation ID: `getTeamRankings`
- Tags: `home-page-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `seasonYear` | path | yes | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[HomePageTeamRankingDto](openapi-schemas.md#homepageteamrankingdto)>

### GET `/api/kbo/schedule`
- Operation ID: `getGamesByDate`
- Tags: `home-page-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `date` | query | yes | `string (date)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[HomePageGameDto](openapi-schemas.md#homepagegamedto)>

### GET `/api/kbo/schedule/navigation`
- Operation ID: `getScheduleNavigation`
- Tags: `home-page-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `date` | query | yes | `string (date)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ScheduleNavigationDto](openapi-schemas.md#schedulenavigationdto)

## image-controller

### DELETE `/api/images/{imageId}`
- Operation ID: `deleteImage`
- Tags: `image-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `imageId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

### POST `/api/images/{imageId}/signed-url`
- Operation ID: `renewSignedUrl`
- Tags: `image-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `imageId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [SignedUrlDto](openapi-schemas.md#signedurldto)

### POST `/api/images/{imageId}/thumbnail`
- Operation ID: `markAsThumbnail`
- Tags: `image-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `imageId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PostImageDto](openapi-schemas.md#postimagedto)

### GET `/api/posts/{postId}/images`
- Operation ID: `listPostImages`
- Tags: `image-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `postId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[PostImageDto](openapi-schemas.md#postimagedto)>

### POST `/api/posts/{postId}/images`
- Operation ID: `uploadPostImages`
- Tags: `image-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `postId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **no**

Media type: `multipart/form-data`
Schema: `{<br>  "properties" : {<br>    "files" : {<br>      "items" : {<br>        "format" : "binary",<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "required" : [ "files" ],<br>  "type" : "object"<br>}`

### Response `200`
OK

Media type: `*/*`
Schema: array<[PostImageDto](openapi-schemas.md#postimagedto)>

## internal-payout-seller-controller

### POST `/api/internal/payout/sellers`
- Operation ID: `upsertSellerProfile`
- Tags: `internal-payout-seller-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [MateSellerPayoutProfileUpsertRequest](openapi-schemas.md#matesellerpayoutprofileupsertrequest)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMateSellerPayoutProfileResponse](openapi-schemas.md#apiresponsematesellerpayoutprofileresponse)

### GET `/api/internal/payout/sellers/{userId}`
- Operation ID: `getSellerProfile`
- Tags: `internal-payout-seller-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `userId` | path | yes | `integer (int64)` | — | — |
| `provider` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMateSellerPayoutProfileResponse](openapi-schemas.md#apiresponsematesellerpayoutprofileresponse)

## internal-settlement-controller

### POST `/api/internal/settlements/{paymentId}/payout`
- Operation ID: `requestPayout`
- Tags: `internal-settlement-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `paymentId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMateInternalSettlementPayoutResponse](openapi-schemas.md#apiresponsemateinternalsettlementpayoutresponse)

## mate-search-term-controller

### POST `/api/parties/search-terms`
- Operation ID: `recordSearchTerm`
- Tags: `mate-search-term-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **no**

Media type: `application/json`
Schema: [RecordRequest](openapi-schemas.md#recordrequest)

### Response `204`
No Content

### GET `/api/parties/search-terms/popular`
- Operation ID: `getPopularSearchTerms`
- Tags: `mate-search-term-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `limit` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[PopularResponse](openapi-schemas.md#popularresponse)>

## media-upload-controller

### POST `/api/media/uploads/init`
- Operation ID: `initUpload`
- Tags: `media-upload-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [InitMediaUploadRequest](openapi-schemas.md#initmediauploadrequest)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### DELETE `/api/media/uploads/{assetId}`
- Operation ID: `deleteUpload`
- Tags: `media-upload-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `assetId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### POST `/api/media/uploads/{assetId}/finalize`
- Operation ID: `finalizeUpload`
- Tags: `media-upload-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `assetId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

## mypage-controller

### DELETE `/api/auth/account`
- Operation ID: `deleteAccount`
- Tags: `mypage-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **no**

Media type: `application/json`
Schema: [DeleteAccountRequest](openapi-schemas.md#deleteaccountrequest)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### GET `/api/auth/mypage`
- Operation ID: `getMyProfile`
- Tags: `mypage-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### PUT `/api/auth/mypage`
- Operation ID: `updateMyProfile`
- Tags: `mypage-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [UserProfileDto](openapi-schemas.md#userprofiledto)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### PUT `/api/auth/password`
- Operation ID: `changePassword`
- Tags: `mypage-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [ChangePasswordRequest](openapi-schemas.md#changepasswordrequest)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### GET `/api/auth/providers`
- Operation ID: `getConnectedProviders`
- Tags: `mypage-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### DELETE `/api/auth/providers/{provider}`
- Operation ID: `unlinkProvider`
- Tags: `mypage-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `provider` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### GET `/api/auth/sessions`
- Operation ID: `getSessions`
- Tags: `mypage-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### DELETE `/api/auth/sessions`
- Operation ID: `deleteSessions`
- Tags: `mypage-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `allExceptCurrent` | query | no | `boolean` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### DELETE `/api/auth/sessions/{sessionId}`
- Operation ID: `deleteSession`
- Tags: `mypage-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `sessionId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

## notification-controller

### POST `/api/notifications/mark-all-read`
- Operation ID: `markAllAsRead`
- Tags: `notification-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

### GET `/api/notifications/my`
- Operation ID: `getMyNotifications`
- Tags: `notification-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[Response](openapi-schemas.md#response)>

### GET `/api/notifications/my/unread-count`
- Operation ID: `getMyUnreadCount`
- Tags: `notification-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: `integer (int64)`

### DELETE `/api/notifications/{notificationId}`
- Operation ID: `deleteNotification`
- Tags: `notification-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `notificationId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `object`

### POST `/api/notifications/{notificationId}/read`
- Operation ID: `markAsRead`
- Tags: `notification-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `notificationId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `object`

## offseason-controller

### GET `/api/kbo/offseason/metadata`
- Operation ID: `getMetadata`
- Tags: `offseason-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `year` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [OffseasonMetaDto](openapi-schemas.md#offseasonmetadto)

### GET `/api/kbo/offseason/movements`
- Operation ID: `getMovements_1`
- Tags: `offseason-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: array<[OffseasonMovementDto](openapi-schemas.md#offseasonmovementdto)>

## offseason-movement-admin-controller

### GET `/api/admin/offseason/movements`
- Operation ID: `getMovements`
- Tags: `offseason-movement-admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `fromDate` | query | no | `string (date)` | — | — |
| `search` | query | no | `string` | — | — |
| `section` | query | no | `string` | — | — |
| `teamCode` | query | no | `string` | — | — |
| `toDate` | query | no | `string (date)` | — | — |

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: [ApiResponseListOffseasonMovementAdminDto](openapi-schemas.md#apiresponselistoffseasonmovementadmindto)

Media type: `application/json;charset=UTF-8`
Schema: [ApiResponseListOffseasonMovementAdminDto](openapi-schemas.md#apiresponselistoffseasonmovementadmindto)

### POST `/api/admin/offseason/movements`
- Operation ID: `createMovement`
- Tags: `offseason-movement-admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [OffseasonMovementAdminRequest](openapi-schemas.md#offseasonmovementadminrequest)

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: [ApiResponseOffseasonMovementAdminDto](openapi-schemas.md#apiresponseoffseasonmovementadmindto)

Media type: `application/json;charset=UTF-8`
Schema: [ApiResponseOffseasonMovementAdminDto](openapi-schemas.md#apiresponseoffseasonmovementadmindto)

### PUT `/api/admin/offseason/movements/{movementId}`
- Operation ID: `updateMovement`
- Tags: `offseason-movement-admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `movementId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [OffseasonMovementAdminRequest](openapi-schemas.md#offseasonmovementadminrequest)

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: [ApiResponseOffseasonMovementAdminDto](openapi-schemas.md#apiresponseoffseasonmovementadmindto)

Media type: `application/json;charset=UTF-8`
Schema: [ApiResponseOffseasonMovementAdminDto](openapi-schemas.md#apiresponseoffseasonmovementadmindto)

### DELETE `/api/admin/offseason/movements/{movementId}`
- Operation ID: `deleteMovement`
- Tags: `offseason-movement-admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `movementId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: [ApiResponseMapStringLong](openapi-schemas.md#apiresponsemapstringlong)

Media type: `application/json;charset=UTF-8`
Schema: [ApiResponseMapStringLong](openapi-schemas.md#apiresponsemapstringlong)

## party-application-controller

### POST `/api/applications`
- Operation ID: `createApplication`
- Tags: `party-application-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [MateApplicationCreateRequest](openapi-schemas.md#mateapplicationcreaterequest)

### Response `200`
OK

Media type: `*/*`
Schema: [MateApplicationResponse](openapi-schemas.md#mateapplicationresponse)

### GET `/api/applications/my`
- Operation ID: `getMyApplications`
- Tags: `party-application-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: array<[MateApplicationResponse](openapi-schemas.md#mateapplicationresponse)>

### GET `/api/applications/party/{partyId}`
- Operation ID: `getApplicationsByPartyId`
- Tags: `party-application-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MateApplicationResponse](openapi-schemas.md#mateapplicationresponse)>

### GET `/api/applications/party/{partyId}/approved`
- Operation ID: `getApprovedApplications`
- Tags: `party-application-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MateApplicationResponse](openapi-schemas.md#mateapplicationresponse)>

### GET `/api/applications/party/{partyId}/mine`
- Operation ID: `getMyApplicationByPartyId`
- Tags: `party-application-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [MateApplicationResponse](openapi-schemas.md#mateapplicationresponse)

### GET `/api/applications/party/{partyId}/pending`
- Operation ID: `getPendingApplications`
- Tags: `party-application-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MateApplicationResponse](openapi-schemas.md#mateapplicationresponse)>

### GET `/api/applications/party/{partyId}/rejected`
- Operation ID: `getRejectedApplications`
- Tags: `party-application-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MateApplicationResponse](openapi-schemas.md#mateapplicationresponse)>

### DELETE `/api/applications/{applicationId}`
- Operation ID: `cancelApplication`
- Tags: `party-application-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `applicationId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

### POST `/api/applications/{applicationId}/approve`
- Operation ID: `approveApplication`
- Tags: `party-application-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `applicationId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [MateApplicationResponse](openapi-schemas.md#mateapplicationresponse)

### POST `/api/applications/{applicationId}/cancel`
- Operation ID: `cancelApplicationWithReason`
- Tags: `party-application-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `applicationId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **no**

Media type: `application/json`
Schema: [MateApplicationCancelRequest](openapi-schemas.md#mateapplicationcancelrequest)

### Response `200`
OK

Media type: `*/*`
Schema: [MateApplicationCancelResponse](openapi-schemas.md#mateapplicationcancelresponse)

### POST `/api/applications/{applicationId}/reject`
- Operation ID: `rejectApplication`
- Tags: `party-application-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `applicationId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [MateApplicationResponse](openapi-schemas.md#mateapplicationresponse)

## party-review-controller

### POST `/api/reviews`
Mate review create
- Operation ID: `createReview`
- Tags: `party-review-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [MateReviewCreateRequest](openapi-schemas.md#matereviewcreaterequest)

### Response `201`
Created

Media type: `*/*`
Schema: [MateReviewResponse](openapi-schemas.md#matereviewresponse)

### GET `/api/reviews/host/{handle}`
- Operation ID: `getReviewsByHost`
- Tags: `party-review-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MateReviewResponse](openapi-schemas.md#matereviewresponse)>

### GET `/api/reviews/party/{partyId}`
- Operation ID: `getReviewsByParty`
- Tags: `party-review-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `partyId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MateReviewResponse](openapi-schemas.md#matereviewresponse)>

## password-reset-controller

### POST `/api/auth/password/reset/confirm`
- Operation ID: `confirmPasswordReset`
- Tags: `password-reset-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [PasswordResetConfirmDto](openapi-schemas.md#passwordresetconfirmdto)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### POST `/api/auth/password/reset/request`
- Operation ID: `requestPasswordReset`
- Tags: `password-reset-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [PasswordResetRequestDto](openapi-schemas.md#passwordresetrequestdto)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

## payment-controller

### GET `/api/payments/capability`
Get Mate payment capability
- Operation ID: `getPaymentCapability`
- Tags: `payment-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [MatePaymentCapabilityResponse](openapi-schemas.md#matepaymentcapabilityresponse)

### POST `/api/payments/toss/confirm`
Confirm Mate Toss payment
- Operation ID: `confirmTossPayment`
- Tags: `payment-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [MatePaymentConfirmRequest](openapi-schemas.md#matepaymentconfirmrequest)

### Response `200`
OK

Media type: `*/*`
Schema: [MateApplicationResponse](openapi-schemas.md#mateapplicationresponse)

### Response `201`
Created

Media type: `*/*`
Schema: [MateApplicationResponse](openapi-schemas.md#mateapplicationresponse)

### POST `/api/payments/toss/prepare`
Prepare Mate Toss payment intent
- Operation ID: `prepareTossPayment`
- Tags: `payment-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [MatePaymentPrepareRequest](openapi-schemas.md#matepaymentpreparerequest)

### Response `200`
OK

Media type: `*/*`
Schema: [MatePaymentPrepareResponse](openapi-schemas.md#matepaymentprepareresponse)

### POST `/api/payments/toss/{intentId}/cancel`
Cancel Mate Toss payment intent
- Operation ID: `cancelTossPayment`
- Tags: `payment-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `intentId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **no**

Media type: `application/json`
Schema: [MatePaymentCancelIntentRequest](openapi-schemas.md#matepaymentcancelintentrequest)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMatePaymentCancelIntentResponse](openapi-schemas.md#apiresponsematepaymentcancelintentresponse)

## profile-image-controller

### POST `/api/profile/image`
- Operation ID: `uploadProfileImage`
- Tags: `profile-image-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **no**

Media type: `multipart/form-data`
Schema: `{<br>  "properties" : {<br>    "file" : {<br>      "format" : "binary",<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "file" ],<br>  "type" : "object"<br>}`

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

## ranking-prediction-controller

### GET `/api/predictions/ranking`
- Operation ID: `getPredction`
- Tags: `ranking-prediction-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `seasonYear` | query | yes | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [RankingPredictionResponseDto](openapi-schemas.md#rankingpredictionresponsedto)

### POST `/api/predictions/ranking`
- Operation ID: `savePrediction`
- Tags: `ranking-prediction-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [RankingPredictionRequestDto](openapi-schemas.md#rankingpredictionrequestdto)

### Response `200`
OK

Media type: `*/*`
Schema: [RankingPredictionResponseDto](openapi-schemas.md#rankingpredictionresponsedto)

### GET `/api/predictions/ranking/current-season`
- Operation ID: `getCurrentSeason`
- Tags: `ranking-prediction-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [RankingPredictionCurrentSeasonDto](openapi-schemas.md#rankingpredictioncurrentseasondto)

### GET `/api/predictions/ranking/init`
- Operation ID: `getInit`
- Tags: `ranking-prediction-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [RankingPredictionInitDto](openapi-schemas.md#rankingpredictioninitdto)

### GET `/api/predictions/ranking/share/{shareId}/{seasonYear}`
- Operation ID: `getSharedPrediction`
- Tags: `ranking-prediction-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `seasonYear` | path | yes | `integer (int32)` | — | — |
| `shareId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [RankingPredictionResponseDto](openapi-schemas.md#rankingpredictionresponsedto)

## reissue-controller

### POST `/api/auth/reissue`
- Operation ID: `reissue`
- Tags: `reissue-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

## stadium-admin-controller

### PUT `/api/admin/stadiums/places/{placeId}`
- Operation ID: `updatePlace`
- Tags: `stadium-admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `placeId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [PlaceRequest](openapi-schemas.md#placerequest)

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

Media type: `application/json;charset=UTF-8`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### DELETE `/api/admin/stadiums/places/{placeId}`
- Operation ID: `deletePlace`
- Tags: `stadium-admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `placeId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

Media type: `application/json;charset=UTF-8`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### POST `/api/admin/stadiums/{stadiumId}/places`
- Operation ID: `createPlace`
- Tags: `stadium-admin-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `stadiumId` | path | yes | `string` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [PlaceRequest](openapi-schemas.md#placerequest)

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

Media type: `application/json;charset=UTF-8`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

## stadium-api-controller

### GET `/api/stadiums`
- Operation ID: `getStadiums`
- Tags: `stadium-api-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: array<[StadiumDto](openapi-schemas.md#stadiumdto)>

Media type: `application/json;charset=UTF-8`
Schema: array<[StadiumDto](openapi-schemas.md#stadiumdto)>

### GET `/api/stadiums/favorites`
- Operation ID: `getFavorites`
- Tags: `stadium-api-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: `{<br>  "additionalProperties" : {<br>    "items" : {<br>      "type" : "string"<br>    },<br>    "type" : "array"<br>  },<br>  "type" : "object"<br>}`

Media type: `application/json;charset=UTF-8`
Schema: `{<br>  "additionalProperties" : {<br>    "items" : {<br>      "type" : "string"<br>    },<br>    "type" : "array"<br>  },<br>  "type" : "object"<br>}`

### GET `/api/stadiums/name/{stadiumName}`
- Operation ID: `getStadiumDetailByName`
- Tags: `stadium-api-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `stadiumName` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: [StadiumDetailDto](openapi-schemas.md#stadiumdetaildto)

Media type: `application/json;charset=UTF-8`
Schema: [StadiumDetailDto](openapi-schemas.md#stadiumdetaildto)

### GET `/api/stadiums/name/{stadiumName}/places`
- Operation ID: `getPlacesByStadiumName`
- Tags: `stadium-api-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `stadiumName` | path | yes | `string` | — | — |
| `category` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: array<[PlaceDto](openapi-schemas.md#placedto)>

Media type: `application/json;charset=UTF-8`
Schema: array<[PlaceDto](openapi-schemas.md#placedto)>

### GET `/api/stadiums/places/all`
- Operation ID: `getAllPlaces`
- Tags: `stadium-api-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: array<[PlaceDto](openapi-schemas.md#placedto)>

Media type: `application/json;charset=UTF-8`
Schema: array<[PlaceDto](openapi-schemas.md#placedto)>

### GET `/api/stadiums/{stadiumId}`
- Operation ID: `getStadiumDetail`
- Tags: `stadium-api-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `stadiumId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: [StadiumDetailDto](openapi-schemas.md#stadiumdetaildto)

Media type: `application/json;charset=UTF-8`
Schema: [StadiumDetailDto](openapi-schemas.md#stadiumdetaildto)

### POST `/api/stadiums/{stadiumId}/favorite`
- Operation ID: `addFavorite`
- Tags: `stadium-api-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `stadiumId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: `{<br>  "additionalProperties" : {<br>    "type" : "boolean"<br>  },<br>  "type" : "object"<br>}`

Media type: `application/json;charset=UTF-8`
Schema: `{<br>  "additionalProperties" : {<br>    "type" : "boolean"<br>  },<br>  "type" : "object"<br>}`

### DELETE `/api/stadiums/{stadiumId}/favorite`
- Operation ID: `removeFavorite`
- Tags: `stadium-api-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `stadiumId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: `{<br>  "additionalProperties" : {<br>    "type" : "boolean"<br>  },<br>  "type" : "object"<br>}`

Media type: `application/json;charset=UTF-8`
Schema: `{<br>  "additionalProperties" : {<br>    "type" : "boolean"<br>  },<br>  "type" : "object"<br>}`

### GET `/api/stadiums/{stadiumId}/places`
- Operation ID: `getPlacesByStadium`
- Tags: `stadium-api-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `stadiumId` | path | yes | `string` | — | — |
| `category` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `application/json; charset=UTF-8`
Schema: array<[PlaceDto](openapi-schemas.md#placedto)>

Media type: `application/json;charset=UTF-8`
Schema: array<[PlaceDto](openapi-schemas.md#placedto)>

## team-controller

### GET `/api/teams`
- Operation ID: `getTeams`
- Tags: `team-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `includeInactive` | query | no | `boolean` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamSummaryDto](openapi-schemas.md#teamsummarydto)>

### GET `/api/teams/active`
- Operation ID: `getActiveTeams`
- Tags: `team-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamSummaryDto](openapi-schemas.md#teamsummarydto)>

## team-franchise-controller

### GET `/api/franchises`
- Operation ID: `getAllFranchises`
- Tags: `team-franchise-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamFranchiseEntity](openapi-schemas.md#teamfranchiseentity)>

### GET `/api/franchises/code/{code}`
- Operation ID: `getFranchiseByCode`
- Tags: `team-franchise-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `code` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [TeamFranchiseEntity](openapi-schemas.md#teamfranchiseentity)

### GET `/api/franchises/search`
- Operation ID: `searchFranchises`
- Tags: `team-franchise-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `keyword` | query | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamFranchiseEntity](openapi-schemas.md#teamfranchiseentity)>

### GET `/api/franchises/{id}`
- Operation ID: `getFranchiseById`
- Tags: `team-franchise-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [TeamFranchiseEntity](openapi-schemas.md#teamfranchiseentity)

### GET `/api/franchises/{id}/current-team`
- Operation ID: `getCurrentTeam`
- Tags: `team-franchise-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [TeamEntity](openapi-schemas.md#teamentity)

### GET `/api/franchises/{id}/history`
- Operation ID: `getFranchiseHistory`
- Tags: `team-franchise-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamHistoryEntity](openapi-schemas.md#teamhistoryentity)>

### GET `/api/franchises/{id}/history/recent`
- Operation ID: `getRecentHistory`
- Tags: `team-franchise-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int32)` | — | — |
| `years` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamHistoryEntity](openapi-schemas.md#teamhistoryentity)>

### GET `/api/franchises/{id}/metadata`
- Operation ID: `getFranchiseMetadata`
- Tags: `team-franchise-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `{<br>  "additionalProperties" : { },<br>  "type" : "object"<br>}`

### GET `/api/franchises/{id}/teams`
- Operation ID: `getFranchiseTeams`
- Tags: `team-franchise-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int32)` | — | — |
| `includeInactive` | query | no | `boolean` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamEntity](openapi-schemas.md#teamentity)>

## team-history-controller

### GET `/api/team-history/range`
- Operation ID: `getHistoryByRange`
- Tags: `team-history-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `endYear` | query | yes | `integer (int32)` | — | — |
| `startYear` | query | yes | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamHistoryEntity](openapi-schemas.md#teamhistoryentity)>

### GET `/api/team-history/recent`
- Operation ID: `getRecentSeasons`
- Tags: `team-history-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `limit` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamHistoryEntity](openapi-schemas.md#teamhistoryentity)>

### GET `/api/team-history/season/{season}`
- Operation ID: `getSeasonTeams`
- Tags: `team-history-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `season` | path | yes | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamHistoryEntity](openapi-schemas.md#teamhistoryentity)>

### GET `/api/team-history/season/{season}/standings`
- Operation ID: `getSeasonStandings`
- Tags: `team-history-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `season` | path | yes | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamHistoryEntity](openapi-schemas.md#teamhistoryentity)>

### GET `/api/team-history/seasons`
- Operation ID: `getAvailableSeasons`
- Tags: `team-history-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: `array<integer (int32)>`

### GET `/api/team-history/stadium/{stadium}`
- Operation ID: `getHistoryByStadium`
- Tags: `team-history-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `stadium` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamHistoryEntity](openapi-schemas.md#teamhistoryentity)>

### GET `/api/team-history/statistics/{season}`
- Operation ID: `getSeasonStatistics`
- Tags: `team-history-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `season` | path | yes | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `{<br>  "additionalProperties" : { },<br>  "type" : "object"<br>}`

### GET `/api/team-history/team/{teamCode}`
- Operation ID: `getTeamCodeHistory`
- Tags: `team-history-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `teamCode` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[TeamHistoryEntity](openapi-schemas.md#teamhistoryentity)>

### GET `/api/team-history/team/{teamCode}/season/{season}`
- Operation ID: `getTeamSeason`
- Tags: `team-history-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `season` | path | yes | `integer (int32)` | — | — |
| `teamCode` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [TeamHistoryEntity](openapi-schemas.md#teamhistoryentity)

## team-recommendation-test-controller

### POST `/api/quiz/result`
- Operation ID: `getResult`
- Tags: `team-recommendation-test-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [TeamUserAnswersDto](openapi-schemas.md#teamuseranswersdto)

### Response `200`
OK

Media type: `*/*`
Schema: [TeamResultDto](openapi-schemas.md#teamresultdto)

## ticket-controller

### POST `/api/tickets/analyze`
- Operation ID: `analyzeTicket`
- Tags: `ticket-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **no**

Media type: `multipart/form-data`
Schema: `{<br>  "properties" : {<br>    "file" : {<br>      "format" : "binary",<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "file" ],<br>  "type" : "object"<br>}`

### Response `200`
OK

Media type: `*/*`
Schema: [TicketInfo](openapi-schemas.md#ticketinfo)

## user-controller

### GET `/api/users/profile/{handle}`
- Operation ID: `getPublicUserProfile`
- Tags: `user-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### GET `/api/users/{userId}/social-verified`
- Operation ID: `checkSocialVerified`
- Tags: `user-controller`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `userId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

## 경기 예측

### GET `/api/games/past`
- Operation ID: `getPastGames`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: array<[MatchDto](openapi-schemas.md#matchdto)>

### GET `/api/matches`
- Operation ID: `getMatches`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `date` | query | yes | `string (date)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MatchDto](openapi-schemas.md#matchdto)>

### GET `/api/matches/bounds`
- Operation ID: `getMatchBounds`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [MatchBoundsResponseDto](openapi-schemas.md#matchboundsresponsedto)

### GET `/api/matches/day`
- Operation ID: `getMatchDay`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `date` | query | yes | `string (date)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [MatchDayNavigationResponseDto](openapi-schemas.md#matchdaynavigationresponsedto)

### GET `/api/matches/range`
- Operation ID: `getMatchesByRange`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `endDate` | query | yes | `string (date)` | — | — |
| `includePast` | query | no | `boolean` | — | — |
| `page` | query | no | `integer (int32)` | — | — |
| `size` | query | no | `integer (int32)` | — | — |
| `startDate` | query | yes | `string (date)` | — | — |
| `withMeta` | query | no | `boolean` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `{<br>  "oneOf" : [ {<br>    "items" : {<br>      "$ref" : "#/components/schemas/MatchDto"<br>    },<br>    "type" : "array"<br>  }, {<br>    "$ref" : "#/components/schemas/MatchRangePageResponseDto"<br>  } ]<br>}`

### GET `/api/matches/{gameId}`
- Operation ID: `getMatchDetail`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `gameId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [GameDetailDto](openapi-schemas.md#gamedetaildto)

### GET `/api/prediction/stats/me`
- Operation ID: `getMyStats`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [PredictionStatsResponseDto](openapi-schemas.md#predictionstatsresponsedto)

### GET `/api/predictions/bootstrap`
- Operation ID: `getPredictionBootstrap`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `date` | query | yes | `string (date)` | — | — |
| `gameId` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PredictionBootstrapResponseDto](openapi-schemas.md#predictionbootstrapresponsedto)

### POST `/api/predictions/my-votes`
- Operation ID: `getMyVotesBulk`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [PredictionMyVotesRequestDto](openapi-schemas.md#predictionmyvotesrequestdto)

### Response `200`
OK

Media type: `*/*`
Schema: [PredictionMyVotesResponseDto](openapi-schemas.md#predictionmyvotesresponsedto)

### GET `/api/predictions/status/{gameId}`
- Operation ID: `getVoteStatus`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `gameId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PredictionResponseDto](openapi-schemas.md#predictionresponsedto)

### POST `/api/predictions/vote`
- Operation ID: `vote`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [PredictionRequestDto](openapi-schemas.md#predictionrequestdto)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

### DELETE `/api/predictions/{gameId}`
- Operation ID: `cancelVote`
- Tags: `경기 예측`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `gameId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponse](openapi-schemas.md#apiresponse)

## 리더보드

### GET `/api/leaderboard`
- Operation ID: `getLeaderboard`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `page` | query | no | `integer (int32)` | — | — |
| `size` | query | no | `integer (int32)` | — | — |
| `type` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelLeaderboardEntryDto](openapi-schemas.md#pagedmodelleaderboardentrydto)

### GET `/api/leaderboard/achievements`
- Operation ID: `getAllAchievements`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: array<[AchievementDto](openapi-schemas.md#achievementdto)>

### GET `/api/leaderboard/achievements/rare`
- Operation ID: `getRecentRareAchievements`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `limit` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[AchievementDto](openapi-schemas.md#achievementdto)>

### GET `/api/leaderboard/achievements/recent`
- Operation ID: `getRecentAchievements`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `limit` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[AchievementDto](openapi-schemas.md#achievementdto)>

### GET `/api/leaderboard/hot-streaks`
- Operation ID: `getHotStreaks`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `limit` | query | no | `integer (int32)` | — | — |
| `minStreak` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[HotStreakDto](openapi-schemas.md#hotstreakdto)>

### GET `/api/leaderboard/me`
- Operation ID: `getMyStats_1`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [UserStatsDto](openapi-schemas.md#userstatsdto)

### GET `/api/leaderboard/me/history`
- Operation ID: `getMyScoreHistory`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `page` | query | no | `integer (int32)` | — | — |
| `size` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelRecentScoreDto](openapi-schemas.md#pagedmodelrecentscoredto)

### GET `/api/leaderboard/powerups`
- Operation ID: `getPowerups`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: `{<br>  "additionalProperties" : {<br>    "format" : "int32",<br>    "type" : "integer"<br>  },<br>  "type" : "object"<br>}`

### GET `/api/leaderboard/powerups/active`
- Operation ID: `getActivePowerups`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: array<[ActivePowerupDto](openapi-schemas.md#activepowerupdto)>

### POST `/api/leaderboard/powerups/{type}/use`
- Operation ID: `usePowerup`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `type` | path | yes | `string` | — | — |
| `gameId` | query | no | `string` | — | — |

#### Request body
Required: **no**

Media type: `application/json`
Schema: `{<br>  "additionalProperties" : { },<br>  "type" : "object"<br>}`

### Response `200`
OK

Media type: `*/*`
Schema: [PowerupUseResultDto](openapi-schemas.md#powerupuseresultdto)

### GET `/api/leaderboard/profile/{handle}`
- Operation ID: `getUserStatsByHandle`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [UserStatsDto](openapi-schemas.md#userstatsdto)

### GET `/api/leaderboard/profile/{handle}/rank`
- Operation ID: `getUserRankByHandle`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [UserRankDto](openapi-schemas.md#userrankdto)

### GET `/api/leaderboard/recent-scores`
- Operation ID: `getRecentScores`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `limit` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[RecentScoreDto](openapi-schemas.md#recentscoredto)>

### GET `/api/leaderboard/stats`
- Operation ID: `getGlobalStats`
- Tags: `리더보드`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: `{<br>  "additionalProperties" : { },<br>  "type" : "object"<br>}`

## 리더보드-개발

### POST `/api/leaderboard/seed-test-data`
- Operation ID: `seedTestData`
- Tags: `리더보드-개발`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: `{<br>  "additionalProperties" : { },<br>  "type" : "object"<br>}`

## 응원 게시판

### GET `/api/cheer/battle/{gameId}/status`
- Operation ID: `getBattleStatus`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `gameId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [CheerBattleStatusRes](openapi-schemas.md#cheerbattlestatusres)

### GET `/api/cheer/bookmarks`
- Operation ID: `getBookmarks`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelPostSummaryRes](openapi-schemas.md#pagedmodelpostsummaryres)

### DELETE `/api/cheer/comments/{commentId}`
- Operation ID: `deleteComment`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `commentId` | path | yes | `integer (int64)` | — | — |

### Response `204`
No Content

### POST `/api/cheer/comments/{commentId}/like`
- Operation ID: `toggleCommentLike`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `commentId` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [LikeToggleResponse](openapi-schemas.md#liketoggleresponse)

### GET `/api/cheer/me/posts`
- Operation ID: `listMyPosts`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelPostSummaryRes](openapi-schemas.md#pagedmodelpostsummaryres)

### GET `/api/cheer/posts`
- Operation ID: `list`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |
| `postType` | query | no | `string` | — | — |
| `summary` | query | no | `boolean` | — | — |
| `teamId` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `object`

### POST `/api/cheer/posts`
- Operation ID: `create`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [CreatePostReq](openapi-schemas.md#createpostreq)

### Response `200`
기존 활성 연결 게시글 반환

Media type: `*/*`
Schema: [PostDetailRes](openapi-schemas.md#postdetailres)

### Response `201`
새 게시글 생성

Media type: `*/*`
Schema: [PostDetailRes](openapi-schemas.md#postdetailres)

### GET `/api/cheer/posts/changes`
- Operation ID: `checkChanges`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `sinceId` | query | no | `integer (int64)` | — | — |
| `teamId` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PostChangesResponse](openapi-schemas.md#postchangesresponse)

### GET `/api/cheer/posts/following`
- Operation ID: `listFollowing`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelPostSummaryRes](openapi-schemas.md#pagedmodelpostsummaryres)

### GET `/api/cheer/posts/hot`
- Operation ID: `listHot`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `algorithm` | query | no | `string` | — | — |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelPostSummaryRes](openapi-schemas.md#pagedmodelpostsummaryres)

### GET `/api/cheer/posts/linked`
- Operation ID: `linked`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `diaryId` | query | no | `integer (int64)` | — | — |
| `partyId` | query | no | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [LinkedPostLookupRes](openapi-schemas.md#linkedpostlookupres)

### GET `/api/cheer/posts/search`
- Operation ID: `search`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |
| `q` | query | yes | `string` | — | — |
| `teamId` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelPostSummaryRes](openapi-schemas.md#pagedmodelpostsummaryres)

### GET `/api/cheer/posts/{id}`
- Operation ID: `get`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PostDetailRes](openapi-schemas.md#postdetailres)

### PUT `/api/cheer/posts/{id}`
- Operation ID: `update`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [UpdatePostReq](openapi-schemas.md#updatepostreq)

### Response `200`
OK

Media type: `*/*`
Schema: [PostDetailRes](openapi-schemas.md#postdetailres)

### DELETE `/api/cheer/posts/{id}`
- Operation ID: `delete`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `204`
No Content

### POST `/api/cheer/posts/{id}/bookmark`
- Operation ID: `toggleBookmark`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [BookmarkResponse](openapi-schemas.md#bookmarkresponse)

### GET `/api/cheer/posts/{id}/comments`
- Operation ID: `comments`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelCommentRes](openapi-schemas.md#pagedmodelcommentres)

### POST `/api/cheer/posts/{id}/comments`
- Operation ID: `addComment`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [CreateCommentReq](openapi-schemas.md#createcommentreq)

### Response `200`
OK

Media type: `*/*`
Schema: [CommentRes](openapi-schemas.md#commentres)

### GET `/api/cheer/posts/{id}/images`
- Operation ID: `getImages`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[PostImageDto](openapi-schemas.md#postimagedto)>

### POST `/api/cheer/posts/{id}/images`
- Operation ID: `uploadImages_1`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **no**

Media type: `multipart/form-data`
Schema: `{<br>  "properties" : {<br>    "files" : {<br>      "items" : {<br>        "format" : "binary",<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "required" : [ "files" ],<br>  "type" : "object"<br>}`

### Response `200`
OK

Media type: `*/*`
Schema: array<[PostImageDto](openapi-schemas.md#postimagedto)>

### POST `/api/cheer/posts/{id}/like`
- Operation ID: `toggleLike`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [LikeToggleResponse](openapi-schemas.md#liketoggleresponse)

### POST `/api/cheer/posts/{id}/quote`
- Operation ID: `createQuoteRepost`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [QuoteRepostReq](openapi-schemas.md#quoterepostreq)

### Response `200`
OK

Media type: `*/*`
Schema: [PostDetailRes](openapi-schemas.md#postdetailres)

### POST `/api/cheer/posts/{id}/report`
- Operation ID: `reportPost`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [ReportRequest](openapi-schemas.md#reportrequest)

### Response `200`
OK

Media type: `*/*`
Schema: [ReportCaseRes](openapi-schemas.md#reportcaseres)

### POST `/api/cheer/posts/{id}/repost`
- Operation ID: `toggleRepost`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [RepostToggleResponse](openapi-schemas.md#reposttoggleresponse)

### DELETE `/api/cheer/posts/{id}/repost`
- Operation ID: `cancelRepost`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [RepostToggleResponse](openapi-schemas.md#reposttoggleresponse)

### POST `/api/cheer/posts/{postId}/comments/{parentCommentId}/replies`
- Operation ID: `addReply`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `parentCommentId` | path | yes | `integer (int64)` | — | — |
| `postId` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [CreateCommentReq](openapi-schemas.md#createcommentreq)

### Response `200`
OK

Media type: `*/*`
Schema: [CommentRes](openapi-schemas.md#commentres)

### GET `/api/cheer/user/{handle}/posts`
- Operation ID: `listByUser`
- Tags: `응원 게시판`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |
| `pageable` | query | yes | [Pageable](openapi-schemas.md#pageable) | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelPostSummaryRes](openapi-schemas.md#pagedmodelpostsummaryres)

## 인증

### GET `/api/auth/check-handle`
- Operation ID: `checkHandle`
- Tags: `인증`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | query | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseAvailabilityCheckResponseDto](openapi-schemas.md#apiresponseavailabilitycheckresponsedto)

### GET `/api/auth/check-name`
- Operation ID: `checkName`
- Tags: `인증`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `name` | query | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMapStringObject](openapi-schemas.md#apiresponsemapstringobject)

### GET `/api/auth/link-token`
- Operation ID: `generateLinkToken`
- Tags: `인증`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: `object`

### POST `/api/auth/login`
- Operation ID: `login`
- Tags: `인증`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [LoginDto](openapi-schemas.md#logindto)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMapStringObject](openapi-schemas.md#apiresponsemapstringobject)

### POST `/api/auth/logout`
- Operation ID: `logout`
- Tags: `인증`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseVoid](openapi-schemas.md#apiresponsevoid)

### GET `/api/auth/oauth2/state/{stateId}`
- Operation ID: `consumeOAuth2State`
- Tags: `인증`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `stateId` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `object`

### POST `/api/auth/policies/consents`
- Operation ID: `submitPolicyConsents`
- Tags: `인증`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [PolicyConsentSubmitDto](openapi-schemas.md#policyconsentsubmitdto)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseMapStringObject](openapi-schemas.md#apiresponsemapstringobject)

### GET `/api/auth/policies/required`
- Operation ID: `getRequiredPolicies`
- Tags: `인증`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponsePolicyRequiredResponseDto](openapi-schemas.md#apiresponsepolicyrequiredresponsedto)

### POST `/api/auth/signup`
- Operation ID: `signUp`
- Tags: `인증`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [SignupDto](openapi-schemas.md#signupdto)

### Response `200`
OK

Media type: `*/*`
Schema: [ApiResponseVoid](openapi-schemas.md#apiresponsevoid)

## 파티 매칭

### GET `/api/parties`
- Operation ID: `getAllParties`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `date` | query | no | `string (date)` | — | — |
| `page` | query | no | `integer (int32)` | — | — |
| `searchQuery` | query | no | `string` | — | — |
| `size` | query | no | `integer (int32)` | — | — |
| `sortBy` | query | no | `string` | — | — |
| `sortDir` | query | no | `string` | — | — |
| `stadium` | query | no | `string` | — | — |
| `status` | query | no | `string` | — | — |
| `teamId` | query | no | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelMatePartyPublicResponse](openapi-schemas.md#pagedmodelmatepartypublicresponse)

### POST `/api/parties`
- Operation ID: `createParty`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [MatePartyCreateRequest](openapi-schemas.md#matepartycreaterequest)

### Response `200`
OK

Media type: `*/*`
Schema: [MatePartyResponse](openapi-schemas.md#matepartyresponse)

### GET `/api/parties/my`
- Operation ID: `getMyParties`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: array<[MatePartyResponse](openapi-schemas.md#matepartyresponse)>

### GET `/api/parties/my/history`
- Operation ID: `getMyPartyHistory`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `group` | query | no | `string` | — | — |
| `page` | query | no | `integer (int32)` | — | — |
| `size` | query | no | `integer (int32)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [PagedModelMatePartyHistoryResponse](openapi-schemas.md#pagedmodelmatepartyhistoryresponse)

### GET `/api/parties/profile/{handle}`
- Operation ID: `getPartiesByHostHandle`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `handle` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MatePartyPublicResponse](openapi-schemas.md#matepartypublicresponse)>

### GET `/api/parties/search`
- Operation ID: `searchParties`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `query` | query | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MatePartyPublicResponse](openapi-schemas.md#matepartypublicresponse)>

### GET `/api/parties/status/{status}`
- Operation ID: `getPartiesByStatus`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `status` | path | yes | `string` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: array<[MatePartyPublicResponse](openapi-schemas.md#matepartypublicresponse)>

### GET `/api/parties/upcoming`
- Operation ID: `getUpcomingParties`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

### Response `200`
OK

Media type: `*/*`
Schema: array<[MatePartyPublicResponse](openapi-schemas.md#matepartypublicresponse)>

### GET `/api/parties/{id}`
- Operation ID: `getPartyById`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: [MatePartyPublicResponse](openapi-schemas.md#matepartypublicresponse)

### PATCH `/api/parties/{id}`
- Operation ID: `updateParty`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

#### Request body
Required: **yes**

Media type: `application/json`
Schema: [MatePartyUpdateRequest](openapi-schemas.md#matepartyupdaterequest)

### Response `200`
OK

Media type: `*/*`
Schema: [MatePartyResponse](openapi-schemas.md#matepartyresponse)

### DELETE `/api/parties/{id}`
- Operation ID: `deleteParty`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

### POST `/api/parties/{id}/favorite`
- Operation ID: `addFavorite_1`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `{<br>  "additionalProperties" : {<br>    "type" : "boolean"<br>  },<br>  "type" : "object"<br>}`

### DELETE `/api/parties/{id}/favorite`
- Operation ID: `removeFavorite_1`
- Tags: `파티 매칭`
- Security: Not specified in OpenAPI
- Deprecated: no

#### Parameters
| Name | In | Required | Schema | Description | Example |
| --- | --- | --- | --- | --- | --- |
| `id` | path | yes | `integer (int64)` | — | — |

### Response `200`
OK

Media type: `*/*`
Schema: `{<br>  "additionalProperties" : {<br>    "type" : "boolean"<br>  },<br>  "type" : "object"<br>}`
