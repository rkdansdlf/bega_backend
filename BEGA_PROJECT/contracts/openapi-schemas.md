# KBO Platform API Schemas

> This file is generated. Do not edit directly.
> Source: `contracts/openapi.json`
> Regenerate with: `./gradlew updateOpenApiContract`

Version: `1.0`
Schemas: **286**

<a id="accountdeletionrecoveryinfodto"></a>
## AccountDeletionRecoveryInfoDto
Schema: `{<br>  "properties" : {<br>    "scheduledFor" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `scheduledFor` | no | `string` | — | — |

<a id="accountdeletionrecoveryrequestdto"></a>
## AccountDeletionRecoveryRequestDto
Schema: `{<br>  "properties" : {<br>    "token" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "token" ],<br>  "type" : "object"<br>}`
Required properties: `token`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `token` | yes | `string` | — | minLength=1 |

<a id="accountsecurityeventdto"></a>
## AccountSecurityEventDto
Schema: `{<br>  "properties" : {<br>    "browser" : {<br>      "type" : "string"<br>    },<br>    "deviceLabel" : {<br>      "type" : "string"<br>    },<br>    "deviceType" : {<br>      "type" : "string"<br>    },<br>    "eventType" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "ip" : {<br>      "type" : "string"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "occurredAt" : {<br>      "type" : "string"<br>    },<br>    "os" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `browser` | no | `string` | — | — |
| `deviceLabel` | no | `string` | — | — |
| `deviceType` | no | `string` | — | — |
| `eventType` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `ip` | no | `string` | — | — |
| `message` | no | `string` | — | — |
| `occurredAt` | no | `string` | — | — |
| `os` | no | `string` | — | — |

<a id="achievementdto"></a>
## AchievementDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "earned" : {<br>      "type" : "boolean"<br>    },<br>    "earnedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "iconUrl" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "name" : {<br>      "type" : "string"<br>    },<br>    "pointsRequired" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "rarity" : {<br>      "type" : "string"<br>    },<br>    "rarityColor" : {<br>      "type" : "string"<br>    },<br>    "rarityKo" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `description` | no | `string` | — | — |
| `earned` | no | `boolean` | — | — |
| `earnedAt` | no | `string (date-time)` | — | — |
| `iconUrl` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `name` | no | `string` | — | — |
| `pointsRequired` | no | `integer (int64)` | — | — |
| `rarity` | no | `string` | — | — |
| `rarityColor` | no | `string` | — | — |
| `rarityKo` | no | `string` | — | — |

<a id="activepowerupdto"></a>
## ActivePowerupDto
Schema: `{<br>  "properties" : {<br>    "activatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "expiresAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "icon" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "name" : {<br>      "type" : "string"<br>    },<br>    "type" : {<br>      "type" : "string"<br>    },<br>    "used" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `activatedAt` | no | `string (date-time)` | — | — |
| `description` | no | `string` | — | — |
| `expiresAt` | no | `string (date-time)` | — | — |
| `gameId` | no | `string` | — | — |
| `icon` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `name` | no | `string` | — | — |
| `type` | no | `string` | — | — |
| `used` | no | `boolean` | — | — |

<a id="adminmatedto"></a>
## AdminMateDto
Schema: `{<br>  "properties" : {<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "currentMembers" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "gameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "hostName" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "maxMembers" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "type" : "string"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    },<br>    "title" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayTeam` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `currentMembers` | no | `integer (int32)` | — | — |
| `gameDate` | no | `string (date)` | — | — |
| `homeTeam` | no | `string` | — | — |
| `hostName` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `maxMembers` | no | `integer (int32)` | — | — |
| `section` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `status` | no | `string` | — | — |
| `teamId` | no | `string` | — | — |
| `title` | no | `string` | — | — |

<a id="adminnoncanonicalcleanuptrackerdto"></a>
## AdminNonCanonicalCleanupTrackerDto
Schema: `{<br>  "properties" : {<br>    "assignee" : {<br>      "type" : "string"<br>    },<br>    "endDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "gameIds" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "note" : {<br>      "type" : "string"<br>    },<br>    "startDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "type" : "string"<br>    },<br>    "ticketUrl" : {<br>      "type" : "string"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `assignee` | no | `string` | — | — |
| `endDate` | no | `string (date)` | — | — |
| `gameIds` | no | `array<string>` | — | — |
| `note` | no | `string` | — | — |
| `startDate` | no | `string (date)` | — | — |
| `status` | no | `string` | — | — |
| `ticketUrl` | no | `string` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |

<a id="adminnoncanonicalcleanuptrackerupsertrequest"></a>
## AdminNonCanonicalCleanupTrackerUpsertRequest
Schema: `{<br>  "properties" : {<br>    "assignee" : {<br>      "maxLength" : 120,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "gameIds" : {<br>      "items" : {<br>        "maxLength" : 64,<br>        "minLength" : 0,<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "note" : {<br>      "maxLength" : 4000,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "pattern" : "draft\|requested\|in_progress\|done",<br>      "type" : "string"<br>    },<br>    "ticketUrl" : {<br>      "maxLength" : 500,<br>      "minLength" : 0,<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `assignee` | no | `string` | — | minLength=0, maxLength=120 |
| `gameIds` | no | `array<string>` | — | — |
| `note` | no | `string` | — | minLength=0, maxLength=4000 |
| `status` | no | `string` | — | pattern=`draft\|requested\|in_progress\|done` |
| `ticketUrl` | no | `string` | — | minLength=0, maxLength=500 |

<a id="adminpostdto"></a>
## AdminPostDto
Schema: `{<br>  "properties" : {<br>    "author" : {<br>      "type" : "string"<br>    },<br>    "commentCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "content" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "isHot" : {<br>      "type" : "boolean"<br>    },<br>    "likeCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "team" : {<br>      "type" : "string"<br>    },<br>    "views" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `author` | no | `string` | — | — |
| `commentCount` | no | `integer (int32)` | — | — |
| `content` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `isHot` | no | `boolean` | — | — |
| `likeCount` | no | `integer (int32)` | — | — |
| `team` | no | `string` | — | — |
| `views` | no | `integer (int32)` | — | — |

<a id="adminreportactionreq"></a>
## AdminReportActionReq
Schema: `{<br>  "properties" : {<br>    "action" : {<br>      "enum" : [ "TAKE_DOWN", "REQUIRE_MODIFICATION", "WARNING", "DISMISS", "RESTORE" ],<br>      "type" : "string"<br>    },<br>    "adminMemo" : {<br>      "type" : "string"<br>    },<br>    "visibleUntil" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `action` | no | `string` | — | — |
| `adminMemo` | no | `string` | — | — |
| `visibleUntil` | no | `string` | — | — |

#### Property metadata: `action`
- Enum: `TAKE_DOWN`, `REQUIRE_MODIFICATION`, `WARNING`, `DISMISS`, `RESTORE`

<a id="adminreportappealreq"></a>
## AdminReportAppealReq
Schema: `{<br>  "properties" : {<br>    "appealReason" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `appealReason` | no | `string` | — | — |

<a id="adminreportdto"></a>
## AdminReportDto
Schema: `{<br>  "properties" : {<br>    "adminAction" : {<br>      "type" : "string"<br>    },<br>    "adminMemo" : {<br>      "type" : "string"<br>    },<br>    "appealCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "appealReason" : {<br>      "type" : "string"<br>    },<br>    "appealStatus" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "evidenceUrl" : {<br>      "type" : "string"<br>    },<br>    "handledAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "handledBy" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "postId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "postPreview" : {<br>      "type" : "string"<br>    },<br>    "reason" : {<br>      "type" : "string"<br>    },<br>    "reporterHandle" : {<br>      "type" : "string"<br>    },<br>    "reporterId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "requestedAction" : {<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `adminAction` | no | `string` | — | — |
| `adminMemo` | no | `string` | — | — |
| `appealCount` | no | `integer (int32)` | — | — |
| `appealReason` | no | `string` | — | — |
| `appealStatus` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `description` | no | `string` | — | — |
| `evidenceUrl` | no | `string` | — | — |
| `handledAt` | no | `string (date-time)` | — | — |
| `handledBy` | no | `integer (int64)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `postId` | no | `integer (int64)` | — | — |
| `postPreview` | no | `string` | — | — |
| `reason` | no | `string` | — | — |
| `reporterHandle` | no | `string` | — | — |
| `reporterId` | no | `integer (int64)` | — | — |
| `requestedAction` | no | `string` | — | — |
| `status` | no | `string` | — | — |

<a id="adminseatviewactionreq"></a>
## AdminSeatViewActionReq
Schema: `{<br>  "properties" : {<br>    "adminLabel" : {<br>      "type" : "string"<br>    },<br>    "adminMemo" : {<br>      "type" : "string"<br>    },<br>    "moderationStatus" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `adminLabel` | no | `string` | — | — |
| `adminMemo` | no | `string` | — | — |
| `moderationStatus` | no | `string` | — | — |

<a id="adminseatviewdto"></a>
## AdminSeatViewDto
Schema: `{<br>  "properties" : {<br>    "adminLabel" : {<br>      "type" : "string"<br>    },<br>    "adminMemo" : {<br>      "type" : "string"<br>    },<br>    "aiConfidence" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "aiReason" : {<br>      "type" : "string"<br>    },<br>    "aiSuggestedLabel" : {<br>      "type" : "string"<br>    },<br>    "block" : {<br>      "type" : "string"<br>    },<br>    "diaryDate" : {<br>      "type" : "string"<br>    },<br>    "diaryId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "moderationStatus" : {<br>      "type" : "string"<br>    },<br>    "photoUrl" : {<br>      "type" : "string"<br>    },<br>    "reviewedAt" : {<br>      "type" : "string"<br>    },<br>    "reviewedBy" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "rewardGranted" : {<br>      "type" : "boolean"<br>    },<br>    "seatNumber" : {<br>      "type" : "string"<br>    },<br>    "seatRow" : {<br>      "type" : "string"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "sourceType" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "storagePath" : {<br>      "type" : "string"<br>    },<br>    "ticketVerified" : {<br>      "type" : "boolean"<br>    },<br>    "ticketVerifiedAt" : {<br>      "type" : "string"<br>    },<br>    "userId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "userSelected" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `adminLabel` | no | `string` | — | — |
| `adminMemo` | no | `string` | — | — |
| `aiConfidence` | no | `number (double)` | — | — |
| `aiReason` | no | `string` | — | — |
| `aiSuggestedLabel` | no | `string` | — | — |
| `block` | no | `string` | — | — |
| `diaryDate` | no | `string` | — | — |
| `diaryId` | no | `integer (int64)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `moderationStatus` | no | `string` | — | — |
| `photoUrl` | no | `string` | — | — |
| `reviewedAt` | no | `string` | — | — |
| `reviewedBy` | no | `integer (int64)` | — | — |
| `rewardGranted` | no | `boolean` | — | — |
| `seatNumber` | no | `string` | — | — |
| `seatRow` | no | `string` | — | — |
| `section` | no | `string` | — | — |
| `sourceType` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `storagePath` | no | `string` | — | — |
| `ticketVerified` | no | `boolean` | — | — |
| `ticketVerifiedAt` | no | `string` | — | — |
| `userId` | no | `integer (int64)` | — | — |
| `userSelected` | no | `boolean` | — | — |

<a id="adminstatsdto"></a>
## AdminStatsDto
Schema: `{<br>  "properties" : {<br>    "totalMates" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "totalPosts" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "totalUsers" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `totalMates` | no | `integer (int64)` | — | — |
| `totalPosts` | no | `integer (int64)` | — | — |
| `totalUsers` | no | `integer (int64)` | — | — |

<a id="adminuserdto"></a>
## AdminUserDto
Schema: `{<br>  "properties" : {<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "email" : {<br>      "type" : "string"<br>    },<br>    "favoriteTeam" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "name" : {<br>      "type" : "string"<br>    },<br>    "postCount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "role" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `createdAt` | no | `string (date-time)` | — | — |
| `email` | no | `string` | — | — |
| `favoriteTeam` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `name` | no | `string` | — | — |
| `postCount` | no | `integer (int64)` | — | — |
| `role` | no | `string` | — | — |

<a id="apiresponseaccountdeletionrecoveryinfodto"></a>
## ApiResponseAccountDeletionRecoveryInfoDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/AccountDeletionRecoveryInfoDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [AccountDeletionRecoveryInfoDto](openapi-schemas.md#accountdeletionrecoveryinfodto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseadminnoncanonicalcleanuptrackerdto"></a>
## ApiResponseAdminNonCanonicalCleanupTrackerDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/AdminNonCanonicalCleanupTrackerDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [AdminNonCanonicalCleanupTrackerDto](openapi-schemas.md#adminnoncanonicalcleanuptrackerdto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseadminreportdto"></a>
## ApiResponseAdminReportDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/AdminReportDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [AdminReportDto](openapi-schemas.md#adminreportdto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseadminseatviewdto"></a>
## ApiResponseAdminSeatViewDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/AdminSeatViewDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [AdminSeatViewDto](openapi-schemas.md#adminseatviewdto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseadminstatsdto"></a>
## ApiResponseAdminStatsDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/AdminStatsDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [AdminStatsDto](openapi-schemas.md#adminstatsdto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseavailabilitycheckresponsedto"></a>
## ApiResponseAvailabilityCheckResponseDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/AvailabilityCheckResponseDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [AvailabilityCheckResponseDto](openapi-schemas.md#availabilitycheckresponsedto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseboolean"></a>
## ApiResponseBoolean
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "type" : "boolean"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `boolean` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsebootstrapresponse"></a>
## ApiResponseBootstrapResponse
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/BootstrapResponse"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [BootstrapResponse](openapi-schemas.md#bootstrapresponse) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsechatfavoriteitem"></a>
## ApiResponseChatFavoriteItem
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/ChatFavoriteItem"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [ChatFavoriteItem](openapi-schemas.md#chatfavoriteitem) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsechatsessionsummary"></a>
## ApiResponseChatSessionSummary
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/ChatSessionSummary"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [ChatSessionSummary](openapi-schemas.md#chatsessionsummary) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseclienterrordashboarddto"></a>
## ApiResponseClientErrorDashboardDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/ClientErrorDashboardDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [ClientErrorDashboardDto](openapi-schemas.md#clienterrordashboarddto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseclienterroreventdetaildto"></a>
## ApiResponseClientErrorEventDetailDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/ClientErrorEventDetailDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [ClientErrorEventDetailDto](openapi-schemas.md#clienterroreventdetaildto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseclienterroreventpagedto"></a>
## ApiResponseClientErrorEventPageDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/ClientErrorEventPageDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [ClientErrorEventPageDto](openapi-schemas.md#clienterroreventpagedto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsefinalizemediauploadresponse"></a>
## ApiResponseFinalizeMediaUploadResponse
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/FinalizeMediaUploadResponse"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [FinalizeMediaUploadResponse](openapi-schemas.md#finalizemediauploadresponse) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsegamescoresyncbatchresultdto"></a>
## ApiResponseGameScoreSyncBatchResultDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/GameScoreSyncBatchResultDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [GameScoreSyncBatchResultDto](openapi-schemas.md#gamescoresyncbatchresultdto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsegamescoresyncresultdto"></a>
## ApiResponseGameScoreSyncResultDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/GameScoreSyncResultDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [GameScoreSyncResultDto](openapi-schemas.md#gamescoresyncresultdto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsegamestatusmismatchbatchresultdto"></a>
## ApiResponseGameStatusMismatchBatchResultDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/GameStatusMismatchBatchResultDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [GameStatusMismatchBatchResultDto](openapi-schemas.md#gamestatusmismatchbatchresultdto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsegamestatusrepairbatchresultdto"></a>
## ApiResponseGameStatusRepairBatchResultDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/GameStatusRepairBatchResultDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [GameStatusRepairBatchResultDto](openapi-schemas.md#gamestatusrepairbatchresultdto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseinitmediauploadresponse"></a>
## ApiResponseInitMediaUploadResponse
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/InitMediaUploadResponse"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [InitMediaUploadResponse](openapi-schemas.md#initmediauploadresponse) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseinteger"></a>
## ApiResponseInteger
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `integer (int32)` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistaccountsecurityeventdto"></a>
## ApiResponseListAccountSecurityEventDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/AccountSecurityEventDto"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[AccountSecurityEventDto](openapi-schemas.md#accountsecurityeventdto)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistadminmatedto"></a>
## ApiResponseListAdminMateDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/AdminMateDto"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[AdminMateDto](openapi-schemas.md#adminmatedto)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistadminnoncanonicalcleanuptrackerdto"></a>
## ApiResponseListAdminNonCanonicalCleanupTrackerDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/AdminNonCanonicalCleanupTrackerDto"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[AdminNonCanonicalCleanupTrackerDto](openapi-schemas.md#adminnoncanonicalcleanuptrackerdto)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistadminpostdto"></a>
## ApiResponseListAdminPostDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/AdminPostDto"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[AdminPostDto](openapi-schemas.md#adminpostdto)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistadminseatviewdto"></a>
## ApiResponseListAdminSeatViewDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/AdminSeatViewDto"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[AdminSeatViewDto](openapi-schemas.md#adminseatviewdto)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistadminuserdto"></a>
## ApiResponseListAdminUserDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/AdminUserDto"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[AdminUserDto](openapi-schemas.md#adminuserdto)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistchatfavoriteitem"></a>
## ApiResponseListChatFavoriteItem
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/ChatFavoriteItem"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[ChatFavoriteItem](openapi-schemas.md#chatfavoriteitem)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistchatsessionsummary"></a>
## ApiResponseListChatSessionSummary
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/ChatSessionSummary"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[ChatSessionSummary](openapi-schemas.md#chatsessionsummary)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistdevicesessiondto"></a>
## ApiResponseListDeviceSessionDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/DeviceSessionDto"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[DeviceSessionDto](openapi-schemas.md#devicesessiondto)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistinboxitem"></a>
## ApiResponseListInboxItem
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/InboxItem"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[InboxItem](openapi-schemas.md#inboxitem)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistoffseasonmovementadmindto"></a>
## ApiResponseListOffseasonMovementAdminDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/OffseasonMovementAdminDto"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[OffseasonMovementAdminDto](openapi-schemas.md#offseasonmovementadmindto)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistresponse"></a>
## ApiResponseListResponse
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/Response"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[Response](openapi-schemas.md#response)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseliststoredchatmessage"></a>
## ApiResponseListStoredChatMessage
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/StoredChatMessage"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[StoredChatMessage](openapi-schemas.md#storedchatmessage)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselisttrusteddevicedto"></a>
## ApiResponseListTrustedDeviceDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/TrustedDeviceDto"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[TrustedDeviceDto](openapi-schemas.md#trusteddevicedto)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponselistuserproviderdto"></a>
## ApiResponseListUserProviderDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/UserProviderDto"<br>      },<br>      "type" : "array"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `array<[UserProviderDto](openapi-schemas.md#userproviderdto)>` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsemapstringlong"></a>
## ApiResponseMapStringLong
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "additionalProperties" : {<br>        "format" : "int64",<br>        "type" : "integer"<br>      },<br>      "type" : "object"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `composition` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `data`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "format" : "int64",
    "type" : "integer"
  },
  "type" : "object"
}
```

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsemapstringobject"></a>
## ApiResponseMapStringObject
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "additionalProperties" : { },<br>      "type" : "object"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `composition` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `data`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : { },
  "type" : "object"
}
```

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsemapstringstring"></a>
## ApiResponseMapStringString
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `composition` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `data`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsematechatimageuploadresponse"></a>
## ApiResponseMateChatImageUploadResponse
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/MateChatImageUploadResponse"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [MateChatImageUploadResponse](openapi-schemas.md#matechatimageuploadresponse) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsemateinternalsettlementpayoutresponse"></a>
## ApiResponseMateInternalSettlementPayoutResponse
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/MateInternalSettlementPayoutResponse"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [MateInternalSettlementPayoutResponse](openapi-schemas.md#mateinternalsettlementpayoutresponse) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsematepaymentcancelintentresponse"></a>
## ApiResponseMatePaymentCancelIntentResponse
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/MatePaymentCancelIntentResponse"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [MatePaymentCancelIntentResponse](openapi-schemas.md#matepaymentcancelintentresponse) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsematesellerpayoutprofileresponse"></a>
## ApiResponseMateSellerPayoutProfileResponse
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/MateSellerPayoutProfileResponse"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [MateSellerPayoutProfileResponse](openapi-schemas.md#matesellerpayoutprofileresponse) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsemediabackfillreport"></a>
## ApiResponseMediaBackfillReport
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/MediaBackfillReport"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [MediaBackfillReport](openapi-schemas.md#mediabackfillreport) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsemediacleanupreport"></a>
## ApiResponseMediaCleanupReport
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/MediaCleanupReport"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [MediaCleanupReport](openapi-schemas.md#mediacleanupreport) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsemediasmokereport"></a>
## ApiResponseMediaSmokeReport
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/MediaSmokeReport"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [MediaSmokeReport](openapi-schemas.md#mediasmokereport) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseoffseasonmovementadmindto"></a>
## ApiResponseOffseasonMovementAdminDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/OffseasonMovementAdminDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [OffseasonMovementAdminDto](openapi-schemas.md#offseasonmovementadmindto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsepageadminreportdto"></a>
## ApiResponsePageAdminReportDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/PageObject"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [PageObject](openapi-schemas.md#pageobject) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsepageauditlogdto"></a>
## ApiResponsePageAuditLogDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/PageObject"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [PageObject](openapi-schemas.md#pageobject) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseplacedto"></a>
## ApiResponsePlaceDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/PlaceDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [PlaceDto](openapi-schemas.md#placedto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsepolicyrequiredresponsedto"></a>
## ApiResponsePolicyRequiredResponseDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/PolicyRequiredResponseDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [PolicyRequiredResponseDto](openapi-schemas.md#policyrequiredresponsedto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseprofileimagedto"></a>
## ApiResponseProfileImageDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/ProfileImageDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [ProfileImageDto](openapi-schemas.md#profileimagedto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsepublicuserprofiledto"></a>
## ApiResponsePublicUserProfileDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/PublicUserProfileDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [PublicUserProfileDto](openapi-schemas.md#publicuserprofiledto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseresponse"></a>
## ApiResponseResponse
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/Response"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [Response](openapi-schemas.md#response) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponserolechangeresponsedto"></a>
## ApiResponseRoleChangeResponseDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/RoleChangeResponseDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [RoleChangeResponseDto](openapi-schemas.md#rolechangeresponsedto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsestoredchatmessage"></a>
## ApiResponseStoredChatMessage
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/StoredChatMessage"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [StoredChatMessage](openapi-schemas.md#storedchatmessage) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponseuserprofiledto"></a>
## ApiResponseUserProfileDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : {<br>      "$ref" : "#/components/schemas/UserProfileDto"<br>    },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | [UserProfileDto](openapi-schemas.md#userprofiledto) | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="apiresponsevoid"></a>
## ApiResponseVoid
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : "string"<br>    },<br>    "data" : { },<br>    "errors" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | no | `string` | — | — |
| `data` | no | `{ }` | — | — |
| `errors` | no | `composition` | — | — |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

#### Property composition: `errors`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="auditlogdto"></a>
## AuditLogDto
Schema: `{<br>  "properties" : {<br>    "action" : {<br>      "type" : "string"<br>    },<br>    "actionDescription" : {<br>      "type" : "string"<br>    },<br>    "adminEmail" : {<br>      "type" : "string"<br>    },<br>    "adminId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "adminName" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "newValue" : {<br>      "type" : "string"<br>    },<br>    "oldValue" : {<br>      "type" : "string"<br>    },<br>    "targetUserEmail" : {<br>      "type" : "string"<br>    },<br>    "targetUserId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "targetUserName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `action` | no | `string` | — | — |
| `actionDescription` | no | `string` | — | — |
| `adminEmail` | no | `string` | — | — |
| `adminId` | no | `integer (int64)` | — | — |
| `adminName` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `description` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `newValue` | no | `string` | — | — |
| `oldValue` | no | `string` | — | — |
| `targetUserEmail` | no | `string` | — | — |
| `targetUserId` | no | `integer (int64)` | — | — |
| `targetUserName` | no | `string` | — | — |

<a id="availabilitycheckresponsedto"></a>
## AvailabilityCheckResponseDto
Schema: `{<br>  "properties" : {<br>    "available" : {<br>      "type" : "boolean"<br>    },<br>    "normalized" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `available` | no | `boolean` | — | — |
| `normalized` | no | `string` | — | — |

<a id="awarddto"></a>
## AwardDto
Schema: `{<br>  "properties" : {<br>    "award" : {<br>      "type" : "string"<br>    },<br>    "playerName" : {<br>      "type" : "string"<br>    },<br>    "stats" : {<br>      "type" : "string"<br>    },<br>    "team" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `award` | no | `string` | — | — |
| `playerName` | no | `string` | — | — |
| `stats` | no | `string` | — | — |
| `team` | no | `string` | — | — |

<a id="blocktoggleresponse"></a>
## BlockToggleResponse
Schema: `{<br>  "properties" : {<br>    "blocked" : {<br>      "type" : "boolean"<br>    },<br>    "blockedCount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `blocked` | no | `boolean` | — | — |
| `blockedCount` | no | `integer (int64)` | — | — |

<a id="bookmarkresponse"></a>
## BookmarkResponse
Schema: `{<br>  "properties" : {<br>    "bookmarked" : {<br>      "type" : "boolean"<br>    },<br>    "count" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `bookmarked` | no | `boolean` | — | — |
| `count` | no | `integer (int32)` | — | — |

<a id="bootstraprequest"></a>
## BootstrapRequest
Schema: `{<br>  "properties" : {<br>    "targetHandle" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "targetHandle" ],<br>  "type" : "object"<br>}`
Required properties: `targetHandle`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `targetHandle` | yes | `string` | — | minLength=1 |

<a id="bootstrapresponse"></a>
## BootstrapResponse
Schema: `{<br>  "properties" : {<br>    "membershipState" : {<br>      "type" : "string"<br>    },<br>    "roomId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "targetUser" : {<br>      "$ref" : "#/components/schemas/TargetUser"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `membershipState` | no | `string` | — | — |
| `roomId` | no | `integer (int64)` | — | — |
| `targetUser` | no | [TargetUser](openapi-schemas.md#targetuser) | — | — |

<a id="changepasswordrequest"></a>
## ChangePasswordRequest
Schema: `{<br>  "properties" : {<br>    "confirmPassword" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "currentPassword" : {<br>      "type" : "string"<br>    },<br>    "newPassword" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "confirmPassword", "newPassword" ],<br>  "type" : "object"<br>}`
Required properties: `confirmPassword`, `newPassword`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `confirmPassword` | yes | `string` | — | minLength=1 |
| `currentPassword` | no | `string` | — | — |
| `newPassword` | yes | `string` | — | minLength=1 |

<a id="chatfavoriteitem"></a>
## ChatFavoriteItem
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "type" : "string"<br>    },<br>    "favoritedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "messageCreatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "messageId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "prompt" : {<br>      "type" : "string"<br>    },<br>    "sessionId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "sessionTitle" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | no | `string` | — | — |
| `favoritedAt` | no | `string (date-time)` | — | — |
| `messageCreatedAt` | no | `string (date-time)` | — | — |
| `messageId` | no | `integer (int64)` | — | — |
| `prompt` | no | `string` | — | — |
| `sessionId` | no | `integer (int64)` | — | — |
| `sessionTitle` | no | `string` | — | — |

<a id="chatsessionsummary"></a>
## ChatSessionSummary
Schema: `{<br>  "properties" : {<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "lastMessageAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "latestMessagePreview" : {<br>      "type" : "string"<br>    },<br>    "messageCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "sessionId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "title" : {<br>      "type" : "string"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `createdAt` | no | `string (date-time)` | — | — |
| `lastMessageAt` | no | `string (date-time)` | — | — |
| `latestMessagePreview` | no | `string` | — | — |
| `messageCount` | no | `integer (int32)` | — | — |
| `sessionId` | no | `integer (int64)` | — | — |
| `title` | no | `string` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |

<a id="checkinlinkedcontentres"></a>
## CheckinLinkedContentRes
Schema: `{<br>  "properties" : {<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "cheeringTeam" : {<br>      "type" : "string"<br>    },<br>    "gameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "verified" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayTeam` | no | `string` | — | — |
| `cheeringTeam` | no | `string` | — | — |
| `gameDate` | no | `string (date)` | — | — |
| `homeTeam` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `verified` | no | `boolean` | — | — |

<a id="cheerbattlestatusres"></a>
## CheerBattleStatusRes
Schema: `{<br>  "properties" : {<br>    "myVote" : {<br>      "type" : "string"<br>    },<br>    "stats" : {<br>      "additionalProperties" : {<br>        "format" : "int32",<br>        "type" : "integer"<br>      },<br>      "type" : "object"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `myVote` | no | `string` | — | — |
| `stats` | no | `composition` | — | — |

#### Property composition: `stats`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "format" : "int32",
    "type" : "integer"
  },
  "type" : "object"
}
```

<a id="clienterroralertnotificationdto"></a>
## ClientErrorAlertNotificationDto
Schema: `{<br>  "properties" : {<br>    "bucket" : {<br>      "type" : "string"<br>    },<br>    "channel" : {<br>      "type" : "string"<br>    },<br>    "deliveryStatus" : {<br>      "type" : "string"<br>    },<br>    "failureReason" : {<br>      "type" : "string"<br>    },<br>    "fingerprint" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "latestEventId" : {<br>      "type" : "string"<br>    },<br>    "latestMessage" : {<br>      "type" : "string"<br>    },<br>    "latestOccurredAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "notifiedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "observedCount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "route" : {<br>      "type" : "string"<br>    },<br>    "source" : {<br>      "type" : "string"<br>    },<br>    "statusGroup" : {<br>      "type" : "string"<br>    },<br>    "thresholdCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "windowMinutes" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `bucket` | no | `string` | — | — |
| `channel` | no | `string` | — | — |
| `deliveryStatus` | no | `string` | — | — |
| `failureReason` | no | `string` | — | — |
| `fingerprint` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `latestEventId` | no | `string` | — | — |
| `latestMessage` | no | `string` | — | — |
| `latestOccurredAt` | no | `string (date-time)` | — | — |
| `notifiedAt` | no | `string (date-time)` | — | — |
| `observedCount` | no | `integer (int64)` | — | — |
| `route` | no | `string` | — | — |
| `source` | no | `string` | — | — |
| `statusGroup` | no | `string` | — | — |
| `thresholdCount` | no | `integer (int32)` | — | — |
| `windowMinutes` | no | `integer (int32)` | — | — |

<a id="clienterrordashboarddto"></a>
## ClientErrorDashboardDto
Schema: `{<br>  "properties" : {<br>    "from" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "granularity" : {<br>      "type" : "string"<br>    },<br>    "recentAlerts" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/ClientErrorAlertNotificationDto"<br>      },<br>      "type" : "array"<br>    },<br>    "recentFeedback" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/ClientErrorRecentFeedbackDto"<br>      },<br>      "type" : "array"<br>    },<br>    "timeSeries" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/ClientErrorTimeSeriesPointDto"<br>      },<br>      "type" : "array"<br>    },<br>    "to" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "topFingerprints" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/ClientErrorTopFingerprintDto"<br>      },<br>      "type" : "array"<br>    },<br>    "totals" : {<br>      "$ref" : "#/components/schemas/ClientErrorDashboardTotalsDto"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `from` | no | `string (date-time)` | — | — |
| `granularity` | no | `string` | — | — |
| `recentAlerts` | no | `array<[ClientErrorAlertNotificationDto](openapi-schemas.md#clienterroralertnotificationdto)>` | — | — |
| `recentFeedback` | no | `array<[ClientErrorRecentFeedbackDto](openapi-schemas.md#clienterrorrecentfeedbackdto)>` | — | — |
| `timeSeries` | no | `array<[ClientErrorTimeSeriesPointDto](openapi-schemas.md#clienterrortimeseriespointdto)>` | — | — |
| `to` | no | `string (date-time)` | — | — |
| `topFingerprints` | no | `array<[ClientErrorTopFingerprintDto](openapi-schemas.md#clienterrortopfingerprintdto)>` | — | — |
| `totals` | no | [ClientErrorDashboardTotalsDto](openapi-schemas.md#clienterrordashboardtotalsdto) | — | — |

<a id="clienterrordashboardtotalsdto"></a>
## ClientErrorDashboardTotalsDto
Schema: `{<br>  "properties" : {<br>    "affectedRoutes" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "api" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "feedback" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "runtime" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "uniqueFingerprints" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `affectedRoutes` | no | `integer (int64)` | — | — |
| `api` | no | `integer (int64)` | — | — |
| `feedback` | no | `integer (int64)` | — | — |
| `runtime` | no | `integer (int64)` | — | — |
| `uniqueFingerprints` | no | `integer (int64)` | — | — |

<a id="clienterroreventdetaildto"></a>
## ClientErrorEventDetailDto
Schema: `{<br>  "properties" : {<br>    "componentStack" : {<br>      "type" : "string"<br>    },<br>    "event" : {<br>      "$ref" : "#/components/schemas/ClientErrorEventSummaryDto"<br>    },<br>    "feedback" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/ClientErrorRecentFeedbackDto"<br>      },<br>      "type" : "array"<br>    },<br>    "sameFingerprintRecentEvents" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/ClientErrorEventSummaryDto"<br>      },<br>      "type" : "array"<br>    },<br>    "stack" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `componentStack` | no | `string` | — | — |
| `event` | no | [ClientErrorEventSummaryDto](openapi-schemas.md#clienterroreventsummarydto) | — | — |
| `feedback` | no | `array<[ClientErrorRecentFeedbackDto](openapi-schemas.md#clienterrorrecentfeedbackdto)>` | — | — |
| `sameFingerprintRecentEvents` | no | `array<[ClientErrorEventSummaryDto](openapi-schemas.md#clienterroreventsummarydto)>` | — | — |
| `stack` | no | `string` | — | — |

<a id="clienterroreventpagedto"></a>
## ClientErrorEventPageDto
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/ClientErrorEventSummaryDto"<br>      },<br>      "type" : "array"<br>    },<br>    "last" : {<br>      "type" : "boolean"<br>    },<br>    "number" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "size" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "totalElements" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "totalPages" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | no | `array<[ClientErrorEventSummaryDto](openapi-schemas.md#clienterroreventsummarydto)>` | — | — |
| `last` | no | `boolean` | — | — |
| `number` | no | `integer (int32)` | — | — |
| `size` | no | `integer (int32)` | — | — |
| `totalElements` | no | `integer (int64)` | — | — |
| `totalPages` | no | `integer (int32)` | — | — |

<a id="clienterroreventrequest"></a>
## ClientErrorEventRequest
Schema: `{<br>  "properties" : {<br>    "category" : {<br>      "maxLength" : 64,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "componentStack" : {<br>      "maxLength" : 8000,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "endpoint" : {<br>      "maxLength" : 500,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "eventId" : {<br>      "maxLength" : 64,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "message" : {<br>      "maxLength" : 1000,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "method" : {<br>      "maxLength" : 16,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "responseCode" : {<br>      "maxLength" : 64,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "route" : {<br>      "maxLength" : 500,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "sessionId" : {<br>      "maxLength" : 128,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "stack" : {<br>      "maxLength" : 8000,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "statusCode" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "timestamp" : {<br>      "maxLength" : 64,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "userId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "required" : [ "category", "eventId", "message", "route", "timestamp" ],<br>  "type" : "object"<br>}`
Required properties: `category`, `eventId`, `message`, `route`, `timestamp`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `category` | yes | `string` | — | minLength=0, maxLength=64 |
| `componentStack` | no | `string` | — | minLength=0, maxLength=8000 |
| `endpoint` | no | `string` | — | minLength=0, maxLength=500 |
| `eventId` | yes | `string` | — | minLength=0, maxLength=64 |
| `message` | yes | `string` | — | minLength=0, maxLength=1000 |
| `method` | no | `string` | — | minLength=0, maxLength=16 |
| `responseCode` | no | `string` | — | minLength=0, maxLength=64 |
| `route` | yes | `string` | — | minLength=0, maxLength=500 |
| `sessionId` | no | `string` | — | minLength=0, maxLength=128 |
| `stack` | no | `string` | — | minLength=0, maxLength=8000 |
| `statusCode` | no | `integer (int32)` | — | — |
| `timestamp` | yes | `string` | — | minLength=0, maxLength=64 |
| `userId` | no | `integer (int64)` | — | — |

<a id="clienterroreventsummarydto"></a>
## ClientErrorEventSummaryDto
Schema: `{<br>  "properties" : {<br>    "bucket" : {<br>      "type" : "string"<br>    },<br>    "endpoint" : {<br>      "type" : "string"<br>    },<br>    "eventId" : {<br>      "type" : "string"<br>    },<br>    "feedbackCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "fingerprint" : {<br>      "type" : "string"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "method" : {<br>      "type" : "string"<br>    },<br>    "normalizedEndpoint" : {<br>      "type" : "string"<br>    },<br>    "normalizedRoute" : {<br>      "type" : "string"<br>    },<br>    "occurredAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "responseCode" : {<br>      "type" : "string"<br>    },<br>    "route" : {<br>      "type" : "string"<br>    },<br>    "sessionId" : {<br>      "type" : "string"<br>    },<br>    "source" : {<br>      "type" : "string"<br>    },<br>    "statusCode" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "statusGroup" : {<br>      "type" : "string"<br>    },<br>    "userId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `bucket` | no | `string` | — | — |
| `endpoint` | no | `string` | — | — |
| `eventId` | no | `string` | — | — |
| `feedbackCount` | no | `integer (int32)` | — | — |
| `fingerprint` | no | `string` | — | — |
| `message` | no | `string` | — | — |
| `method` | no | `string` | — | — |
| `normalizedEndpoint` | no | `string` | — | — |
| `normalizedRoute` | no | `string` | — | — |
| `occurredAt` | no | `string (date-time)` | — | — |
| `responseCode` | no | `string` | — | — |
| `route` | no | `string` | — | — |
| `sessionId` | no | `string` | — | — |
| `source` | no | `string` | — | — |
| `statusCode` | no | `integer (int32)` | — | — |
| `statusGroup` | no | `string` | — | — |
| `userId` | no | `integer (int64)` | — | — |

<a id="clienterrorfeedbackrequest"></a>
## ClientErrorFeedbackRequest
Schema: `{<br>  "properties" : {<br>    "actionTaken" : {<br>      "maxLength" : 64,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "comment" : {<br>      "maxLength" : 2000,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "eventId" : {<br>      "maxLength" : 64,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "route" : {<br>      "maxLength" : 500,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "timestamp" : {<br>      "maxLength" : 64,<br>      "minLength" : 0,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "actionTaken", "comment", "eventId", "route", "timestamp" ],<br>  "type" : "object"<br>}`
Required properties: `actionTaken`, `comment`, `eventId`, `route`, `timestamp`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `actionTaken` | yes | `string` | — | minLength=0, maxLength=64 |
| `comment` | yes | `string` | — | minLength=0, maxLength=2000 |
| `eventId` | yes | `string` | — | minLength=0, maxLength=64 |
| `route` | yes | `string` | — | minLength=0, maxLength=500 |
| `timestamp` | yes | `string` | — | minLength=0, maxLength=64 |

<a id="clienterrorrecentfeedbackdto"></a>
## ClientErrorRecentFeedbackDto
Schema: `{<br>  "properties" : {<br>    "actionTaken" : {<br>      "type" : "string"<br>    },<br>    "comment" : {<br>      "type" : "string"<br>    },<br>    "eventId" : {<br>      "type" : "string"<br>    },<br>    "occurredAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "route" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `actionTaken` | no | `string` | — | — |
| `comment` | no | `string` | — | — |
| `eventId` | no | `string` | — | — |
| `occurredAt` | no | `string (date-time)` | — | — |
| `route` | no | `string` | — | — |

<a id="clienterrortimeseriespointdto"></a>
## ClientErrorTimeSeriesPointDto
Schema: `{<br>  "properties" : {<br>    "api" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "bucketStart" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "feedback" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "runtime" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `api` | no | `integer (int64)` | — | — |
| `bucketStart` | no | `string (date-time)` | — | — |
| `feedback` | no | `integer (int64)` | — | — |
| `runtime` | no | `integer (int64)` | — | — |

<a id="clienterrortopfingerprintdto"></a>
## ClientErrorTopFingerprintDto
Schema: `{<br>  "properties" : {<br>    "bucket" : {<br>      "type" : "string"<br>    },<br>    "count" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "endpoint" : {<br>      "type" : "string"<br>    },<br>    "fingerprint" : {<br>      "type" : "string"<br>    },<br>    "latestAlertChannel" : {<br>      "type" : "string"<br>    },<br>    "latestAlertSentAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "latestEventId" : {<br>      "type" : "string"<br>    },<br>    "latestOccurredAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "method" : {<br>      "type" : "string"<br>    },<br>    "route" : {<br>      "type" : "string"<br>    },<br>    "source" : {<br>      "type" : "string"<br>    },<br>    "statusGroup" : {<br>      "type" : "string"<br>    },<br>    "uniqueSessions" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `bucket` | no | `string` | — | — |
| `count` | no | `integer (int64)` | — | — |
| `endpoint` | no | `string` | — | — |
| `fingerprint` | no | `string` | — | — |
| `latestAlertChannel` | no | `string` | — | — |
| `latestAlertSentAt` | no | `string (date-time)` | — | — |
| `latestEventId` | no | `string` | — | — |
| `latestOccurredAt` | no | `string (date-time)` | — | — |
| `message` | no | `string` | — | — |
| `method` | no | `string` | — | — |
| `route` | no | `string` | — | — |
| `source` | no | `string` | — | — |
| `statusGroup` | no | `string` | — | — |
| `uniqueSessions` | no | `integer (int64)` | — | — |

<a id="commentres"></a>
## CommentRes
Schema: `{<br>  "properties" : {<br>    "author" : {<br>      "type" : "string"<br>    },<br>    "authorHandle" : {<br>      "type" : "string"<br>    },<br>    "authorProfileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "authorTeamId" : {<br>      "type" : "string"<br>    },<br>    "content" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "likeCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "likedByMe" : {<br>      "type" : "boolean"<br>    },<br>    "replies" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/CommentRes"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `author` | no | `string` | — | — |
| `authorHandle` | no | `string` | — | — |
| `authorProfileImageUrl` | no | `string` | — | — |
| `authorTeamId` | no | `string` | — | — |
| `content` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `likeCount` | no | `integer (int32)` | — | — |
| `likedByMe` | no | `boolean` | — | — |
| `replies` | no | `array<[CommentRes](openapi-schemas.md#commentres)>` | — | — |

<a id="createassistantchatmessagerequest"></a>
## CreateAssistantChatMessageRequest
Schema: `{<br>  "properties" : {<br>    "cached" : {<br>      "type" : "boolean"<br>    },<br>    "cancelled" : {<br>      "type" : "boolean"<br>    },<br>    "citations" : {<br>      "$ref" : "#/components/schemas/JsonNode"<br>    },<br>    "content" : {<br>      "maxLength" : 12000,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "errorCode" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "fallbackReason" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "finishReason" : {<br>      "maxLength" : 50,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "intent" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "metadata" : {<br>      "$ref" : "#/components/schemas/JsonNode"<br>    },<br>    "plannerCacheHit" : {<br>      "type" : "boolean"<br>    },<br>    "plannerMode" : {<br>      "maxLength" : 50,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "type" : "string"<br>    },<br>    "strategy" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "toolCalls" : {<br>      "$ref" : "#/components/schemas/JsonNode"<br>    },<br>    "toolExecutionMode" : {<br>      "maxLength" : 50,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "verified" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "required" : [ "content" ],<br>  "type" : "object"<br>}`
Required properties: `content`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `cached` | no | `boolean` | — | — |
| `cancelled` | no | `boolean` | — | — |
| `citations` | no | [JsonNode](openapi-schemas.md#jsonnode) | — | — |
| `content` | yes | `string` | — | minLength=0, maxLength=12000 |
| `errorCode` | no | `string` | — | minLength=0, maxLength=100 |
| `fallbackReason` | no | `string` | — | minLength=0, maxLength=100 |
| `finishReason` | no | `string` | — | minLength=0, maxLength=50 |
| `intent` | no | `string` | — | minLength=0, maxLength=100 |
| `metadata` | no | [JsonNode](openapi-schemas.md#jsonnode) | — | — |
| `plannerCacheHit` | no | `boolean` | — | — |
| `plannerMode` | no | `string` | — | minLength=0, maxLength=50 |
| `status` | no | `string` | — | — |
| `strategy` | no | `string` | — | minLength=0, maxLength=100 |
| `toolCalls` | no | [JsonNode](openapi-schemas.md#jsonnode) | — | — |
| `toolExecutionMode` | no | `string` | — | minLength=0, maxLength=50 |
| `verified` | no | `boolean` | — | — |

<a id="createcommentreq"></a>
## CreateCommentReq
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "maxLength" : 2000,<br>      "minLength" : 0,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "content" ],<br>  "type" : "object"<br>}`
Required properties: `content`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | yes | `string` | — | minLength=0, maxLength=2000 |

<a id="createpostreq"></a>
## CreatePostReq
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "diaryId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "images" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "postType" : {<br>      "type" : "string"<br>    },<br>    "shareMode" : {<br>      "enum" : [ "INTERNAL_REPOST", "INTERNAL_QUOTE", "EXTERNAL_LINK", "EXTERNAL_COPY", "EXTERNAL_EMBED", "EXTERNAL_SUMMARY" ],<br>      "type" : "string"<br>    },<br>    "sourceAuthor" : {<br>      "type" : "string"<br>    },<br>    "sourceChangedNote" : {<br>      "type" : "string"<br>    },<br>    "sourceLicense" : {<br>      "type" : "string"<br>    },<br>    "sourceLicenseUrl" : {<br>      "type" : "string"<br>    },<br>    "sourceSnapshotType" : {<br>      "type" : "string"<br>    },<br>    "sourceTitle" : {<br>      "type" : "string"<br>    },<br>    "sourceUrl" : {<br>      "type" : "string"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "content" ],<br>  "type" : "object"<br>}`
Required properties: `content`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | yes | `string` | — | minLength=1 |
| `diaryId` | no | `integer (int64)` | — | — |
| `images` | no | `array<string>` | — | — |
| `partyId` | no | `integer (int64)` | — | — |
| `postType` | no | `string` | — | — |
| `shareMode` | no | `string` | — | — |
| `sourceAuthor` | no | `string` | — | — |
| `sourceChangedNote` | no | `string` | — | — |
| `sourceLicense` | no | `string` | — | — |
| `sourceLicenseUrl` | no | `string` | — | — |
| `sourceSnapshotType` | no | `string` | — | — |
| `sourceTitle` | no | `string` | — | — |
| `sourceUrl` | no | `string` | — | — |
| `teamId` | no | `string` | — | — |

#### Property metadata: `shareMode`
- Enum: `INTERNAL_REPOST`, `INTERNAL_QUOTE`, `EXTERNAL_LINK`, `EXTERNAL_COPY`, `EXTERNAL_EMBED`, `EXTERNAL_SUMMARY`

<a id="createuserchatmessagerequest"></a>
## CreateUserChatMessageRequest
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "maxLength" : 4000,<br>      "minLength" : 0,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "content" ],<br>  "type" : "object"<br>}`
Required properties: `content`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | yes | `string` | — | minLength=0, maxLength=4000 |

<a id="daystats"></a>
## DayStats
Schema: `{<br>  "properties" : {<br>    "count" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "winRate" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "wins" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `count` | no | `integer (int32)` | — | — |
| `winRate` | no | `number (double)` | — | — |
| `wins` | no | `integer (int32)` | — | — |

<a id="deleteaccountrequest"></a>
## DeleteAccountRequest
Schema: `{<br>  "properties" : {<br>    "confirmText" : {<br>      "type" : "string"<br>    },<br>    "password" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `confirmText` | no | `string` | — | — |
| `password` | no | `string` | — | — |

<a id="devicesessiondto"></a>
## DeviceSessionDto
Schema: `{<br>  "properties" : {<br>    "browser" : {<br>      "type" : "string"<br>    },<br>    "deviceLabel" : {<br>      "type" : "string"<br>    },<br>    "deviceType" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "type" : "string"<br>    },<br>    "ip" : {<br>      "type" : "string"<br>    },<br>    "isCurrent" : {<br>      "type" : "boolean"<br>    },<br>    "isRevoked" : {<br>      "type" : "boolean"<br>    },<br>    "lastActiveAt" : {<br>      "type" : "string"<br>    },<br>    "lastSeenAt" : {<br>      "type" : "string"<br>    },<br>    "os" : {<br>      "type" : "string"<br>    },<br>    "sessionName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `browser` | no | `string` | — | — |
| `deviceLabel` | no | `string` | — | — |
| `deviceType` | no | `string` | — | — |
| `id` | no | `string` | — | — |
| `ip` | no | `string` | — | — |
| `isCurrent` | no | `boolean` | — | — |
| `isRevoked` | no | `boolean` | — | — |
| `lastActiveAt` | no | `string` | — | — |
| `lastSeenAt` | no | `string` | — | — |
| `os` | no | `string` | — | — |
| `sessionName` | no | `string` | — | — |

<a id="diaryrequestdto"></a>
## DiaryRequestDto
Schema: `{<br>  "properties" : {<br>    "block" : {<br>      "type" : "string"<br>    },<br>    "date" : {<br>      "type" : "string"<br>    },<br>    "emojiName" : {<br>      "type" : "string"<br>    },<br>    "gameId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "memo" : {<br>      "type" : "string"<br>    },<br>    "photos" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "seatNumber" : {<br>      "type" : "string"<br>    },<br>    "seatRow" : {<br>      "type" : "string"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "ticketVerificationToken" : {<br>      "type" : "string"<br>    },<br>    "type" : {<br>      "type" : "string"<br>    },<br>    "winningName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `block` | no | `string` | — | — |
| `date` | no | `string` | — | — |
| `emojiName` | no | `string` | — | — |
| `gameId` | no | `integer (int64)` | — | — |
| `memo` | no | `string` | — | — |
| `photos` | no | `array<string>` | — | — |
| `seatNumber` | no | `string` | — | — |
| `seatRow` | no | `string` | — | — |
| `section` | no | `string` | — | — |
| `ticketVerificationToken` | no | `string` | — | — |
| `type` | no | `string` | — | — |
| `winningName` | no | `string` | — | — |

<a id="diaryresponsedto"></a>
## DiaryResponseDto
Schema: `{<br>  "properties" : {<br>    "block" : {<br>      "type" : "string"<br>    },<br>    "date" : {<br>      "type" : "string"<br>    },<br>    "emojiName" : {<br>      "type" : "string"<br>    },<br>    "gameId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "memo" : {<br>      "type" : "string"<br>    },<br>    "photoStoragePaths" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "photos" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "seatNumber" : {<br>      "type" : "string"<br>    },<br>    "seatRow" : {<br>      "type" : "string"<br>    },<br>    "seatViewReward" : {<br>      "$ref" : "#/components/schemas/SeatViewRewardDto"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "team" : {<br>      "type" : "string"<br>    },<br>    "ticketVerified" : {<br>      "type" : "boolean"<br>    },<br>    "type" : {<br>      "type" : "string"<br>    },<br>    "winningName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `block` | no | `string` | — | — |
| `date` | no | `string` | — | — |
| `emojiName` | no | `string` | — | — |
| `gameId` | no | `integer (int64)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `memo` | no | `string` | — | — |
| `photoStoragePaths` | no | `array<string>` | — | — |
| `photos` | no | `array<string>` | — | — |
| `seatNumber` | no | `string` | — | — |
| `seatRow` | no | `string` | — | — |
| `seatViewReward` | no | [SeatViewRewardDto](openapi-schemas.md#seatviewrewarddto) | — | — |
| `section` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `team` | no | `string` | — | — |
| `ticketVerified` | no | `boolean` | — | — |
| `type` | no | `string` | — | — |
| `winningName` | no | `string` | — | — |

<a id="diarystatisticsdto"></a>
## DiaryStatisticsDto
Schema: `{<br>  "properties" : {<br>    "awayVisitCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "bestOpponent" : {<br>      "type" : "string"<br>    },<br>    "cheerPostCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "currentLossStreak" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "currentWinStreak" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "dayOfWeekStats" : {<br>      "additionalProperties" : {<br>        "$ref" : "#/components/schemas/DayStats"<br>      },<br>      "type" : "object"<br>    },<br>    "earnedBadges" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "emojiCounts" : {<br>      "additionalProperties" : {<br>        "format" : "int64",<br>        "type" : "integer"<br>      },<br>      "type" : "object"<br>    },<br>    "firstDiaryDate" : {<br>      "type" : "string"<br>    },<br>    "happiestCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "happiestMonth" : {<br>      "type" : "string"<br>    },<br>    "homeVisitCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "longestWinStreak" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "luckyDay" : {<br>      "type" : "string"<br>    },<br>    "mateParticipationCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "monthlyCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "monthlyVisitCounts" : {<br>      "additionalProperties" : {<br>        "format" : "int32",<br>        "type" : "integer"<br>      },<br>      "type" : "object"<br>    },<br>    "mostVisitedCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "mostVisitedStadium" : {<br>      "type" : "string"<br>    },<br>    "opponentWinRates" : {<br>      "additionalProperties" : {<br>        "$ref" : "#/components/schemas/OpponentStats"<br>      },<br>      "type" : "object"<br>    },<br>    "scheduledCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "stadiumVisitCounts" : {<br>      "additionalProperties" : {<br>        "format" : "int32",<br>        "type" : "integer"<br>      },<br>      "type" : "object"<br>    },<br>    "totalCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "totalDraws" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "totalLosses" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "totalWins" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "winRate" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "worstOpponent" : {<br>      "type" : "string"<br>    },<br>    "yearlyCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "yearlyWinRate" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "yearlyWins" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayVisitCount` | no | `integer (int32)` | — | — |
| `bestOpponent` | no | `string` | — | — |
| `cheerPostCount` | no | `integer (int32)` | — | — |
| `currentLossStreak` | no | `integer (int32)` | — | — |
| `currentWinStreak` | no | `integer (int32)` | — | — |
| `dayOfWeekStats` | no | `composition` | — | — |
| `earnedBadges` | no | `array<string>` | — | — |
| `emojiCounts` | no | `composition` | — | — |
| `firstDiaryDate` | no | `string` | — | — |
| `happiestCount` | no | `integer (int32)` | — | — |
| `happiestMonth` | no | `string` | — | — |
| `homeVisitCount` | no | `integer (int32)` | — | — |
| `longestWinStreak` | no | `integer (int32)` | — | — |
| `luckyDay` | no | `string` | — | — |
| `mateParticipationCount` | no | `integer (int32)` | — | — |
| `monthlyCount` | no | `integer (int32)` | — | — |
| `monthlyVisitCounts` | no | `composition` | — | — |
| `mostVisitedCount` | no | `integer (int32)` | — | — |
| `mostVisitedStadium` | no | `string` | — | — |
| `opponentWinRates` | no | `composition` | — | — |
| `scheduledCount` | no | `integer (int32)` | — | — |
| `stadiumVisitCounts` | no | `composition` | — | — |
| `totalCount` | no | `integer (int32)` | — | — |
| `totalDraws` | no | `integer (int32)` | — | — |
| `totalLosses` | no | `integer (int32)` | — | — |
| `totalWins` | no | `integer (int32)` | — | — |
| `winRate` | no | `number (double)` | — | — |
| `worstOpponent` | no | `string` | — | — |
| `yearlyCount` | no | `integer (int32)` | — | — |
| `yearlyWinRate` | no | `number (double)` | — | — |
| `yearlyWins` | no | `integer (int32)` | — | — |

#### Property composition: `dayOfWeekStats`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "$ref" : "#/components/schemas/DayStats"
  },
  "type" : "object"
}
```

#### Property composition: `emojiCounts`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "format" : "int64",
    "type" : "integer"
  },
  "type" : "object"
}
```

#### Property composition: `monthlyVisitCounts`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "format" : "int32",
    "type" : "integer"
  },
  "type" : "object"
}
```

#### Property composition: `opponentWinRates`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "$ref" : "#/components/schemas/OpponentStats"
  },
  "type" : "object"
}
```

#### Property composition: `stadiumVisitCounts`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "format" : "int32",
    "type" : "integer"
  },
  "type" : "object"
}
```

<a id="embeddedpostdto"></a>
## EmbeddedPostDto
Schema: `{<br>  "properties" : {<br>    "author" : {<br>      "type" : "string"<br>    },<br>    "authorHandle" : {<br>      "type" : "string"<br>    },<br>    "authorProfileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "commentCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "content" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "deleted" : {<br>      "type" : "boolean"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "imageUrls" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "likeCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "linkedContent" : {<br>      "$ref" : "#/components/schemas/LinkedContentRes"<br>    },<br>    "postType" : {<br>      "type" : "string"<br>    },<br>    "repostCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "teamColor" : {<br>      "type" : "string"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `author` | no | `string` | — | — |
| `authorHandle` | no | `string` | — | — |
| `authorProfileImageUrl` | no | `string` | — | — |
| `commentCount` | no | `integer (int32)` | — | — |
| `content` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `deleted` | no | `boolean` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `imageUrls` | no | `array<string>` | — | — |
| `likeCount` | no | `integer (int32)` | — | — |
| `linkedContent` | no | [LinkedContentRes](openapi-schemas.md#linkedcontentres) | — | — |
| `postType` | no | `string` | — | — |
| `repostCount` | no | `integer (int32)` | — | — |
| `teamColor` | no | `string` | — | — |
| `teamId` | no | `string` | — | — |

<a id="featuredmatecarddto"></a>
## FeaturedMateCardDto
Schema: `{<br>  "properties" : {<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "currentParticipants" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "gameDate" : {<br>      "type" : "string"<br>    },<br>    "gameTime" : {<br>      "type" : "string"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "hostId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "maxParticipants" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "type" : "string"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    },<br>    "ticketPrice" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayTeam` | no | `string` | — | — |
| `currentParticipants` | no | `integer (int32)` | — | — |
| `description` | no | `string` | — | — |
| `gameDate` | no | `string` | — | — |
| `gameTime` | no | `string` | — | — |
| `homeTeam` | no | `string` | — | — |
| `hostId` | no | `integer (int64)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `maxParticipants` | no | `integer (int32)` | — | — |
| `section` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `status` | no | `string` | — | — |
| `teamId` | no | `string` | — | — |
| `ticketPrice` | no | `integer (int32)` | — | — |

<a id="finalizemediauploadresponse"></a>
## FinalizeMediaUploadResponse
Schema: `{<br>  "properties" : {<br>    "assetId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "publicUrl" : {<br>      "type" : "string"<br>    },<br>    "storagePath" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `assetId` | no | `integer (int64)` | — | — |
| `publicUrl` | no | `string` | — | — |
| `storagePath` | no | `string` | — | — |

<a id="followcountresponse"></a>
## FollowCountResponse
Schema: `{<br>  "properties" : {<br>    "blockedByMe" : {<br>      "type" : "boolean"<br>    },<br>    "blockingMe" : {<br>      "type" : "boolean"<br>    },<br>    "followerCount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "followingCount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "isFollowedByMe" : {<br>      "type" : "boolean"<br>    },<br>    "notifyNewPosts" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `blockedByMe` | no | `boolean` | — | — |
| `blockingMe` | no | `boolean` | — | — |
| `followerCount` | no | `integer (int64)` | — | — |
| `followingCount` | no | `integer (int64)` | — | — |
| `isFollowedByMe` | no | `boolean` | — | — |
| `notifyNewPosts` | no | `boolean` | — | — |

<a id="followtoggleresponse"></a>
## FollowToggleResponse
Schema: `{<br>  "properties" : {<br>    "followerCount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "following" : {<br>      "type" : "boolean"<br>    },<br>    "followingCount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "notifyNewPosts" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `followerCount` | no | `integer (int64)` | — | — |
| `following` | no | `boolean` | — | — |
| `followingCount` | no | `integer (int64)` | — | — |
| `notifyNewPosts` | no | `boolean` | — | — |

<a id="gamedetaildto"></a>
## GameDetailDto
Schema: `{<br>  "properties" : {<br>    "attendance" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "awayPitcher" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "awayScore" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "gameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "gameStatus" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "gameTimeMinutes" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "homePitcher" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "homeScore" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "inningScores" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/GameInningScoreDto"<br>      },<br>      "type" : "array"<br>    },<br>    "stadium" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "stadiumName" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "startTime" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "summary" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/GameSummaryDto"<br>      },<br>      "type" : "array"<br>    },<br>    "weather" : {<br>      "type" : [ "string", "null" ]<br>    }<br>  },<br>  "required" : [ "attendance", "awayPitcher", "awayScore", "awayTeam", "gameDate", "gameId", "gameStatus", "gameTimeMinutes", "homePitcher", "homeScore", "homeTeam", "inningScores", "stadium", "stadiumName", "startTime", "summary", "weather" ],<br>  "type" : "object"<br>}`
Required properties: `attendance`, `awayPitcher`, `awayScore`, `awayTeam`, `gameDate`, `gameId`, `gameStatus`, `gameTimeMinutes`, `homePitcher`, `homeScore`, `homeTeam`, `inningScores`, `stadium`, `stadiumName`, `startTime`, `summary`, `weather`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `attendance` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `awayPitcher` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `awayScore` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `awayTeam` | yes | `string` | — | — |
| `gameDate` | yes | `string (date)` | — | — |
| `gameId` | yes | `string` | — | — |
| `gameStatus` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `gameTimeMinutes` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `homePitcher` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `homeScore` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `homeTeam` | yes | `string` | — | — |
| `inningScores` | yes | `array<[GameInningScoreDto](openapi-schemas.md#gameinningscoredto)>` | — | — |
| `stadium` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `stadiumName` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `startTime` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `summary` | yes | `array<[GameSummaryDto](openapi-schemas.md#gamesummarydto)>` | — | — |
| `weather` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |

<a id="gameinningscoredto"></a>
## GameInningScoreDto
Schema: `{<br>  "properties" : {<br>    "inning" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "isExtra" : {<br>      "type" : [ "boolean", "null" ]<br>    },<br>    "runs" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "teamCode" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "teamSide" : {<br>      "type" : [ "string", "null" ]<br>    }<br>  },<br>  "required" : [ "inning", "isExtra", "runs", "teamCode", "teamSide" ],<br>  "type" : "object"<br>}`
Required properties: `inning`, `isExtra`, `runs`, `teamCode`, `teamSide`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `inning` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `isExtra` | yes | `{<br>  "type" : [ "boolean", "null" ]<br>}` | — | — |
| `runs` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `teamCode` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `teamSide` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |

<a id="gameinningscorerequestdto"></a>
## GameInningScoreRequestDto
Schema: `{<br>  "properties" : {<br>    "inning" : {<br>      "format" : "int32",<br>      "maximum" : 20,<br>      "minimum" : 1,<br>      "type" : "integer"<br>    },<br>    "isExtra" : {<br>      "type" : "boolean"<br>    },<br>    "runs" : {<br>      "format" : "int32",<br>      "minimum" : 0,<br>      "type" : "integer"<br>    },<br>    "teamCode" : {<br>      "type" : "string"<br>    },<br>    "teamSide" : {<br>      "minLength" : 1,<br>      "pattern" : "^(home\|away)$",<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "inning", "runs", "teamSide" ],<br>  "type" : "object"<br>}`
Required properties: `inning`, `runs`, `teamSide`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `inning` | yes | `integer (int32)` | — | minimum=1, maximum=20 |
| `isExtra` | no | `boolean` | — | — |
| `runs` | yes | `integer (int32)` | — | minimum=0 |
| `teamCode` | no | `string` | — | — |
| `teamSide` | yes | `string` | — | minLength=1, pattern=`^(home\|away)$` |

<a id="gameliveeventdto"></a>
## GameLiveEventDto
Schema: `{<br>  "properties" : {<br>    "awayScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "basesAfter" : {<br>      "type" : "string"<br>    },<br>    "basesBefore" : {<br>      "type" : "string"<br>    },<br>    "batterName" : {<br>      "type" : "string"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "eventSeq" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "eventType" : {<br>      "type" : "string"<br>    },<br>    "homeScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "inning" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "inningHalf" : {<br>      "type" : "string"<br>    },<br>    "outs" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "pitcherName" : {<br>      "type" : "string"<br>    },<br>    "rbi" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "resultCode" : {<br>      "type" : "string"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "winExpectancyAfter" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "winExpectancyBefore" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "wpa" : {<br>      "format" : "double",<br>      "type" : "number"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayScore` | no | `integer (int32)` | — | — |
| `basesAfter` | no | `string` | — | — |
| `basesBefore` | no | `string` | — | — |
| `batterName` | no | `string` | — | — |
| `description` | no | `string` | — | — |
| `eventSeq` | no | `integer (int32)` | — | — |
| `eventType` | no | `string` | — | — |
| `homeScore` | no | `integer (int32)` | — | — |
| `inning` | no | `integer (int32)` | — | — |
| `inningHalf` | no | `string` | — | — |
| `outs` | no | `integer (int32)` | — | — |
| `pitcherName` | no | `string` | — | — |
| `rbi` | no | `integer (int32)` | — | — |
| `resultCode` | no | `string` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |
| `winExpectancyAfter` | no | `number (double)` | — | — |
| `winExpectancyBefore` | no | `number (double)` | — | — |
| `wpa` | no | `number (double)` | — | — |

<a id="gamelivesnapshotdto"></a>
## GameLiveSnapshotDto
Live game snapshot used by prediction detail polling.
Schema: `{<br>  "description" : "Live game snapshot used by prediction detail polling.",<br>  "properties" : {<br>    "awayScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "currentInning" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "currentInningHalf" : {<br>      "type" : "string"<br>    },<br>    "events" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/GameLiveEventDto"<br>      },<br>      "type" : "array"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "gameStatus" : {<br>      "type" : "string"<br>    },<br>    "homeScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "inningScores" : {<br>      "description" : "Normalized meaningful inning scores from game_inning_scores or derived from cumulative game_events scores. Older clients should tolerate this field being absent.",<br>      "items" : {<br>        "$ref" : "#/components/schemas/GameInningScoreDto"<br>      },<br>      "type" : "array"<br>    },<br>    "lastEventSeq" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "lastUpdatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayScore` | no | `integer (int32)` | — | — |
| `currentInning` | no | `integer (int32)` | — | — |
| `currentInningHalf` | no | `string` | — | — |
| `events` | no | `array<[GameLiveEventDto](openapi-schemas.md#gameliveeventdto)>` | — | — |
| `gameId` | no | `string` | — | — |
| `gameStatus` | no | `string` | — | — |
| `homeScore` | no | `integer (int32)` | — | — |
| `inningScores` | no | `array<[GameInningScoreDto](openapi-schemas.md#gameinningscoredto)>` | Normalized meaningful inning scores from game_inning_scores or derived from cumulative game_events scores. Older clients should tolerate this field being absent. | — |
| `lastEventSeq` | no | `integer (int32)` | — | — |
| `lastUpdatedAt` | no | `string (date-time)` | — | — |

<a id="gamelivesummarydto"></a>
## GameLiveSummaryDto
Schema: `{<br>  "properties" : {<br>    "awayScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "gameStatus" : {<br>      "type" : "string"<br>    },<br>    "homeScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "lastEventSeq" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "lastUpdatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayScore` | no | `integer (int32)` | — | — |
| `gameId` | no | `string` | — | — |
| `gameStatus` | no | `string` | — | — |
| `homeScore` | no | `integer (int32)` | — | — |
| `lastEventSeq` | no | `integer (int32)` | — | — |
| `lastUpdatedAt` | no | `string (date-time)` | — | — |

<a id="gamerelayeventdto"></a>
## GameRelayEventDto
Schema: `{<br>  "properties" : {<br>    "batterName" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "eventType" : {<br>      "type" : "string"<br>    },<br>    "inning" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "inningHalf" : {<br>      "type" : "string"<br>    },<br>    "pitcherName" : {<br>      "type" : "string"<br>    },<br>    "playDescription" : {<br>      "type" : "string"<br>    },<br>    "relayId" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "result" : {<br>      "type" : "string"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `batterName` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `eventType` | no | `string` | — | — |
| `inning` | no | `integer (int32)` | — | — |
| `inningHalf` | no | `string` | — | — |
| `pitcherName` | no | `string` | — | — |
| `playDescription` | no | `string` | — | — |
| `relayId` | no | `integer (int32)` | — | — |
| `result` | no | `string` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |

<a id="gamerelaysnapshotdto"></a>
## GameRelaySnapshotDto
Schema: `{<br>  "properties" : {<br>    "events" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/GameRelayEventDto"<br>      },<br>      "type" : "array"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "lastRelayId" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "lastUpdatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `events` | no | `array<[GameRelayEventDto](openapi-schemas.md#gamerelayeventdto)>` | — | — |
| `gameId` | no | `string` | — | — |
| `lastRelayId` | no | `integer (int32)` | — | — |
| `lastUpdatedAt` | no | `string (date-time)` | — | — |

<a id="gameresponsedto"></a>
## GameResponseDto
Schema: `{<br>  "properties" : {<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "date" : {<br>      "type" : "string"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "score" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayTeam` | no | `string` | — | — |
| `date` | no | `string` | — | — |
| `homeTeam` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `score` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |

<a id="gamescoresyncbatchresultdto"></a>
## GameScoreSyncBatchResultDto
Schema: `{<br>  "properties" : {<br>    "endDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "results" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/GameScoreSyncResultDto"<br>      },<br>      "type" : "array"<br>    },<br>    "skippedGames" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "startDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "syncedGames" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "totalGames" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `endDate` | no | `string (date)` | — | — |
| `results` | no | `array<[GameScoreSyncResultDto](openapi-schemas.md#gamescoresyncresultdto)>` | — | — |
| `skippedGames` | no | `integer (int32)` | — | — |
| `startDate` | no | `string (date)` | — | — |
| `syncedGames` | no | `integer (int32)` | — | — |
| `totalGames` | no | `integer (int32)` | — | — |

<a id="gamescoresyncresultdto"></a>
## GameScoreSyncResultDto
Schema: `{<br>  "properties" : {<br>    "awayScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "gameStatus" : {<br>      "type" : "string"<br>    },<br>    "homeScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "inningScoreCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "synced" : {<br>      "type" : "boolean"<br>    },<br>    "usedInningScores" : {<br>      "type" : "boolean"<br>    },<br>    "winningScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "winningTeam" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayScore` | no | `integer (int32)` | — | — |
| `gameId` | no | `string` | — | — |
| `gameStatus` | no | `string` | — | — |
| `homeScore` | no | `integer (int32)` | — | — |
| `inningScoreCount` | no | `integer (int32)` | — | — |
| `synced` | no | `boolean` | — | — |
| `usedInningScores` | no | `boolean` | — | — |
| `winningScore` | no | `integer (int32)` | — | — |
| `winningTeam` | no | `string` | — | — |

<a id="gamestatusmismatchbatchresultdto"></a>
## GameStatusMismatchBatchResultDto
Schema: `{<br>  "properties" : {<br>    "endDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "mismatchCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "mismatches" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/GameStatusMismatchDto"<br>      },<br>      "type" : "array"<br>    },<br>    "nonCanonicalCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "nonCanonicalGames" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/NonCanonicalGameDto"<br>      },<br>      "type" : "array"<br>    },<br>    "startDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "totalGames" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `endDate` | no | `string (date)` | — | — |
| `mismatchCount` | no | `integer (int32)` | — | — |
| `mismatches` | no | `array<[GameStatusMismatchDto](openapi-schemas.md#gamestatusmismatchdto)>` | — | — |
| `nonCanonicalCount` | no | `integer (int32)` | — | — |
| `nonCanonicalGames` | no | `array<[NonCanonicalGameDto](openapi-schemas.md#noncanonicalgamedto)>` | — | — |
| `startDate` | no | `string (date)` | — | — |
| `totalGames` | no | `integer (int32)` | — | — |

<a id="gamestatusmismatchdto"></a>
## GameStatusMismatchDto
Schema: `{<br>  "properties" : {<br>    "awayScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "effectiveStatus" : {<br>      "type" : "string"<br>    },<br>    "gameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "hasInningScores" : {<br>      "type" : "boolean"<br>    },<br>    "hasKnownScore" : {<br>      "type" : "boolean"<br>    },<br>    "homeScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "inningScoreCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "normalizedRawStatus" : {<br>      "type" : "string"<br>    },<br>    "rawStatus" : {<br>      "type" : "string"<br>    },<br>    "reasons" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "startTime" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayScore` | no | `integer (int32)` | — | — |
| `effectiveStatus` | no | `string` | — | — |
| `gameDate` | no | `string (date)` | — | — |
| `gameId` | no | `string` | — | — |
| `hasInningScores` | no | `boolean` | — | — |
| `hasKnownScore` | no | `boolean` | — | — |
| `homeScore` | no | `integer (int32)` | — | — |
| `inningScoreCount` | no | `integer (int32)` | — | — |
| `normalizedRawStatus` | no | `string` | — | — |
| `rawStatus` | no | `string` | — | — |
| `reasons` | no | `array<string>` | — | — |
| `startTime` | no | `string` | — | — |

<a id="gamestatusrepairbatchresultdto"></a>
## GameStatusRepairBatchResultDto
Schema: `{<br>  "properties" : {<br>    "dryRun" : {<br>      "type" : "boolean"<br>    },<br>    "endDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "mismatchCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "mismatches" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/GameStatusMismatchDto"<br>      },<br>      "type" : "array"<br>    },<br>    "nonCanonicalCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "nonCanonicalGames" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/NonCanonicalGameDto"<br>      },<br>      "type" : "array"<br>    },<br>    "repairedCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "repairedGames" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/GameScoreSyncResultDto"<br>      },<br>      "type" : "array"<br>    },<br>    "startDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "totalGames" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `dryRun` | no | `boolean` | — | — |
| `endDate` | no | `string (date)` | — | — |
| `mismatchCount` | no | `integer (int32)` | — | — |
| `mismatches` | no | `array<[GameStatusMismatchDto](openapi-schemas.md#gamestatusmismatchdto)>` | — | — |
| `nonCanonicalCount` | no | `integer (int32)` | — | — |
| `nonCanonicalGames` | no | `array<[NonCanonicalGameDto](openapi-schemas.md#noncanonicalgamedto)>` | — | — |
| `repairedCount` | no | `integer (int32)` | — | — |
| `repairedGames` | no | `array<[GameScoreSyncResultDto](openapi-schemas.md#gamescoresyncresultdto)>` | — | — |
| `startDate` | no | `string (date)` | — | — |
| `totalGames` | no | `integer (int32)` | — | — |

<a id="gamesummarydto"></a>
## GameSummaryDto
Schema: `{<br>  "properties" : {<br>    "detail" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "playerId" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "playerName" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "type" : {<br>      "type" : [ "string", "null" ]<br>    }<br>  },<br>  "required" : [ "detail", "playerId", "playerName", "type" ],<br>  "type" : "object"<br>}`
Required properties: `detail`, `playerId`, `playerName`, `type`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `detail` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `playerId` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `playerName` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `type` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |

<a id="homebootstraploadstatedto"></a>
## HomeBootstrapLoadStateDto
Schema: `{<br>  "properties" : {<br>    "failedSections" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "failureReason" : {<br>      "type" : "string"<br>    },<br>    "isFallback" : {<br>      "type" : "boolean"<br>    },<br>    "manualDataRequest" : {<br>      "$ref" : "#/components/schemas/ManualBaseballDataRequest"<br>    },<br>    "timedOut" : {<br>      "type" : "boolean"<br>    },<br>    "timedOutSections" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `failedSections` | no | `array<string>` | — | — |
| `failureReason` | no | `string` | — | — |
| `isFallback` | no | `boolean` | — | — |
| `manualDataRequest` | no | [ManualBaseballDataRequest](openapi-schemas.md#manualbaseballdatarequest) | — | — |
| `timedOut` | no | `boolean` | — | — |
| `timedOutSections` | no | `array<string>` | — | — |

<a id="homebootstrapresponsedto"></a>
## HomeBootstrapResponseDto
Schema: `{<br>  "properties" : {<br>    "games" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/HomePageGameDto"<br>      },<br>      "type" : "array"<br>    },<br>    "leagueStartDates" : {<br>      "$ref" : "#/components/schemas/LeagueStartDatesDto"<br>    },<br>    "loadState" : {<br>      "$ref" : "#/components/schemas/HomeBootstrapLoadStateDto"<br>    },<br>    "navigation" : {<br>      "$ref" : "#/components/schemas/HomeScheduleNavigationDto"<br>    },<br>    "scheduledGamesWindow" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/HomePageScheduledGameDto"<br>      },<br>      "type" : "array"<br>    },<br>    "selectedDate" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `games` | no | `array<[HomePageGameDto](openapi-schemas.md#homepagegamedto)>` | — | — |
| `leagueStartDates` | no | [LeagueStartDatesDto](openapi-schemas.md#leaguestartdatesdto) | — | — |
| `loadState` | no | [HomeBootstrapLoadStateDto](openapi-schemas.md#homebootstraploadstatedto) | — | — |
| `navigation` | no | [HomeScheduleNavigationDto](openapi-schemas.md#homeschedulenavigationdto) | — | — |
| `scheduledGamesWindow` | no | `array<[HomePageScheduledGameDto](openapi-schemas.md#homepagescheduledgamedto)>` | — | — |
| `selectedDate` | no | `string` | — | — |

<a id="homepagegamedto"></a>
## HomePageGameDto
Schema: `{<br>  "properties" : {<br>    "awayScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "awayTeamFull" : {<br>      "type" : "string"<br>    },<br>    "gameDate" : {<br>      "type" : "string"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "gameInfo" : {<br>      "type" : "string"<br>    },<br>    "gameStatus" : {<br>      "type" : "string"<br>    },<br>    "gameStatusKr" : {<br>      "type" : "string"<br>    },<br>    "homeScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "homeTeamFull" : {<br>      "type" : "string"<br>    },<br>    "leagueType" : {<br>      "type" : "string"<br>    },<br>    "sourceDate" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "time" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayScore` | no | `integer (int32)` | — | — |
| `awayTeam` | no | `string` | — | — |
| `awayTeamFull` | no | `string` | — | — |
| `gameDate` | no | `string` | — | — |
| `gameId` | no | `string` | — | — |
| `gameInfo` | no | `string` | — | — |
| `gameStatus` | no | `string` | — | — |
| `gameStatusKr` | no | `string` | — | — |
| `homeScore` | no | `integer (int32)` | — | — |
| `homeTeam` | no | `string` | — | — |
| `homeTeamFull` | no | `string` | — | — |
| `leagueType` | no | `string` | — | — |
| `sourceDate` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `time` | no | `string` | — | — |

<a id="homepagescheduledgamedto"></a>
## HomePageScheduledGameDto
Schema: `{<br>  "properties" : {<br>    "awayScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "awayTeamFull" : {<br>      "type" : "string"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "gameInfo" : {<br>      "type" : "string"<br>    },<br>    "gameStatus" : {<br>      "type" : "string"<br>    },<br>    "gameStatusKr" : {<br>      "type" : "string"<br>    },<br>    "homeScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "homeTeamFull" : {<br>      "type" : "string"<br>    },<br>    "leagueBadge" : {<br>      "type" : "string"<br>    },<br>    "leagueType" : {<br>      "type" : "string"<br>    },<br>    "sourceDate" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "time" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayScore` | no | `integer (int32)` | — | — |
| `awayTeam` | no | `string` | — | — |
| `awayTeamFull` | no | `string` | — | — |
| `gameId` | no | `string` | — | — |
| `gameInfo` | no | `string` | — | — |
| `gameStatus` | no | `string` | — | — |
| `gameStatusKr` | no | `string` | — | — |
| `homeScore` | no | `integer (int32)` | — | — |
| `homeTeam` | no | `string` | — | — |
| `homeTeamFull` | no | `string` | — | — |
| `leagueBadge` | no | `string` | — | — |
| `leagueType` | no | `string` | — | — |
| `sourceDate` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `time` | no | `string` | — | — |

<a id="homepageteamrankingdto"></a>
## HomePageTeamRankingDto
Schema: `{<br>  "properties" : {<br>    "draws" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "games" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "gamesBehind" : {<br>      "format" : "double",<br>      "type" : [ "number", "null" ]<br>    },<br>    "losses" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "rank" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "recentForm" : {<br>      "description" : "최근 5경기 결과, 최신순 (W/L/D), 데이터 없으면 null 또는 빈 리스트",<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : [ "array", "null" ]<br>    },<br>    "teamId" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "teamName" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "winRate" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "wins" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    }<br>  },<br>  "required" : [ "draws", "games", "gamesBehind", "losses", "rank", "teamId", "teamName", "winRate", "wins" ],<br>  "type" : "object"<br>}`
Required properties: `draws`, `games`, `gamesBehind`, `losses`, `rank`, `teamId`, `teamName`, `winRate`, `wins`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `draws` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `games` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `gamesBehind` | yes | `{<br>  "format" : "double",<br>  "type" : [ "number", "null" ]<br>}` | — | — |
| `losses` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `rank` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `recentForm` | no | `{<br>  "description" : "최근 5경기 결과, 최신순 (W/L/D), 데이터 없으면 null 또는 빈 리스트",<br>  "items" : {<br>    "type" : "string"<br>  },<br>  "type" : [ "array", "null" ]<br>}` | 최근 5경기 결과, 최신순 (W/L/D), 데이터 없으면 null 또는 빈 리스트 | — |
| `teamId` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `teamName` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `winRate` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `wins` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |

<a id="homerankingsnapshotdto"></a>
## HomeRankingSnapshotDto
Schema: `{<br>  "properties" : {<br>    "isOffSeason" : {<br>      "type" : "boolean"<br>    },<br>    "rankingSeasonYear" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "rankingSourceMessage" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "rankings" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/HomePageTeamRankingDto"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "required" : [ "isOffSeason", "rankingSeasonYear", "rankingSourceMessage", "rankings" ],<br>  "type" : "object"<br>}`
Required properties: `isOffSeason`, `rankingSeasonYear`, `rankingSourceMessage`, `rankings`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `isOffSeason` | yes | `boolean` | — | — |
| `rankingSeasonYear` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `rankingSourceMessage` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `rankings` | yes | `array<[HomePageTeamRankingDto](openapi-schemas.md#homepageteamrankingdto)>` | — | — |

<a id="homeschedulenavigationdto"></a>
## HomeScheduleNavigationDto
Schema: `{<br>  "properties" : {<br>    "hasNext" : {<br>      "type" : "boolean"<br>    },<br>    "hasPrev" : {<br>      "type" : "boolean"<br>    },<br>    "nextGameDate" : {<br>      "type" : "string"<br>    },<br>    "prevGameDate" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `hasNext` | no | `boolean` | — | — |
| `hasPrev` | no | `boolean` | — | — |
| `nextGameDate` | no | `string` | — | — |
| `prevGameDate` | no | `string` | — | — |

<a id="homescopednavigationdto"></a>
## HomeScopedNavigationDto
Schema: `{<br>  "properties" : {<br>    "hasNext" : {<br>      "type" : "boolean"<br>    },<br>    "hasPrev" : {<br>      "type" : "boolean"<br>    },<br>    "nextGameDate" : {<br>      "type" : "string"<br>    },<br>    "prevGameDate" : {<br>      "type" : "string"<br>    },<br>    "resolvedDate" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `hasNext` | no | `boolean` | — | — |
| `hasPrev` | no | `boolean` | — | — |
| `nextGameDate` | no | `string` | — | — |
| `prevGameDate` | no | `string` | — | — |
| `resolvedDate` | no | `string` | — | — |

<a id="homewidgetsresponsedto"></a>
## HomeWidgetsResponseDto
Schema: `{<br>  "properties" : {<br>    "featuredMates" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/FeaturedMateCardDto"<br>      },<br>      "type" : "array"<br>    },<br>    "hotCheerPosts" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/PostSummaryRes"<br>      },<br>      "type" : "array"<br>    },<br>    "rankingSnapshot" : {<br>      "$ref" : "#/components/schemas/HomeRankingSnapshotDto"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `featuredMates` | no | `array<[FeaturedMateCardDto](openapi-schemas.md#featuredmatecarddto)>` | — | — |
| `hotCheerPosts` | no | `array<[PostSummaryRes](openapi-schemas.md#postsummaryres)>` | — | — |
| `rankingSnapshot` | no | [HomeRankingSnapshotDto](openapi-schemas.md#homerankingsnapshotdto) | — | — |

<a id="hotstreakdto"></a>
## HotStreakDto
Schema: `{<br>  "properties" : {<br>    "handle" : {<br>      "type" : "string"<br>    },<br>    "level" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "profileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "rankTier" : {<br>      "type" : "string"<br>    },<br>    "streak" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "totalScore" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "userName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `handle` | no | `string` | — | — |
| `level` | no | `integer (int32)` | — | — |
| `profileImageUrl` | no | `string` | — | — |
| `rankTier` | no | `string` | — | — |
| `streak` | no | `integer (int32)` | — | — |
| `totalScore` | no | `integer (int64)` | — | — |
| `userName` | no | `string` | — | — |

<a id="inboxitem"></a>
## InboxItem
Schema: `{<br>  "properties" : {<br>    "hasUnread" : {<br>      "type" : "boolean"<br>    },<br>    "lastMessage" : {<br>      "$ref" : "#/components/schemas/LastMessagePreview"<br>    },<br>    "roomId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "targetUser" : {<br>      "$ref" : "#/components/schemas/TargetUser"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `hasUnread` | no | `boolean` | — | — |
| `lastMessage` | no | [LastMessagePreview](openapi-schemas.md#lastmessagepreview) | — | — |
| `roomId` | no | `integer (int64)` | — | — |
| `targetUser` | no | [TargetUser](openapi-schemas.md#targetuser) | — | — |

<a id="initmediauploadrequest"></a>
## InitMediaUploadRequest
Schema: `{<br>  "properties" : {<br>    "contentLength" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "contentType" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "domain" : {<br>      "enum" : [ "PROFILE", "DIARY", "CHEER", "CHAT" ],<br>      "type" : "string"<br>    },<br>    "fileName" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "height" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "width" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "required" : [ "contentLength", "contentType", "domain", "fileName", "height", "width" ],<br>  "type" : "object"<br>}`
Required properties: `contentLength`, `contentType`, `domain`, `fileName`, `height`, `width`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `contentLength` | yes | `integer (int64)` | — | — |
| `contentType` | yes | `string` | — | minLength=1 |
| `domain` | yes | `string` | — | — |
| `fileName` | yes | `string` | — | minLength=1 |
| `height` | yes | `integer (int32)` | — | — |
| `width` | yes | `integer (int32)` | — | — |

#### Property metadata: `domain`
- Enum: `PROFILE`, `DIARY`, `CHEER`, `CHAT`

<a id="initmediauploadresponse"></a>
## InitMediaUploadResponse
Schema: `{<br>  "properties" : {<br>    "assetId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "expiresAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "requiredHeaders" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    },<br>    "stagingObjectKey" : {<br>      "type" : "string"<br>    },<br>    "uploadUrl" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `assetId` | no | `integer (int64)` | — | — |
| `expiresAt` | no | `string (date-time)` | — | — |
| `requiredHeaders` | no | `composition` | — | — |
| `stagingObjectKey` | no | `string` | — | — |
| `uploadUrl` | no | `string` | — | — |

#### Property composition: `requiredHeaders`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="jsonnode"></a>
## JsonNode
Schema: `{ }`

<a id="lastmessagepreview"></a>
## LastMessagePreview
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "senderId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `senderId` | no | `integer (int64)` | — | — |

<a id="leaderboardentrydto"></a>
## LeaderboardEntryDto
Schema: `{<br>  "properties" : {<br>    "accuracy" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "handle" : {<br>      "type" : "string"<br>    },<br>    "level" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "maxStreak" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "profileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "rank" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "rankTitle" : {<br>      "type" : "string"<br>    },<br>    "score" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "streak" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "userName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `accuracy` | no | `number (double)` | — | — |
| `handle` | no | `string` | — | — |
| `level` | no | `integer (int32)` | — | — |
| `maxStreak` | no | `integer (int32)` | — | — |
| `profileImageUrl` | no | `string` | — | — |
| `rank` | no | `integer (int64)` | — | — |
| `rankTitle` | no | `string` | — | — |
| `score` | no | `integer (int64)` | — | — |
| `streak` | no | `integer (int32)` | — | — |
| `userName` | no | `string` | — | — |

<a id="leaguestartdatesdto"></a>
## LeagueStartDatesDto
Schema: `{<br>  "properties" : {<br>    "koreanSeriesStart" : {<br>      "type" : "string"<br>    },<br>    "postseasonStart" : {<br>      "type" : "string"<br>    },<br>    "regularSeasonStart" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `koreanSeriesStart` | no | `string` | — | — |
| `postseasonStart` | no | `string` | — | — |
| `regularSeasonStart` | no | `string` | — | — |

<a id="liketoggleresponse"></a>
## LikeToggleResponse
Schema: `{<br>  "properties" : {<br>    "liked" : {<br>      "type" : "boolean"<br>    },<br>    "likes" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `liked` | no | `boolean` | — | — |
| `likes` | no | `integer (int32)` | — | — |

<a id="linkedcontentres"></a>
## LinkedContentRes
Schema: `{<br>  "properties" : {<br>    "available" : {<br>      "type" : "boolean"<br>    },<br>    "checkin" : {<br>      "$ref" : "#/components/schemas/CheckinLinkedContentRes"<br>    },<br>    "kind" : {<br>      "enum" : [ "CHECKIN", "RECRUITMENT" ],<br>      "type" : "string"<br>    },<br>    "recruitment" : {<br>      "$ref" : "#/components/schemas/RecruitmentLinkedContentRes"<br>    },<br>    "unavailableReason" : {<br>      "enum" : [ "SOURCE_MISSING", "SOURCE_INELIGIBLE", "MANUAL_BASEBALL_DATA_REQUIRED" ],<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `available` | no | `boolean` | — | — |
| `checkin` | no | [CheckinLinkedContentRes](openapi-schemas.md#checkinlinkedcontentres) | — | — |
| `kind` | no | `string` | — | — |
| `recruitment` | no | [RecruitmentLinkedContentRes](openapi-schemas.md#recruitmentlinkedcontentres) | — | — |
| `unavailableReason` | no | `string` | — | — |

#### Property metadata: `kind`
- Enum: `CHECKIN`, `RECRUITMENT`

#### Property metadata: `unavailableReason`
- Enum: `SOURCE_MISSING`, `SOURCE_INELIGIBLE`, `MANUAL_BASEBALL_DATA_REQUIRED`

<a id="linkedpostlookupres"></a>
## LinkedPostLookupRes
Schema: `{<br>  "properties" : {<br>    "postId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "preview" : {<br>      "$ref" : "#/components/schemas/LinkedContentRes"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `postId` | no | `integer (int64)` | — | — |
| `preview` | no | [LinkedContentRes](openapi-schemas.md#linkedcontentres) | — | — |

<a id="logindto"></a>
## LoginDto
Schema: `{<br>  "properties" : {<br>    "captchaToken" : {<br>      "type" : "string"<br>    },<br>    "email" : {<br>      "format" : "email",<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "password" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "email", "password" ],<br>  "type" : "object"<br>}`
Required properties: `email`, `password`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `captchaToken` | no | `string` | — | — |
| `email` | yes | `string (email)` | — | minLength=1 |
| `password` | yes | `string` | — | minLength=1 |

<a id="manualbaseballdatamissingitem"></a>
## ManualBaseballDataMissingItem
Schema: `{<br>  "properties" : {<br>    "expected_format" : {<br>      "type" : "string"<br>    },<br>    "key" : {<br>      "type" : "string"<br>    },<br>    "label" : {<br>      "type" : "string"<br>    },<br>    "reason" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `expected_format` | no | `string` | — | — |
| `key` | no | `string` | — | — |
| `label` | no | `string` | — | — |
| `reason` | no | `string` | — | — |

<a id="manualbaseballdatarequest"></a>
## ManualBaseballDataRequest
Schema: `{<br>  "properties" : {<br>    "blocking" : {<br>      "type" : "boolean"<br>    },<br>    "missingItems" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/ManualBaseballDataMissingItem"<br>      },<br>      "type" : "array"<br>    },<br>    "operatorMessage" : {<br>      "type" : "string"<br>    },<br>    "scope" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `blocking` | no | `boolean` | — | — |
| `missingItems` | no | `array<[ManualBaseballDataMissingItem](openapi-schemas.md#manualbaseballdatamissingitem)>` | — | — |
| `operatorMessage` | no | `string` | — | — |
| `scope` | no | `string` | — | — |

<a id="matchboundsresponsedto"></a>
## MatchBoundsResponseDto
Schema: `{<br>  "properties" : {<br>    "earliestGameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "hasData" : {<br>      "type" : "boolean"<br>    },<br>    "latestGameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `earliestGameDate` | no | `string (date)` | — | — |
| `hasData` | no | `boolean` | — | — |
| `latestGameDate` | no | `string (date)` | — | — |

<a id="matchdaynavigationresponsedto"></a>
## MatchDayNavigationResponseDto
Schema: `{<br>  "properties" : {<br>    "date" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "games" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/MatchDto"<br>      },<br>      "type" : "array"<br>    },<br>    "hasNext" : {<br>      "type" : "boolean"<br>    },<br>    "hasPrev" : {<br>      "type" : "boolean"<br>    },<br>    "nextDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "prevDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `date` | no | `string (date)` | — | — |
| `games` | no | `array<[MatchDto](openapi-schemas.md#matchdto)>` | — | — |
| `hasNext` | no | `boolean` | — | — |
| `hasPrev` | no | `boolean` | — | — |
| `nextDate` | no | `string (date)` | — | — |
| `prevDate` | no | `string (date)` | — | — |

<a id="matchdto"></a>
## MatchDto
Schema: `{<br>  "properties" : {<br>    "aiSummary" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "awayPitcher" : {<br>      "$ref" : "#/components/schemas/PitcherDto",<br>      "type" : "null"<br>    },<br>    "awayScore" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "gameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "gameStatus" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "homePitcher" : {<br>      "$ref" : "#/components/schemas/PitcherDto",<br>      "type" : "null"<br>    },<br>    "homeScore" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "isDummy" : {<br>      "type" : [ "boolean", "null" ]<br>    },<br>    "leagueType" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "postSeasonSeries" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "seasonId" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "seriesGameNo" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "stadium" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "startTime" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "winProbability" : {<br>      "$ref" : "#/components/schemas/WinProbabilityDto",<br>      "type" : "null"<br>    },<br>    "winner" : {<br>      "type" : [ "string", "null" ]<br>    }<br>  },<br>  "required" : [ "aiSummary", "awayPitcher", "awayScore", "awayTeam", "gameDate", "gameId", "gameStatus", "homePitcher", "homeScore", "homeTeam", "isDummy", "leagueType", "postSeasonSeries", "seasonId", "seriesGameNo", "stadium", "startTime", "winProbability", "winner" ],<br>  "type" : "object"<br>}`
Required properties: `aiSummary`, `awayPitcher`, `awayScore`, `awayTeam`, `gameDate`, `gameId`, `gameStatus`, `homePitcher`, `homeScore`, `homeTeam`, `isDummy`, `leagueType`, `postSeasonSeries`, `seasonId`, `seriesGameNo`, `stadium`, `startTime`, `winProbability`, `winner`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `aiSummary` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `awayPitcher` | yes | [PitcherDto](openapi-schemas.md#pitcherdto) | — | — |
| `awayScore` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `awayTeam` | yes | `string` | — | — |
| `gameDate` | yes | `string (date)` | — | — |
| `gameId` | yes | `string` | — | — |
| `gameStatus` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `homePitcher` | yes | [PitcherDto](openapi-schemas.md#pitcherdto) | — | — |
| `homeScore` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `homeTeam` | yes | `string` | — | — |
| `isDummy` | yes | `{<br>  "type" : [ "boolean", "null" ]<br>}` | — | — |
| `leagueType` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `postSeasonSeries` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `seasonId` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `seriesGameNo` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `stadium` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `startTime` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `winProbability` | yes | [WinProbabilityDto](openapi-schemas.md#winprobabilitydto) | — | — |
| `winner` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |

<a id="matchrangepageresponsedto"></a>
## MatchRangePageResponseDto
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/MatchDto"<br>      },<br>      "type" : "array"<br>    },<br>    "hasNext" : {<br>      "type" : "boolean"<br>    },<br>    "hasPrevious" : {<br>      "type" : "boolean"<br>    },<br>    "page" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "size" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "totalElements" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "totalPages" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "required" : [ "content", "page", "size", "totalElements", "totalPages", "hasNext", "hasPrevious" ]<br>}`
Required properties: `content`, `hasNext`, `hasPrevious`, `page`, `size`, `totalElements`, `totalPages`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | yes | `array<[MatchDto](openapi-schemas.md#matchdto)>` | — | — |
| `hasNext` | yes | `boolean` | — | — |
| `hasPrevious` | yes | `boolean` | — | — |
| `page` | yes | `integer (int32)` | — | — |
| `size` | yes | `integer (int32)` | — | — |
| `totalElements` | yes | `integer (int64)` | — | — |
| `totalPages` | yes | `integer (int32)` | — | — |

<a id="mateapplicationcancelrequest"></a>
## MateApplicationCancelRequest
Schema: `{<br>  "properties" : {<br>    "cancelMemo" : {<br>      "maxLength" : 500,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "cancelReasonType" : {<br>      "enum" : [ "BUYER_CHANGED_MIND", "SELLER_CHANGED_MIND", "SYSTEM", "EVENT_CANCELED", "OTHER" ],<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `cancelMemo` | no | `string` | — | minLength=0, maxLength=500 |
| `cancelReasonType` | no | `string` | — | — |

#### Property metadata: `cancelReasonType`
- Enum: `BUYER_CHANGED_MIND`, `SELLER_CHANGED_MIND`, `SYSTEM`, `EVENT_CANCELED`, `OTHER`

<a id="mateapplicationcancelresponse"></a>
## MateApplicationCancelResponse
Schema: `{<br>  "properties" : {<br>    "applicationId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "feeCharged" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "paymentStatus" : {<br>      "enum" : [ "PAID", "REFUND_REQUESTED", "CANCELED", "REFUND_FAILED" ],<br>      "type" : "string"<br>    },<br>    "refundAmount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "refundPolicyApplied" : {<br>      "type" : "string"<br>    },<br>    "settlementStatus" : {<br>      "enum" : [ "PENDING", "REQUESTED", "COMPLETED", "FAILED", "SKIPPED", "REFUNDED_AFTER_SETTLEMENT" ],<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `applicationId` | no | `integer (int64)` | — | — |
| `feeCharged` | no | `integer (int32)` | — | — |
| `paymentStatus` | no | `string` | — | — |
| `refundAmount` | no | `integer (int32)` | — | — |
| `refundPolicyApplied` | no | `string` | — | — |
| `settlementStatus` | no | `string` | — | — |

#### Property metadata: `paymentStatus`
- Enum: `PAID`, `REFUND_REQUESTED`, `CANCELED`, `REFUND_FAILED`

#### Property metadata: `settlementStatus`
- Enum: `PENDING`, `REQUESTED`, `COMPLETED`, `FAILED`, `SKIPPED`, `REFUNDED_AFTER_SETTLEMENT`

<a id="mateapplicationcreaterequest"></a>
## MateApplicationCreateRequest
Schema: `{<br>  "properties" : {<br>    "depositAmount" : {<br>      "format" : "int32",<br>      "minimum" : 0,<br>      "type" : "integer"<br>    },<br>    "message" : {<br>      "maxLength" : 500,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "paymentType" : {<br>      "enum" : [ "DEPOSIT", "FULL" ],<br>      "type" : "string"<br>    },<br>    "ticketImageUrl" : {<br>      "maxLength" : 2048,<br>      "minLength" : 0,<br>      "type" : [ "string", "null" ]<br>    },<br>    "ticketVerified" : {<br>      "type" : "boolean"<br>    },<br>    "verificationToken" : {<br>      "maxLength" : 128,<br>      "minLength" : 0,<br>      "type" : [ "string", "null" ]<br>    }<br>  },<br>  "required" : [ "partyId" ],<br>  "type" : "object"<br>}`
Required properties: `partyId`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `depositAmount` | no | `integer (int32)` | — | minimum=0 |
| `message` | no | `string` | — | minLength=0, maxLength=500 |
| `partyId` | yes | `integer (int64)` | — | — |
| `paymentType` | no | `string` | — | — |
| `ticketImageUrl` | no | `{<br>  "maxLength" : 2048,<br>  "minLength" : 0,<br>  "type" : [ "string", "null" ]<br>}` | — | minLength=0, maxLength=2048 |
| `ticketVerified` | no | `boolean` | — | — |
| `verificationToken` | no | `{<br>  "maxLength" : 128,<br>  "minLength" : 0,<br>  "type" : [ "string", "null" ]<br>}` | — | minLength=0, maxLength=128 |

#### Property metadata: `paymentType`
- Enum: `DEPOSIT`, `FULL`

<a id="mateapplicationresponse"></a>
## MateApplicationResponse
Schema: `{<br>  "properties" : {<br>    "applicantBadge" : {<br>      "enum" : [ "NEW", "VERIFIED", "TRUSTED" ],<br>      "type" : "string"<br>    },<br>    "applicantHandle" : {<br>      "type" : "string"<br>    },<br>    "applicantName" : {<br>      "type" : "string"<br>    },<br>    "applicantRating" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "approvedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "depositAmount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "feeAmount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "isApproved" : {<br>      "type" : "boolean"<br>    },<br>    "isPaid" : {<br>      "type" : "boolean"<br>    },<br>    "isRejected" : {<br>      "type" : "boolean"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "netSettlementAmount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "orderId" : {<br>      "type" : "string"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "paymentKey" : {<br>      "type" : "string"<br>    },<br>    "paymentStatus" : {<br>      "enum" : [ "PAID", "REFUND_REQUESTED", "CANCELED", "REFUND_FAILED" ],<br>      "type" : "string"<br>    },<br>    "paymentType" : {<br>      "enum" : [ "DEPOSIT", "FULL" ],<br>      "type" : "string"<br>    },<br>    "rejectedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "responseDeadline" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "settlementStatus" : {<br>      "enum" : [ "PENDING", "REQUESTED", "COMPLETED", "FAILED", "SKIPPED", "REFUNDED_AFTER_SETTLEMENT" ],<br>      "type" : "string"<br>    },<br>    "ticketImageUrl" : {<br>      "type" : "string"<br>    },<br>    "ticketVerified" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `applicantBadge` | no | `string` | — | — |
| `applicantHandle` | no | `string` | — | — |
| `applicantName` | no | `string` | — | — |
| `applicantRating` | no | `number (double)` | — | — |
| `approvedAt` | no | `string (date-time)` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `depositAmount` | no | `integer (int32)` | — | — |
| `feeAmount` | no | `integer (int32)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `isApproved` | no | `boolean` | — | — |
| `isPaid` | no | `boolean` | — | — |
| `isRejected` | no | `boolean` | — | — |
| `message` | no | `string` | — | — |
| `netSettlementAmount` | no | `integer (int32)` | — | — |
| `orderId` | no | `string` | — | — |
| `partyId` | no | `integer (int64)` | — | — |
| `paymentKey` | no | `string` | — | — |
| `paymentStatus` | no | `string` | — | — |
| `paymentType` | no | `string` | — | — |
| `rejectedAt` | no | `string (date-time)` | — | — |
| `responseDeadline` | no | `string (date-time)` | — | — |
| `settlementStatus` | no | `string` | — | — |
| `ticketImageUrl` | no | `string` | — | — |
| `ticketVerified` | no | `boolean` | — | — |

#### Property metadata: `applicantBadge`
- Enum: `NEW`, `VERIFIED`, `TRUSTED`

#### Property metadata: `paymentStatus`
- Enum: `PAID`, `REFUND_REQUESTED`, `CANCELED`, `REFUND_FAILED`

#### Property metadata: `paymentType`
- Enum: `DEPOSIT`, `FULL`

#### Property metadata: `settlementStatus`
- Enum: `PENDING`, `REQUESTED`, `COMPLETED`, `FAILED`, `SKIPPED`, `REFUNDED_AFTER_SETTLEMENT`

<a id="matechatimageuploadresponse"></a>
## MateChatImageUploadResponse
Schema: `{<br>  "properties" : {<br>    "path" : {<br>      "type" : "string"<br>    },<br>    "url" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `path` | no | `string` | — | — |
| `url` | no | `string` | — | — |

<a id="matechatmessagerequest"></a>
## MateChatMessageRequest
Schema: `{<br>  "properties" : {<br>    "clientMessageId" : {<br>      "maxLength" : 64,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "imageUrl" : {<br>      "maxLength" : 2048,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "message" : {<br>      "maxLength" : 1000,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "required" : [ "clientMessageId", "partyId" ],<br>  "type" : "object"<br>}`
Required properties: `clientMessageId`, `partyId`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `clientMessageId` | yes | `string` | — | minLength=0, maxLength=64 |
| `imageUrl` | no | `string` | — | minLength=0, maxLength=2048 |
| `message` | no | `string` | — | minLength=0, maxLength=1000 |
| `partyId` | yes | `integer (int64)` | — | — |

<a id="matechatmessageresponse"></a>
## MateChatMessageResponse
Schema: `{<br>  "properties" : {<br>    "clientMessageId" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "imageUrl" : {<br>      "type" : "string"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "senderId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "senderName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `clientMessageId` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `imageUrl` | no | `string` | — | — |
| `message` | no | `string` | — | — |
| `partyId` | no | `integer (int64)` | — | — |
| `senderId` | no | `integer (int64)` | — | — |
| `senderName` | no | `string` | — | — |

<a id="matechatreadresponse"></a>
## MateChatReadResponse
Schema: `{<br>  "properties" : {<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `message` | no | `string` | — | — |
| `success` | no | `boolean` | — | — |

<a id="matechatunreadcountresponse"></a>
## MateChatUnreadCountResponse
Schema: `{<br>  "properties" : {<br>    "data" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `data` | no | `integer (int64)` | — | — |
| `success` | no | `boolean` | — | — |

<a id="matecheckinqrsessionrequest"></a>
## MateCheckInQrSessionRequest
Schema: `{<br>  "properties" : {<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "required" : [ "partyId" ],<br>  "type" : "object"<br>}`
Required properties: `partyId`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `partyId` | yes | `integer (int64)` | — | — |

<a id="matecheckinqrsessionresponse"></a>
## MateCheckInQrSessionResponse
Schema: `{<br>  "properties" : {<br>    "checkinUrl" : {<br>      "type" : "string"<br>    },<br>    "expiresAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "manualCode" : {<br>      "type" : "string"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "sessionId" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `checkinUrl` | no | `string` | — | — |
| `expiresAt` | no | `string (date-time)` | — | — |
| `manualCode` | no | `string` | — | — |
| `partyId` | no | `integer (int64)` | — | — |
| `sessionId` | no | `string` | — | — |

<a id="matecheckinrequest"></a>
## MateCheckInRequest
Schema: `{<br>  "properties" : {<br>    "location" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "manualCode" : {<br>      "pattern" : "^\\d{4}$",<br>      "type" : "string"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "qrSessionId" : {<br>      "maxLength" : 128,<br>      "minLength" : 0,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "location", "partyId" ],<br>  "type" : "object"<br>}`
Required properties: `location`, `partyId`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `location` | yes | `string` | — | minLength=0, maxLength=100 |
| `manualCode` | no | `string` | — | pattern=`^\d{4}$` |
| `partyId` | yes | `integer (int64)` | — | — |
| `qrSessionId` | no | `string` | — | minLength=0, maxLength=128 |

<a id="matecheckinresponse"></a>
## MateCheckInResponse
Schema: `{<br>  "properties" : {<br>    "checkedInAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "location" : {<br>      "type" : "string"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "userHandle" : {<br>      "type" : "string"<br>    },<br>    "userName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `checkedInAt` | no | `string (date-time)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `location` | no | `string` | — | — |
| `partyId` | no | `integer (int64)` | — | — |
| `userHandle` | no | `string` | — | — |
| `userName` | no | `string` | — | — |

<a id="matehostreviewsnippet"></a>
## MateHostReviewSnippet
Schema: `{<br>  "properties" : {<br>    "comment" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "rating" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "reviewerHandle" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `comment` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `rating` | no | `integer (int32)` | — | — |
| `reviewerHandle` | no | `string` | — | — |

<a id="matehosttrustmetrics"></a>
## MateHostTrustMetrics
Schema: `{<br>  "properties" : {<br>    "averageResponseMinutes" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "completedMateCount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "lastActiveAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "recentHostReviews" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/MateHostReviewSnippet"<br>      },<br>      "type" : "array"<br>    },<br>    "recentNoShowCount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "reviewKeywordSummary" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/MateReviewKeywordSummary"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `averageResponseMinutes` | no | `integer (int32)` | — | — |
| `completedMateCount` | no | `integer (int64)` | — | — |
| `lastActiveAt` | no | `string (date-time)` | — | — |
| `recentHostReviews` | no | `array<[MateHostReviewSnippet](openapi-schemas.md#matehostreviewsnippet)>` | — | — |
| `recentNoShowCount` | no | `integer (int64)` | — | — |
| `reviewKeywordSummary` | no | `array<[MateReviewKeywordSummary](openapi-schemas.md#matereviewkeywordsummary)>` | — | — |

<a id="mateinternalsettlementpayoutresponse"></a>
## MateInternalSettlementPayoutResponse
Schema: `{<br>  "properties" : {<br>    "completedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "failReason" : {<br>      "type" : "string"<br>    },<br>    "failureCode" : {<br>      "type" : "string"<br>    },<br>    "payoutId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "providerRef" : {<br>      "type" : "string"<br>    },<br>    "requestedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "enum" : [ "PENDING", "REQUESTED", "COMPLETED", "FAILED", "SKIPPED", "REFUNDED_AFTER_SETTLEMENT" ],<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `completedAt` | no | `string (date-time)` | — | — |
| `failReason` | no | `string` | — | — |
| `failureCode` | no | `string` | — | — |
| `payoutId` | no | `integer (int64)` | — | — |
| `providerRef` | no | `string` | — | — |
| `requestedAt` | no | `string (date-time)` | — | — |
| `status` | no | `string` | — | — |

#### Property metadata: `status`
- Enum: `PENDING`, `REQUESTED`, `COMPLETED`, `FAILED`, `SKIPPED`, `REFUNDED_AFTER_SETTLEMENT`

<a id="matepartycreaterequest"></a>
## MatePartyCreateRequest
Schema: `{<br>  "properties" : {<br>    "awayTeam" : {<br>      "maxLength" : 20,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "cheeringSide" : {<br>      "enum" : [ "HOME", "AWAY", "NEUTRAL" ],<br>      "type" : "string"<br>    },<br>    "description" : {<br>      "maxLength" : 200,<br>      "minLength" : 10,<br>      "type" : "string"<br>    },<br>    "gameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "gameTime" : {<br>      "type" : "string"<br>    },<br>    "homeTeam" : {<br>      "maxLength" : 20,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "maxParticipants" : {<br>      "format" : "int32",<br>      "maximum" : 20,<br>      "minimum" : 2,<br>      "type" : "integer"<br>    },<br>    "reservationDepositAmount" : {<br>      "format" : "int32",<br>      "minimum" : 0,<br>      "type" : "integer"<br>    },<br>    "reservationNumber" : {<br>      "maxLength" : 50,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "seatDetail" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "section" : {<br>      "maxLength" : 50,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    },<br>    "ticketImageUrl" : {<br>      "maxLength" : 2048,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "ticketPrice" : {<br>      "format" : "int32",<br>      "minimum" : 0,<br>      "type" : "integer"<br>    },<br>    "verificationToken" : {<br>      "maxLength" : 128,<br>      "minLength" : 0,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "awayTeam", "cheeringSide", "description", "gameDate", "gameTime", "homeTeam", "maxParticipants", "section", "stadium", "verificationToken" ],<br>  "type" : "object"<br>}`
Required properties: `awayTeam`, `cheeringSide`, `description`, `gameDate`, `gameTime`, `homeTeam`, `maxParticipants`, `section`, `stadium`, `verificationToken`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayTeam` | yes | `string` | — | minLength=0, maxLength=20 |
| `cheeringSide` | yes | `string` | — | — |
| `description` | yes | `string` | — | minLength=10, maxLength=200 |
| `gameDate` | yes | `string (date)` | — | — |
| `gameTime` | yes | `string` | — | — |
| `homeTeam` | yes | `string` | — | minLength=0, maxLength=20 |
| `maxParticipants` | yes | `integer (int32)` | — | minimum=2, maximum=20 |
| `reservationDepositAmount` | no | `integer (int32)` | — | minimum=0 |
| `reservationNumber` | no | `string` | — | minLength=0, maxLength=50 |
| `seatDetail` | no | `string` | — | minLength=0, maxLength=100 |
| `section` | yes | `string` | — | minLength=0, maxLength=50 |
| `stadium` | yes | `string` | — | minLength=0, maxLength=100 |
| `teamId` | no | `string` | — | — |
| `ticketImageUrl` | no | `string` | — | minLength=0, maxLength=2048 |
| `ticketPrice` | no | `integer (int32)` | — | minimum=0 |
| `verificationToken` | yes | `string` | — | minLength=0, maxLength=128 |

#### Property metadata: `cheeringSide`
- Enum: `HOME`, `AWAY`, `NEUTRAL`

<a id="matepartyhistoryresponse"></a>
## MatePartyHistoryResponse
Schema: `{<br>  "properties" : {<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "cheeringSide" : {<br>      "enum" : [ "HOME", "AWAY", "NEUTRAL" ],<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "currentParticipants" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "gameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "gameTime" : {<br>      "type" : "string"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "hostHandle" : {<br>      "type" : "string"<br>    },<br>    "hostId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "maxParticipants" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "enum" : [ "PENDING", "MATCHED", "FAILED", "SELLING", "SOLD", "CHECKED_IN", "COMPLETED" ],<br>      "type" : "string"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayTeam` | no | `string` | — | — |
| `cheeringSide` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `currentParticipants` | no | `integer (int32)` | — | — |
| `description` | no | `string` | — | — |
| `gameDate` | no | `string (date)` | — | — |
| `gameTime` | no | `string` | — | — |
| `homeTeam` | no | `string` | — | — |
| `hostHandle` | no | `string` | — | — |
| `hostId` | no | `integer (int64)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `maxParticipants` | no | `integer (int32)` | — | — |
| `section` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `status` | no | `string` | — | — |
| `teamId` | no | `string` | — | — |

#### Property metadata: `cheeringSide`
- Enum: `HOME`, `AWAY`, `NEUTRAL`

#### Property metadata: `status`
- Enum: `PENDING`, `MATCHED`, `FAILED`, `SELLING`, `SOLD`, `CHECKED_IN`, `COMPLETED`

<a id="matepartymembersummary"></a>
## MatePartyMemberSummary
Schema: `{<br>  "properties" : {<br>    "host" : {<br>      "type" : "boolean"<br>    },<br>    "initial" : {<br>      "type" : "string"<br>    },<br>    "profileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "role" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `host` | no | `boolean` | — | — |
| `initial` | no | `string` | — | — |
| `profileImageUrl` | no | `string` | — | — |
| `role` | no | `string` | — | — |

<a id="matepartypublicresponse"></a>
## MatePartyPublicResponse
Schema: `{<br>  "properties" : {<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "cheeringSide" : {<br>      "enum" : [ "HOME", "AWAY", "NEUTRAL" ],<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "currentParticipants" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "favorited" : {<br>      "type" : "boolean"<br>    },<br>    "gameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "gameTime" : {<br>      "type" : "string"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "hostAverageRating" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "hostBadge" : {<br>      "enum" : [ "NEW", "VERIFIED", "TRUSTED" ],<br>      "type" : "string"<br>    },<br>    "hostFavoriteTeam" : {<br>      "type" : "string"<br>    },<br>    "hostHandle" : {<br>      "type" : "string"<br>    },<br>    "hostName" : {<br>      "type" : "string"<br>    },<br>    "hostProfileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "hostReviewCount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "hostTrustMetrics" : {<br>      "$ref" : "#/components/schemas/MateHostTrustMetrics"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "maxParticipants" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "members" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/MatePartyMemberSummary"<br>      },<br>      "type" : "array"<br>    },<br>    "price" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "reservationDepositAmount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "seatDetail" : {<br>      "type" : "string"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "enum" : [ "PENDING", "MATCHED", "FAILED", "SELLING", "SOLD", "CHECKED_IN", "COMPLETED" ],<br>      "type" : "string"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    },<br>    "ticketPrice" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "ticketVerified" : {<br>      "type" : "boolean"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayTeam` | no | `string` | — | — |
| `cheeringSide` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `currentParticipants` | no | `integer (int32)` | — | — |
| `description` | no | `string` | — | — |
| `favorited` | no | `boolean` | — | — |
| `gameDate` | no | `string (date)` | — | — |
| `gameTime` | no | `string` | — | — |
| `homeTeam` | no | `string` | — | — |
| `hostAverageRating` | no | `number (double)` | — | — |
| `hostBadge` | no | `string` | — | — |
| `hostFavoriteTeam` | no | `string` | — | — |
| `hostHandle` | no | `string` | — | — |
| `hostName` | no | `string` | — | — |
| `hostProfileImageUrl` | no | `string` | — | — |
| `hostReviewCount` | no | `integer (int64)` | — | — |
| `hostTrustMetrics` | no | [MateHostTrustMetrics](openapi-schemas.md#matehosttrustmetrics) | — | — |
| `id` | no | `integer (int64)` | — | — |
| `maxParticipants` | no | `integer (int32)` | — | — |
| `members` | no | `array<[MatePartyMemberSummary](openapi-schemas.md#matepartymembersummary)>` | — | — |
| `price` | no | `integer (int32)` | — | — |
| `reservationDepositAmount` | no | `integer (int32)` | — | — |
| `seatDetail` | no | `string` | — | — |
| `section` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `status` | no | `string` | — | — |
| `teamId` | no | `string` | — | — |
| `ticketPrice` | no | `integer (int32)` | — | — |
| `ticketVerified` | no | `boolean` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |

#### Property metadata: `cheeringSide`
- Enum: `HOME`, `AWAY`, `NEUTRAL`

#### Property metadata: `hostBadge`
- Enum: `NEW`, `VERIFIED`, `TRUSTED`

#### Property metadata: `status`
- Enum: `PENDING`, `MATCHED`, `FAILED`, `SELLING`, `SOLD`, `CHECKED_IN`, `COMPLETED`

<a id="matepartyresponse"></a>
## MatePartyResponse
Schema: `{<br>  "properties" : {<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "cheeringSide" : {<br>      "enum" : [ "HOME", "AWAY", "NEUTRAL" ],<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "currentParticipants" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "gameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "gameTime" : {<br>      "type" : "string"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "hostAverageRating" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "hostBadge" : {<br>      "enum" : [ "NEW", "VERIFIED", "TRUSTED" ],<br>      "type" : "string"<br>    },<br>    "hostFavoriteTeam" : {<br>      "type" : "string"<br>    },<br>    "hostHandle" : {<br>      "type" : "string"<br>    },<br>    "hostId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "hostName" : {<br>      "type" : "string"<br>    },<br>    "hostProfileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "hostReviewCount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "hostTrustMetrics" : {<br>      "$ref" : "#/components/schemas/MateHostTrustMetrics"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "maxParticipants" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "price" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "reservationDepositAmount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "reservationNumber" : {<br>      "type" : "string"<br>    },<br>    "seatDetail" : {<br>      "type" : "string"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "enum" : [ "PENDING", "MATCHED", "FAILED", "SELLING", "SOLD", "CHECKED_IN", "COMPLETED" ],<br>      "type" : "string"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    },<br>    "ticketImageUrl" : {<br>      "type" : "string"<br>    },<br>    "ticketPrice" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "ticketVerified" : {<br>      "type" : "boolean"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayTeam` | no | `string` | — | — |
| `cheeringSide` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `currentParticipants` | no | `integer (int32)` | — | — |
| `description` | no | `string` | — | — |
| `gameDate` | no | `string (date)` | — | — |
| `gameTime` | no | `string` | — | — |
| `homeTeam` | no | `string` | — | — |
| `hostAverageRating` | no | `number (double)` | — | — |
| `hostBadge` | no | `string` | — | — |
| `hostFavoriteTeam` | no | `string` | — | — |
| `hostHandle` | no | `string` | — | — |
| `hostId` | no | `integer (int64)` | — | — |
| `hostName` | no | `string` | — | — |
| `hostProfileImageUrl` | no | `string` | — | — |
| `hostReviewCount` | no | `integer (int64)` | — | — |
| `hostTrustMetrics` | no | [MateHostTrustMetrics](openapi-schemas.md#matehosttrustmetrics) | — | — |
| `id` | no | `integer (int64)` | — | — |
| `maxParticipants` | no | `integer (int32)` | — | — |
| `price` | no | `integer (int32)` | — | — |
| `reservationDepositAmount` | no | `integer (int32)` | — | — |
| `reservationNumber` | no | `string` | — | — |
| `seatDetail` | no | `string` | — | — |
| `section` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `status` | no | `string` | — | — |
| `teamId` | no | `string` | — | — |
| `ticketImageUrl` | no | `string` | — | — |
| `ticketPrice` | no | `integer (int32)` | — | — |
| `ticketVerified` | no | `boolean` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |

#### Property metadata: `cheeringSide`
- Enum: `HOME`, `AWAY`, `NEUTRAL`

#### Property metadata: `hostBadge`
- Enum: `NEW`, `VERIFIED`, `TRUSTED`

#### Property metadata: `status`
- Enum: `PENDING`, `MATCHED`, `FAILED`, `SELLING`, `SOLD`, `CHECKED_IN`, `COMPLETED`

<a id="matepartyupdaterequest"></a>
## MatePartyUpdateRequest
Schema: `{<br>  "properties" : {<br>    "description" : {<br>      "maxLength" : 200,<br>      "minLength" : 10,<br>      "type" : "string"<br>    },<br>    "maxParticipants" : {<br>      "format" : "int32",<br>      "maximum" : 20,<br>      "minimum" : 2,<br>      "type" : "integer"<br>    },<br>    "price" : {<br>      "format" : "int32",<br>      "minimum" : 100,<br>      "type" : "integer"<br>    },<br>    "reservationDepositAmount" : {<br>      "format" : "int32",<br>      "minimum" : 0,<br>      "type" : "integer"<br>    },<br>    "seatDetail" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "section" : {<br>      "maxLength" : 50,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "enum" : [ "PENDING", "MATCHED", "FAILED", "SELLING", "SOLD", "CHECKED_IN", "COMPLETED" ],<br>      "type" : "string"<br>    },<br>    "ticketPrice" : {<br>      "format" : "int32",<br>      "minimum" : 0,<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `description` | no | `string` | — | minLength=10, maxLength=200 |
| `maxParticipants` | no | `integer (int32)` | — | minimum=2, maximum=20 |
| `price` | no | `integer (int32)` | — | minimum=100 |
| `reservationDepositAmount` | no | `integer (int32)` | — | minimum=0 |
| `seatDetail` | no | `string` | — | minLength=0, maxLength=100 |
| `section` | no | `string` | — | minLength=0, maxLength=50 |
| `status` | no | `string` | — | — |
| `ticketPrice` | no | `integer (int32)` | — | minimum=0 |

#### Property metadata: `status`
- Enum: `PENDING`, `MATCHED`, `FAILED`, `SELLING`, `SOLD`, `CHECKED_IN`, `COMPLETED`

<a id="matepaymentcancelintentrequest"></a>
## MatePaymentCancelIntentRequest
Schema: `{<br>  "properties" : {<br>    "cancelReason" : {<br>      "maxLength" : 200,<br>      "minLength" : 0,<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `cancelReason` | no | `string` | — | minLength=0, maxLength=200 |

<a id="matepaymentcancelintentresponse"></a>
## MatePaymentCancelIntentResponse
Schema: `{<br>  "properties" : {<br>    "intentId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "status" : {<br>      "enum" : [ "PREPARED", "CONFIRMED", "APPLICATION_CREATED", "CANCEL_REQUESTED", "CANCELED", "CANCEL_FAILED", "EXPIRED" ],<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `intentId` | no | `integer (int64)` | — | — |
| `status` | no | `string` | — | — |

#### Property metadata: `status`
- Enum: `PREPARED`, `CONFIRMED`, `APPLICATION_CREATED`, `CANCEL_REQUESTED`, `CANCELED`, `CANCEL_FAILED`, `EXPIRED`

<a id="matepaymentcapabilityresponse"></a>
## MatePaymentCapabilityResponse
Schema: `{<br>  "properties" : {<br>    "businessMode" : {<br>      "enum" : [ "DIRECT_TRADE", "IN_APP_PAYMENT" ],<br>      "type" : "string"<br>    },<br>    "environment" : {<br>      "enum" : [ "NONE", "TEST", "LIVE" ],<br>      "type" : "string"<br>    },<br>    "paymentMode" : {<br>      "enum" : [ "DIRECT_TRADE", "TOSS_TEST", "IN_APP_PAYMENT" ],<br>      "type" : "string"<br>    },<br>    "payoutEnabled" : {<br>      "type" : "boolean"<br>    },<br>    "payoutProvider" : {<br>      "enum" : [ "SIM", "TOSS", "UNSUPPORTED" ],<br>      "type" : "string"<br>    },<br>    "provider" : {<br>      "enum" : [ "TOSS", "UNSUPPORTED" ],<br>      "type" : "string"<br>    },<br>    "sellingPaymentRequired" : {<br>      "type" : "boolean"<br>    },<br>    "tossPaymentEnabled" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "required" : [ "businessMode", "environment", "paymentMode", "payoutEnabled", "payoutProvider", "provider", "sellingPaymentRequired", "tossPaymentEnabled" ],<br>  "type" : "object"<br>}`
Required properties: `businessMode`, `environment`, `paymentMode`, `payoutEnabled`, `payoutProvider`, `provider`, `sellingPaymentRequired`, `tossPaymentEnabled`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `businessMode` | yes | `string` | — | — |
| `environment` | yes | `string` | — | — |
| `paymentMode` | yes | `string` | — | — |
| `payoutEnabled` | yes | `boolean` | — | — |
| `payoutProvider` | yes | `string` | — | — |
| `provider` | yes | `string` | — | — |
| `sellingPaymentRequired` | yes | `boolean` | — | — |
| `tossPaymentEnabled` | yes | `boolean` | — | — |

#### Property metadata: `businessMode`
- Enum: `DIRECT_TRADE`, `IN_APP_PAYMENT`

#### Property metadata: `environment`
- Enum: `NONE`, `TEST`, `LIVE`

#### Property metadata: `paymentMode`
- Enum: `DIRECT_TRADE`, `TOSS_TEST`, `IN_APP_PAYMENT`

#### Property metadata: `payoutProvider`
- Enum: `SIM`, `TOSS`, `UNSUPPORTED`

#### Property metadata: `provider`
- Enum: `TOSS`, `UNSUPPORTED`

<a id="matepaymentconfirmrequest"></a>
## MatePaymentConfirmRequest
Schema: `{<br>  "properties" : {<br>    "cancelPolicyVersion" : {<br>      "type" : "string"<br>    },<br>    "flowType" : {<br>      "enum" : [ "DEPOSIT", "SELLING_FULL" ],<br>      "type" : "string"<br>    },<br>    "intentId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "orderId" : {<br>      "type" : "string"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "paymentKey" : {<br>      "type" : "string"<br>    },<br>    "paymentType" : {<br>      "enum" : [ "DEPOSIT", "FULL" ],<br>      "type" : "string"<br>    },<br>    "ticketImageUrl" : {<br>      "type" : "string"<br>    },<br>    "ticketVerified" : {<br>      "type" : "boolean"<br>    },<br>    "verificationToken" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `cancelPolicyVersion` | no | `string` | — | — |
| `flowType` | no | `string` | — | — |
| `intentId` | no | `integer (int64)` | — | — |
| `message` | no | `string` | — | — |
| `orderId` | no | `string` | — | — |
| `partyId` | no | `integer (int64)` | — | — |
| `paymentKey` | no | `string` | — | — |
| `paymentType` | no | `string` | — | — |
| `ticketImageUrl` | no | `string` | — | — |
| `ticketVerified` | no | `boolean` | — | — |
| `verificationToken` | no | `string` | — | — |

#### Property metadata: `flowType`
- Enum: `DEPOSIT`, `SELLING_FULL`

#### Property metadata: `paymentType`
- Enum: `DEPOSIT`, `FULL`

<a id="matepaymentpreparerequest"></a>
## MatePaymentPrepareRequest
Schema: `{<br>  "properties" : {<br>    "cancelPolicyVersion" : {<br>      "type" : "string"<br>    },<br>    "flowType" : {<br>      "enum" : [ "DEPOSIT", "SELLING_FULL" ],<br>      "type" : "string"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `cancelPolicyVersion` | no | `string` | — | — |
| `flowType` | no | `string` | — | — |
| `partyId` | no | `integer (int64)` | — | — |

#### Property metadata: `flowType`
- Enum: `DEPOSIT`, `SELLING_FULL`

<a id="matepaymentprepareresponse"></a>
## MatePaymentPrepareResponse
Schema: `{<br>  "properties" : {<br>    "amount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "cancelPolicyVersion" : {<br>      "type" : "string"<br>    },<br>    "currency" : {<br>      "type" : "string"<br>    },<br>    "flowType" : {<br>      "enum" : [ "DEPOSIT", "SELLING_FULL" ],<br>      "type" : "string"<br>    },<br>    "intentId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "orderId" : {<br>      "type" : "string"<br>    },<br>    "orderName" : {<br>      "type" : "string"<br>    },<br>    "paymentType" : {<br>      "enum" : [ "DEPOSIT", "FULL" ],<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `amount` | no | `integer (int32)` | — | — |
| `cancelPolicyVersion` | no | `string` | — | — |
| `currency` | no | `string` | — | — |
| `flowType` | no | `string` | — | — |
| `intentId` | no | `integer (int64)` | — | — |
| `orderId` | no | `string` | — | — |
| `orderName` | no | `string` | — | — |
| `paymentType` | no | `string` | — | — |

#### Property metadata: `flowType`
- Enum: `DEPOSIT`, `SELLING_FULL`

#### Property metadata: `paymentType`
- Enum: `DEPOSIT`, `FULL`

<a id="matereviewcreaterequest"></a>
## MateReviewCreateRequest
Schema: `{<br>  "properties" : {<br>    "comment" : {<br>      "type" : "string"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "rating" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "revieweeHandle" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `comment` | no | `string` | — | — |
| `partyId` | no | `integer (int64)` | — | — |
| `rating` | no | `integer (int32)` | — | — |
| `revieweeHandle` | no | `string` | — | — |

<a id="matereviewkeywordsummary"></a>
## MateReviewKeywordSummary
Schema: `{<br>  "properties" : {<br>    "count" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "label" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `count` | no | `integer (int64)` | — | — |
| `label` | no | `string` | — | — |

<a id="matereviewresponse"></a>
## MateReviewResponse
Schema: `{<br>  "properties" : {<br>    "comment" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "rating" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "revieweeHandle" : {<br>      "type" : "string"<br>    },<br>    "reviewerHandle" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `comment` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `partyId` | no | `integer (int64)` | — | — |
| `rating` | no | `integer (int32)` | — | — |
| `revieweeHandle` | no | `string` | — | — |
| `reviewerHandle` | no | `string` | — | — |

<a id="matesellerpayoutprofileresponse"></a>
## MateSellerPayoutProfileResponse
Schema: `{<br>  "properties" : {<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "kycStatus" : {<br>      "type" : "string"<br>    },<br>    "metadataJson" : {<br>      "type" : "string"<br>    },<br>    "provider" : {<br>      "type" : "string"<br>    },<br>    "providerSellerId" : {<br>      "type" : "string"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "userId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `createdAt` | no | `string (date-time)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `kycStatus` | no | `string` | — | — |
| `metadataJson` | no | `string` | — | — |
| `provider` | no | `string` | — | — |
| `providerSellerId` | no | `string` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |
| `userId` | no | `integer (int64)` | — | — |

<a id="matesellerpayoutprofileupsertrequest"></a>
## MateSellerPayoutProfileUpsertRequest
Schema: `{<br>  "properties" : {<br>    "kycStatus" : {<br>      "type" : "string"<br>    },<br>    "metadataJson" : {<br>      "type" : "string"<br>    },<br>    "provider" : {<br>      "type" : "string"<br>    },<br>    "providerSellerId" : {<br>      "type" : "string"<br>    },<br>    "userId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `kycStatus` | no | `string` | — | — |
| `metadataJson` | no | `string` | — | — |
| `provider` | no | `string` | — | — |
| `providerSellerId` | no | `string` | — | — |
| `userId` | no | `integer (int64)` | — | — |

<a id="mediabackfilldomainreport"></a>
## MediaBackfillDomainReport
Schema: `{<br>  "properties" : {<br>    "auditCounts" : {<br>      "additionalProperties" : {<br>        "format" : "int32",<br>        "type" : "integer"<br>      },<br>      "type" : "object"<br>    },<br>    "auditSamples" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/MediaBackfillIssueSample"<br>      },<br>      "type" : "array"<br>    },<br>    "clearedCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "domain" : {<br>      "type" : "string"<br>    },<br>    "legacyPathRetainedCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "linkSyncedCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "manualReviewCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "normalizedCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "sampleLegacyRetainedTargets" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "sampleManualReviewTargets" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "sampleNormalizedTargets" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "scannedCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "updatedCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `auditCounts` | no | `composition` | — | — |
| `auditSamples` | no | `array<[MediaBackfillIssueSample](openapi-schemas.md#mediabackfillissuesample)>` | — | — |
| `clearedCount` | no | `integer (int32)` | — | — |
| `domain` | no | `string` | — | — |
| `legacyPathRetainedCount` | no | `integer (int32)` | — | — |
| `linkSyncedCount` | no | `integer (int32)` | — | — |
| `manualReviewCount` | no | `integer (int32)` | — | — |
| `normalizedCount` | no | `integer (int32)` | — | — |
| `sampleLegacyRetainedTargets` | no | `array<string>` | — | — |
| `sampleManualReviewTargets` | no | `array<string>` | — | — |
| `sampleNormalizedTargets` | no | `array<string>` | — | — |
| `scannedCount` | no | `integer (int32)` | — | — |
| `updatedCount` | no | `integer (int32)` | — | — |

#### Property composition: `auditCounts`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "format" : "int32",
    "type" : "integer"
  },
  "type" : "object"
}
```

<a id="mediabackfillissuesample"></a>
## MediaBackfillIssueSample
Schema: `{<br>  "properties" : {<br>    "detail" : {<br>      "type" : "string"<br>    },<br>    "objectKey" : {<br>      "type" : "string"<br>    },<br>    "subject" : {<br>      "type" : "string"<br>    },<br>    "type" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `detail` | no | `string` | — | — |
| `objectKey` | no | `string` | — | — |
| `subject` | no | `string` | — | — |
| `type` | no | `string` | — | — |

<a id="mediabackfillreport"></a>
## MediaBackfillReport
Schema: `{<br>  "properties" : {<br>    "applied" : {<br>      "type" : "boolean"<br>    },<br>    "batchSize" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "domains" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/MediaBackfillDomainReport"<br>      },<br>      "type" : "array"<br>    },<br>    "hasFailures" : {<br>      "type" : "boolean"<br>    },<br>    "requestedDomains" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `applied` | no | `boolean` | — | — |
| `batchSize` | no | `integer (int32)` | — | — |
| `domains` | no | `array<[MediaBackfillDomainReport](openapi-schemas.md#mediabackfilldomainreport)>` | — | — |
| `hasFailures` | no | `boolean` | — | — |
| `requestedDomains` | no | `array<string>` | — | — |

<a id="mediacleanupreport"></a>
## MediaCleanupReport
Schema: `{<br>  "properties" : {<br>    "hasFailures" : {<br>      "type" : "boolean"<br>    },<br>    "requestedTargets" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "targets" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/MediaCleanupTargetReport"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `hasFailures` | no | `boolean` | — | — |
| `requestedTargets` | no | `array<string>` | — | — |
| `targets` | no | `array<[MediaCleanupTargetReport](openapi-schemas.md#mediacleanuptargetreport)>` | — | — |

<a id="mediacleanuptargetreport"></a>
## MediaCleanupTargetReport
Schema: `{<br>  "properties" : {<br>    "deletedCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "errorCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "scannedCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "target" : {<br>      "enum" : [ "PENDING", "ORPHAN" ],<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `deletedCount` | no | `integer (int32)` | — | — |
| `errorCount` | no | `integer (int32)` | — | — |
| `scannedCount` | no | `integer (int32)` | — | — |
| `target` | no | `string` | — | — |

#### Property metadata: `target`
- Enum: `PENDING`, `ORPHAN`

<a id="mediasmokedomainreport"></a>
## MediaSmokeDomainReport
Schema: `{<br>  "properties" : {<br>    "checkedCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "domain" : {<br>      "enum" : [ "PROFILE", "DIARY", "CHEER", "CHAT" ],<br>      "type" : "string"<br>    },<br>    "failedObjectKeys" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "feedDerivativeMissingCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "missingObjectCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "urlFailureCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `checkedCount` | no | `integer (int32)` | — | — |
| `domain` | no | `string` | — | — |
| `failedObjectKeys` | no | `array<string>` | — | — |
| `feedDerivativeMissingCount` | no | `integer (int32)` | — | — |
| `missingObjectCount` | no | `integer (int32)` | — | — |
| `urlFailureCount` | no | `integer (int32)` | — | — |

#### Property metadata: `domain`
- Enum: `PROFILE`, `DIARY`, `CHEER`, `CHAT`

<a id="mediasmokereport"></a>
## MediaSmokeReport
Schema: `{<br>  "properties" : {<br>    "domains" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/MediaSmokeDomainReport"<br>      },<br>      "type" : "array"<br>    },<br>    "hasFailures" : {<br>      "type" : "boolean"<br>    },<br>    "requestedDomains" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "sampleLimit" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `domains` | no | `array<[MediaSmokeDomainReport](openapi-schemas.md#mediasmokedomainreport)>` | — | — |
| `hasFailures` | no | `boolean` | — | — |
| `requestedDomains` | no | `array<string>` | — | — |
| `sampleLimit` | no | `integer (int32)` | — | — |

<a id="noncanonicalgamedto"></a>
## NonCanonicalGameDto
Schema: `{<br>  "properties" : {<br>    "awayScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "gameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "homeScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "rawStatus" : {<br>      "type" : "string"<br>    },<br>    "reasons" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "startTime" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayScore` | no | `integer (int32)` | — | — |
| `awayTeam` | no | `string` | — | — |
| `gameDate` | no | `string (date)` | — | — |
| `gameId` | no | `string` | — | — |
| `homeScore` | no | `integer (int32)` | — | — |
| `homeTeam` | no | `string` | — | — |
| `rawStatus` | no | `string` | — | — |
| `reasons` | no | `array<string>` | — | — |
| `startTime` | no | `string` | — | — |

<a id="offseasonmetadto"></a>
## OffseasonMetaDto
Schema: `{<br>  "properties" : {<br>    "awards" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/AwardDto"<br>      },<br>      "type" : "array"<br>    },<br>    "postSeasonResults" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/PostSeasonResultDto"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awards` | no | `array<[AwardDto](openapi-schemas.md#awarddto)>` | — | — |
| `postSeasonResults` | no | `array<[PostSeasonResultDto](openapi-schemas.md#postseasonresultdto)>` | — | — |

<a id="offseasonmovementadmindto"></a>
## OffseasonMovementAdminDto
Schema: `{<br>  "properties" : {<br>    "announcedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "contractTerm" : {<br>      "type" : "string"<br>    },<br>    "contractValue" : {<br>      "type" : "string"<br>    },<br>    "counterpartyDetails" : {<br>      "type" : "string"<br>    },<br>    "counterpartyTeam" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "details" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "movementDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "optionDetails" : {<br>      "type" : "string"<br>    },<br>    "playerName" : {<br>      "type" : "string"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "sourceLabel" : {<br>      "type" : "string"<br>    },<br>    "sourceUrl" : {<br>      "type" : "string"<br>    },<br>    "summary" : {<br>      "type" : "string"<br>    },<br>    "teamCode" : {<br>      "type" : "string"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `announcedAt` | no | `string (date-time)` | — | — |
| `contractTerm` | no | `string` | — | — |
| `contractValue` | no | `string` | — | — |
| `counterpartyDetails` | no | `string` | — | — |
| `counterpartyTeam` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `details` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `movementDate` | no | `string (date)` | — | — |
| `optionDetails` | no | `string` | — | — |
| `playerName` | no | `string` | — | — |
| `section` | no | `string` | — | — |
| `sourceLabel` | no | `string` | — | — |
| `sourceUrl` | no | `string` | — | — |
| `summary` | no | `string` | — | — |
| `teamCode` | no | `string` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |

<a id="offseasonmovementadminrequest"></a>
## OffseasonMovementAdminRequest
Schema: `{<br>  "properties" : {<br>    "announcedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "contractTerm" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "contractValue" : {<br>      "maxLength" : 120,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "counterpartyDetails" : {<br>      "maxLength" : 500,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "counterpartyTeam" : {<br>      "maxLength" : 50,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "details" : {<br>      "maxLength" : 4000,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "movementDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "optionDetails" : {<br>      "maxLength" : 300,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "playerName" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "section" : {<br>      "maxLength" : 50,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "sourceLabel" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "sourceUrl" : {<br>      "maxLength" : 500,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "summary" : {<br>      "maxLength" : 300,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "teamCode" : {<br>      "maxLength" : 20,<br>      "minLength" : 0,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "movementDate", "playerName", "section", "teamCode" ],<br>  "type" : "object"<br>}`
Required properties: `movementDate`, `playerName`, `section`, `teamCode`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `announcedAt` | no | `string (date-time)` | — | — |
| `contractTerm` | no | `string` | — | minLength=0, maxLength=100 |
| `contractValue` | no | `string` | — | minLength=0, maxLength=120 |
| `counterpartyDetails` | no | `string` | — | minLength=0, maxLength=500 |
| `counterpartyTeam` | no | `string` | — | minLength=0, maxLength=50 |
| `details` | no | `string` | — | minLength=0, maxLength=4000 |
| `movementDate` | yes | `string (date)` | — | — |
| `optionDetails` | no | `string` | — | minLength=0, maxLength=300 |
| `playerName` | yes | `string` | — | minLength=0, maxLength=100 |
| `section` | yes | `string` | — | minLength=0, maxLength=50 |
| `sourceLabel` | no | `string` | — | minLength=0, maxLength=100 |
| `sourceUrl` | no | `string` | — | minLength=0, maxLength=500 |
| `summary` | no | `string` | — | minLength=0, maxLength=300 |
| `teamCode` | yes | `string` | — | minLength=0, maxLength=20 |

<a id="offseasonmovementdto"></a>
## OffseasonMovementDto
Schema: `{<br>  "properties" : {<br>    "announcedAt" : {<br>      "type" : "string"<br>    },<br>    "contractTerm" : {<br>      "type" : "string"<br>    },<br>    "contractValue" : {<br>      "type" : "string"<br>    },<br>    "counterpartyDetails" : {<br>      "type" : "string"<br>    },<br>    "counterpartyTeam" : {<br>      "type" : "string"<br>    },<br>    "date" : {<br>      "type" : "string"<br>    },<br>    "displayAmount" : {<br>      "type" : "string"<br>    },<br>    "estimatedAmount" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "isBigEvent" : {<br>      "type" : "boolean"<br>    },<br>    "optionDetails" : {<br>      "type" : "string"<br>    },<br>    "player" : {<br>      "type" : "string"<br>    },<br>    "remarks" : {<br>      "type" : "string"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "sourceLabel" : {<br>      "type" : "string"<br>    },<br>    "sourceUrl" : {<br>      "type" : "string"<br>    },<br>    "summary" : {<br>      "type" : "string"<br>    },<br>    "team" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `announcedAt` | no | `string` | — | — |
| `contractTerm` | no | `string` | — | — |
| `contractValue` | no | `string` | — | — |
| `counterpartyDetails` | no | `string` | — | — |
| `counterpartyTeam` | no | `string` | — | — |
| `date` | no | `string` | — | — |
| `displayAmount` | no | `string` | — | — |
| `estimatedAmount` | no | `integer (int64)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `isBigEvent` | no | `boolean` | — | — |
| `optionDetails` | no | `string` | — | — |
| `player` | no | `string` | — | — |
| `remarks` | no | `string` | — | — |
| `section` | no | `string` | — | — |
| `sourceLabel` | no | `string` | — | — |
| `sourceUrl` | no | `string` | — | — |
| `summary` | no | `string` | — | — |
| `team` | no | `string` | — | — |

<a id="opponentstats"></a>
## OpponentStats
Schema: `{<br>  "properties" : {<br>    "draws" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "losses" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "winRate" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "wins" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `draws` | no | `integer (int32)` | — | — |
| `losses` | no | `integer (int32)` | — | — |
| `winRate` | no | `number (double)` | — | — |
| `wins` | no | `integer (int32)` | — | — |

<a id="pagemetadata"></a>
## PageMetadata
Schema: `{<br>  "properties" : {<br>    "number" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "size" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "totalElements" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "totalPages" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `number` | no | `integer (int64)` | — | — |
| `size` | no | `integer (int64)` | — | — |
| `totalElements` | no | `integer (int64)` | — | — |
| `totalPages` | no | `integer (int64)` | — | — |

<a id="pageobject"></a>
## PageObject
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/AuditLogDto"<br>      },<br>      "type" : "array"<br>    },<br>    "empty" : {<br>      "type" : "boolean"<br>    },<br>    "first" : {<br>      "type" : "boolean"<br>    },<br>    "last" : {<br>      "type" : "boolean"<br>    },<br>    "number" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "numberOfElements" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "pageable" : {<br>      "$ref" : "#/components/schemas/PageableObject"<br>    },<br>    "size" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "sort" : {<br>      "$ref" : "#/components/schemas/SortObject"<br>    },<br>    "totalElements" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "totalPages" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | no | `array<[AuditLogDto](openapi-schemas.md#auditlogdto)>` | — | — |
| `empty` | no | `boolean` | — | — |
| `first` | no | `boolean` | — | — |
| `last` | no | `boolean` | — | — |
| `number` | no | `integer (int32)` | — | — |
| `numberOfElements` | no | `integer (int32)` | — | — |
| `pageable` | no | [PageableObject](openapi-schemas.md#pageableobject) | — | — |
| `size` | no | `integer (int32)` | — | — |
| `sort` | no | [SortObject](openapi-schemas.md#sortobject) | — | — |
| `totalElements` | no | `integer (int64)` | — | — |
| `totalPages` | no | `integer (int32)` | — | — |

<a id="pageable"></a>
## Pageable
Schema: `{<br>  "properties" : {<br>    "page" : {<br>      "format" : "int32",<br>      "minimum" : 0,<br>      "type" : "integer"<br>    },<br>    "size" : {<br>      "format" : "int32",<br>      "minimum" : 1,<br>      "type" : "integer"<br>    },<br>    "sort" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `page` | no | `integer (int32)` | — | minimum=0 |
| `size` | no | `integer (int32)` | — | minimum=1 |
| `sort` | no | `array<string>` | — | — |

<a id="pageableobject"></a>
## PageableObject
Schema: `{<br>  "properties" : {<br>    "offset" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "pageNumber" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "pageSize" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "paged" : {<br>      "type" : "boolean"<br>    },<br>    "sort" : {<br>      "$ref" : "#/components/schemas/SortObject"<br>    },<br>    "unpaged" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `offset` | no | `integer (int64)` | — | — |
| `pageNumber` | no | `integer (int32)` | — | — |
| `pageSize` | no | `integer (int32)` | — | — |
| `paged` | no | `boolean` | — | — |
| `sort` | no | [SortObject](openapi-schemas.md#sortobject) | — | — |
| `unpaged` | no | `boolean` | — | — |

<a id="pagedmodelcommentres"></a>
## PagedModelCommentRes
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/CommentRes"<br>      },<br>      "type" : "array"<br>    },<br>    "page" : {<br>      "$ref" : "#/components/schemas/PageMetadata"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | no | `array<[CommentRes](openapi-schemas.md#commentres)>` | — | — |
| `page` | no | [PageMetadata](openapi-schemas.md#pagemetadata) | — | — |

<a id="pagedmodelleaderboardentrydto"></a>
## PagedModelLeaderboardEntryDto
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/LeaderboardEntryDto"<br>      },<br>      "type" : "array"<br>    },<br>    "page" : {<br>      "$ref" : "#/components/schemas/PageMetadata"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | no | `array<[LeaderboardEntryDto](openapi-schemas.md#leaderboardentrydto)>` | — | — |
| `page` | no | [PageMetadata](openapi-schemas.md#pagemetadata) | — | — |

<a id="pagedmodelmatepartyhistoryresponse"></a>
## PagedModelMatePartyHistoryResponse
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/MatePartyHistoryResponse"<br>      },<br>      "type" : "array"<br>    },<br>    "page" : {<br>      "$ref" : "#/components/schemas/PageMetadata"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | no | `array<[MatePartyHistoryResponse](openapi-schemas.md#matepartyhistoryresponse)>` | — | — |
| `page` | no | [PageMetadata](openapi-schemas.md#pagemetadata) | — | — |

<a id="pagedmodelmatepartypublicresponse"></a>
## PagedModelMatePartyPublicResponse
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/MatePartyPublicResponse"<br>      },<br>      "type" : "array"<br>    },<br>    "page" : {<br>      "$ref" : "#/components/schemas/PageMetadata"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | no | `array<[MatePartyPublicResponse](openapi-schemas.md#matepartypublicresponse)>` | — | — |
| `page` | no | [PageMetadata](openapi-schemas.md#pagemetadata) | — | — |

<a id="pagedmodelpostsummaryres"></a>
## PagedModelPostSummaryRes
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/PostSummaryRes"<br>      },<br>      "type" : "array"<br>    },<br>    "page" : {<br>      "$ref" : "#/components/schemas/PageMetadata"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | no | `array<[PostSummaryRes](openapi-schemas.md#postsummaryres)>` | — | — |
| `page` | no | [PageMetadata](openapi-schemas.md#pagemetadata) | — | — |

<a id="pagedmodelrecentscoredto"></a>
## PagedModelRecentScoreDto
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/RecentScoreDto"<br>      },<br>      "type" : "array"<br>    },<br>    "page" : {<br>      "$ref" : "#/components/schemas/PageMetadata"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | no | `array<[RecentScoreDto](openapi-schemas.md#recentscoredto)>` | — | — |
| `page` | no | [PageMetadata](openapi-schemas.md#pagemetadata) | — | — |

<a id="pagedmodeluserfollowsummarydto"></a>
## PagedModelUserFollowSummaryDto
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/UserFollowSummaryDto"<br>      },<br>      "type" : "array"<br>    },<br>    "page" : {<br>      "$ref" : "#/components/schemas/PageMetadata"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | no | `array<[UserFollowSummaryDto](openapi-schemas.md#userfollowsummarydto)>` | — | — |
| `page` | no | [PageMetadata](openapi-schemas.md#pagemetadata) | — | — |

<a id="passwordresetconfirmdto"></a>
## PasswordResetConfirmDto
Schema: `{<br>  "properties" : {<br>    "confirmPassword" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "newPassword" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "token" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "confirmPassword", "newPassword", "token" ],<br>  "type" : "object"<br>}`
Required properties: `confirmPassword`, `newPassword`, `token`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `confirmPassword` | yes | `string` | — | minLength=1 |
| `newPassword` | yes | `string` | — | minLength=1 |
| `token` | yes | `string` | — | minLength=1 |

<a id="passwordresetrequestdto"></a>
## PasswordResetRequestDto
Schema: `{<br>  "properties" : {<br>    "email" : {<br>      "format" : "email",<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "redirect" : {<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "email" ],<br>  "type" : "object"<br>}`
Required properties: `email`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `email` | yes | `string (email)` | — | minLength=1 |
| `redirect` | no | `string` | — | — |

<a id="pitcherdto"></a>
## PitcherDto
Schema: `{<br>  "properties" : {<br>    "era" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "imgUrl" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "loss" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "name" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "win" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    }<br>  },<br>  "required" : [ "era", "imgUrl", "loss", "name", "win" ],<br>  "type" : "object"<br>}`
Required properties: `era`, `imgUrl`, `loss`, `name`, `win`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `era` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `imgUrl` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `loss` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `name` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `win` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |

<a id="placedto"></a>
## PlaceDto
Schema: `{<br>  "properties" : {<br>    "address" : {<br>      "type" : "string"<br>    },<br>    "category" : {<br>      "type" : "string"<br>    },<br>    "closeTime" : {<br>      "type" : "string"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "lat" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "lng" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "name" : {<br>      "type" : "string"<br>    },<br>    "openTime" : {<br>      "type" : "string"<br>    },<br>    "phone" : {<br>      "type" : "string"<br>    },<br>    "rating" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "stadiumName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `address` | no | `string` | — | — |
| `category` | no | `string` | — | — |
| `closeTime` | no | `string` | — | — |
| `description` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `lat` | no | `number (double)` | — | — |
| `lng` | no | `number (double)` | — | — |
| `name` | no | `string` | — | — |
| `openTime` | no | `string` | — | — |
| `phone` | no | `string` | — | — |
| `rating` | no | `number (double)` | — | — |
| `stadiumName` | no | `string` | — | — |

<a id="placerequest"></a>
## PlaceRequest
Schema: `{<br>  "properties" : {<br>    "address" : {<br>      "maxLength" : 255,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "category" : {<br>      "maxLength" : 50,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "closeTime" : {<br>      "maxLength" : 50,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "lat" : {<br>      "format" : "double",<br>      "maximum" : 90.0,<br>      "minimum" : -90.0,<br>      "type" : "number"<br>    },<br>    "lng" : {<br>      "format" : "double",<br>      "maximum" : 180.0,<br>      "minimum" : -180.0,<br>      "type" : "number"<br>    },<br>    "name" : {<br>      "maxLength" : 100,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "openTime" : {<br>      "maxLength" : 50,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "phone" : {<br>      "maxLength" : 20,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "rating" : {<br>      "format" : "double",<br>      "maximum" : 5.0,<br>      "minimum" : 0.0,<br>      "type" : "number"<br>    }<br>  },<br>  "required" : [ "category", "lat", "lng", "name" ],<br>  "type" : "object"<br>}`
Required properties: `category`, `lat`, `lng`, `name`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `address` | no | `string` | — | minLength=0, maxLength=255 |
| `category` | yes | `string` | — | minLength=0, maxLength=50 |
| `closeTime` | no | `string` | — | minLength=0, maxLength=50 |
| `description` | no | `string` | — | — |
| `lat` | yes | `number (double)` | — | minimum=-90.0, maximum=90.0 |
| `lng` | yes | `number (double)` | — | minimum=-180.0, maximum=180.0 |
| `name` | yes | `string` | — | minLength=0, maxLength=100 |
| `openTime` | no | `string` | — | minLength=0, maxLength=50 |
| `phone` | no | `string` | — | minLength=0, maxLength=20 |
| `rating` | no | `number (double)` | — | minimum=0.0, maximum=5.0 |

<a id="policyconsentitemdto"></a>
## PolicyConsentItemDto
Schema: `{<br>  "properties" : {<br>    "agreed" : {<br>      "type" : "boolean"<br>    },<br>    "policyType" : {<br>      "enum" : [ "TERMS", "PRIVACY", "DATA_DISCLAIMER" ],<br>      "type" : "string"<br>    },<br>    "version" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "agreed", "policyType", "version" ],<br>  "type" : "object"<br>}`
Required properties: `agreed`, `policyType`, `version`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `agreed` | yes | `boolean` | — | — |
| `policyType` | yes | `string` | — | — |
| `version` | yes | `string` | — | minLength=1 |

#### Property metadata: `policyType`
- Enum: `TERMS`, `PRIVACY`, `DATA_DISCLAIMER`

<a id="policyconsentsubmitdto"></a>
## PolicyConsentSubmitDto
Schema: `{<br>  "properties" : {<br>    "policyConsents" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/PolicyConsentItemDto"<br>      },<br>      "minItems" : 1,<br>      "type" : "array"<br>    }<br>  },<br>  "required" : [ "policyConsents" ],<br>  "type" : "object"<br>}`
Required properties: `policyConsents`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `policyConsents` | yes | `array<[PolicyConsentItemDto](openapi-schemas.md#policyconsentitemdto)>` | — | minItems=1 |

<a id="policyrequiredresponsedto"></a>
## PolicyRequiredResponseDto
Schema: `{<br>  "properties" : {<br>    "effectiveDate" : {<br>      "type" : "string"<br>    },<br>    "gracePeriodDays" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "hardGateDate" : {<br>      "type" : "string"<br>    },<br>    "policies" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/PolicyRequirementItemDto"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `effectiveDate` | no | `string` | — | — |
| `gracePeriodDays` | no | `integer (int32)` | — | — |
| `hardGateDate` | no | `string` | — | — |
| `policies` | no | `array<[PolicyRequirementItemDto](openapi-schemas.md#policyrequirementitemdto)>` | — | — |

<a id="policyrequirementitemdto"></a>
## PolicyRequirementItemDto
Schema: `{<br>  "properties" : {<br>    "effectiveDate" : {<br>      "type" : "string"<br>    },<br>    "path" : {<br>      "type" : "string"<br>    },<br>    "policyType" : {<br>      "type" : "string"<br>    },<br>    "required" : {<br>      "type" : "boolean"<br>    },<br>    "version" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `effectiveDate` | no | `string` | — | — |
| `path` | no | `string` | — | — |
| `policyType` | no | `string` | — | — |
| `required` | no | `boolean` | — | — |
| `version` | no | `string` | — | — |

<a id="popularresponse"></a>
## PopularResponse
Schema: `{<br>  "properties" : {<br>    "count" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "rank" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "term" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `count` | no | `integer (int64)` | — | — |
| `rank` | no | `integer (int32)` | — | — |
| `term` | no | `string` | — | — |

<a id="postchangesresponse"></a>
## PostChangesResponse
Schema: `{<br>  "properties" : {<br>    "latestId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "newCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `latestId` | no | `integer (int64)` | — | — |
| `newCount` | no | `integer (int32)` | — | — |

<a id="postdetailres"></a>
## PostDetailRes
Schema: `{<br>  "properties" : {<br>    "author" : {<br>      "type" : "string"<br>    },<br>    "authorHandle" : {<br>      "type" : "string"<br>    },<br>    "authorProfileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "bookmarkCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "comments" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "content" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "imageUrls" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "isBookmarked" : {<br>      "type" : "boolean"<br>    },<br>    "isOwner" : {<br>      "type" : "boolean"<br>    },<br>    "likedByMe" : {<br>      "type" : "boolean"<br>    },<br>    "likes" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "linkedContent" : {<br>      "$ref" : "#/components/schemas/LinkedContentRes"<br>    },<br>    "originalDeleted" : {<br>      "type" : "boolean"<br>    },<br>    "originalPost" : {<br>      "$ref" : "#/components/schemas/EmbeddedPostDto"<br>    },<br>    "postType" : {<br>      "type" : "string"<br>    },<br>    "repostCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "repostOfId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "repostType" : {<br>      "type" : "string"<br>    },<br>    "repostedByMe" : {<br>      "type" : "boolean"<br>    },<br>    "shareMode" : {<br>      "type" : "string"<br>    },<br>    "sourceInfo" : {<br>      "$ref" : "#/components/schemas/SourceInfoRes"<br>    },<br>    "teamColor" : {<br>      "type" : "string"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    },<br>    "teamName" : {<br>      "type" : "string"<br>    },<br>    "teamShortName" : {<br>      "type" : "string"<br>    },<br>    "views" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `author` | no | `string` | — | — |
| `authorHandle` | no | `string` | — | — |
| `authorProfileImageUrl` | no | `string` | — | — |
| `bookmarkCount` | no | `integer (int32)` | — | — |
| `comments` | no | `integer (int32)` | — | — |
| `content` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `imageUrls` | no | `array<string>` | — | — |
| `isBookmarked` | no | `boolean` | — | — |
| `isOwner` | no | `boolean` | — | — |
| `likedByMe` | no | `boolean` | — | — |
| `likes` | no | `integer (int32)` | — | — |
| `linkedContent` | no | [LinkedContentRes](openapi-schemas.md#linkedcontentres) | — | — |
| `originalDeleted` | no | `boolean` | — | — |
| `originalPost` | no | [EmbeddedPostDto](openapi-schemas.md#embeddedpostdto) | — | — |
| `postType` | no | `string` | — | — |
| `repostCount` | no | `integer (int32)` | — | — |
| `repostOfId` | no | `integer (int64)` | — | — |
| `repostType` | no | `string` | — | — |
| `repostedByMe` | no | `boolean` | — | — |
| `shareMode` | no | `string` | — | — |
| `sourceInfo` | no | [SourceInfoRes](openapi-schemas.md#sourceinfores) | — | — |
| `teamColor` | no | `string` | — | — |
| `teamId` | no | `string` | — | — |
| `teamName` | no | `string` | — | — |
| `teamShortName` | no | `string` | — | — |
| `views` | no | `integer (int32)` | — | — |

<a id="postimagedto"></a>
## PostImageDto
Schema: `{<br>  "properties" : {<br>    "bytes" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "isThumbnail" : {<br>      "type" : "boolean"<br>    },<br>    "mimeType" : {<br>      "type" : "string"<br>    },<br>    "storagePath" : {<br>      "type" : "string"<br>    },<br>    "url" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `bytes` | no | `integer (int64)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `isThumbnail` | no | `boolean` | — | — |
| `mimeType` | no | `string` | — | — |
| `storagePath` | no | `string` | — | — |
| `url` | no | `string` | — | — |

<a id="postseasonresultdto"></a>
## PostSeasonResultDto
Schema: `{<br>  "properties" : {<br>    "detail" : {<br>      "type" : "string"<br>    },<br>    "result" : {<br>      "type" : "string"<br>    },<br>    "title" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `detail` | no | `string` | — | — |
| `result` | no | `string` | — | — |
| `title` | no | `string` | — | — |

<a id="postsummaryres"></a>
## PostSummaryRes
Schema: `{<br>  "properties" : {<br>    "author" : {<br>      "type" : "string"<br>    },<br>    "authorHandle" : {<br>      "type" : "string"<br>    },<br>    "authorProfileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "authorTeamId" : {<br>      "type" : "string"<br>    },<br>    "bookmarkCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "comments" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "content" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "imageUrls" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "isBookmarked" : {<br>      "type" : "boolean"<br>    },<br>    "isHot" : {<br>      "type" : "boolean"<br>    },<br>    "isOwner" : {<br>      "type" : "boolean"<br>    },<br>    "liked" : {<br>      "type" : "boolean"<br>    },<br>    "likes" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "linkedContent" : {<br>      "$ref" : "#/components/schemas/LinkedContentRes"<br>    },<br>    "originalDeleted" : {<br>      "type" : "boolean"<br>    },<br>    "originalPost" : {<br>      "$ref" : "#/components/schemas/EmbeddedPostDto"<br>    },<br>    "postType" : {<br>      "type" : "string"<br>    },<br>    "repostCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "repostOfId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "repostType" : {<br>      "type" : "string"<br>    },<br>    "repostedByMe" : {<br>      "type" : "boolean"<br>    },<br>    "shareMode" : {<br>      "type" : "string"<br>    },<br>    "sourceInfo" : {<br>      "$ref" : "#/components/schemas/SourceInfoRes"<br>    },<br>    "teamColor" : {<br>      "type" : "string"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    },<br>    "teamName" : {<br>      "type" : "string"<br>    },<br>    "teamShortName" : {<br>      "type" : "string"<br>    },<br>    "views" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `author` | no | `string` | — | — |
| `authorHandle` | no | `string` | — | — |
| `authorProfileImageUrl` | no | `string` | — | — |
| `authorTeamId` | no | `string` | — | — |
| `bookmarkCount` | no | `integer (int32)` | — | — |
| `comments` | no | `integer (int32)` | — | — |
| `content` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `imageUrls` | no | `array<string>` | — | — |
| `isBookmarked` | no | `boolean` | — | — |
| `isHot` | no | `boolean` | — | — |
| `isOwner` | no | `boolean` | — | — |
| `liked` | no | `boolean` | — | — |
| `likes` | no | `integer (int32)` | — | — |
| `linkedContent` | no | [LinkedContentRes](openapi-schemas.md#linkedcontentres) | — | — |
| `originalDeleted` | no | `boolean` | — | — |
| `originalPost` | no | [EmbeddedPostDto](openapi-schemas.md#embeddedpostdto) | — | — |
| `postType` | no | `string` | — | — |
| `repostCount` | no | `integer (int32)` | — | — |
| `repostOfId` | no | `integer (int64)` | — | — |
| `repostType` | no | `string` | — | — |
| `repostedByMe` | no | `boolean` | — | — |
| `shareMode` | no | `string` | — | — |
| `sourceInfo` | no | [SourceInfoRes](openapi-schemas.md#sourceinfores) | — | — |
| `teamColor` | no | `string` | — | — |
| `teamId` | no | `string` | — | — |
| `teamName` | no | `string` | — | — |
| `teamShortName` | no | `string` | — | — |
| `views` | no | `integer (int32)` | — | — |

<a id="powerupinventorydto"></a>
## PowerupInventoryDto
Schema: `{<br>  "properties" : {<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "icon" : {<br>      "type" : "string"<br>    },<br>    "multiplier" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "name" : {<br>      "type" : "string"<br>    },<br>    "quantity" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "type" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `description` | no | `string` | — | — |
| `icon` | no | `string` | — | — |
| `multiplier` | no | `number (double)` | — | — |
| `name` | no | `string` | — | — |
| `quantity` | no | `integer (int32)` | — | — |
| `type` | no | `string` | — | — |

<a id="powerupuseresultdto"></a>
## PowerupUseResultDto
Schema: `{<br>  "properties" : {<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "remainingCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `message` | no | `string` | — | — |
| `remainingCount` | no | `integer (int32)` | — | — |
| `success` | no | `boolean` | — | — |

<a id="predictionbootstraperrordto"></a>
## PredictionBootstrapErrorDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    }<br>  },<br>  "required" : [ "code", "message", "status" ],<br>  "type" : "object"<br>}`
Required properties: `code`, `message`, `status`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `message` | yes | `string` | — | — |
| `status` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |

<a id="predictionbootstrapresourcedtogamedetaildto"></a>
## PredictionBootstrapResourceDtoGameDetailDto
Schema: `{<br>  "properties" : {<br>    "data" : {<br>      "oneOf" : [ {<br>        "$ref" : "#/components/schemas/GameDetailDto"<br>      }, {<br>        "type" : "null"<br>      } ]<br>    },<br>    "error" : {<br>      "oneOf" : [ {<br>        "$ref" : "#/components/schemas/PredictionBootstrapErrorDto"<br>      }, {<br>        "type" : "null"<br>      } ]<br>    },<br>    "ok" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "required" : [ "data", "error", "ok" ],<br>  "type" : "object"<br>}`
Required properties: `data`, `error`, `ok`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `data` | yes | `composition` | — | — |
| `error` | yes | `composition` | — | — |
| `ok` | yes | `boolean` | — | — |

#### Property composition: `data`
Includes: `oneOf`
```json
{
  "oneOf" : [ {
    "$ref" : "#/components/schemas/GameDetailDto"
  }, {
    "type" : "null"
  } ]
}
```

#### Property composition: `error`
Includes: `oneOf`
```json
{
  "oneOf" : [ {
    "$ref" : "#/components/schemas/PredictionBootstrapErrorDto"
  }, {
    "type" : "null"
  } ]
}
```

<a id="predictionbootstrapresourcedtopredictionresponsedto"></a>
## PredictionBootstrapResourceDtoPredictionResponseDto
Schema: `{<br>  "properties" : {<br>    "data" : {<br>      "oneOf" : [ {<br>        "$ref" : "#/components/schemas/PredictionResponseDto"<br>      }, {<br>        "type" : "null"<br>      } ]<br>    },<br>    "error" : {<br>      "oneOf" : [ {<br>        "$ref" : "#/components/schemas/PredictionBootstrapErrorDto"<br>      }, {<br>        "type" : "null"<br>      } ]<br>    },<br>    "ok" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "required" : [ "data", "error", "ok" ],<br>  "type" : "object"<br>}`
Required properties: `data`, `error`, `ok`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `data` | yes | `composition` | — | — |
| `error` | yes | `composition` | — | — |
| `ok` | yes | `boolean` | — | — |

#### Property composition: `data`
Includes: `oneOf`
```json
{
  "oneOf" : [ {
    "$ref" : "#/components/schemas/PredictionResponseDto"
  }, {
    "type" : "null"
  } ]
}
```

#### Property composition: `error`
Includes: `oneOf`
```json
{
  "oneOf" : [ {
    "$ref" : "#/components/schemas/PredictionBootstrapErrorDto"
  }, {
    "type" : "null"
  } ]
}
```

<a id="predictionbootstrapresponsedto"></a>
## PredictionBootstrapResponseDto
Schema: `{<br>  "properties" : {<br>    "detail" : {<br>      "oneOf" : [ {<br>        "$ref" : "#/components/schemas/PredictionBootstrapResourceDtoGameDetailDto"<br>      }, {<br>        "type" : "null"<br>      } ]<br>    },<br>    "schedule" : {<br>      "$ref" : "#/components/schemas/MatchDayNavigationResponseDto"<br>    },<br>    "selectedGameFound" : {<br>      "type" : "boolean"<br>    },<br>    "selectedGameId" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "voteStatus" : {<br>      "oneOf" : [ {<br>        "$ref" : "#/components/schemas/PredictionBootstrapResourceDtoPredictionResponseDto"<br>      }, {<br>        "type" : "null"<br>      } ]<br>    }<br>  },<br>  "required" : [ "detail", "schedule", "selectedGameFound", "selectedGameId", "voteStatus" ],<br>  "type" : "object"<br>}`
Required properties: `detail`, `schedule`, `selectedGameFound`, `selectedGameId`, `voteStatus`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `detail` | yes | `composition` | — | — |
| `schedule` | yes | [MatchDayNavigationResponseDto](openapi-schemas.md#matchdaynavigationresponsedto) | — | — |
| `selectedGameFound` | yes | `boolean` | — | — |
| `selectedGameId` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `voteStatus` | yes | `composition` | — | — |

#### Property composition: `detail`
Includes: `oneOf`
```json
{
  "oneOf" : [ {
    "$ref" : "#/components/schemas/PredictionBootstrapResourceDtoGameDetailDto"
  }, {
    "type" : "null"
  } ]
}
```

#### Property composition: `voteStatus`
Includes: `oneOf`
```json
{
  "oneOf" : [ {
    "$ref" : "#/components/schemas/PredictionBootstrapResourceDtoPredictionResponseDto"
  }, {
    "type" : "null"
  } ]
}
```

<a id="predictionmyvoteentrydto"></a>
## PredictionMyVoteEntryDto
Schema: `{<br>  "properties" : {<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "votedTeam" : {<br>      "oneOf" : [ {<br>        "enum" : [ "home", "away" ],<br>        "type" : "string"<br>      }, {<br>        "type" : "null"<br>      } ]<br>    }<br>  },<br>  "required" : [ "gameId", "votedTeam" ],<br>  "type" : "object"<br>}`
Required properties: `gameId`, `votedTeam`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `gameId` | yes | `string` | — | — |
| `votedTeam` | yes | `composition` | — | — |

#### Property composition: `votedTeam`
Includes: `oneOf`
```json
{
  "oneOf" : [ {
    "enum" : [ "home", "away" ],
    "type" : "string"
  }, {
    "type" : "null"
  } ]
}
```

<a id="predictionmyvotesrequestdto"></a>
## PredictionMyVotesRequestDto
Schema: `{<br>  "properties" : {<br>    "gameIds" : {<br>      "items" : {<br>        "minLength" : 1,<br>        "pattern" : "^[A-Za-z0-9_-]+$",<br>        "type" : "string"<br>      },<br>      "maxItems" : 250,<br>      "minItems" : 0,<br>      "type" : "array"<br>    }<br>  },<br>  "required" : [ "gameIds" ],<br>  "type" : "object"<br>}`
Required properties: `gameIds`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `gameIds` | yes | `array<string>` | — | minItems=0, maxItems=250 |

<a id="predictionmyvotesresponsedto"></a>
## PredictionMyVotesResponseDto
Schema: `{<br>  "properties" : {<br>    "entries" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/PredictionMyVoteEntryDto"<br>      },<br>      "type" : "array"<br>    },<br>    "votes" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    }<br>  },<br>  "required" : [ "entries", "votes" ],<br>  "type" : "object"<br>}`
Required properties: `entries`, `votes`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `entries` | yes | `array<[PredictionMyVoteEntryDto](openapi-schemas.md#predictionmyvoteentrydto)>` | — | — |
| `votes` | yes | `composition` | — | — |

#### Property composition: `votes`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="predictionrequestdto"></a>
## PredictionRequestDto
Schema: `{<br>  "properties" : {<br>    "gameId" : {<br>      "minLength" : 1,<br>      "pattern" : "^[A-Za-z0-9_-]+$",<br>      "type" : "string"<br>    },<br>    "votedTeam" : {<br>      "pattern" : "(?i)^(home\|away)$",<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "gameId" ],<br>  "type" : "object"<br>}`
Required properties: `gameId`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `gameId` | yes | `string` | — | minLength=1, pattern=`^[A-Za-z0-9_-]+$` |
| `votedTeam` | no | `string` | — | pattern=`(?i)^(home\|away)$` |

<a id="predictionresponsedto"></a>
## PredictionResponseDto
Schema: `{<br>  "properties" : {<br>    "awayPercentage" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "awayVotes" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "gameId" : {<br>      "type" : "string"<br>    },<br>    "homePercentage" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "homeVotes" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "totalVotes" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "required" : [ "awayPercentage", "awayVotes", "gameId", "homePercentage", "homeVotes", "totalVotes" ],<br>  "type" : "object"<br>}`
Required properties: `awayPercentage`, `awayVotes`, `gameId`, `homePercentage`, `homeVotes`, `totalVotes`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayPercentage` | yes | `integer (int32)` | — | — |
| `awayVotes` | yes | `integer (int64)` | — | — |
| `gameId` | yes | `string` | — | — |
| `homePercentage` | yes | `integer (int32)` | — | — |
| `homeVotes` | yes | `integer (int64)` | — | — |
| `totalVotes` | yes | `integer (int64)` | — | — |

<a id="predictionstatsresponsedto"></a>
## PredictionStatsResponseDto
Schema: `{<br>  "properties" : {<br>    "code" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "data" : {<br>      "oneOf" : [ {<br>        "$ref" : "#/components/schemas/UserPredictionStatsDto"<br>      }, {<br>        "type" : "null"<br>      } ]<br>    },<br>    "errors" : {<br>      "oneOf" : [ {<br>        "additionalProperties" : {<br>          "type" : "string"<br>        },<br>        "type" : "object"<br>      }, {<br>        "type" : "null"<br>      } ]<br>    },<br>    "message" : {<br>      "type" : "string"<br>    },<br>    "success" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "required" : [ "code", "data", "errors", "message", "success" ],<br>  "type" : "object"<br>}`
Required properties: `code`, `data`, `errors`, `message`, `success`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `code` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `data` | yes | `composition` | — | — |
| `errors` | yes | `composition` | — | — |
| `message` | yes | `string` | — | — |
| `success` | yes | `boolean` | — | — |

#### Property composition: `data`
Includes: `oneOf`
```json
{
  "oneOf" : [ {
    "$ref" : "#/components/schemas/UserPredictionStatsDto"
  }, {
    "type" : "null"
  } ]
}
```

#### Property composition: `errors`
Includes: `oneOf`
```json
{
  "oneOf" : [ {
    "additionalProperties" : {
      "type" : "string"
    },
    "type" : "object"
  }, {
    "type" : "null"
  } ]
}
```

<a id="profileimagedto"></a>
## ProfileImageDto
Schema: `{<br>  "properties" : {<br>    "bytes" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "mimeType" : {<br>      "type" : "string"<br>    },<br>    "publicUrl" : {<br>      "type" : "string"<br>    },<br>    "storagePath" : {<br>      "type" : "string"<br>    },<br>    "userId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `bytes` | no | `integer (int64)` | — | — |
| `mimeType` | no | `string` | — | — |
| `publicUrl` | no | `string` | — | — |
| `storagePath` | no | `string` | — | — |
| `userId` | no | `integer (int64)` | — | — |

<a id="publicuserprofiledto"></a>
## PublicUserProfileDto
Schema: `{<br>  "properties" : {<br>    "bio" : {<br>      "type" : "string"<br>    },<br>    "cheerPoints" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "favoriteTeam" : {<br>      "type" : "string"<br>    },<br>    "handle" : {<br>      "type" : "string"<br>    },<br>    "name" : {<br>      "type" : "string"<br>    },<br>    "profileImageUrl" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `bio` | no | `string` | — | — |
| `cheerPoints` | no | `integer (int32)` | — | — |
| `favoriteTeam` | no | `string` | — | — |
| `handle` | no | `string` | — | — |
| `name` | no | `string` | — | — |
| `profileImageUrl` | no | `string` | — | — |

<a id="quoterepostreq"></a>
## QuoteRepostReq
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "maxLength" : 500,<br>      "minLength" : 0,<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "content" ],<br>  "type" : "object"<br>}`
Required properties: `content`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | yes | `string` | — | minLength=0, maxLength=500 |

<a id="rankingpredictioncurrentseasondto"></a>
## RankingPredictionCurrentSeasonDto
Schema: `{<br>  "properties" : {<br>    "seasonYear" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "required" : [ "seasonYear" ],<br>  "type" : "object"<br>}`
Required properties: `seasonYear`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `seasonYear` | yes | `integer (int32)` | — | — |

<a id="rankingpredictioninitdto"></a>
## RankingPredictionInitDto
Schema: `{<br>  "properties" : {<br>    "saved" : {<br>      "oneOf" : [ {<br>        "$ref" : "#/components/schemas/RankingPredictionResponseDto"<br>      }, {<br>        "type" : "null"<br>      } ]<br>    },<br>    "seasonYear" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "required" : [ "saved", "seasonYear" ],<br>  "type" : "object"<br>}`
Required properties: `saved`, `seasonYear`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `saved` | yes | `composition` | — | — |
| `seasonYear` | yes | `integer (int32)` | — | — |

#### Property composition: `saved`
Includes: `oneOf`
```json
{
  "oneOf" : [ {
    "$ref" : "#/components/schemas/RankingPredictionResponseDto"
  }, {
    "type" : "null"
  } ]
}
```

<a id="rankingpredictionrequestdto"></a>
## RankingPredictionRequestDto
Schema: `{<br>  "properties" : {<br>    "seasonYear" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "teamIdsInOrder" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "required" : [ "seasonYear" ],<br>  "type" : "object"<br>}`
Required properties: `seasonYear`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `seasonYear` | yes | `integer (int32)` | — | — |
| `teamIdsInOrder` | no | `array<string>` | — | — |

<a id="rankingpredictionresponsedto"></a>
## RankingPredictionResponseDto
Schema: `{<br>  "properties" : {<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "exactMatchCount" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "seasonYear" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "settledAt" : {<br>      "format" : "date-time",<br>      "type" : [ "string", "null" ]<br>    },<br>    "shareId" : {<br>      "type" : [ "string", "null" ]<br>    },<br>    "teamDetails" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/TeamRankingDetail"<br>      },<br>      "type" : "array"<br>    },<br>    "teamIdsInOrder" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "required" : [ "createdAt", "exactMatchCount", "id", "seasonYear", "settledAt", "shareId", "teamDetails", "teamIdsInOrder" ],<br>  "type" : "object"<br>}`
Required properties: `createdAt`, `exactMatchCount`, `id`, `seasonYear`, `settledAt`, `shareId`, `teamDetails`, `teamIdsInOrder`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `createdAt` | yes | `string (date-time)` | — | — |
| `exactMatchCount` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `id` | yes | `integer (int64)` | — | — |
| `seasonYear` | yes | `integer (int32)` | — | — |
| `settledAt` | yes | `{<br>  "format" : "date-time",<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `shareId` | yes | `{<br>  "type" : [ "string", "null" ]<br>}` | — | — |
| `teamDetails` | yes | `array<[TeamRankingDetail](openapi-schemas.md#teamrankingdetail)>` | — | — |
| `teamIdsInOrder` | yes | `array<string>` | — | — |

<a id="recentscoredto"></a>
## RecentScoreDto
Schema: `{<br>  "properties" : {<br>    "baseScore" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "eventType" : {<br>      "type" : "string"<br>    },<br>    "eventTypeKo" : {<br>      "type" : "string"<br>    },<br>    "handle" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "multiplier" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "profileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "score" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "streak" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "timestamp" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "userName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `baseScore` | no | `integer (int32)` | — | — |
| `description` | no | `string` | — | — |
| `eventType` | no | `string` | — | — |
| `eventTypeKo` | no | `string` | — | — |
| `handle` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `multiplier` | no | `number (double)` | — | — |
| `profileImageUrl` | no | `string` | — | — |
| `score` | no | `integer (int32)` | — | — |
| `streak` | no | `integer (int32)` | — | — |
| `timestamp` | no | `string (date-time)` | — | — |
| `userName` | no | `string` | — | — |

<a id="recordrequest"></a>
## RecordRequest
Schema: `{<br>  "properties" : {<br>    "term" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `term` | no | `string` | — | — |

<a id="recruitmentlinkedcontentres"></a>
## RecruitmentLinkedContentRes
Schema: `{<br>  "properties" : {<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "currentParticipants" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "gameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "gameTime" : {<br>      "example" : "18:30:00",<br>      "format" : "time",<br>      "type" : "string"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "maxParticipants" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "partyId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "price" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "recruiting" : {<br>      "type" : "boolean"<br>    },<br>    "reservationDepositAmount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "status" : {<br>      "type" : "string"<br>    },<br>    "ticketPrice" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayTeam` | no | `string` | — | — |
| `currentParticipants` | no | `integer (int32)` | — | — |
| `description` | no | `string` | — | — |
| `gameDate` | no | `string (date)` | — | — |
| `gameTime` | no | `string (time)` | — | — |
| `homeTeam` | no | `string` | — | — |
| `maxParticipants` | no | `integer (int32)` | — | — |
| `partyId` | no | `integer (int64)` | — | — |
| `price` | no | `integer (int32)` | — | — |
| `recruiting` | no | `boolean` | — | — |
| `reservationDepositAmount` | no | `integer (int32)` | — | — |
| `section` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `status` | no | `string` | — | — |
| `ticketPrice` | no | `integer (int32)` | — | — |

#### Property metadata: `gameTime`
- Example: `"18:30:00"`

<a id="reportcaseres"></a>
## ReportCaseRes
Schema: `{<br>  "properties" : {<br>    "adminMessage" : {<br>      "type" : "string"<br>    },<br>    "caseId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "handledAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "nextAction" : {<br>      "type" : "string"<br>    },<br>    "reportStatus" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `adminMessage` | no | `string` | — | — |
| `caseId` | no | `integer (int64)` | — | — |
| `handledAt` | no | `string (date-time)` | — | — |
| `nextAction` | no | `string` | — | — |
| `reportStatus` | no | `string` | — | — |

<a id="reportrequest"></a>
## ReportRequest
Schema: `{<br>  "properties" : {<br>    "description" : {<br>      "type" : "string"<br>    },<br>    "evidenceUrl" : {<br>      "type" : "string"<br>    },<br>    "hasRightEvidence" : {<br>      "type" : "boolean"<br>    },<br>    "license" : {<br>      "type" : "string"<br>    },<br>    "ownerContact" : {<br>      "type" : "string"<br>    },<br>    "reason" : {<br>      "enum" : [ "SPAM", "INAPPROPRIATE_CONTENT", "ABUSIVE_LANGUAGE", "ADVERTISEMENT", "COPYRIGHT_INFRINGEMENT", "FAKE_INFORMATION", "OTHER" ],<br>      "type" : "string"<br>    },<br>    "requestedAction" : {<br>      "type" : "string"<br>    },<br>    "requestedReason" : {<br>      "type" : "string"<br>    },<br>    "sourceUrl" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `description` | no | `string` | — | — |
| `evidenceUrl` | no | `string` | — | — |
| `hasRightEvidence` | no | `boolean` | — | — |
| `license` | no | `string` | — | — |
| `ownerContact` | no | `string` | — | — |
| `reason` | no | `string` | — | — |
| `requestedAction` | no | `string` | — | — |
| `requestedReason` | no | `string` | — | — |
| `sourceUrl` | no | `string` | — | — |

#### Property metadata: `reason`
- Enum: `SPAM`, `INAPPROPRIATE_CONTENT`, `ABUSIVE_LANGUAGE`, `ADVERTISEMENT`, `COPYRIGHT_INFRINGEMENT`, `FAKE_INFORMATION`, `OTHER`

<a id="reposttoggleresponse"></a>
## RepostToggleResponse
Schema: `{<br>  "properties" : {<br>    "count" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "reposted" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `count` | no | `integer (int32)` | — | — |
| `reposted` | no | `boolean` | — | — |

<a id="request"></a>
## Request
Schema: `{<br>  "properties" : {<br>    "clientMessageId" : {<br>      "maxLength" : 64,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "content" : {<br>      "maxLength" : 1000,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "roomId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "required" : [ "content", "roomId" ],<br>  "type" : "object"<br>}`
Required properties: `content`, `roomId`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `clientMessageId` | no | `string` | — | minLength=0, maxLength=64 |
| `content` | yes | `string` | — | minLength=0, maxLength=1000 |
| `roomId` | yes | `integer (int64)` | — | — |

<a id="response"></a>
## Response
Schema: `{<br>  "properties" : {<br>    "clientMessageId" : {<br>      "type" : "string"<br>    },<br>    "content" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "roomId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "senderId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `clientMessageId` | no | `string` | — | — |
| `content` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `roomId` | no | `integer (int64)` | — | — |
| `senderId` | no | `integer (int64)` | — | — |

<a id="rolechangerequestdto"></a>
## RoleChangeRequestDto
Schema: `{<br>  "properties" : {<br>    "reason" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `reason` | no | `string` | — | — |

<a id="rolechangeresponsedto"></a>
## RoleChangeResponseDto
Schema: `{<br>  "properties" : {<br>    "changedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "email" : {<br>      "type" : "string"<br>    },<br>    "name" : {<br>      "type" : "string"<br>    },<br>    "newRole" : {<br>      "type" : "string"<br>    },<br>    "previousRole" : {<br>      "type" : "string"<br>    },<br>    "userId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `changedAt` | no | `string (date-time)` | — | — |
| `email` | no | `string` | — | — |
| `name` | no | `string` | — | — |
| `newRole` | no | `string` | — | — |
| `previousRole` | no | `string` | — | — |
| `userId` | no | `integer (int64)` | — | — |

<a id="schedulenavigationdto"></a>
## ScheduleNavigationDto
Schema: `{<br>  "properties" : {<br>    "hasNext" : {<br>      "type" : "boolean"<br>    },<br>    "hasPrev" : {<br>      "type" : "boolean"<br>    },<br>    "nextGameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    },<br>    "prevGameDate" : {<br>      "format" : "date",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `hasNext` | no | `boolean` | — | — |
| `hasPrev` | no | `boolean` | — | — |
| `nextGameDate` | no | `string (date)` | — | — |
| `prevGameDate` | no | `string (date)` | — | — |

<a id="seatviewcandidatecreaterequest"></a>
## SeatViewCandidateCreateRequest
Schema: `{<br>  "properties" : {<br>    "sourceTypes" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "storagePaths" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `sourceTypes` | no | `array<string>` | — | — |
| `storagePaths` | no | `array<string>` | — | — |

<a id="seatviewphotodto"></a>
## SeatViewPhotoDto
Schema: `{<br>  "properties" : {<br>    "block" : {<br>      "type" : "string"<br>    },<br>    "diaryDate" : {<br>      "type" : "string"<br>    },<br>    "photoUrl" : {<br>      "type" : "string"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `block` | no | `string` | — | — |
| `diaryDate` | no | `string` | — | — |
| `photoUrl` | no | `string` | — | — |
| `section` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |

<a id="seatviewrewarddto"></a>
## SeatViewRewardDto
Schema: `{<br>  "properties" : {<br>    "firstContribution" : {<br>      "type" : "boolean"<br>    },<br>    "pointsEarned" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "totalContributions" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "unlockedAchievements" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/AchievementDto"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `firstContribution` | no | `boolean` | — | — |
| `pointsEarned` | no | `integer (int32)` | — | — |
| `totalContributions` | no | `integer (int64)` | — | — |
| `unlockedAchievements` | no | `array<[AchievementDto](openapi-schemas.md#achievementdto)>` | — | — |

<a id="seatviewselectionrequest"></a>
## SeatViewSelectionRequest
Schema: `{<br>  "properties" : {<br>    "candidateIds" : {<br>      "items" : {<br>        "format" : "int64",<br>        "type" : "integer"<br>      },<br>      "type" : "array"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `candidateIds` | no | `array<integer (int64)>` | — | — |

<a id="signedurldto"></a>
## SignedUrlDto
Schema: `{<br>  "properties" : {<br>    "expiresAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "url" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `expiresAt` | no | `string (date-time)` | — | — |
| `url` | no | `string` | — | — |

<a id="signupdto"></a>
## SignupDto
Schema: `{<br>  "properties" : {<br>    "confirmPassword" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "email" : {<br>      "format" : "email",<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "favoriteTeam" : {<br>      "type" : "string"<br>    },<br>    "handle" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "name" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "password" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "policyConsents" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/PolicyConsentItemDto"<br>      },<br>      "minItems" : 1,<br>      "type" : "array"<br>    },<br>    "provider" : {<br>      "type" : "string"<br>    },<br>    "providerId" : {<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "confirmPassword", "email", "handle", "name", "password", "policyConsents" ],<br>  "type" : "object"<br>}`
Required properties: `confirmPassword`, `email`, `handle`, `name`, `password`, `policyConsents`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `confirmPassword` | yes | `string` | — | minLength=1 |
| `email` | yes | `string (email)` | — | minLength=1 |
| `favoriteTeam` | no | `string` | — | — |
| `handle` | yes | `string` | — | minLength=1 |
| `name` | yes | `string` | — | minLength=1 |
| `password` | yes | `string` | — | minLength=1 |
| `policyConsents` | yes | `array<[PolicyConsentItemDto](openapi-schemas.md#policyconsentitemdto)>` | — | minItems=1 |
| `provider` | no | `string` | — | — |
| `providerId` | no | `string` | — | — |

<a id="sortobject"></a>
## SortObject
Schema: `{<br>  "properties" : {<br>    "empty" : {<br>      "type" : "boolean"<br>    },<br>    "sorted" : {<br>      "type" : "boolean"<br>    },<br>    "unsorted" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `empty` | no | `boolean` | — | — |
| `sorted` | no | `boolean` | — | — |
| `unsorted` | no | `boolean` | — | — |

<a id="sourceinfores"></a>
## SourceInfoRes
Schema: `{<br>  "properties" : {<br>    "author" : {<br>      "type" : "string"<br>    },<br>    "changedNote" : {<br>      "type" : "string"<br>    },<br>    "license" : {<br>      "type" : "string"<br>    },<br>    "licenseUrl" : {<br>      "type" : "string"<br>    },<br>    "snapshotType" : {<br>      "type" : "string"<br>    },<br>    "title" : {<br>      "type" : "string"<br>    },<br>    "url" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `author` | no | `string` | — | — |
| `changedNote` | no | `string` | — | — |
| `license` | no | `string` | — | — |
| `licenseUrl` | no | `string` | — | — |
| `snapshotType` | no | `string` | — | — |
| `title` | no | `string` | — | — |
| `url` | no | `string` | — | — |

<a id="stadiumdetaildto"></a>
## StadiumDetailDto
Schema: `{<br>  "properties" : {<br>    "address" : {<br>      "type" : "string"<br>    },<br>    "lat" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "lng" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "phone" : {<br>      "type" : "string"<br>    },<br>    "places" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/PlaceDto"<br>      },<br>      "type" : "array"<br>    },<br>    "stadiumId" : {<br>      "type" : "string"<br>    },<br>    "stadiumName" : {<br>      "type" : "string"<br>    },<br>    "team" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `address` | no | `string` | — | — |
| `lat` | no | `number (double)` | — | — |
| `lng` | no | `number (double)` | — | — |
| `phone` | no | `string` | — | — |
| `places` | no | `array<[PlaceDto](openapi-schemas.md#placedto)>` | — | — |
| `stadiumId` | no | `string` | — | — |
| `stadiumName` | no | `string` | — | — |
| `team` | no | `string` | — | — |

<a id="stadiumdto"></a>
## StadiumDto
Schema: `{<br>  "properties" : {<br>    "address" : {<br>      "type" : "string"<br>    },<br>    "lat" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "lng" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "phone" : {<br>      "type" : "string"<br>    },<br>    "stadiumId" : {<br>      "type" : "string"<br>    },<br>    "stadiumName" : {<br>      "type" : "string"<br>    },<br>    "team" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `address` | no | `string` | — | — |
| `lat` | no | `number (double)` | — | — |
| `lng` | no | `number (double)` | — | — |
| `phone` | no | `string` | — | — |
| `stadiumId` | no | `string` | — | — |
| `stadiumName` | no | `string` | — | — |
| `team` | no | `string` | — | — |

<a id="storedchatmessage"></a>
## StoredChatMessage
Schema: `{<br>  "properties" : {<br>    "cached" : {<br>      "type" : "boolean"<br>    },<br>    "cancelled" : {<br>      "type" : "boolean"<br>    },<br>    "citations" : {<br>      "$ref" : "#/components/schemas/JsonNode"<br>    },<br>    "content" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "errorCode" : {<br>      "type" : "string"<br>    },<br>    "fallbackReason" : {<br>      "type" : "string"<br>    },<br>    "favorite" : {<br>      "type" : "boolean"<br>    },<br>    "finishReason" : {<br>      "type" : "string"<br>    },<br>    "intent" : {<br>      "type" : "string"<br>    },<br>    "messageId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "metadata" : {<br>      "$ref" : "#/components/schemas/JsonNode"<br>    },<br>    "plannerCacheHit" : {<br>      "type" : "boolean"<br>    },<br>    "plannerMode" : {<br>      "type" : "string"<br>    },<br>    "role" : {<br>      "enum" : [ "USER", "ASSISTANT" ],<br>      "type" : "string"<br>    },<br>    "sessionId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "status" : {<br>      "enum" : [ "COMPLETED", "CANCELLED", "ERROR" ],<br>      "type" : "string"<br>    },<br>    "strategy" : {<br>      "type" : "string"<br>    },<br>    "toolCalls" : {<br>      "$ref" : "#/components/schemas/JsonNode"<br>    },<br>    "toolExecutionMode" : {<br>      "type" : "string"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "verified" : {<br>      "type" : "boolean"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `cached` | no | `boolean` | — | — |
| `cancelled` | no | `boolean` | — | — |
| `citations` | no | [JsonNode](openapi-schemas.md#jsonnode) | — | — |
| `content` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `errorCode` | no | `string` | — | — |
| `fallbackReason` | no | `string` | — | — |
| `favorite` | no | `boolean` | — | — |
| `finishReason` | no | `string` | — | — |
| `intent` | no | `string` | — | — |
| `messageId` | no | `integer (int64)` | — | — |
| `metadata` | no | [JsonNode](openapi-schemas.md#jsonnode) | — | — |
| `plannerCacheHit` | no | `boolean` | — | — |
| `plannerMode` | no | `string` | — | — |
| `role` | no | `string` | — | — |
| `sessionId` | no | `integer (int64)` | — | — |
| `status` | no | `string` | — | — |
| `strategy` | no | `string` | — | — |
| `toolCalls` | no | [JsonNode](openapi-schemas.md#jsonnode) | — | — |
| `toolExecutionMode` | no | `string` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |
| `verified` | no | `boolean` | — | — |

#### Property metadata: `role`
- Enum: `USER`, `ASSISTANT`

#### Property metadata: `status`
- Enum: `COMPLETED`, `CANCELLED`, `ERROR`

<a id="streamingresponsebody"></a>
## StreamingResponseBody
Schema: `{ }`

<a id="targetuser"></a>
## TargetUser
Schema: `{<br>  "properties" : {<br>    "favoriteTeam" : {<br>      "type" : "string"<br>    },<br>    "handle" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "name" : {<br>      "type" : "string"<br>    },<br>    "profileImageUrl" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `favoriteTeam` | no | `string` | — | — |
| `handle` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `name` | no | `string` | — | — |
| `profileImageUrl` | no | `string` | — | — |

<a id="teamentity"></a>
## TeamEntity
Schema: `{<br>  "properties" : {<br>    "activeKboTeam" : {<br>      "type" : "boolean"<br>    },<br>    "aliases" : {<br>      "type" : "string"<br>    },<br>    "city" : {<br>      "type" : "string"<br>    },<br>    "color" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "foundedYear" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "franchise" : {<br>      "$ref" : "#/components/schemas/TeamFranchiseEntity"<br>    },<br>    "franchiseId" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "isActive" : {<br>      "type" : "boolean"<br>    },<br>    "stadiumName" : {<br>      "type" : "string"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    },<br>    "teamName" : {<br>      "type" : "string"<br>    },<br>    "teamShortName" : {<br>      "type" : "string"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `activeKboTeam` | no | `boolean` | — | — |
| `aliases` | no | `string` | — | — |
| `city` | no | `string` | — | — |
| `color` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `foundedYear` | no | `integer (int32)` | — | — |
| `franchise` | no | [TeamFranchiseEntity](openapi-schemas.md#teamfranchiseentity) | — | — |
| `franchiseId` | no | `integer (int32)` | — | — |
| `isActive` | no | `boolean` | — | — |
| `stadiumName` | no | `string` | — | — |
| `teamId` | no | `string` | — | — |
| `teamName` | no | `string` | — | — |
| `teamShortName` | no | `string` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |

<a id="teamfranchiseentity"></a>
## TeamFranchiseEntity
Schema: `{<br>  "properties" : {<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "currentCode" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "metadataJson" : {<br>      "type" : "string"<br>    },<br>    "name" : {<br>      "type" : "string"<br>    },<br>    "originalCode" : {<br>      "type" : "string"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "webUrl" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `createdAt` | no | `string (date-time)` | — | — |
| `currentCode` | no | `string` | — | — |
| `id` | no | `integer (int32)` | — | — |
| `metadataJson` | no | `string` | — | — |
| `name` | no | `string` | — | — |
| `originalCode` | no | `string` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |
| `webUrl` | no | `string` | — | — |

<a id="teamhistoryentity"></a>
## TeamHistoryEntity
Schema: `{<br>  "properties" : {<br>    "city" : {<br>      "type" : "string"<br>    },<br>    "color" : {<br>      "type" : "string"<br>    },<br>    "createdAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    },<br>    "franchise" : {<br>      "$ref" : "#/components/schemas/TeamFranchiseEntity"<br>    },<br>    "franchiseId" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "id" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "logoUrl" : {<br>      "type" : "string"<br>    },<br>    "ranking" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "season" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "teamCode" : {<br>      "type" : "string"<br>    },<br>    "teamName" : {<br>      "type" : "string"<br>    },<br>    "updatedAt" : {<br>      "format" : "date-time",<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `city` | no | `string` | — | — |
| `color` | no | `string` | — | — |
| `createdAt` | no | `string (date-time)` | — | — |
| `franchise` | no | [TeamFranchiseEntity](openapi-schemas.md#teamfranchiseentity) | — | — |
| `franchiseId` | no | `integer (int32)` | — | — |
| `id` | no | `integer (int32)` | — | — |
| `logoUrl` | no | `string` | — | — |
| `ranking` | no | `integer (int32)` | — | — |
| `season` | no | `integer (int32)` | — | — |
| `stadium` | no | `string` | — | — |
| `teamCode` | no | `string` | — | — |
| `teamName` | no | `string` | — | — |
| `updatedAt` | no | `string (date-time)` | — | — |

<a id="teamrankingdetail"></a>
## TeamRankingDetail
Schema: `{<br>  "properties" : {<br>    "currentRank" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "lastSeasonRank" : {<br>      "format" : "int32",<br>      "type" : [ "integer", "null" ]<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    },<br>    "teamName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "currentRank", "lastSeasonRank", "teamId", "teamName" ],<br>  "type" : "object"<br>}`
Required properties: `currentRank`, `lastSeasonRank`, `teamId`, `teamName`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `currentRank` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `lastSeasonRank` | yes | `{<br>  "format" : "int32",<br>  "type" : [ "integer", "null" ]<br>}` | — | — |
| `teamId` | yes | `string` | — | — |
| `teamName` | yes | `string` | — | — |

<a id="teamresultdto"></a>
## TeamResultDto
Schema: `{<br>  "properties" : {<br>    "color" : {<br>      "type" : "string"<br>    },<br>    "name" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `color` | no | `string` | — | — |
| `name` | no | `string` | — | — |

<a id="teamsummarydto"></a>
## TeamSummaryDto
Schema: `{<br>  "properties" : {<br>    "isActive" : {<br>      "type" : "boolean"<br>    },<br>    "teamId" : {<br>      "type" : "string"<br>    },<br>    "teamName" : {<br>      "type" : "string"<br>    },<br>    "teamShortName" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `isActive` | no | `boolean` | — | — |
| `teamId` | no | `string` | — | — |
| `teamName` | no | `string` | — | — |
| `teamShortName` | no | `string` | — | — |

<a id="teamuseranswersdto"></a>
## TeamUserAnswersDto
Schema: `{<br>  "properties" : {<br>    "answers" : {<br>      "additionalProperties" : {<br>        "type" : "string"<br>      },<br>      "type" : "object"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `answers` | no | `composition` | — | — |

#### Property composition: `answers`
Includes: `additionalProperties`
```json
{
  "additionalProperties" : {
    "type" : "string"
  },
  "type" : "object"
}
```

<a id="ticketinfo"></a>
## TicketInfo
Schema: `{<br>  "properties" : {<br>    "awayTeam" : {<br>      "type" : "string"<br>    },<br>    "date" : {<br>      "type" : "string"<br>    },<br>    "gameId" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "homeTeam" : {<br>      "type" : "string"<br>    },<br>    "peopleCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "price" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "reservationNumber" : {<br>      "type" : "string"<br>    },<br>    "row" : {<br>      "type" : "string"<br>    },<br>    "seat" : {<br>      "type" : "string"<br>    },<br>    "section" : {<br>      "type" : "string"<br>    },<br>    "stadium" : {<br>      "type" : "string"<br>    },<br>    "time" : {<br>      "type" : "string"<br>    },<br>    "verificationToken" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `awayTeam` | no | `string` | — | — |
| `date` | no | `string` | — | — |
| `gameId` | no | `integer (int64)` | — | — |
| `homeTeam` | no | `string` | — | — |
| `peopleCount` | no | `integer (int32)` | — | — |
| `price` | no | `integer (int32)` | — | — |
| `reservationNumber` | no | `string` | — | — |
| `row` | no | `string` | — | — |
| `seat` | no | `string` | — | — |
| `section` | no | `string` | — | — |
| `stadium` | no | `string` | — | — |
| `time` | no | `string` | — | — |
| `verificationToken` | no | `string` | — | — |

<a id="trusteddevicedto"></a>
## TrustedDeviceDto
Schema: `{<br>  "properties" : {<br>    "browser" : {<br>      "type" : "string"<br>    },<br>    "deviceLabel" : {<br>      "type" : "string"<br>    },<br>    "deviceType" : {<br>      "type" : "string"<br>    },<br>    "firstSeenAt" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "lastIp" : {<br>      "type" : "string"<br>    },<br>    "lastLoginAt" : {<br>      "type" : "string"<br>    },<br>    "lastSeenAt" : {<br>      "type" : "string"<br>    },<br>    "os" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `browser` | no | `string` | — | — |
| `deviceLabel` | no | `string` | — | — |
| `deviceType` | no | `string` | — | — |
| `firstSeenAt` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `lastIp` | no | `string` | — | — |
| `lastLoginAt` | no | `string` | — | — |
| `lastSeenAt` | no | `string` | — | — |
| `os` | no | `string` | — | — |

<a id="updatepostreq"></a>
## UpdatePostReq
Schema: `{<br>  "properties" : {<br>    "content" : {<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "images" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "shareMode" : {<br>      "enum" : [ "INTERNAL_REPOST", "INTERNAL_QUOTE", "EXTERNAL_LINK", "EXTERNAL_COPY", "EXTERNAL_EMBED", "EXTERNAL_SUMMARY" ],<br>      "type" : "string"<br>    },<br>    "sourceAuthor" : {<br>      "type" : "string"<br>    },<br>    "sourceChangedNote" : {<br>      "type" : "string"<br>    },<br>    "sourceLicense" : {<br>      "type" : "string"<br>    },<br>    "sourceLicenseUrl" : {<br>      "type" : "string"<br>    },<br>    "sourceSnapshotType" : {<br>      "type" : "string"<br>    },<br>    "sourceTitle" : {<br>      "type" : "string"<br>    },<br>    "sourceUrl" : {<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "content" ],<br>  "type" : "object"<br>}`
Required properties: `content`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `content` | yes | `string` | — | minLength=1 |
| `images` | no | `array<string>` | — | — |
| `shareMode` | no | `string` | — | — |
| `sourceAuthor` | no | `string` | — | — |
| `sourceChangedNote` | no | `string` | — | — |
| `sourceLicense` | no | `string` | — | — |
| `sourceLicenseUrl` | no | `string` | — | — |
| `sourceSnapshotType` | no | `string` | — | — |
| `sourceTitle` | no | `string` | — | — |
| `sourceUrl` | no | `string` | — | — |

#### Property metadata: `shareMode`
- Enum: `INTERNAL_REPOST`, `INTERNAL_QUOTE`, `EXTERNAL_LINK`, `EXTERNAL_COPY`, `EXTERNAL_EMBED`, `EXTERNAL_SUMMARY`

<a id="userfollowsummarydto"></a>
## UserFollowSummaryDto
Schema: `{<br>  "properties" : {<br>    "favoriteTeam" : {<br>      "type" : "string"<br>    },<br>    "handle" : {<br>      "type" : "string"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "isFollowedByMe" : {<br>      "type" : "boolean"<br>    },<br>    "name" : {<br>      "type" : "string"<br>    },<br>    "profileImageUrl" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `favoriteTeam` | no | `string` | — | — |
| `handle` | no | `string` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `isFollowedByMe` | no | `boolean` | — | — |
| `name` | no | `string` | — | — |
| `profileImageUrl` | no | `string` | — | — |

<a id="userpredictionstatsdto"></a>
## UserPredictionStatsDto
Schema: `{<br>  "properties" : {<br>    "accuracy" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "correctPredictions" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "streak" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "totalPredictions" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    }<br>  },<br>  "required" : [ "accuracy", "correctPredictions", "streak", "totalPredictions" ],<br>  "type" : "object"<br>}`
Required properties: `accuracy`, `correctPredictions`, `streak`, `totalPredictions`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `accuracy` | yes | `number (double)` | — | — |
| `correctPredictions` | yes | `integer (int32)` | — | — |
| `streak` | yes | `integer (int32)` | — | — |
| `totalPredictions` | yes | `integer (int32)` | — | — |

<a id="userprofiledto"></a>
## UserProfileDto
Schema: `{<br>  "properties" : {<br>    "bio" : {<br>      "maxLength" : 500,<br>      "minLength" : 0,<br>      "type" : "string"<br>    },<br>    "cheerPoints" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "createdAt" : {<br>      "type" : "string"<br>    },<br>    "email" : {<br>      "format" : "email",<br>      "minLength" : 1,<br>      "type" : "string"<br>    },<br>    "favoriteTeam" : {<br>      "type" : "string"<br>    },<br>    "handle" : {<br>      "type" : "string"<br>    },<br>    "hasPassword" : {<br>      "type" : "boolean"<br>    },<br>    "id" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "missingPolicyTypes" : {<br>      "items" : {<br>        "type" : "string"<br>      },<br>      "type" : "array"<br>    },<br>    "name" : {<br>      "maxLength" : 20,<br>      "minLength" : 2,<br>      "type" : "string"<br>    },<br>    "policyConsentEffectiveDate" : {<br>      "type" : "string"<br>    },<br>    "policyConsentHardGateDate" : {<br>      "type" : "string"<br>    },<br>    "policyConsentNoticeRequired" : {<br>      "type" : "boolean"<br>    },<br>    "policyConsentRequired" : {<br>      "type" : "boolean"<br>    },<br>    "profileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "provider" : {<br>      "type" : "string"<br>    },<br>    "providerId" : {<br>      "type" : "string"<br>    },<br>    "role" : {<br>      "type" : "string"<br>    }<br>  },<br>  "required" : [ "email", "name" ],<br>  "type" : "object"<br>}`
Required properties: `email`, `name`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `bio` | no | `string` | — | minLength=0, maxLength=500 |
| `cheerPoints` | no | `integer (int32)` | — | — |
| `createdAt` | no | `string` | — | — |
| `email` | yes | `string (email)` | — | minLength=1 |
| `favoriteTeam` | no | `string` | — | — |
| `handle` | no | `string` | — | — |
| `hasPassword` | no | `boolean` | — | — |
| `id` | no | `integer (int64)` | — | — |
| `missingPolicyTypes` | no | `array<string>` | — | — |
| `name` | yes | `string` | — | minLength=2, maxLength=20 |
| `policyConsentEffectiveDate` | no | `string` | — | — |
| `policyConsentHardGateDate` | no | `string` | — | — |
| `policyConsentNoticeRequired` | no | `boolean` | — | — |
| `policyConsentRequired` | no | `boolean` | — | — |
| `profileImageUrl` | no | `string` | — | — |
| `provider` | no | `string` | — | — |
| `providerId` | no | `string` | — | — |
| `role` | no | `string` | — | — |

<a id="userproviderdto"></a>
## UserProviderDto
Schema: `{<br>  "properties" : {<br>    "connectedAt" : {<br>      "type" : "string"<br>    },<br>    "email" : {<br>      "type" : "string"<br>    },<br>    "provider" : {<br>      "type" : "string"<br>    },<br>    "providerId" : {<br>      "type" : "string"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `connectedAt` | no | `string` | — | — |
| `email` | no | `string` | — | — |
| `provider` | no | `string` | — | — |
| `providerId` | no | `string` | — | — |

<a id="userrankdto"></a>
## UserRankDto
Schema: `{<br>  "properties" : {<br>    "level" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "rank" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "score" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `level` | no | `integer (int32)` | — | — |
| `rank` | no | `integer (int64)` | — | — |
| `score` | no | `integer (int64)` | — | — |

<a id="userstatsdto"></a>
## UserStatsDto
Schema: `{<br>  "properties" : {<br>    "accuracy" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "achievementCount" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "correctPredictions" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "currentStreak" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "experiencePoints" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "handle" : {<br>      "type" : "string"<br>    },<br>    "level" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "levelProgress" : {<br>      "format" : "double",<br>      "type" : "number"<br>    },<br>    "maxStreak" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "monthlyRank" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "monthlyScore" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "nextLevelExp" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "powerups" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/PowerupInventoryDto"<br>      },<br>      "type" : "array"<br>    },<br>    "profileImageUrl" : {<br>      "type" : "string"<br>    },<br>    "rank" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "rankTitle" : {<br>      "type" : "string"<br>    },<br>    "recentAchievements" : {<br>      "items" : {<br>        "$ref" : "#/components/schemas/AchievementDto"<br>      },<br>      "type" : "array"<br>    },<br>    "seasonRank" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "seasonScore" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "totalPredictions" : {<br>      "format" : "int32",<br>      "type" : "integer"<br>    },<br>    "totalRank" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "totalScore" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "userName" : {<br>      "type" : "string"<br>    },<br>    "weeklyRank" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    },<br>    "weeklyScore" : {<br>      "format" : "int64",<br>      "type" : "integer"<br>    }<br>  },<br>  "type" : "object"<br>}`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `accuracy` | no | `number (double)` | — | — |
| `achievementCount` | no | `integer (int32)` | — | — |
| `correctPredictions` | no | `integer (int32)` | — | — |
| `currentStreak` | no | `integer (int32)` | — | — |
| `experiencePoints` | no | `integer (int64)` | — | — |
| `handle` | no | `string` | — | — |
| `level` | no | `integer (int32)` | — | — |
| `levelProgress` | no | `number (double)` | — | — |
| `maxStreak` | no | `integer (int32)` | — | — |
| `monthlyRank` | no | `integer (int64)` | — | — |
| `monthlyScore` | no | `integer (int64)` | — | — |
| `nextLevelExp` | no | `integer (int64)` | — | — |
| `powerups` | no | `array<[PowerupInventoryDto](openapi-schemas.md#powerupinventorydto)>` | — | — |
| `profileImageUrl` | no | `string` | — | — |
| `rank` | no | `integer (int64)` | — | — |
| `rankTitle` | no | `string` | — | — |
| `recentAchievements` | no | `array<[AchievementDto](openapi-schemas.md#achievementdto)>` | — | — |
| `seasonRank` | no | `integer (int64)` | — | — |
| `seasonScore` | no | `integer (int64)` | — | — |
| `totalPredictions` | no | `integer (int32)` | — | — |
| `totalRank` | no | `integer (int64)` | — | — |
| `totalScore` | no | `integer (int64)` | — | — |
| `userName` | no | `string` | — | — |
| `weeklyRank` | no | `integer (int64)` | — | — |
| `weeklyScore` | no | `integer (int64)` | — | — |

<a id="winprobabilitydto"></a>
## WinProbabilityDto
Schema: `{<br>  "properties" : {<br>    "away" : {<br>      "format" : "double",<br>      "type" : [ "number", "null" ]<br>    },<br>    "home" : {<br>      "format" : "double",<br>      "type" : [ "number", "null" ]<br>    }<br>  },<br>  "required" : [ "away", "home" ],<br>  "type" : "object"<br>}`
Required properties: `away`, `home`

### Properties
| Property | Required | Schema | Description | Constraints |
| --- | --- | --- | --- | --- |
| `away` | yes | `{<br>  "format" : "double",<br>  "type" : [ "number", "null" ]<br>}` | — | — |
| `home` | yes | `{<br>  "format" : "double",<br>  "type" : [ "number", "null" ]<br>}` | — | — |
