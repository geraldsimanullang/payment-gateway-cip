# Payment Gateway Service

A Spring Boot-based payment gateway service that handles payment transactions from multiple channels (Mobile Banking, Internet Banking, ATM), integrates with Core Banking and Biller Aggregator, and publishes events to Kafka.

## Prerequisites

- Java 21
- Maven 3.8+
- Docker & Docker Compose

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/yourusername/payment-gateway-cip.git
cd payment-gateway-cip
```

### 2. Setup environment variables

```bash
cp .env.example .env
```

Update `.env` with your local configuration if needed.

### 3. Start infrastructure services

```bash
docker compose up -d
```

This will start:
- PostgreSQL on port 5432
- Kafka on port 29092
- Zookeeper on port 2181

### 4. Run the application

```bash
./mvnw spring-boot:run
```

The application will start on port 8080. Flyway will automatically run the database migration on startup.

## API Documentation

Once the application is running, access the Swagger UI at:
http://localhost:8080/swagger-ui/index.html

## API Endpoints

### Create Payment
POST /api/payments
Authorization: Bearer {jwt_token}
Content-Type: application/json

Request body:
```json
{
  "orderId": "INV-12345",
  "channel": "MOBILE_BANKING",
  "amount": 250000,
  "account": "1234567890",
  "currency": "IDR",
  "paymentMethod": "VIRTUAL_ACCOUNT"
}
```

Success response:
```json
{
  "transactionId": "uuid",
  "orderId": "INV-12345",
  "status": "SUCCESS",
  "corebankReference": "CB123456789",
  "billerReference": "BILLER987654321"
}
```

Failed response:
```json
{
  "transactionId": "uuid",
  "orderId": "INV-12345",
  "status": "FAILED",
  "message": "Insufficient balance"
}
```

### Get Transaction
GET /api/payments/{id}
Authorization: Bearer {jwt_token}

## Running Tests
```bash
./mvnw test
```