# personal-details-validation

[![Build Status](https://travis-ci.org/hmrc/personal-details-validation.svg)](https://travis-ci.org/hmrc/personal-details-validation) [ ![Download](https://api.bintray.com/packages/hmrc/releases/personal-details-validation/images/download.svg) ](https://bintray.com/hmrc/releases/personal-details-validation/_latestVersion)

Manages personal details validation.

### Endpoints

| Method | Path                                             | Description                                              |
|--------|--------------------------------------------------|----------------------------------------------------------|
|  POST  | ```/personal-details-validation```               | Performs validation of the given Personal details.       |
|  GET   | ```/personal-details-validation/:validationId``` | Returns validation results for the given `validationId`. |

#### POST /personal-details-validation

Validates the given Personal details data and returns a url to a GET endpoint to retrieve the validation results.

**Request format**
```
{
   "firstName": "Jim",
   "lastName": "Ferguson",
   "nino": "AA000003D",
   "dateOfBirth": "1948-04-23"
}
```

**Response**

|Status     |Description|
|-----------|-----------|
|OK         | Regardless of validation results. Response contains `Location` header pointing to an endpoint to retrieve the results.|
|BAD REQUEST| When the given payload is invalid.|

Example of BAD REQUEST response:
 ```
{
  "errors": [
    "firstName is missing",
    "lastName is missing",
    "dateOfBirth is missing",
    "nino is missing"
  ]
}
```

#### GET /personal-details-validation/:validationId

Returns validation results for the given `validationId`.

**Response**

|Status   |Description|
|---------|-----------|
|OK       | When validation data exists.|
|NOT FOUND| When there is no validation results for the given `validationId`.|

Examples of OK responses:
* Successful validation
```
{
  "validationId": "502f90f7-13ab-44c4-a4fa-474da0f0fe03",
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
  "validationId": "502f90f7-13ab-44c4-a4fa-474da0f0fe03",
  "validationStatus": "failure"
}
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

