# AI Patient Case-Taking System

A professional healthcare case-management and clinical case-taking web application built with Spring Boot, Thymeleaf, and PostgreSQL.

> **Clinical Disclaimer:** This application is a software tool for recording clinician-entered clinical information. It is not a medically validated diagnostic system. All clinical information must be reviewed and verified by an appropriately qualified healthcare professional.

---

## Overview

The AI Patient Case-Taking System provides a complete clinical workflow:

- Patient registration and management
- Clinical case management
- Structured clinical encounters with case-taking workflow
- Symptoms, vitals, clinical examinations
- Assessment, diagnosis, treatment, and follow-up
- Appointment scheduling
- Document management
- Audit logging
- Role-based access control

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Security | Spring Security 6 |
| ORM | Spring Data JPA / Hibernate |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Templates | Thymeleaf 3 |
| Frontend | Bootstrap 5, Vanilla JavaScript |
| Build | Maven 3.9+ |
| Testing | JUnit 5, Mockito, Spring Boot Test |

---

## Requirements

- **Java:** 21+
- **Maven:** 3.9+
- **PostgreSQL:** 14+ (or Docker for development)
- **Docker:** Optional (for local database)

---

## Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd ai-patient-case-system
```

### 2. Set Up the Database

Using Docker (recommended for development):

```bash
docker-compose up -d
```

This starts PostgreSQL on port 5432 with:
- Database: `patientcase`
- Username: `patientcase`
- Password: `patientcase`

### 3. Configure Environment

Copy and edit the environment file:

```bash
cp .env.example .env
```

Or set environment variables directly:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/patientcase
export DATABASE_USERNAME=patientcase
export DATABASE_PASSWORD=patientcase
```

### 4. Run Database Migrations

Flyway runs automatically on startup. Migrations are in:
```
src/main/resources/db/migration/
├── V1__initial_schema.sql   # Schema creation
└── V2__demo_data.sql        # Demo data
```

### 5. Run the Application

```bash
mvn spring-boot:run
```

Or build and run the JAR:

```bash
mvn clean package -DskipTests
java -jar target/ai-patient-case-system-1.0.0.jar
```

The application starts at: **http://localhost:8080**

---

## Running Tests

```bash
mvn clean test
```

Tests use H2 in-memory database with `application-test.yml` configuration. No PostgreSQL required for tests.

---

## Demo Credentials

> All demo accounts use fictional data for demonstration only.

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `Admin@123` |
| Doctor | `dr.smith` | `Doctor@123` |
| Doctor | `dr.johnson` | `Doctor@123` |
| Nurse | `nurse.jones` | `Doctor@123` |
| Receptionist | `reception` | `Doctor@123` |

---

## Project Structure

```
src/
├── main/
│   ├── java/com/patientcase/
│   │   ├── PatientCaseApplication.java
│   │   ├── config/           # Security, Web configuration
│   │   ├── security/         # UserDetailsService
│   │   ├── auth/             # Login/logout controller
│   │   ├── user/             # User entity, service, repository
│   │   ├── patient/          # Patient management
│   │   ├── case_management/  # Clinical cases
│   │   ├── encounter/        # Clinical encounters, case-taking
│   │   ├── clinical/         # Symptoms, vitals, examination, diagnosis, treatment
│   │   ├── appointment/      # Appointment management
│   │   ├── document/         # Document management
│   │   ├── dashboard/        # Dashboard
│   │   ├── audit/            # Audit logging
│   │   └── common/           # Shared exceptions
│   └── resources/
│       ├── application.yml
│       ├── db/migration/
│       ├── templates/        # Thymeleaf templates
│       └── static/           # CSS, JavaScript
└── test/
    └── java/com/patientcase/
        ├── auth/             # Authentication tests
        ├── patient/          # Patient service tests
        ├── case_management/  # Case service tests
        └── security/         # Security access tests
```

---

## Security Notes

- Passwords are hashed with BCrypt (cost factor 12)
- CSRF protection is enabled on all state-changing requests
- Session management with concurrent session control
- Role-based access control (ADMIN, DOCTOR, NURSE, RECEPTIONIST)
- No plaintext passwords stored or logged
- Secure HTTP headers configured
- Document uploads validated for type and size
- Path traversal prevention on file storage

---

## Clinical Disclaimer

This application is a software tool for healthcare data management. It does not:
- Generate automatic diagnoses
- Replace clinical judgment
- Constitute medical advice

All clinical information must be reviewed and verified by an appropriately qualified healthcare professional before acting upon it.

---

## Limitations

- **Document Storage:** Files stored on local filesystem. For production, replace `DocumentService` with cloud storage (S3, Azure Blob, etc.)
- **No email/notifications:** Appointment reminders not implemented
- **No audit UI:** Audit logs stored in DB but no admin UI to view them
- **Single-tenant:** No multi-tenancy support
