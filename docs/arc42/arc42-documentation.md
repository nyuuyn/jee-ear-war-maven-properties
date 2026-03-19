# arc42 Architecture Documentation

## jee-ear-war-maven-properties

**Version:** 1.0.0-SNAPSHOT
**Date:** 2026-03-19
**Status:** Active — migrated to Jakarta EE 10 / Java 17

---

> arc42, the template for documentation of software and system architecture.
> Template Version 8.2 EN. (based upon AsciiDoc version), January 2023
> Created, maintained and © by Dr. Peter Hruschka, Dr. Gernot Starke and contributors.

---

# Table of Contents

1. [Introduction and Goals](#1-introduction-and-goals)
2. [Architecture Constraints](#2-architecture-constraints)
3. [System Scope and Context](#3-system-scope-and-context)
4. [Solution Strategy](#4-solution-strategy)
5. [Building Block View](#5-building-block-view)
6. [Runtime View](#6-runtime-view)
7. [Deployment View](#7-deployment-view)
8. [Cross-cutting Concepts](#8-cross-cutting-concepts)
9. [Architecture Decisions](#9-architecture-decisions)
10. [Quality Requirements](#10-quality-requirements)
11. [Risks and Technical Debt](#11-risks-and-technical-debt)
12. [Glossary](#12-glossary)

---

## 1. Introduction and Goals

### 1.1 Requirements Overview

This project is a **reference implementation** that demonstrates how to correctly configure Maven property filtering for JEE applications packaged as an EAR containing a WAR module. It specifically solves the challenge of injecting build-time Maven properties into deployment descriptors located in `WEB-INF/` — a non-standard resource directory that Maven does not filter by default.

**Core functional requirement:**
The `jboss-web.xml` deployment descriptor inside `WEB-INF/` must contain the resolved value of `${security.domain}` at runtime. This value must be injectable at build time without modifying source code.

**Secondary goals:**
- Serve as a minimal, working example of EAR/WAR multi-module Maven project structure
- Support environment-specific builds (dev, test, production) via Maven property overrides
- Enable exploded EAR deployment from IntelliJ IDEA to WildFly/JBoss EAP

### 1.2 Quality Goals

| Priority | Quality Goal        | Motivation                                                                 |
|----------|---------------------|----------------------------------------------------------------------------|
| 1        | Correctness         | Filtered property values must appear exactly in the built artifact         |
| 2        | Reproducibility     | Any environment can override properties via `-D` flags without code changes|
| 3        | Simplicity          | Minimal project serving as a clear, understandable reference example       |
| 4        | IDE Compatibility   | Exploded deployment from IntelliJ must work without manual file editing    |

### 1.3 Stakeholders

| Role              | Expectations                                                                |
|-------------------|-----------------------------------------------------------------------------|
| JEE Developer     | Understand and reuse the Maven property filtering pattern in own projects    |
| DevOps / Build Engineer | Understand how to override properties for different target environments |
| IntelliJ IDEA User| Learn how to configure exploded EAR deployment to WildFly                  |

---

## 2. Architecture Constraints

### 2.1 Technical Constraints

| Constraint          | Background / Motivation                                                      |
|---------------------|------------------------------------------------------------------------------|
| Java 17             | Baseline JVM version; required by Jakarta EE 10                             |
| Jakarta EE 10       | Target API set; provided by WildFly 27+ / JBoss EAP 8                      |
| Maven 3.6+          | Required by the plugin versions used (maven-war-plugin 3.3.2, maven-ear-plugin 3.3.0) |
| WildFly 27+ / JBoss EAP 8 | Runtime target; JBoss-specific `jboss-web.xml` is only understood by these servers |
| EAR packaging       | Required by the deployment scenario; WAR must be bundled inside an EAR      |

### 2.2 Organizational Constraints

| Constraint                            | Background                                                       |
|---------------------------------------|------------------------------------------------------------------|
| Single-repository multi-module Maven  | Standard convention for tightly coupled EAR/WAR projects         |
| No runtime environment setup in source | Environment configuration is injected at build time only         |

---

## 3. System Scope and Context

### 3.1 Business Context

```
┌─────────────────────────────────────────────────────────────────┐
│                        Build Environment                         │
│                                                                  │
│  Developer / CI Pipeline                                         │
│       │                                                          │
│       │  mvn clean package [-Dsecurity.domain=<value>]          │
│       ▼                                                          │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │           jee-ear-war-maven-properties (Maven Build)    │    │
│  │                                                          │    │
│  │  Reads: Maven properties (pom.xml + -D overrides)       │    │
│  │  Produces: hello-world.ear (with filtered descriptors)  │    │
│  └──────────────────────────┬──────────────────────────────┘    │
│                             │                                    │
└─────────────────────────────┼────────────────────────────────────┘
                              │  Deployment artifact
                              ▼
                  ┌───────────────────────┐
                  │  WildFly / JBoss EAP  │
                  │  (Application Server) │
                  └───────────┬───────────┘
                              │  HTTP
                              ▼
                  ┌───────────────────────┐
                  │    HTTP Client        │
                  │ (Browser / Test Tool) │
                  └───────────────────────┘
```

**External interfaces:**

| Neighbour             | Description                                                              |
|-----------------------|--------------------------------------------------------------------------|
| GitHub Actions        | Runs `mvn clean verify` on every push and pull request to `master`       |
| Maven / CI Pipeline   | Executes the build; provides property values via `-D` flags or profiles  |
| WildFly / JBoss EAP   | Hosts the deployed EAR; reads and enforces `jboss-web.xml` security domain |
| HTTP Client           | Accesses the deployed servlet at `http://<host>:8080/hello-world/hello`  |

### 3.2 Technical Context

```
┌────────────────────────────────────────────────────┐
│                  Maven Build System                 │
│                                                     │
│  pom.xml (parent)                                   │
│  ├── hello-world-impl/pom.xml  (WAR)                │
│  │   └── maven-war-plugin (with webResources filter)│
│  └── hello-world-app/pom.xml   (EAR)               │
│      └── maven-ear-plugin (unpack=true)             │
└───────────────────────┬────────────────────────────┘
                        │ produces
                        ▼
              hello-world.ear
              └── hello-world-impl.war/   (unpacked)
                  ├── WEB-INF/
                  │   ├── web.xml
                  │   └── jboss-web.xml   (filtered)
                  ├── index.jsp
                  └── classes/
                      └── HelloServlet.class
```

---

## 4. Solution Strategy

### Core Strategy

The key challenge is that `WEB-INF/` is not a Maven resource directory, so standard Maven resource filtering does not apply to files within it. The solution uses the `maven-war-plugin`'s `<webResources>` configuration to explicitly declare `src/main/webapp/WEB-INF` as an additional filtered resource set.

| Problem                                              | Solution                                                                     |
|------------------------------------------------------|------------------------------------------------------------------------------|
| Maven does not filter `WEB-INF/` by default          | Declare it as a `<webResources>` entry with `<filtering>true</filtering>`   |
| IntelliJ exploded EAR needs consistent naming        | Set `bundleFileName` in EAR plugin and align with `application.xml`         |
| Environment-specific security domain at deploy time  | Define default in parent POM; override with `-Dsecurity.domain=<value>`     |
| WAR must be accessible as directory for exploded EAR | Set `<unpack>true</unpack>` in the EAR plugin WAR module configuration      |

---

## 5. Building Block View

### 5.1 Level 1 — Overall System

```
┌──────────────────────────────────────────────────────────────┐
│              jee-ear-war-maven-properties                    │
│                  (Parent Maven Project)                      │
│                                                              │
│  ┌────────────────────────┐  ┌────────────────────────────┐  │
│  │   hello-world-impl     │  │    hello-world-app         │  │
│  │   (WAR Module)         │  │    (EAR Module)            │  │
│  └────────────────────────┘  └────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### 5.2 Level 2 — WAR Module (hello-world-impl)

**Responsibility:** Implement and package the web application with filtered deployment descriptors.

```
hello-world-impl/
└── src/main/
    ├── java/com/example/hello/
    │   └── HelloServlet.java        ← HTTP GET handler at /hello
    └── webapp/
        ├── index.jsp                ← Welcome page with link to servlet
        └── WEB-INF/
            ├── web.xml              ← Standard servlet descriptor (welcome file)
            └── jboss-web.xml        ← JBoss-specific descriptor with ${security.domain}
```

| Component         | Responsibility                                                      |
|-------------------|---------------------------------------------------------------------|
| `HelloServlet`    | Handles `GET /hello`, returns HTML response                         |
| `index.jsp`       | Landing page at context root `/hello-world/`                        |
| `web.xml`         | Declares `index.jsp` as welcome file; no servlet mappings (uses annotation) |
| `jboss-web.xml`   | Sets the JBoss security domain using a Maven property placeholder   |

### 5.3 Level 2 — EAR Module (hello-world-app)

**Responsibility:** Bundle the WAR into an EAR with correct naming and context root for WildFly deployment.

```
hello-world-app/
└── src/main/application/
    └── META-INF/
        └── application.xml      ← Declares the bundled WAR module and context root
```

| Component          | Responsibility                                                      |
|--------------------|---------------------------------------------------------------------|
| `application.xml`  | Declares `hello-world-impl.war` with context root `/hello-world`   |
| EAR plugin config  | Unpacks WAR (`unpack=true`), sets `bundleFileName`, sets `contextRoot` |

---

## 6. Runtime View

### 6.1 Build-time Property Filtering

```
Sequence: Maven Build with Property Filtering

Developer                 Maven Build               File System
    │                         │                         │
    │  mvn clean package       │                         │
    │  [-Dsecurity.domain=X]  │                         │
    │─────────────────────────>│                         │
    │                         │                         │
    │                         │  Read pom.xml properties│
    │                         │  (security.domain=...)  │
    │                         │<────────────────────────│
    │                         │                         │
    │                         │  Process webResources   │
    │                         │  (filter WEB-INF/*.xml) │
    │                         │─────────────────────────>
    │                         │                         │
    │                         │  jboss-web.xml:         │
    │                         │  ${security.domain}     │
    │                         │  → mySecurityDomain     │
    │                         │<────────────────────────│
    │                         │                         │
    │                         │  Package WAR            │
    │                         │  Package EAR (unpack)   │
    │                         │─────────────────────────>
    │                         │                         │
    │  hello-world.ear ready  │                         │
    │<─────────────────────────│                         │
```

### 6.2 HTTP Request Handling

```
HTTP Client
    │
    │  GET http://localhost:8080/hello-world/hello
    │
    ▼
WildFly (reads jboss-web.xml security-domain at startup)
    │
    │  Route to context /hello-world
    │  Dispatch to HelloServlet (mapped to /hello)
    │
    ▼
HelloServlet.doGet()
    │
    │  Returns: <html>...<h1>Hello World!</h1>...</html>
    │
    ▼
HTTP Client receives 200 OK with HTML body
```

---

## 7. Deployment View

### 7.1 Target Infrastructure

```
┌──────────────────────────────────────────────────────────┐
│               WildFly / JBoss EAP Server                  │
│               (localhost or remote host)                  │
│                                                          │
│  Management: http://localhost:9990                       │
│  HTTP:       http://localhost:8080                       │
│                                                          │
│  deployments/                                            │
│  └── hello-world.ear                                     │
│      └── hello-world-impl.war/    (unpacked directory)   │
│          ├── WEB-INF/                                    │
│          │   ├── web.xml                                 │
│          │   └── jboss-web.xml  ← security.domain set   │
│          ├── index.jsp                                   │
│          └── classes/                                    │
│              └── com/example/hello/HelloServlet.class    │
└──────────────────────────────────────────────────────────┘
```

### 7.2 Build and Deployment Commands

**Standard build:**
```bash
mvn clean package
```

**Environment-specific build:**
```bash
mvn clean package -Dsecurity.domain=productionDomain
```

**Deploy to local WildFly (via WildFly Maven Plugin):**
```bash
mvn wildfly:deploy
```

**Undeploy:**
```bash
mvn wildfly:undeploy
```

### 7.3 CI Pipeline (GitHub Actions)

The project uses GitHub Actions for continuous integration. The workflow is defined in `.github/workflows/build.yml`.

**Triggers:** push and pull requests to `master`

**Steps:**
1. Check out source
2. Set up Java 17 (Temurin) with Maven dependency cache
3. Run `mvn clean verify` — compiles, runs unit tests, and packages the EAR

The workflow ensures every change is built and tested automatically before merging.

### 7.4 IntelliJ IDEA Exploded Deployment

For development, the project supports IntelliJ's exploded EAR deployment:

1. Run `mvn clean package` to populate filtered files in target directories
2. In IntelliJ, create a **WildFly Local** run configuration
3. Add deployment artifact: `hello-world-app:ear exploded`
4. Start the run configuration — IntelliJ deploys the exploded EAR from `hello-world-app/target/hello-world/`

**Why this works:** The EAR plugin's `<unpack>true</unpack>` setting causes the WAR to be extracted as a directory. The `bundleFileName` and `application.xml` `web-uri` both reference `hello-world-impl.war`, so IntelliJ recognizes the unpacked directory correctly.

---

## 8. Cross-cutting Concepts

### 8.1 Maven Property Filtering

The central cross-cutting concept of this project. Property filtering allows build-time injection of values into resource files using `${propertyName}` placeholders.

**Standard Maven filtering** applies to files in `src/main/resources/`. Files in `src/main/webapp/WEB-INF/` are **not** filtered by default.

**Solution pattern** used here:

```xml
<!-- In hello-world-impl/pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-war-plugin</artifactId>
    <configuration>
        <webResources>
            <resource>
                <directory>src/main/webapp/WEB-INF</directory>
                <filtering>true</filtering>
                <targetPath>WEB-INF</targetPath>
            </resource>
        </webResources>
    </configuration>
</plugin>
```

This explicitly adds `WEB-INF/` as a filtered web resource, making Maven process all `${...}` placeholders in that directory.

### 8.2 Environment Configuration

Properties are defined with defaults in the parent POM and overridden at build time:

| Property          | Default Value      | Override Mechanism                    |
|-------------------|--------------------|---------------------------------------|
| `security.domain` | `mySecurityDomain` | `-Dsecurity.domain=<value>` on `mvn`  |

Maven profiles can also be used for systematic environment management:
```bash
# Example: using a Maven profile for production
mvn clean package -Pprod
```

### 8.3 Jakarta EE 10 API Usage

All Jakarta EE APIs are declared with `provided` scope — the application server supplies them at runtime:

| API       | Spec Artifact                                  | Usage in project           |
|-----------|------------------------------------------------|----------------------------|
| Servlet   | `jakarta.platform:jakarta.jakartaee-api`       | `HelloServlet` base class  |
| CDI       | `jakarta.platform:jakarta.jakartaee-api`       | Available, not yet used    |
| JAX-RS    | `jakarta.platform:jakarta.jakartaee-api`       | Available, not yet used    |

All three APIs are covered by the single `jakarta.platform:jakarta.jakartaee-api:10.0.0` dependency with `provided` scope.

---

## 9. Architecture Decisions

### ADR-001: Use `<webResources>` for WEB-INF Filtering

**Context:**
`jboss-web.xml` in `WEB-INF/` must contain a build-time-resolved security domain name.

**Decision:**
Use the `maven-war-plugin`'s `<webResources>` configuration to explicitly include `WEB-INF/` as a filtered resource directory.

**Alternatives considered:**

| Alternative                             | Reason rejected                                                       |
|-----------------------------------------|-----------------------------------------------------------------------|
| Use a Maven resource directory instead  | Would require moving files outside standard `webapp/` structure       |
| Use a Maven filter file                 | More complex; still needs `<webResources>` for `WEB-INF/` targeting   |
| Use shell/Ant script post-processing    | Non-portable; breaks IDE build integration                            |
| Use environment variables at server runtime | Requires server-specific configuration outside the build artifact  |

**Consequences:**
- Pro: Standard Maven mechanism, portable, IDE-compatible
- Pro: Filtered value is baked into the artifact — consistent across deployments
- Con: Filtering happens at build time; changing the value requires a rebuild

---

### ADR-002: Use `<unpack>true</unpack>` in EAR Module

**Context:**
IntelliJ IDEA's WildFly integration deploys exploded artifacts. For an EAR, the contained WAR must also be in exploded (directory) form for IntelliJ to manage it correctly.

**Decision:**
Set `<unpack>true</unpack>` on the WAR module entry in the `maven-ear-plugin` configuration.

**Consequences:**
- Pro: Enables IntelliJ exploded EAR deployment without manual steps
- Pro: Faster incremental redeployment (no WAR re-packaging required)
- Con: Produces a directory instead of a `.war` file inside the EAR; servers must support exploded WARs inside EARs (WildFly/JBoss EAP do)

---

### ADR-003: Align `bundleFileName` with `application.xml`

**Context:**
The EAR plugin's `bundleFileName` controls the name of the WAR file (or directory) inside the EAR. The `application.xml` `web-uri` must match this name exactly for the server to map it correctly.

**Decision:**
Set `bundleFileName=hello-world-impl.war` in the EAR plugin and use `hello-world-impl.war` as the `web-uri` in `application.xml`.

**Consequences:**
- Pro: Decouples the artifact name from the Maven artifactId versioning
- Pro: IntelliJ recognizes the unpacked directory as the correct WAR deployment
- Con: Must keep `bundleFileName` and `application.xml` in sync manually

---

## 10. Quality Requirements

### 10.1 Quality Tree

```
Quality
├── Correctness
│   └── Property placeholder ${security.domain} must resolve at build time
├── Portability
│   ├── Build must work on any OS with Maven 3.6+ and Java 8+
│   └── Override must be possible via -D flags without source changes
├── Maintainability
│   ├── Project structure follows Maven conventions
│   └── Minimal code; easy to understand and adapt
└── IDE Compatibility
    └── IntelliJ exploded EAR deployment must work without workarounds
```

### 10.2 Quality Scenarios

| ID  | Scenario                                                      | Expected Result                                              |
|-----|---------------------------------------------------------------|--------------------------------------------------------------|
| Q1  | Developer runs `mvn clean package`                            | EAR built; `jboss-web.xml` contains `mySecurityDomain`       |
| Q2  | CI runs `mvn clean package -Dsecurity.domain=prodDomain`      | EAR built; `jboss-web.xml` contains `prodDomain`             |
| Q3  | Developer deploys exploded EAR from IntelliJ                  | Application accessible at `http://localhost:8080/hello-world/hello` |
| Q4  | New developer clones repo and runs `mvn clean package`        | Build succeeds without any additional configuration           |

---

## 11. Risks and Technical Debt

### 11.1 Risks

| ID  | Risk                                                      | Probability | Impact | Mitigation                                         |
|-----|-----------------------------------------------------------|-------------|--------|----------------------------------------------------|
| R1  | Security domain name hardcoded in POM default             | Low         | Medium | Use Maven profiles or CI variable injection        |
| R2  | WildFly plugin version incompatibility with newer servers | Medium      | Low    | Pin or regularly update `wildfly-maven-plugin` version |
| ~~R3~~  | ~~Java 8 EOL; newer JEE specs require Java 11+~~      | ~~Low~~     | ~~High~~ | **Resolved** — migrated to Jakarta EE 10 and Java 17 (2026-03-19) |

### 11.2 Technical Debt

| ID  | Item                                            | Impact | Effort to Resolve                              |
|-----|-------------------------------------------------|--------|------------------------------------------------|
| ~~TD1~~ | ~~Java 8 / JEE 7 — both past end-of-life~~ | ~~Medium~~ | **Resolved** — migrated to Jakarta EE 10 + Java 17 (2026-03-19) |
| TD2 | No Maven profiles for environment separation   | Low    | Add `dev`, `test`, `prod` profiles in parent POM |
| ~~TD3~~ | ~~No unit or integration tests~~           | ~~Low~~ | **Resolved** — added JUnit 5 + Mockito unit tests for `HelloServlet` (2026-03-19) |
| TD4 | CDI and JAX-RS dependencies declared but unused | Low    | Remove or implement features that use them     |

---

## 12. Glossary

| Term               | Definition                                                                                     |
|--------------------|-----------------------------------------------------------------------------------------------|
| **EAR**            | Enterprise Archive — a `.ear` file bundling multiple JEE modules (WARs, EJB-JARs, etc.)      |
| **WAR**            | Web Application Archive — a `.war` file containing a web application (servlets, JSPs, etc.)  |
| **Maven Filtering** | Maven feature that replaces `${property}` placeholders in resource files with resolved values |
| **webResources**   | `maven-war-plugin` configuration element for adding extra resource sets to the WAR with optional filtering |
| **jboss-web.xml**  | JBoss/WildFly-specific deployment descriptor for configuring security domain, context root, etc. |
| **security-domain**| JBoss/WildFly concept for a named security policy configuration used for authentication       |
| **Exploded deployment** | Deployment of an artifact as an unpacked directory structure rather than a compressed archive |
| **Jakarta EE**     | Successor to Java EE — the platform specification for enterprise Java applications, governed by the Eclipse Foundation. Version 10 requires Java 17+ and uses the `jakarta.*` package namespace. |
| **WildFly**        | Open-source Java application server by Red Hat, previously known as JBoss AS. WildFly 27+ supports Jakarta EE 10. |
| **JBoss EAP**      | JBoss Enterprise Application Platform — Red Hat's supported version of WildFly. EAP 8 supports Jakarta EE 10. |
| **Context Root**   | The URL path prefix at which a web application is accessible on the server                    |
| **bundleFileName** | EAR plugin configuration for the name of the WAR module inside the EAR archive               |
| **provided scope** | Maven dependency scope for libraries supplied by the runtime environment (application server) |