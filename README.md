# Virtual Classroom – Backend (Spring Boot 3, Java 17, PostgreSQL, STOMP)

## Run
1. Create the database: `CREATE DATABASE classroom;`
2. Edit `src/main/resources/application.properties` (DB user/password, upload folder).
3. `mvn spring-boot:run` (or `mvn package` then `java -jar target/virtual-classroom-1.0.0.jar`)
4. Default admin (ADM-1): `admin@college.edu` / `Admin@123` (change in properties before first start).
Tables are created automatically (`ddl-auto=update`).

## Login (all JSON, session cookie)
`POST /api/auth/login` `{email, password, role: "ADMIN"|"TEACHER"|"STUDENT"}` -> keep the `JSESSIONID` cookie.
`POST /api/auth/logout`. Teachers/students must call `POST /api/common/change-password` first; other APIs return 403 until then.

## REST endpoints
**Admin** (`/api/admin`, role ADMIN)
| Req | Endpoint |
|---|---|
| ADM-3/4 | `POST /teachers`, `POST /students` |
| ADM-5 | `GET /users?role=TEACHER\|STUDENT`, `PUT /users/{id}`, `PATCH /users/{id}/deactivate`, `PATCH /users/{id}/activate` |
| ADM-6 | `POST /users/{id}/reset-password` `{newPassword}` |
| ADM-7 | `POST/GET /classes`, `PUT/DELETE /classes/{id}` |
| ADM-8 | `PUT /classes/{classId}/teacher/{teacherId}` (or `teacherId` in the class body) |
| ADM-9 | `GET/POST/DELETE /classes/{classId}/students[/{studentId}]` |
| ADM-10/11/12 | `GET /sessions`, `GET /login-history?limit=`, `GET /dashboard` |
| ADM-13 | deactivated users fail login (`AppUserDetails.isEnabled`) and are cut off at every room/class check (`AccessService.ensureActive`) |

**Teacher** (`/api/teacher`, role TEACHER)
`GET /classes` (TCH-2) · `POST /classes/{id}/sessions` start (TCH-3) · `POST /sessions/{id}/end` (TCH-4) ·
`PUT /sessions/{id}/drawing/{studentId}`, `DELETE /sessions/{id}/drawing` (TCH-6) ·
`PUT|DELETE /sessions/{id}/mute/{studentId}`, `DELETE /sessions/{id}/chat` (TCH-8) ·
`DELETE /sessions/{id}/students/{studentId}` (TCH-10) · `GET /sessions/{id}/attendance` (TCH-13) ·
`POST /classes/{id}/materials` multipart field `file` (TCH-11) · `DELETE /materials/{id}` (TCH-12) ·
`GET /sessions/{id}/whiteboard.png|.pdf` (TCH-15)

**Student** (`/api/student`): `GET /classes` (STD-2), `GET /attendance?classId=` (STD-11)

**Common** (`/api/common`, teacher of the class or enrolled student only, else 403)
`GET /me` · `POST /change-password` (TCH-1/STD-1) · `GET /classes/{id}/sessions` · `GET /classes/{id}/active-session` (STD-3, 204 if none) ·
`GET /sessions/{id}/state` (STD-4/12, SYS-13) · `GET /sessions/{id}/participants` (TCH-9/STD-8) ·
`GET /sessions/{id}/replay` (TCH-14/STD-10, SYS-17) · `GET /classes/{id}/materials`, `GET /materials/{id}/download` (STD-9, SYS-9)

## WebSocket (STOMP at `ws://host:8080/ws`, uses the login cookie)
Client -> server: `/app/room/{id}/join`, `/leave`, `/draw`, `/chat`
- draw: `{action:"draw", strokeId:"s1", color:"#ff0000", width:3, eraser:false, points:[[x,y],...]}` – canvas logical size **1280x720**; send a batch every ~100 ms or at stroke end (max 500 points) and repeat the last point at the start of the next batch of the same stroke. Teacher only: `{action:"clear"}`.
- chat: `{text:"hello"}`

Subscribe: `/topic/room.{id}.draw`, `.chat`, `.presence`, `.control`, and `/user/queue/errors`.
`control` messages: `{type:"drawing", userId}` · `{type:"muted", userId, muted}` · `{type:"kicked", userId}` · `{type:"ended"}`.
Chat messages carry `senderName, role, time` (SYS-14).

**Client flow (join & reconnect, STD-12):** connect -> subscribe to the 4 topics + errors -> send `/join` -> `GET /api/common/sessions/{id}/state` and redraw `strokes`, show `chat`. Do the same after every automatic reconnect (stompjs `reconnectDelay`).

## Layers
controller -> service -> repository (+ entity/dto). Security: `SecurityConfig` (URL roles, BCrypt), `AuthService` (role match, 5-attempt lock, login history), `AccessService` (class membership), `StompSecurityInterceptor` (every WebSocket subscribe/send).

## Known simplifications
- CSRF is disabled (demo over plain HTTP). Enable it or use HTTPS before real use.
- Live room state (online users, drawer, muted, removed) is in memory; sessions left open are closed on restart.
- Uploads are checked by file extension and size only.
- A network drop creates a new attendance row when the user reconnects.

## Frontend (dark theme, served by Spring Boot from `src/main/resources/static`)
Open http://localhost:8080 after starting the app. No Node/npm needed and no external CDNs (works on a college LAN).

| Page | URL |
|---|---|
| Role choice | `/` |
| Login | `/login.html?role=ADMIN\|TEACHER\|STUDENT` |
| First-login password change | `/change-password.html` |
| Admin console | `/admin/home.html` |
| Teacher dashboard | `/teacher/home.html` |
| Student dashboard | `/student/home.html` |
| Live room (whiteboard + chat) | `/room.html?session=ID` |
| Replay | `/replay.html?session=ID` |
| 403 page | `/error/403.html` |

Quick demo: log in as admin -> create a teacher, a student and a class (assign teacher, enroll student) -> log in as the teacher in one browser and the student in another (or a private window) -> teacher clicks "Start live class" -> student clicks "Join live class".

Live Demo Link :
https://virtual-classroom-620k.onrender.com
