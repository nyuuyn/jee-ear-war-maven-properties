# jee-ear-war-maven-properties

A minimal working example of **Maven property filtering inside `jboss-web.xml`** for a JEE application packaged as an EAR containing a WAR, with full support for **IntelliJ IDEA exploded deployment** to WildFly / JBoss EAP.

---

## What This Project Demonstrates

Maven's resource filtering lets you inject build-time values (properties) into deployment descriptors. This project shows how to do that specifically for `jboss-web.xml`, which lives inside `WEB-INF/` of a WAR that is itself nested inside an EAR.

The challenge is that `WEB-INF/` is not a standard Maven resource directory — extra configuration is required to opt it into filtering. This project also ensures the setup works correctly when deploying an **exploded EAR** from IntelliJ, where the WAR is unpacked as a directory rather than a `.war` file.

---

## Project Structure

```
jee-ear-war-maven-properties/
├── pom.xml                          # Parent POM — defines properties & plugin versions
├── hello-world-impl/                # WAR module
│   ├── pom.xml                      # Configures maven-war-plugin with WEB-INF filtering
│   └── src/main/
│       ├── java/com/example/hello/
│       │   └── HelloServlet.java    # Simple servlet at /hello
│       └── webapp/
│           ├── index.jsp
│           └── WEB-INF/
│               ├── web.xml
│               └── jboss-web.xml    # Contains ${security.domain} placeholder
└── hello-world-app/                 # EAR module
    ├── pom.xml                      # Configures maven-ear-plugin
    └── src/main/application/
        └── META-INF/
            └── application.xml
```

---

## How the Filtering Works

### 1. Define the property in the parent POM

```xml
<!-- pom.xml (parent) -->
<properties>
    <security.domain>mySecurityDomain</security.domain>
</properties>
```

### 2. Enable filtering for WEB-INF in the WAR module

By default, Maven does not filter files under `src/main/webapp/WEB-INF/`. The `maven-war-plugin` must be told explicitly via `<webResources>`:

```xml
<!-- hello-world-impl/pom.xml -->
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

### 3. Use the placeholder in jboss-web.xml

```xml
<!-- hello-world-impl/src/main/webapp/WEB-INF/jboss-web.xml -->
<jboss-web ...>
    <security-domain>${security.domain}</security-domain>
</jboss-web>
```

### 4. Maven replaces the placeholder at build time

After `mvn package`, the output contains the resolved value:

```xml
<!-- hello-world-impl/target/hello-world-impl/WEB-INF/jboss-web.xml -->
<jboss-web ...>
    <security-domain>mySecurityDomain</security-domain>
</jboss-web>
```

The EAR then bundles the already-filtered WAR, so the resolved value propagates automatically.

---

## IntelliJ Exploded Deployment

When deploying an **exploded EAR** from IntelliJ, the WAR is unpacked as a directory (`hello-world-impl.war/`) inside the EAR directory. Two things must be configured correctly for this to work:

### Ensure the WAR is named to match application.xml

The `maven-ear-plugin` by default names the bundled WAR using Maven's artifact coordinates (e.g. `com.example-hello-world-impl-1.0.0-SNAPSHOT.war`), which does not match the `<web-uri>` declared in `application.xml`. Use `<bundleFileName>` to align them:

```xml
<!-- hello-world-app/pom.xml -->
<webModule>
    <groupId>com.example</groupId>
    <artifactId>hello-world-impl</artifactId>
    <bundleFileName>hello-world-impl.war</bundleFileName>
    <contextRoot>/hello-world</contextRoot>
    <unpack>true</unpack>
</webModule>
```

### application.xml references the same name

```xml
<!-- hello-world-app/src/main/application/META-INF/application.xml -->
<module>
    <web>
        <web-uri>hello-world-impl.war</web-uri>
        <context-root>/hello-world</context-root>
    </web>
</module>
```

WildFly accepts `hello-world-impl.war` as both a packed `.war` file and an unpacked directory with that name.

---

## Architecture Documentation

A full [arc42](https://arc42.org) architecture documentation is available at:

📄 [`docs/arc42/arc42-documentation.md`](docs/arc42/arc42-documentation.md)

It covers system scope and context, building block view, runtime and deployment views, architecture decisions (ADRs), quality requirements, and a glossary.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.6+ |
| WildFly / JBoss EAP | WildFly 27+ or JBoss EAP 8+ (Jakarta EE 10) |
| IntelliJ IDEA | Any recent version (optional) |

---

## Build

```bash
mvn clean package
```

The filtered EAR is produced at:

```
hello-world-app/target/hello-world.ear
```

---

## Deploy

### Via WildFly Maven plugin

Ensure WildFly is running, then:

```bash
mvn wildfly:deploy -pl hello-world-app
```

### Via IntelliJ exploded deployment

1. Open **Run/Edit Configurations**
2. Add a **JBoss/WildFly Local** run configuration
3. On the **Deployment** tab, add `ear:ear exploded`
4. Run `mvn package` before the first deployment so the `target/` directory is populated with filtered files
5. Start the server — IntelliJ will deploy from `hello-world-app/target/hello-world/`

### Verify

Navigate to [http://localhost:8080/hello-world/hello](http://localhost:8080/hello-world/hello)

---

## Overriding Properties at Build Time

The property can be overridden on the command line without touching any source file:

```bash
mvn clean package -Dsecurity.domain=productionDomain
```

This makes the pattern suitable for environment-specific builds (dev / test / prod).
