

# personal-details-validation

Manages personal details validation.

### Endpoints

| Method | Path                                                   | Description                                                                                                                                                              |
|--------|--------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| POST   | ```/personal-details-validation[?origin=<origin>]```   | Performs validation of the given Personal details.                                                                                                                       |
| GET    | ```/personal-details-validation/:validationId```       | [internal] Returns validation results for the given `validationId`. (For `identity-verification-frontend` and `personal-details-validation-frontend` microservices only) |
| POST   | ```/personal-details-validation/retrieve-by-session``` | Returns validation results for the given session data.                                                                                                                   |

#### POST /personal-details-validation

Validates the given Personal details data and returns a url to a GET endpoint to retrieve the validation results.

**Request format**

There are two possible formats that can be used: -

```
{
   "firstName": "Jim",
   "lastName": "Ferguson",
   "nino": "AA000003D",
   "dateOfBirth": "1948-04-23"
}
```

or

```
{
   "firstName": "Jim",
   "lastName": "Ferguson",
   "postCode": "SE1 9NT",
   "dateOfBirth": "1948-04-23"
}
```

**Response**

| Status | Description                                                                                                                                                            |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 200    | Regardless of validation results. Response contains `Location` header pointing to an endpoint to retrieve the results.                                                 |
| 200    | Validation precondition failure, birth date is less than 15 years 9 months prior. Response contains `Location` header pointing to an endpoint to retrieve the results. |
| 400    | When the given payload is invalid.                                                                                                                                     |

Examples of OK response body:
* Successful validation
```
{
  "id": "502f90f7-13ab-44c4-a4fa-474da0f0fe03",
  "validationStatus": "success",
  "personalDetails": {
    "firstName": "Jim",
    "lastName": "Ferguson",
    "nino": "AA000003D",
    "dateOfBirth": "1948-04-23"
  }
}
```

* Failed validation
```
{
  "id": "502f90f7-13ab-44c4-a4fa-474da0f0fe03",
  "validationStatus": "failure"
}
```

Example of BAD REQUEST response:
 ```
{
  "errors": [
    "firstName is missing",
    "firstName is blank/empty",
    "lastName is missing",
    "lastName is blank/empty",
    "dateOfBirth is missing/invalid",
    "dateOfBirth is missing/invalid. Reasons: error.expected.date.isoformat",
    "invalid nino format",
    "at least nino or postcode needs to be supplied",
    "both nino and postcode supplied"
  ]
}
```

#### GET /personal-details-validation/:validationId
#### For `identity-verification-frontend` and `personal-details-validation-frontend` microservices only
Returns validation results for the given `validationId`.

**Response**

| Status | Description                                                       |
|--------|-------------------------------------------------------------------|
| 200    | When validation data exists.                                      |
| 404    | When there is no validation results for the given `validationId`. |

Examples of OK responses:
* Successful validation
```
{
  "id": "502f90f7-13ab-44c4-a4fa-474da0f0fe03",
  "validationStatus": "success",
  "personalDetails": {
    "firstName": "Jim",
    "lastName": "Ferguson",
    "nino": "AA000003D",
    "dateOfBirth": "1948-04-23",
    "postCode" : "postcode"
  }
}
```

* Failed validation
```
{
  "id": "502f90f7-13ab-44c4-a4fa-474da0f0fe03",
  "validationStatus": "failure"
}
```
#### POST /personal-details-validation/retrieve-by-session
**Request**

```
{
    "credentialId" : "providerIdInAuth",
    "sessionId"    : "users-session-id"
}
```
**Response**

| Status | Description                                                     |
|--------|-----------------------------------------------------------------|
| 200    | When validation data exists.                                    |
| 404    | When there is no validation results for the given request body. |
| 400    | Invalid request                                                 |

Examples of OK responses:
* Successful validation
```
{
  "id": "502f90f7-13ab-44c4-a4fa-474da0f0fe03",
  "validationStatus": "success",
  "personalDetails": {
    "firstName": "Jim",
    "lastName": "Ferguson",
    "nino": "AA000003D",
    "dateOfBirth": "1948-04-23",
    "postCode": "postcode"
  }
}
```

* Failed validation
```
{
  "id": "502f90f7-13ab-44c4-a4fa-474da0f0fe03",
  "validationStatus": "failure"
}
```
Examples of NOT FOUND responses:
```
{
    "error" : "No association found"
}
```
```
{
    "error" : "No record found using validation ID 12345"
}
```

## Test-only endpoints

The service provides test-only endpoints for setting up manual test scenarios without having to drive the full journey repeatedly (e.g. to test retry lockouts). These endpoints are **not available in production** - they are only active when the service is started with the test router:

```
sbt run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes
```

### Retry lockout testing

Since the introduction of the NINO retry lock, PDV tracks failed validation attempts in two places:

* **CredID state** (`personal-details-validation-retry-store`) - the source of the attempt number, which selects which failure/lockout page the user sees (attempt 1: first failure page, attempt 2: final-attempt page, attempt 3: lockout).
* **NINO state** (`personal-details-validation-nino-lock-store`) - once a NINO has 3 failed direct validations it is locked for 24 hours, blocking further validations of that NINO across all CredIDs.

The endpoints below let you seed or reset this state directly.

**Set CredID attempts**

```
curl -X POST http://localhost:9967/test-only/cred-id-attempts \
  -H 'Content-Type: application/json' \
  -d '{"credId": "<credential-id>", "attempts": 2}'
```

* `attempts: 2` - the next failed validation is the user's final attempt (final-attempt page, then lockout on the failure after that)
* `attempts: 3` - the user is locked out at journey entry

Returns `201 Created`.

**Reset CredID attempts**

```
curl -X POST http://localhost:9967/test-only/cred-id-attempts/delete \
  -H 'Content-Type: application/json' \
  -d '{"credId": "<credential-id>"}'
```

Returns `204 No Content`.

**Set NINO failure state / lock the NINO**

```
curl -X POST http://localhost:9967/test-only/nino-lock \
  -H 'Content-Type: application/json' \
  -d '{"nino": "AA000003D", "failedAttempts": 3, "locked": true}'
```

* `locked: true` locks the NINO for the configured lock duration (24 hours by default)
* `locked` defaults to `false`, so you can also set partial failure counts (e.g. `failedAttempts: 2` so one more failure locks the NINO)
* The NINO is normalised the same way as in production (case-insensitive, spaces stripped, suffix ignored), so any variant such as `aa 00 00 03 d` targets the same state

Returns `201 Created`.

**Reset NINO state**

```
curl -X POST http://localhost:9967/test-only/nino-lock/delete \
  -H 'Content-Type: application/json' \
  -d '{"nino": "AA000003D"}'
```

Returns `204 No Content`.

### Finding your CredID

The CredID endpoints require your actual Government Gateway credential ID. While signed in to the journey, this is visible in the response of the existing `GET /personal-details-validation/get-user-attempts` endpoint (called by the frontend, so it can be read from the browser network tab). For example:

```
{
  "attempts": 0,
  "maybeCredId": "<credential-id>"
}
```

### Inserting a successful validation (test data)

```
curl -X POST http://localhost:9967/test-only/validNino \
  -H 'Content-Type: application/json' \
  -d '{"validationId": "502f90f7-13ab-44c4-a4fa-474da0f0fe03", "nino": "AA000003D"}'
```

Returns `201 Created`.

## Test Repositories

The personal details validation service is tested by the [personal details validation acceptance tests](https://github.com/hmrc/personal-details-validation-acceptance-tests). If any changes are made to this service please run those tests before raising a PR. Information on how to run the tests are located in the respective repository readme.
