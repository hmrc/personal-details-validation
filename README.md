# personal-details-validation

Manages personal details validation.

### Endpoints

| Method | Path                                                   | Description                                              |
|--------|--------------------------------------------------------|----------------------------------------------------------|
| POST   | ```/personal-details-validation[?origin=<origin>]```   | Performs validation of the given Personal details.       |
| GET    | ```/personal-details-validation/:validationId```       | Returns validation results for the given `validationId`. |
| POST   | ```/personal-details-validation/retrieve-by-session``` | Returns validation results for the given session data.   |

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

| Status |Description|
|--------|-----------|
| 200    | Regardless of validation results. Response contains `Location` header pointing to an endpoint to retrieve the results.|
| 400    | When the given payload is invalid.|

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

Returns validation results for the given `validationId`.

**Response**

| Status  |Description|
|---------|-----------|
| 200     | When validation data exists.|
| 404     | When there is no validation results for the given `validationId`.|

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
| 400    | Invalid request                                                 

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

## Test Repositories

The personal details validation service is tested by the [personal details validation acceptance tests](https://github.com/hmrc/personal-details-validation-acceptance-tests). If any changes are made to this service please run those tests before raising a PR. Information on how to run the tests are located in the respective repository readme.
