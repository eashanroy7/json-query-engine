# **JSON Query Engine**

A REST API built with **Spring Boot** and **Redis** to handle structured JSON data. The application supports **CRUD operations**, **JSON Schema validation**, **ETag-based conditional read/writes**, **JSON Merge Patch for partial updates**, and security via **Google OAuth 2.0** with RS256.

---

## **Features**
- **CRUD Operations**:
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
      - Deletes a plan resource by its `objectId`.

- **Validation:**
    - All incoming JSON payloads are validated against a pre-defined JSON Schema (`plan-schema.json`).

- **ETag Support:**
    - ETags are computed on the fly (using an MD5 hash of the JSON content) to support conditional reads and writes.
    - GET requests use `If-None-Match` for caching (returning 304 Not Modified if the ETag matches).
    - PATCH requests require an `If-Match` header (returning 412 Precondition Failed if it does not match).

- **Data Storage:**
    - Uses Redis as a key-value store.
    - Each plan is stored as a Redis hash with the key pattern `plan:data:{objectId}` and a single field `"json"` containing the plan’s JSON.

- **Security (Google OAuth 2.0):**
    - All endpoints are secured using Bearer tokens issued by Google.
    - The API uses Spring Security’s OAuth2 Resource Server to validate JWT tokens (signed with RS256).

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

### 3. **GET `/api/plans*`**
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
- Maven
- Redis (either installed locally or running in Docker)
- Google Cloud Account for OAuth 2.0 credentials

### **Running the Application**

1. **Start Redis**:
   Locally:
      ```bash
      redis-server
      ```
   Docker:
      ```bash
      docker run --name redis -p 6379:6379 -d redis
      ```

2. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd json-query-engine
   ```

3. **Build and run the application**:
   ```bash
   ./mvnw spring-boot:run
   /* The API will start on port 8081 */
   ```

4. **Configure Google OAuth 2.0**:
    - Create OAuth 2.0 credentials in Google Cloud.
    - Configure your OAuth Client (for example, use https://oauth.pstmn.io/v1/callback for Postman).
    - Use the Client ID and Client Secret to obtain a Bearer token.
    - Ensure your API’s security configuration is set to validate Google’s JWT tokens.

## **Configuration**

Edit the following properties in `src/main/resources/application.properties` if needed:

```properties
server.port=8081
spring.redis.host=localhost
spring.redis.port=6379

# OAuth2 Resource Server configuration
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
   Update the port in `application.properties` or free the port.

2. **Redis connection errors**:  
   Ensure Redis is running and accessible at `localhost:6379`.
3. **OAuth 2.0 Errors**:
   Verify that the Bearer token is valid and that the security configuration is correctly set up.

---

## **Technologies Used**

- **Spring Boot** (REST API framework)
- **Redis** (Key-value store)
- **JSON Schema Validator (NetworkNT)** (for payload validation)
- **Spring Security: OAuth2 Resource Server (with Google IDP using RS256)**
- **Maven** (Build tool)

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


