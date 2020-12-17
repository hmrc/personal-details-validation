# personal-details-validation

Manages personal details validation.

### Endpoints

| Method | Path                                             | Description                                              |
|--------|--------------------------------------------------|----------------------------------------------------------|
|  POST  | ```/personal-details-validation```               | Performs validation of the given Personal details.       |
|  GET   | ```/personal-details-validation/:validationId``` | Returns validation results for the given `validationId`. |

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

|Status     |Description|
|-----------|-----------|
|OK         | Regardless of validation results. Response contains `Location` header pointing to an endpoint to retrieve the results.|
|BAD REQUEST| When the given payload is invalid.|

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

|Status   |Description|
|---------|-----------|
|OK       | When validation data exists.|
|NOT FOUND| When there is no validation results for the given `validationId`.|

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


