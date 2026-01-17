# **JSON Query Engine**

A production-grade, distributed RESTful API built with **Spring Boot** demonstrating advanced backend engineering through intelligent JSON document management. The application implements a **multi-store architecture** with **Redis** for transactional storage, **Elasticsearch** for hierarchical indexing and full-text search, and **RabbitMQ** for asynchronous message-driven synchronization. Features include **CRUD operations with optimistic concurrency control**, **JSON Schema validation**, **ETag-based conditional reads/writes**, **RFC 7386-compliant JSON Merge Patch**, and **Google OAuth 2.0 with RS256 JWT** security.

---

## **About**

A production-grade RESTful API built with Spring Boot demonstrating advanced backend engineering through intelligent JSON document management. Implements ETag-based optimistic concurrency control, RFC 7386-compliant JSON Merge Patch for partial updates, and Google OAuth 2.0 with RS256 JWT validation for security. Features a distributed architecture with Redis for high-performance transactional storage, Elasticsearch with parent-child join relations for hierarchical document indexing and full-text search, and RabbitMQ for asynchronous message-driven indexing between data stores. Showcases expertise in RESTful API design, distributed systems patterns, event-driven architectures, and scalable multi-store persistence—core competencies for backend roles at top-tier companies.

---

## **Architecture Overview**

This application demonstrates a **distributed, event-driven architecture** with multiple data stores:

- **Redis**: Primary transactional data store for high-performance key-value operations
- **Elasticsearch**: Secondary store with parent-child join relations for hierarchical document indexing and full-text search capabilities
- **RabbitMQ**: Message broker enabling asynchronous, decoupled synchronization between Redis and Elasticsearch
- **Event-Driven Design**: All write operations (CREATE, UPDATE, DELETE) publish messages to RabbitMQ, which are consumed by listeners that update Elasticsearch indices asynchronously

```
Client Request → REST Controller → Redis (Primary Store)
                                  ↓
                            RabbitMQ Message Queue
                                  ↓
                         Message Listener → Elasticsearch (Search Index)
```

---

## **Features**

### **1. CRUD Operations with Optimistic Concurrency Control**
    - **Create (POST /api/plans):**  
      Creates a new plan resource.
      - If a plan with the same `objectId` does not exist, it creates the resource (returns **201 Created**).
      - If a plan with the same `objectId` exists and the incoming JSON is identical (ETag match), returns **304 Not Modified**.
      - If a plan with the same `objectId` exists but with different content, it overwrites the resource and returns **201 Created**.

    - **Read (GET /api/plans/{objectId}):**  
          Retrieves a plan by its `objectId` with conditional read support using ETag/If-None-Match.

    - **Read All (GET /api/plans):**  
      - Returns a list of all stored plans.

    - **Update (PATCH /api/plans/{objectId}):**  
      Supports JSON Merge Patch for partial updates with conditional write using the `If-Match` header.
      - If the request header doesn't contain the `If-Match` header, or if the ETag value is not the latest one, returns **412 Precondition Failed**. 
      - If the PATCH payload makes no effective change (ETag remains the same), returns **304 Not Modified**.

    - **Delete (DELETE /api/plans/{objectId}):**  
      - Deletes a plan resource by its `objectId` and publishes a delete event to RabbitMQ for Elasticsearch cleanup.

### **2. JSON Schema Validation**
- All incoming JSON payloads are validated against a pre-defined JSON Schema (`plan-schema.json`) using the NetworkNT JSON Schema Validator.
- Ensures data integrity and contract compliance before persistence.

### **3. ETag-Based Optimistic Concurrency Control**
- ETags are computed dynamically using MD5 hashing of JSON content to support conditional reads and writes.
- **Conditional Reads**: GET requests use `If-None-Match` header for efficient caching (returning 304 Not Modified if ETag matches).
- **Conditional Writes**: PATCH requests require an `If-Match` header to prevent lost updates (returning 412 Precondition Failed if ETag doesn't match).
- Prevents race conditions and ensures data consistency in distributed systems.

### **4. RFC 7386-Compliant JSON Merge Patch**
- Implements **JSON Merge Patch (RFC 7386)** for partial updates with intelligent array merging.
- Supports nested object merging and array element matching by `objectId`.
- If a patch element with a new `objectId` is provided, it's automatically appended to the array.

### **5. Multi-Store Architecture**
- **Redis**: Serves as the source of truth for transactional consistency and low-latency reads/writes.
- **Elasticsearch**: Provides advanced search capabilities with parent-child join relations for hierarchical document structure:
  - `plan` → `linkedPlanService`, `planCostShare` (level 1)
  - `linkedPlanService` → `planserviceCostShare`, `linkedService` (level 2)
- **RabbitMQ**: Decouples write operations from indexing, enabling asynchronous Elasticsearch updates.

### **6. Event-Driven Indexing**
- All write operations (CREATE, PATCH, DELETE) publish messages to RabbitMQ topic exchange.
- `PlanIndexListener` consumes messages and updates Elasticsearch indices asynchronously.
- Routing keys enable flexible message handling (`plan.create`, `plan.patch`, `plan.delete`).

### **7. Security (Google OAuth 2.0)**
- All endpoints are secured using Bearer tokens issued by Google Identity Platform.
- Spring Security's OAuth2 Resource Server validates JWT tokens signed with RS256.
- JWK Set URI auto-rotation ensures cryptographic key freshness.

---

## **Endpoints**

### 1. **POST `/api/plans`**
- **Description:** Create a new plan and store it in Redis.
- **Request Body Example:**
  ```json
  {
    "planCostShares": {
      "deductible": 2000,
      "_org": "example.com",
      "copay": 23,
      "objectId": "1234vxc2324sdf-501",
      "objectType": "membercostshare"
    },
    "linkedPlanServices": [
      {
        "linkedService": {
          "_org": "example.com",
          "objectId": "1234520xvc30asdf-502",
          "objectType": "service",
          "name": "Yearly physical"
        },
        "planserviceCostShares": {
          "deductible": 10,
          "_org": "example.com",
          "copay": 0,
          "objectId": "1234512xvc1314asdfs-503",
          "objectType": "membercostshare"
        },
        "_org": "example.com",
        "objectId": "27283xvx9asdff-504",
        "objectType": "planservice"
      }
    ],
    "_org": "example.com",
    "objectId": "12xvxc345ssdsds-508",
    "objectType": "plan",
    "planType": "inNetwork",
    "creationDate": "12-12-2017"
  }
  ```
- **Response:**
  - **201 Created:** Resource created or overwritten.
  - **304 Not Modified:** If the resource already exists with identical content.  
  **Headers:**
    - `Location`: `/api/plans/{objectId}`
    - `ETag`: `<computed-etag>`

---

### 2. **GET `/api/plans/{objectId}`**
- **Description:** Retrieve the plan data by `objectId`. Supports conditional reads using `ETag`.
- **Headers:**
    - `If-None-Match`: `<etag>` (optional)
- **Response:**
    - `200 OK` with JSON body if the resource exists and ETag doesn't match/not provided.
    - `304 Not Modified` if the `If-None-Match` header matches the current ETag.
    - `404 Not Found` if the resource does not exist.

---

### 3. **GET `/api/plans`**
- **Description:** Retrieves all stored plans.

- **Response:**
    - `200 OK` with a JSON array of plans.
    - `404 Not Found` if resource does not exist.

---

### 4. **PATCH `/api/plans/{objectId}`**
- **Description:** Applies a **JSON Merge Patch (RFC 7386)** to update a plan.

- **Conditional Write:**
    - Requires an `If-Match` header that must match the computed ETag. If it doesn't match, returns **412 Precondition Failed**
    - If the patch makes no effective change, returns **304 Not Modified**.
    - If element with new `objectId` is provided in the patch payload, a new object is created and appended.

- **Response:**
    - `200 OK`: Returns the updated JSON and new ETag.
    - `304 Not Modified`: If no effective change is made.
    - `412 Precondition Failed:` If the `If-Match` header does not match.

---

### 5. **DELETE `/api/plans/{objectId}`**
- **Description:** Delete the plan by `objectId`.
- **Response:**
    - `204 No Content` if the object was deleted successfully.
    - `404 Not Found` if the object does not exist.

---

## **How to Run**

### **Prerequisites**
- Java 17+
- Maven 3.6+
- Redis 6+ (either installed locally or running in Docker)
- Elasticsearch 8.x (running locally or in Docker)
- RabbitMQ 3.x (running locally or in Docker)
- Google Cloud Account for OAuth 2.0 credentials

### **Running the Application**

1. **Start Infrastructure Services**:
   
   Using Docker Compose (recommended):
   ```bash
   docker-compose up -d
   ```
   
   Or manually:
   
   **Redis:**
   ```bash
   docker run --name redis -p 6379:6379 -d redis
   ```
   
   **Elasticsearch:**
   ```bash
   docker run --name elasticsearch -p 9200:9200 -e "discovery.type=single-node" -e "xpack.security.enabled=false" -d elasticsearch:8.17.4
   ```
   
   **RabbitMQ:**
   ```bash
   docker run --name rabbitmq -p 5672:5672 -p 15672:15672 -d rabbitmq:3-management
   ```

2. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd json-query-engine
   ```

3. **Build and run the application**:
   ```bash
   ./mvnw spring-boot:run
   # The API will start on port 8081
   ```

4. **Configure Google OAuth 2.0**:
    - Create OAuth 2.0 credentials in [Google Cloud Console](https://console.cloud.google.com/apis/credentials).
    - Configure your OAuth Client (for Postman testing, use `https://oauth.pstmn.io/v1/callback` as the redirect URI).
    - Use the Client ID and Client Secret to obtain a Bearer token.
    - The API's security configuration validates Google's JWT tokens automatically.

## **Configuration**

Edit the following properties in `src/main/resources/application.properties`:

```properties
# Server
server.port=8081

# Redis (Primary Store)
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Elasticsearch (Search Index)
spring.elasticsearch.uris=http://localhost:9200

# RabbitMQ (Message Broker)
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672

# OAuth2 Resource Server (Google IDP)
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://accounts.google.com
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://www.googleapis.com/oauth2/v3/certs
```
## **Testing in Postman**

When testing with Postman, you need to configure **OAuth 2.0** to obtain a valid Google-signed JWT token, then set your requests to **inherit** that authentication.

---

### **1. Configure Parent Auth in Postman**
This screenshot shows how to set up OAuth 2.0 at the **Collection level**, so individual requests can inherit it.
![Configure Auth in Postman](docs/images/Configure%20Parent%20Auth%20in%20Postman.png)

---

### **2. Requesting a New Google IDP Token**
After setting up OAuth 2.0, click **"Get New Access Token"** to start the Google login flow.
![Requesting new Google IDP token](docs/images/Requesting%20new%20Google%20IDP%20token.png)

---

### **3. Selecting Your Gmail Account**
Here, Postman prompts you to select the Google account for which you want to grant access.
![Select Gmail ID](docs/images/Select%20gmail%20id.jpg)

---

### **4. Consent Screen to Grant Access**
Google shows a consent screen asking you to allow Postman to access your profile and email.
![Consent screen to grant access to Postman](docs/images/Consent%20screen%20to%20grant%20access%20to%20Postman.jpg)

---

### **5. Inherit Auth from Parent**
Finally, each request can inherit the OAuth 2.0 configuration from the parent collection, making it easier to manage tokens.
![Inherit Auth from Parent](docs/images/Inherit%20Auth%20from%20Parent.png)

## **Troubleshooting**

1. **Port already in use**:  
   Update the port in `application.properties` or free the port using:
   ```bash
   # Windows
   netstat -ano | findstr :8081
   taskkill /PID <PID> /F
   
   # Linux/Mac
   lsof -ti:8081 | xargs kill -9
   ```

2. **Redis connection errors**:  
   Ensure Redis is running and accessible at `localhost:6379`:
   ```bash
   redis-cli ping  # Should return "PONG"
   ```

3. **Elasticsearch connection errors**:  
   Verify Elasticsearch is running:
   ```bash
   curl http://localhost:9200  # Should return cluster info
   ```

4. **RabbitMQ connection errors**:  
   Check RabbitMQ status and management console at `http://localhost:15672` (default credentials: guest/guest).

5. **OAuth 2.0 Errors**:
   - Verify that the Bearer token is valid and not expired.
   - Ensure the token is issued by Google (`iss: https://accounts.google.com`).
   - Check that the security configuration matches the issuer URI.

---

## **Technologies Used**

### **Backend Framework**
- **Spring Boot 3.4.2** (Java 17)
- **Spring Web** (REST API)
- **Spring Security** (OAuth2 Resource Server)

### **Data Stores**
- **Redis** (Primary transactional key-value store)
- **Elasticsearch 8.17.4** (Search index with parent-child join relations)

### **Messaging**
- **RabbitMQ** (AMQP message broker)
- **Spring AMQP** (Message publishing and consumption)

### **Security**
- **Google OAuth 2.0** (Identity Provider)
- **JWT with RS256** (Token validation)

### **Data Validation & Processing**
- **JSON Schema Validator (NetworkNT)** (Payload validation)
- **Jackson** (JSON serialization/deserialization)
- **JSON Merge Patch (RFC 7386)** (Partial updates)

### **Build & Tooling**
- **Maven** (Dependency management and build tool)
- **Lombok** (Boilerplate reduction)
- **Docker** (Containerization)

---

## **Key Technical Highlights**

This project demonstrates several production-ready backend engineering patterns:

1. **Polyglot Persistence**: Strategic use of multiple specialized data stores (Redis for speed, Elasticsearch for search) rather than forcing a single database to handle all use cases.

2. **Event-Driven Architecture**: Decoupled write and indexing operations using message queues, enabling horizontal scalability and fault tolerance.

3. **Optimistic Concurrency Control**: ETag-based conditional requests prevent lost updates and race conditions without pessimistic locking overhead.

4. **REST API Best Practices**: 
   - Proper HTTP semantics (201 Created, 304 Not Modified, 412 Precondition Failed)
   - RFC-compliant implementations (RFC 7386 for JSON Merge Patch)
   - Conditional requests (If-Match, If-None-Match headers)

5. **Parent-Child Document Relationships**: Elasticsearch join relations enable efficient querying of hierarchical data while maintaining denormalization benefits.

6. **Asynchronous Processing**: Non-blocking indexing operations improve API response times and user experience.

7. **Security**: Industry-standard OAuth 2.0 with JWT validation, ensuring stateless authentication scalability.

8. **Contract-First Design**: JSON Schema validation ensures API contracts are enforced at runtime.

---

## **Contributing**

Contributions are welcome! Please fork the repository and submit a pull request with your changes.

---

## **License**

This project is licensed under the MIT License.

---

## **Contact**

For questions or issues, please reach out to:  
**Eashan** – [eashanroy7@gmail.com](mailto:eashanroy7@gmail.com)


