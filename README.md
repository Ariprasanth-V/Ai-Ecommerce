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
