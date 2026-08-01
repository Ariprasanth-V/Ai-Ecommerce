# AI-Commerce

> AI-powered e-commerce platform — Spring Boot 3.x · Java 21 · Maven

---

## Architecture Decision: Package-by-Feature

This project uses a **package-by-feature** structure instead of the traditional
package-by-layer (controller / service / repository) approach.

```
com.yourname.aicommerce
├── catalog/        ← Products, categories, inventory
├── cart/           ← Shopping cart
├── order/          ← Order placement & history
├── auth/           ← Registration, login, JWT
├── payment/        ← Checkout & payment gateway
├── ai/             ← AI-powered recommendations, search, chatbot
├── common/         ← Cross-cutting: exception handling, response wrappers
│   ├── exception/
│   └── response/
└── config/         ← Security, OpenAPI, and other Spring config
```

### Why package-by-feature?

| Benefit | Explanation |
|---|---|
| **High cohesion** | All code related to a feature lives together — controller, service, repository, DTOs, and domain model. |
| **Low coupling** | Features are isolated; changes to *catalog* don't ripple into *payment*. |
| **Easier navigation** | You work on one folder at a time instead of jumping between `controller/`, `service/`, `repository/`. |
| **Modular growth** | Each feature package can eventually become its own module or microservice with minimal refactoring. |
| **Better encapsulation** | Package-private visibility hides implementation details within a feature. |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.4.3 |
| Build | Maven |
| Web | Spring MVC (spring-boot-starter-web) |
| Data | Spring Data JPA + PostgreSQL (H2 for dev/test) |
| Security | Spring Security (open during scaffolding) |
| Validation | Jakarta Bean Validation |
| Docs | springdoc-openapi + Swagger UI |
| Monitoring | Spring Boot Actuator (health + info) |

---

## Prerequisites

- **Java 21** (verify with `java -version`)
- **Maven 3.9+** (or use the Maven wrapper once added)
- **PostgreSQL 15+** *(optional — H2 is used by default in dev)*

---

## Environment Variables

The following environment variables are required at runtime:

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_URL` | Dev: No, Prod: Yes | `jdbc:postgresql://localhost:5432/aicommerce` | JDBC URL for PostgreSQL |
| `DB_USER` | Dev: No, Prod: Yes | `postgres` | Database username |
| `DB_PASSWORD` | Dev: No, Prod: Yes | `postgres` | Database password |
| `JWT_SECRET` | **Yes — all profiles** | *(none — fails fast)* | Base64-encoded HS256 signing key, minimum 32 bytes |
| `JWT_ACCESS_EXPIRY_MS` | No | `900000` (15 min) | Access token lifetime in milliseconds |
| `JWT_REFRESH_EXPIRY_MS` | No | `604800000` (7 days) | Refresh token lifetime in milliseconds |
| `RAZORPAY_KEY_ID` | **Yes — all profiles** | *(none — fails fast)* | Razorpay Sandbox/Test Key ID (e.g. `rzp_test_...`) |
| `RAZORPAY_KEY_SECRET` | **Yes — all profiles** | *(none — fails fast)* | Razorpay Sandbox/Test Key Secret |
| `RAZORPAY_WEBHOOK_SECRET` | **Yes — all profiles** | *(none — fails fast)* | Razorpay Webhook Secret for HMAC-SHA256 signature verification |

### Local development `.env` file

Create a `.env` file in the project root (it is already in `.gitignore`). The
`spring-dotenv` library loads it automatically at startup:

```bash
# .env — never commit this file
JWT_SECRET=<your-base64-secret>
JWT_ACCESS_EXPIRY_MS=900000
JWT_REFRESH_EXPIRY_MS=604800000
RAZORPAY_KEY_ID=rzp_test_TKXIx4TOtoqqfN
RAZORPAY_KEY_SECRET=23Yr7Bb0PQmaqd55zg8JwkMc
RAZORPAY_WEBHOOK_SECRET=rzp_whsec_test_1234567890
```

---

## Razorpay Payment Integration & Testing (Curl Commands)

### 1. Create Razorpay Payment Order (Authenticated)

```bash
curl -X POST http://localhost:8080/api/v1/payments/create-order/1 \
  -H "Authorization: Bearer <YOUR_CUSTOMER_JWT_TOKEN>"
```

**Response**:
```json
{
  "success": true,
  "message": "Razorpay payment order created successfully",
  "data": {
    "orderId": 1,
    "razorpayOrderId": "order_PZ123456789",
    "amount": 599.97,
    "currency": "INR",
    "keyId": "rzp_test_TKXIx4TOtoqqfN"
  }
}
```

### 2. Simulate Razorpay Webhook Call (`payment.captured`)

Compute HMAC-SHA256 of the JSON body using your `RAZORPAY_WEBHOOK_SECRET` and pass it in the `X-Razorpay-Signature` header:

```bash
# Example payload
PAYLOAD='{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_test_999","order_id":"order_PZ123456789","amount":59997,"notes":{"orderId":"1"}}}}}'

# Compute signature (bash/openssl)
SIG=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "rzp_whsec_test_1234567890" | sed 's/^.*= //')

curl -X POST http://localhost:8080/api/v1/payments/webhook \
  -H "Content-Type: application/json" \
  -H "X-Razorpay-Signature: $SIG" \
  -d "$PAYLOAD"
```

### 3. Simulate Razorpay Webhook Call (`payment.failed`)

```bash
PAYLOAD='{"event":"payment.failed","payload":{"payment":{"entity":{"id":"pay_test_888","order_id":"order_PZ123456789","amount":59997,"notes":{"orderId":"1"}}}}}'

SIG=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "rzp_whsec_test_1234567890" | sed 's/^.*= //')

curl -X POST http://localhost:8080/api/v1/payments/webhook \
  -H "Content-Type: application/json" \
  -H "X-Razorpay-Signature: $SIG" \
  -d "$PAYLOAD"
```


**Generate a secure JWT secret** (PowerShell):
```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

---

## Running Locally

### 1. Clone & build

```bash
git clone <repo-url>
cd ai-commerce
mvn clean install
```

### 2. Start with the dev profile (H2 in-memory — no Postgres needed)

```bash
mvn spring-boot:run
```

The app starts on **http://localhost:8080** with the `dev` profile active by
default.

### 3. (Optional) Use a local PostgreSQL database

Set these environment variables before starting:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/aicommerce
export DB_USER=postgres
export DB_PASSWORD=secret
export DB_DRIVER=org.postgresql.Driver
mvn spring-boot:run
```

### 4. Verify

| Endpoint | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Actuator Health | http://localhost:8080/actuator/health |
| Actuator Info | http://localhost:8080/actuator/info |
| H2 Console (dev) | http://localhost:8080/h2-console |

---

## Spring Profiles

| Profile | Datasource | DDL Strategy | Logging |
|---|---|---|---|
| `dev` (default) | H2 in-memory | `update` | DEBUG |
| `test` | H2 in-memory | `create-drop` | WARN |
| `prod` | PostgreSQL (env vars required) | `validate` | INFO |

---

## Project Structure

```
ai-commerce/
├── pom.xml
├── README.md
├── .gitignore
└── src/
    ├── main/
    │   ├── java/com/yourname/aicommerce/
    │   │   ├── AiCommerceApplication.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   └── OpenApiConfig.java
    │   │   ├── common/
    │   │   │   ├── exception/
    │   │   │   │   ├── ErrorResponse.java
    │   │   │   │   └── GlobalExceptionHandler.java
    │   │   │   └── response/
    │   │   │       └── ApiResponse.java
    │   │   ├── ai/
    │   │   ├── auth/
    │   │   ├── cart/
    │   │   ├── catalog/
    │   │   ├── order/
    │   │   └── payment/
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-test.yml
    │       └── application-prod.yml
    └── test/
        └── java/com/yourname/aicommerce/
            └── AiCommerceApplicationTests.java
```

---

## License

MIT
