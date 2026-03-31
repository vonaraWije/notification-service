# Batch Email Service Documentation

## Overview

The Batch Email Service allows you to send emails to multiple recipients in one API request. Since Resend supports a maximum of 100 emails per batch call, the service automatically splits larger requests into chunks of 100 and sends them sequentially (first 100, next 100, and so on).

## Features

- **Automatic Chunking**: Requests larger than 100 are automatically split into batches of 100
- **Mixed Templates**: Support both Resend templates and internal Thymeleaf templates in the same batch
- **Error Handling**: Comprehensive error responses with HTTP status codes (400, 401, 403, 404, 429, 5xx)
- **Circuit Breaker**: Resilience4j circuit breaker protection
- **Logging**: Automatic logging of all batch operations to database
- **Partial Success**: Individual email failures don't prevent others from being sent

## API Endpoints

### 1. Batch Email Direct Send
**POST** `/api/notifications/batch/emails`

Send batch emails with direct email configurations.

#### Request Body
```json
{
  "emails": [
    {
      "to": "user@example.com",
      "subject": "Order Confirmation",
      "templateType": "RESEND",
      "templateId": "order-confirmation",
      "data": {
        "PRODUCT": "Item Name",
        "PRICE": 100
      }
    },
    {
      "to": "another@example.com",
      "subject": "Welcome",
      "templateType": "INTERNAL",
      "template": "<h1>Hello {{name}}</h1>",
      "data": {
        "name": "John"
      }
    }
  ]
}
```

#### Response (Success)
```json
{
  "status": "SUCCESS",
  "message": "2 of 2 emails sent successfully",
  "totalEmails": 2,
  "successCount": 2,
  "failureCount": 0,
  "results": [
    {
      "recipient": "user@example.com",
      "id": "ae2014de-c168-4c61-8267-70d2662a1ce1",
      "status": "SUCCESS",
      "statusCode": 200
    },
    {
      "recipient": "another@example.com",
      "id": "faccb7a5-8a28-4e9a-ac64-8da1cc3bc1cb",
      "status": "SUCCESS",
      "statusCode": 200
    }
  ]
}
```

#### Response (Partial Success)
```json
{
  "status": "PARTIAL",
  "message": "1 of 2 emails sent successfully",
  "totalEmails": 2,
  "successCount": 1,
  "failureCount": 1,
  "results": [
    {
      "recipient": "user@example.com",
      "id": "ae2014de-c168-4c61-8267-70d2662a1ce1",
      "status": "SUCCESS",
      "statusCode": 200
    },
    {
      "recipient": "invalid-template@example.com",
      "status": "FAILED",
      "statusCode": 404,
      "error": "Not Found: The resource was not found."
    }
  ]
}
```

### 2. Batch Notification Send
**POST** `/api/notifications/batch/send`

Send batch notifications with full notification metadata including type, metadata, and channel.

#### Request Body
```json
{
  "notifications": [
    {
      "type": "WELCOME",
      "channel": "EMAIL",
      "recipient": "newuser@example.com",
      "subject": "Welcome!",
      "templateType": "RESEND",
      "templateId": "welcome-template",
      "data": {
        "ACTIVATION_LINK": "https://app.example.com/activate"
      },
      "metadata": {
        "eventId": "evt-welcome-001"
      }
    }
  ]
}
```

#### Response
```json
{
  "status": "SUCCESS",
  "message": "Batch notifications sent",
  "totalEmails": 1,
  "results": [
    {
      "recipient": "newuser@example.com",
      "id": "ae2014de-c168-4c61-8267-70d2662a1ce1",
      "status": "SUCCESS",
      "statusCode": 200
    }
  ]
}
```

## Error Responses

### 400 Bad Request
```json
{
  "status": "FAILED",
  "message": "Invalid request: Emails list cannot be empty"
}
```

**Causes:**
- Empty emails list
- Missing required fields (to, subject)
- Invalid template configuration

### 401 Unauthorized
```json
{
  "status": "FAILED",
  "message": "Unauthorized: The API key used was missing."
}
```

**Cause:** Resend API key not configured or missing

### 403 Forbidden
```json
{
  "status": "FAILED",
  "message": "Forbidden: The API key used was invalid."
}
```

**Cause:** Invalid Resend API key

### 404 Not Found
```json
{
  "status": "FAILED",
  "message": "Not Found: The resource was not found."
}
```

**Cause:** Template ID doesn't exist in Resend

### 429 Too Many Requests
```json
{
  "status": "FAILED",
  "message": "Too Many Requests: The rate limit was exceeded."
}
```

**Cause:** Exceeded Resend rate limits

### 500+ Server Error
```json
{
  "status": "FAILED",
  "message": "Server Error: Indicates an error with Resend servers. Try again later."
}
```

**Cause:** Resend service unavailable

## Field Descriptions

### Email Item (BatchEmailRequest.EmailItem)
- **to** (string, required): Recipient email address
- **subject** (string, required): Email subject line
- **templateType** (enum: INTERNAL, RESEND): Type of template to use
- **templateId** (string): ID of published Resend template (required if templateType=RESEND)
- **template** (string): HTML template content (for INTERNAL) or template name (fallback for RESEND)
- **data** (object): Template variables to interpolate
- **html** (string): Raw HTML content (alternative to template)

### Notification Item (BatchNotificationRequest.NotificationItem)
- **type** (string): Notification type (e.g., WELCOME, RESET_PASSWORD)
- **channel** (string): Channel (e.g., EMAIL)
- **recipient** (string): Email address
- **subject** (string): Email subject
- **templateType** (enum): INTERNAL or RESEND
- **templateId** (string): Resend template ID
- **template** (string): Template content
- **data** (object): Template variables
- **metadata** (object): Event tracking metadata

## Template Types

### INTERNAL Templates
- Uses Thymeleaf template engine
- Variables referenced with `{{variableName}}`
- HTML content sent directly to Resend
- Example:
```json
{
  "templateType": "INTERNAL",
  "template": "<h1>Hello {{name}}!</h1>",
  "data": { "name": "Alice" }
}
```

### RESEND Templates
- Uses published Resend templates
- Variables referenced with capital letters: PRODUCT, PRICE, etc.
- Resend renders the template server-side
- Example:
```json
{
  "templateType": "RESEND",
  "templateId": "order-confirmation",
  "data": {
    "PRODUCT": "Laptop",
    "PRICE": 1500
  }
}
```

## Configuration

### Application Configuration (application.yml)
```yaml
resend:
  api:
    key: re_xxxxxxxxx  # Your Resend API key
    base-url: https://api.resend.com
  from:
    email: no-reply@classpicker.io  # Must be a sender from your verified domain

resilience4j:
  circuitbreaker:
    instances:
      resendBatchEmail:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
```

## Limitations

- **Max batch size**: 100 emails per request
- **Max variable value**: 2000 characters per string variable
- **Max variables per template**: 20 variables
- **Max variable name length**: 50 characters
- **Attachments**: Not yet supported by Resend batch API
- **Scheduled sending**: Not yet supported by Resend batch API

## Logging

All batch operations are logged to the database with:
- Recipient email
- Notification type
- Template type and ID
- Success/failure status
- Error messages (if any)
- Event metadata (if provided)

Query logs:
```
GET /api/notifications/logs
```

## Usage Examples

If your recipients are personal emails (for example classpicker.io@gmail.com and qbinom.dev@gmail.com), keep those in the request payload under the `to` field. They are recipients, not your sender domain.

### Example 1: Newsletter to Multiple Subscribers
```bash
curl -X POST http://localhost:8080/api/notifications/batch/emails \
  -H "Content-Type: application/json" \
  -d '{
    "emails": [
      {
        "to": "subscriber1@example.com",
        "subject": "March Newsletter",
        "templateType": "RESEND",
        "templateId": "newsletter",
        "data": { "ISSUE": "March 2026" }
      },
      {
        "to": "subscriber2@example.com",
        "subject": "March Newsletter",
        "templateType": "RESEND",
        "templateId": "newsletter",
        "data": { "ISSUE": "March 2026" }
      }
    ]
  }'
```

### Example 2: Order Confirmations (Mixed Templates)
```bash
curl -X POST http://localhost:8080/api/notifications/batch/send \
  -H "Content-Type: application/json" \
  -d '{
    "notifications": [
      {
        "type": "ORDER_CONFIRMATION",
        "channel": "EMAIL",
        "recipient": "customer1@example.com",
        "subject": "Your Order #123456",
        "templateType": "RESEND",
        "templateId": "order-confirmed",
        "data": {
          "ORDER_ID": "123456",
          "TOTAL": "99.99"
        }
      }
    ]
  }'
```

## Performance Notes

- Each batch request consumes one API call to Resend
- Processing ~100 emails takes ~2-5 seconds
- Circuit breaker protects against cascading failures
- Recommended batch size: 50-80 emails for optimal performance
- Rate limits apply per second/hour (check Resend docs)

## Troubleshooting

### Issue: "Maximum batch size exceeded"
- **Solution**: Split request into smaller batches (max 100)

### Issue: "templateId is required when templateType is RESEND"
- **Solution**: Provide valid templateId for RESEND type templates

### Issue: "The resource was not found" (404 error)
- **Solution**: Ensure template is published in Resend dashboard

### Issue: "The API key used was invalid" (403 error)
- **Solution**: Verify RESEND_API_KEY in application.yml

### Issue: Circuit breaker is open
- **Solution**: Wait 30 seconds for circuit to half-open, then retry

## Monitoring

Monitor batch operations via:
- Database logs table: `notification_log`
- Application logs: Search for "BatchEmailService" or "ResendBatchEmailProvider"
- Resend dashboard: View delivery status for each email ID
