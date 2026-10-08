> Business transactions are implemented and service proxies require no annotations. See [TRANSACTIONS.md](TRANSACTIONS.md) for proxies, all seven propagation modes, timeouts, rollback rules, and Road Rescue wiring.

# Sentinal Persistence Framework

Sentinal Persistence Framework is a lightweight Java persistence layer designed to reduce repetitive JDBC code while keeping database operations explicit and easy to understand.

The framework provides query construction, criteria-based filtering, joins, row-based data access, inserts, updates, generated-ID hydration, and schema-aware persistence.

It is used by the RoadRescue backend as the database access layer.

---

## Overview

The framework sits between application business logic and the relational database.

```text
Application
    |
    v
PersistenceQuery
    |
    v
PersistenceManager
    |
    v
PersistenceObject
    |
    +-- Row
    +-- Insert Operations
    +-- Update Operations
    |
    v
Relational Database
```

The application creates queries and rows using framework classes instead of writing repetitive JDBC boilerplate for common operations.

---

## Features

* **Query builder**
* **Criteria-based filtering**
* **Multiple-condition criteria**
* **Join support**
* **Explicit column selection**
* **Row-based result access**
* **Insert support**
* **Update support**
* **Generated ID hydration**
* **Last-modified timestamp hydration**
* **Multi-row persistence operations**
* **Schema-driven table metadata**
* **Foreign-key aware schema definitions**
* **Lightweight alternative to a full ORM**
* **Reusable across Java backend applications**

---

## Project Structure

```text
persistence_fw/
├── README.md
├── build.gradle
├── settings.gradle
└── src/
    ├── main/
    │   ├── java/
    │   └── resources/
    └── test/
        └── java/
```

---

## Requirements

* **Java 17**
* **Gradle**
* **JDBC-compatible relational database** (MySQL is used by RoadRescue)

---

## Build

Run:

```bash
./gradlew clean build
```

---

## Main Framework Classes

The primary application-facing classes are:

* `PersistenceManager`
* `PersistenceQuery`
* `PersistenceObject`
* `Row`
* `Criteria`
* `Join`

Each class has a specific responsibility:

* **PersistenceManager** $\rightarrow$ framework initialization and query/update execution
* **PersistenceQuery** $\rightarrow$ database query construction
* **PersistenceObject** $\rightarrow$ query results and pending row changes
* **Row** $\rightarrow$ one table row
* **Criteria** $\rightarrow$ filtering conditions
* **Join** $\rightarrow$ table join definitions

---

## Initialization

The persistence framework is initialized once during application startup.

```java
PersistenceManager.initialize(
    url,
    username,
    password
);
```

**Example:**

```java
PersistenceManager.initialize(
    "jdbc:mysql://localhost:3306/example",
    "root",
    "password"
);
```

> **Note:** Production credentials should come from a secure environment or runtime configuration rather than source code.

---

## Querying Data

Create a query using `PersistenceQuery`:

```java
PersistenceQuery query =
    new PersistenceQuery(
        "Users"
    )
    .where(
        new Criteria(
            "Users.UserID",
            Criteria.EQUAL,
            userID
        )
    );
```

Execute it using:

```java
PersistenceObject dataObject =
    PersistenceManager.get(
        query
    );
```

The returned `PersistenceObject` contains rows grouped by table.

---

## Reading Rows

Rows can be retrieved using:

```java
dataObject.getRows(
    "Users"
);
```

**Example:**

```java
for (Row row : dataObject.getRows("Users"))
{
    System.out.println(
        row.get("Name")
    );
}
```

A single row can also be retrieved where appropriate using framework row-access methods.

---

## Reading Values

String values can be read directly:

```java
String name = (String) row.get("Name");
```

Numeric database values should be handled through `Number`:

**Example (`long`):**

```java
long userID = ((Number) row.get("UserID")).longValue();
```

**Integer values:**

```java
int status = ((Number) row.get("Status")).intValue();
```

**Double values:**

```java
double latitude = ((Number) row.get("Latitude")).doubleValue();
```

This keeps numeric conversion safe across JDBC driver implementations.

---

## Criteria

`Criteria` is used to filter query results.

**Example:**

```java
new Criteria(
    "Mechanic.AvailabilityStatus",
    Criteria.EQUAL,
);
```

This represents:

```sql
Mechanic.AvailabilityStatus = 1
```

### Multiple Criteria

Conditions can be combined using `Criteria.and(...)`:

```java
Criteria criteria =
    Criteria.and(
        new Criteria(
            "Users.Status",
            Criteria.EQUAL,
        ),
        new Criteria(
            "Users.UserType",
            Criteria.EQUAL,
            2
        )
    );
```

Use it in a query:

```java
PersistenceQuery query =
    new PersistenceQuery(
        "Users"
    )
    .where(
        criteria
    );
```

---

## Example Mechanic Query


```java
PersistenceQuery query =
    new PersistenceQuery(
        "Mechanic"
    )
    .where(
        new Criteria(
            "Mechanic.AvailabilityStatus",
            Criteria.EQUAL,
            MechanicAvailabilityStatus.AVAILABLE.getCode()
        )
    );
```

Execute:

```java
PersistenceObject dataObject =
    PersistenceManager.get(
        query
    );
```

Read available mechanics:

```java
for (Row mechanic : dataObject.getRows("Mechanic"))
{
    long mechanicID =
        ((Number) mechanic.get("MechanicID")).longValue();

    System.out.println(mechanicID);
}
```

---

## Column Selection

Columns can be explicitly selected:

```java
PersistenceQuery query =
    new PersistenceQuery(
        "Mechanic"
    )
    .select(
        "Mechanic",
        "MechanicID"
    )
    .select(
        "Mechanic",
        "UserID"
    )
    .select(
        "Mechanic",
        "Latitude"
    )
    .select(
        "Mechanic",
        "Longitude"
    );
```

This keeps query results focused on the data required by the application.

---

## Joins

The framework supports joins between tables:

```java
PersistenceQuery query =
    new PersistenceQuery(
        "Mechanic"
    )
    .join(
        Join.INNER,
        "MechanicService",
        "Mechanic",
        "MechanicID",
        "MechanicService",
        "MechanicID"
    );
```

This joins `Mechanic.MechanicID` with `MechanicService.MechanicID`.

### Join With Selected Columns

```java
PersistenceQuery query =
    new PersistenceQuery(
        "Mechanic"
    )
    .join(
        Join.INNER,
        "MechanicService",
        "Mechanic",
        "MechanicID",
        "MechanicService",
        "MechanicID"
    )
    .select(
        "Mechanic",
        "MechanicID"
    )
    .select(
        "Mechanic",
        "UserID"
    )
    .select(
        "MechanicService",
        "ServiceType"
    );
```

---

## Creating a Row

A new row is created with:

```java
Row row = new Row("Users");
```

Set values using:

```java
row.set("Name", "Example User");
```

**Example:**

```java
Row user = new Row("Users");

user.set("Name", "Example User");
user.set("Email", "user@example.com");
user.set("Phone", "9999999999");
user.set("UserType", 1);
user.set("Status", 1);
```

---

## Insert Operation

To insert a new row:

```java
PersistenceObject dataObject = new PersistenceObject();

dataObject.addRowForInsert(user);

PersistenceManager.update(dataObject);
```

After the update completes, generated database values such as auto-increment IDs can be hydrated back into the same row:

```java
long userID = ((Number) user.get("UserID")).longValue();
```

---

## Generated Values

The framework supports hydration of generated values after persistence. This can include:

* Auto-increment primary keys
* Last-modified timestamps

**Example:**

```java
PersistenceManager.update(dataObject);

long generatedID = ((Number) user.get("UserID")).longValue();
```

The application can continue using the same `Row` instance after insertion.

---

## Updating a Row

Modify an existing row:

```java
user.set("Name", "Updated User");
```

Add it to a persistence object:

```java
PersistenceObject dataObject = new PersistenceObject();

dataObject.updateRow(user);
```

Persist it:

```java
PersistenceManager.update(dataObject);
```

---

## Multiple Row Operations

A major use case is updating several related rows together.

```java
PersistenceObject dataObject = new PersistenceObject();

dataObject.updateRow(serviceRequest);
dataObject.updateRow(mechanic);
dataObject.addRowForInsert(assignment);

PersistenceManager.update(dataObject);
```

---



The application may prepare:

```java
serviceRequest.set(
    "Status",
    ServiceRequestStatus.ASSIGNED.getCode()
);

mechanic.set(
    "AvailabilityStatus",
    MechanicAvailabilityStatus.RESERVED.getCode()
);
```

Create an assignment row:

```java
Row assignment = new Row("MechanicAssignment");

assignment.set("ServiceRequestID", serviceRequestID);
assignment.set("MechanicID", mechanicID);
assignment.set("Status", MechanicAssignmentStatus.PENDING.getCode());
assignment.set("AssignedTime", System.currentTimeMillis());
```

Persist all related changes:

```java
PersistenceObject dataObject = new PersistenceObject();

dataObject.updateRow(serviceRequest);
dataObject.updateRow(mechanic);
dataObject.addRowForInsert(assignment);

PersistenceManager.update(dataObject);
```

---

## PersistenceObject

`PersistenceObject` represents both:

* Query results
* Rows waiting to be inserted or updated

Typical operations include:

```java
dataObject.getRows("Users");
dataObject.getRow("Users");
dataObject.addRowForInsert(row);
dataObject.updateRow(row);
```

This allows one object model to be used throughout the persistence lifecycle.

---

## Direct Query Execution

The framework also provides:

```java
PersistenceManager.execute(query);
```

for supported query execution operations where a returned `PersistenceObject` is not required.

---

## Schema-Driven Persistence

The framework uses schema metadata to describe tables and columns.

A schema can define:
* Table name
* Column name
* JDBC type
* Size
* Nullability
* Primary key
* Unique constraint
* Auto increment
* Foreign key
* Last modified timestamp

### Example Table Definition

```xml
<Table name="Users">

    <Column autoIncrement="true" jdbcType="BIGINT" name="UserID" nullable="false" primaryKey="true" unique="true"/>

    <Column jdbcType="VARCHAR" name="Name" nullable="false" size="255"/>

    <Column jdbcType="VARCHAR" name="Email" nullable="false" size="255" unique="true"/>

    <Column jdbcType="VARCHAR" name="Phone" nullable="false" size="20"/>

    <Column jdbcType="INTEGER" name="UserType" nullable="false"/>

    <Column jdbcType="INTEGER" name="Status" nullable="false"/>

    <Column jdbcType="BIGINT" lastModifiedTime="true" name="LastModifiedTime" nullable="false"/>

</Table>
```

### Foreign Keys

Schema metadata can describe relationships between tables.

Example concepts:
* `Mechanic.UserID` $\rightarrow$ `Users.UserID`
* `MechanicAssignment.ServiceRequestID` $\rightarrow$ `ServiceRequest.ServiceRequestID`

This keeps relational structure explicit in the persistence model.

---

## Separation of Responsibilities

* **The persistence framework is responsible for:** query construction, criteria handling, joins, row mapping, insert operations, update operations, schema metadata, generated-value hydration, and database interaction.
* **The application is responsible for:** business logic, validation, workflow rules, domain state transitions, and authorization decisions.

---

## Design Goals

The framework was designed to:

* Reduce repetitive JDBC code
* Keep persistence operations explicit
* Provide readable query construction
* Avoid hidden ORM behavior
* Keep database interaction understandable
* Support schema metadata
* Support related row operations
* Remain lightweight
* Stay reusable across different applications

---

## Why Not a Full ORM?

The goal of Sentinal Persistence Framework is not to replace all features of Hibernate or JPA. Instead, it focuses on a smaller set of core concepts:

* Table
* Row
* Query
* Criteria
* Join
* Insert
* Update

This keeps persistence behavior explicit and easier to inspect. Application developers remain aware of the underlying relational data model.

---

## Advantages of the Row Model

Using `Row` keeps the mapping close to the database:

```java
Row mechanic = new Row("Mechanic");
```

Values correspond directly to table columns:

```java
mechanic.set("AvailabilityStatus", 1);
```

This avoids the need to create a dedicated Java entity class for every database table.

---

## Numeric Value Handling

JDBC drivers may return numeric values using different `Number` implementations. For that reason, application code should use:

```java
((Number) value).longValue();
// OR
((Number) value).intValue();
// OR
((Number) value).doubleValue();
```

instead of casting directly to specific wrapper classes.

---

## Error Handling

Persistence operations may fail because of:

* Invalid schema configuration
* Database connection failures
* Duplicate unique values
* Foreign-key violations
* Invalid queries
* Invalid column names
* Database unavailability

Applications should handle persistence failures at appropriate service boundaries.

---

## Testing

Run framework tests using:

```bash
./gradlew test
```

Run the complete build using:

```bash
./gradlew clean build
```

Tests should cover areas such as:
* Query construction
* Criteria generation
* Joins
* Inserts
* Updates
* Generated primary keys
* Generated last-modified values
* Null handling
* Numeric values
* Schema parsing
* Row hydration
* Multiple-row operations

---

## Deployment

The persistence framework is packaged as a reusable Java JAR.

Build:

```bash
./gradlew clean build
```

Typical output:

```text
build/
└── libs/
    └── persistence_fw.jar
```

The JAR can then be consumed by a backend application such as RoadRescue.

---

## Current Scope

The current framework focuses on:
* Relational database access
* Table-based data models
* Explicit query construction
* Insert/update workflows
* Schema-driven persistence

It is intentionally smaller than large ORM frameworks and is currently designed around the needs of Java backend applications such as RoadRescue.

---

---

## Future Improvements

Possible future improvements include:

* Additional join types
* Richer criteria operators
* Ordering support improvements
* Pagination helpers
* Batch insert support
* Batch update support
* Optimistic locking
* Improved schema validation
* Migration support
* Connection-pool integration improvements
* Query performance metrics
* SQL diagnostics
* Stronger startup validation
* Improved error messages
* Additional database compatibility
* Integration testing across multiple JDBC databases

---

## Project Purpose

Sentinal Persistence Framework was created to understand and implement the responsibilities normally hidden behind larger persistence frameworks.

The project demonstrates concepts such as:
* JDBC abstraction
* Query construction
* Relational schema metadata
* Row mapping
* Insert/update tracking
* Generated key handling
* Join construction
* Criteria handling
* Framework/application separation

---

## Related Projects

* **Sentinal REST Framework**: Provides HTTP routing, parameter extraction, Java type conversion, and method invocation (`rest_fw`).
* **Sentinal Security Framework**: Provides authentication, authorization, security context, and rate limiting (`security_fw`).
* **RoadRescue Backend**: Uses Sentinal Persistence Framework for application data storage (`road_rescue_server`).

---

## Status

The framework is under active development and is currently used by the RoadRescue backend project.

---
