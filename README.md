# **JSON Query Engine**

A REST API built with **Spring Boot** and **Redis** to handle structured JSON data. The application supports **CRUD operations**, **JSON Schema validation**, **ETag-based conditional reads**, and stores data in a **key-value store** (Redis).

---

## **Features**
- **CRUD Operations**:
    - Create (`POST /api/plans`)
    - Read (`GET /api/plans/{objectId}`)
    - Delete (`DELETE /api/plans/{objectId}`)

- **Validation**:  
  Validates incoming JSON payloads against a pre-defined **JSON Schema** (`plan-schema.json`).

- **ETag Support**:  
  The API generates an **ETag** based on the JSON payload and uses it for **conditional reads** to optimize caching and concurrency.

- **Data Storage**:  
  Stores and retrieves data from **Redis** as a key-value store, using the `objectId` as the key.

---

## **Endpoints**

### 1. **POST `/api/plans`**
- **Description:** Create a new plan and store it in Redis.
- **Request Body:**
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
- **Response:** `201 Created`
    - Headers:
        - `Location`: `/api/plans/12xvxc345ssdsds-508`
        - `ETag`: `<generated-etag>`

---

### 2. **GET `/api/plans/{objectId}`**
- **Description:** Retrieve the plan data by `objectId`. Supports conditional reads using `ETag`.
- **Headers:**
    - `If-None-Match`: `<etag>` (optional)
- **Response:**
    - `200 OK` with JSON body if the resource exists and is not modified.
    - `304 Not Modified` if the `If-None-Match` header matches the current ETag.
    - `404 Not Found` if the resource does not exist.

---

### 3. **DELETE `/api/plans/{objectId}`**
- **Description:** Delete the plan by `objectId`.
- **Response:**
    - `204 No Content` if the object was deleted successfully.
    - `404 Not Found` if the object does not exist.

---

## **How to Run**

### **Prerequisites**
- Java 17+
- Maven
- Redis (either installed locally or running in Docker)

### **Running the Application**

1. **Start Redis**:
    - Locally:
      ```bash
      redis-server
      ```
    - Docker:
      ```bash
      docker run --name redis -p 6379:6379 -d redis
      ```

2. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd json-query-engine
   ```

3**Build and run the application**:
   ```bash
   ./mvnw spring-boot:run
   ```
4**Access the API**:
   ```bash
   Base URL: http://localhost:8081
   ```

## **Configuration**

Modify the following properties in `src/main/resources/application.properties` if needed:

```properties
server.port=8081
spring.redis.host=localhost
spring.redis.port=6379
```

## **Testing**

### **Example Requests**

#### **POST** `/api/plans`
- **Request Body**:
  ```json
  {
    "objectId": "12xvxc345ssdsds-508",
    "objectType": "plan",
    "planType": "inNetwork",
    "creationDate": "12-12-2017"
  }
  ```
- **Response:**: `201 Created`

#### **GET** `/api/plans/12xvxc345ssdsds-508`
- **Response**: `200 OK`
  ```json
  {
    "objectId": "12xvxc345ssdsds-508",
    "objectType": "plan",
    "planType": "inNetwork",
    "creationDate": "12-12-2017"
  }
  ```

#### **GET** with ETag:
- **Request Header**:
  ```http
  If-None-Match: "<etag>"
  ```
- **Response**: `304 Not Modified`

#### **DELETE** `/api/plans/12xvxc345ssdsds-508`
- **Response**: `204 No Content`

---

## **Schema Validation**

The API validates incoming JSON payloads using the **JSON Schema** defined in `src/main/resources/schemas/plan-schema.json`. If validation fails, the API returns a `400 Bad Request` with details about the errors.

---

## **Troubleshooting**

### **Common Errors**
1. **Port already in use**:  
   Update the port in `application.properties` or free the port.

2. **Redis connection errors**:  
   Ensure Redis is running and accessible at `localhost:6379`.

---

## **Technologies Used**

- **Spring Boot** (REST API framework)
- **Redis** (Key-value store)
- **JSON Schema Validator (NetworkNT)** (for payload validation)
- **Maven** (Build tool)

---

## **Future Enhancements**

- Add support for **PUT (update)** requests.
- Implement more complex **querying capabilities** for JSON data.
- Add **authentication and authorization**.

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


