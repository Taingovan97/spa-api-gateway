Dưới đây là phần **giải thích đầy đủ – theo đúng kiến trúc bạn đang xây dựng** – về **`spa-api-gateway`**. Mình sẽ đi từ **vai trò → cơ chế định tuyến → luồng xử lý chi tiết → câu hỏi auto-scale & multi-instance**.

---

# 1. Nhiệm vụ của `spa-api-gateway`

`spa-api-gateway` là **điểm vào duy nhất (Single Entry Point)** của toàn bộ hệ thống Spa Booking.

### Các nhiệm vụ cốt lõi

### (1) Security Boundary (quan trọng nhất)

* Verify **JWT access token** phát hành bởi **Keycloak**
* Enforce **authentication** & **authorization**
* Áp dụng **RBAC / policy theo route**
* Các service phía sau **KHÔNG cần xử lý auth**

👉 Đây là lý do **không có Auth Service riêng** trong phương án C.

---

### (2) Request Routing / API Composition

* Nhận HTTP request từ client
* Dựa vào **route rule** để xác định:

  * request này thuộc service nào
  * forward đến đâu

---

### (3) Context Propagation

* Trích xuất thông tin identity từ token:

  * `sub`
  * `roles`
  * `email`
* Inject thành header nội bộ:

  * `X-User-Id`
  * `X-User-Roles`
  * `X-User-Email`
* Downstream services **chỉ tin gateway**, không đọc JWT

---

### (4) Cross-cutting concerns (sau này)

* Rate limit
* Circuit breaker
* Request/response logging
* Metrics / tracing
* API versioning

---

# 2. Gateway xác định & điều hướng request như thế nào?

Trong Spring Cloud Gateway, routing dựa trên **3 khái niệm**:

## 2.1 Route

Một route gồm:

* **Predicate**: điều kiện match request
* **URI**: nơi forward request đến
* **Filters**: xử lý trước/sau khi forward

Ví dụ bạn đang dùng:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://spa-user-service:8082
          predicates:
            - Path=/me/**
```

👉 Ý nghĩa:

* Nếu request path match `/me/**`
* Gateway forward request đến `spa-user-service`

---

## 2.2 Predicate (điều kiện match)

Có nhiều loại predicate:

* Path
* Method
* Header
* Query
* Host
* Time

Bạn đang dùng **Path predicate** → rất phổ biến cho API Gateway.

---

## 2.3 Filter

Filter chạy:

* **trước khi forward** (pre)
* **sau khi nhận response** (post)

`UserContextGlobalFilter` của bạn là **Global Filter**, áp dụng cho mọi route.

---

# 3. Luồng hoạt động chi tiết của 1 request

Giả sử client gọi:

```http
GET /me
Authorization: Bearer <access_token>
```

---

## Bước 1 – Request đến Gateway

Client chỉ biết:

```
https://api.spa-booking.com/me
```

Gateway nhận request tại port 8080.

---

## Bước 2 – Spring Security intercept

Gateway đã được config:

```yaml
spring.security.oauth2.resourceserver.jwt.issuer-uri
```

Spring Security sẽ:

1. Trích `Authorization: Bearer`
2. Decode JWT
3. Fetch JWKS từ Keycloak
4. Verify:

   * chữ ký
   * exp
   * iss
5. Nếu fail → **401 ngay tại gateway**

⛔ Request **không bao giờ tới service backend** nếu token invalid.

---

## Bước 3 – Authorization / RBAC

Trong `SecurityConfig`:

```java
.pathMatchers("/me/**").authenticated()
.pathMatchers("/admin/**").hasRole("ADMIN")
```

Gateway check:

* role trong token
* route policy

⛔ Không đủ quyền → **403 tại gateway**

---

## Bước 4 – GlobalFilter inject user context

`UserContextGlobalFilter` chạy:

* Lấy `JwtAuthenticationToken`
* Extract:

  * `sub`
  * `realm_access.roles`
  * `email`
* Inject headers:

```http
X-User-Id: <uuid>
X-User-Roles: ADMIN,STAFF
X-User-Email: xxx
```

---

## Bước 5 – Route resolution

Gateway duyệt danh sách routes:

* `/me` match route `user-service`
* Gateway chọn `uri = http://spa-user-service:8082`

---

## Bước 6 – Forward request

Gateway forward request:

```http
GET http://spa-user-service:8082/me
X-User-Id: ...
X-User-Roles: ...
X-User-Email: ...
```

---

## Bước 7 – User Service xử lý

`spa-user-service`:

* KHÔNG verify JWT
* KHÔNG cần Spring Security
* Tin gateway là “trusted entry”

Controller đọc headers:

```java
@RequestHeader("X-User-Id") UUID userId
```

---

## Bước 8 – Response quay ngược

Response từ user-service → gateway → client.

Gateway không thay đổi response (trừ khi có filter).

---

# 4. Route config cố định trong `application.yml` có scale được không?

👉 **CÓ thể chạy production**, nhưng **KHÔNG phải kiến trúc scale hoàn chỉnh**.

### Hiện tại bạn đang dùng:

```yaml
uri: http://spa-user-service:8082
```

Điều này có nghĩa:

* Gateway **chỉ biết 1 endpoint cố định**
* Không biết có bao nhiêu instance
* Không load balance

---

# 5. Khi có nhiều instance của 1 service, request sẽ đi đâu?

### Với cấu hình hiện tại

❌ **Không có load balancing**

```text
Client → Gateway → spa-user-service:8082 (1 instance)
```

Nếu bạn chạy:

* 3 instance user-service
* Gateway **vẫn chỉ gửi vào 1 instance duy nhất**

---

# 6. Cách làm ĐÚNG để auto-scale

## Option A – Service Discovery (chuẩn microservices)

### Kiến trúc

* Gateway + Services đăng ký vào **Service Registry**
* Gateway route bằng **service name**
* Load balancing tự động

### Ví dụ

```yaml
uri: lb://spa-user-service
```

Gateway sẽ:

* resolve tất cả instance của `spa-user-service`
* round-robin / random / weighted

Bạn có thể dùng:

* Spring Cloud Discovery + Eureka
* Consul
* Kubernetes Service (khuyến nghị nếu dùng K8s)

---

## Option B – Kubernetes Native (khuyến nghị production)

Nếu deploy trên Kubernetes:

```yaml
uri: http://spa-user-service.default.svc.cluster.local
```

* K8s Service đứng trước nhiều pod
* Kube-proxy load balance
* Gateway không cần biết instance cụ thể

---

## Option C – External Load Balancer

Gateway gọi:

```
uri: http://user-service-lb.internal
```

LB sẽ route tới instance.

---

# 7. Đánh giá kiến trúc hiện tại của bạn

| Tiêu chí               | Đánh giá          |
| ---------------------- | ----------------- |
| Security boundary      | ✅ Rất đúng        |
| Gateway responsibility | ✅ Chuẩn           |
| JWT + RBAC             | ✅ Đúng hướng      |
| Context propagation    | ✅ Chuẩn           |
| Route config           | ⚠️ OK cho Phase 0 |
| Auto-scale             | ❌ Chưa            |

👉 **Hiện tại phù hợp Phase 0 / Phase 1**
👉 Sang Phase 2+ cần chuyển sang **service discovery / K8s service**

---

# 8. Tóm tắt ngắn gọn (để “chốt tư duy”)

* `spa-api-gateway` = **cổng bảo vệ + điều phối**
* Nó:

  * verify token
  * enforce policy
  * inject user context
  * forward request
* Các service phía sau:

  * không auth
  * không biết Keycloak
* Route static:

  * OK cho giai đoạn đầu
  * không auto-scale
* Scale thật:

  * dùng `lb://`
  * hoặc Kubernetes Service

---

Nếu bạn muốn, bước tiếp theo mình có thể:

* Vẽ **sequence diagram** riêng cho gateway flow
* Đề xuất **roadmap migrate gateway từ static route → discovery / K8s**
* Viết **gateway checklist production-ready** (rate limit, circuit breaker, retry)

Bạn muốn đi theo hướng nào tiếp?
