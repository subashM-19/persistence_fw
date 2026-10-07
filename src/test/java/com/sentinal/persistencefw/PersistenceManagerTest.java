package com.sentinal.persistencefw;

import com.sentinal.persistencefw.exception.OptimisticLockException;
import com.sentinal.persistencefw.exception.PersistenceException;
import com.sentinal.persistencefw.metadata.ColumnMetaData;
import com.sentinal.persistencefw.metadata.Schema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PersistenceManagerTest
{
	private static final String URL =
		"jdbc:h2:mem:persistence_test;DB_CLOSE_DELAY=-1";

	private static final String USERNAME = "sa";
	private static final String PASSWORD = "";

	private PersistenceManager persistenceManager;
	private Connection connection;

	@BeforeEach
	void setUp() throws Exception
	{
		Schema.clear();

		connection = DriverManager.getConnection(
			URL,
			USERNAME,
			PASSWORD
		);

		PersistenceManager.initialize(
			URL,
			USERNAME,
			PASSWORD
		);

		createTables();
		registerSchema();
		Schema.validate();
	}

	@AfterEach
	void tearDown() throws Exception
	{
		Schema.clear();

		if (connection != null)
		{
			connection.close();
		}
	}

	private void createTables() throws Exception
	{
		connection.createStatement().execute(
			"DROP ALL OBJECTS"
		);

		connection.createStatement().execute(
			"CREATE TABLE Company (" +
				"id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
				"name VARCHAR(100) NOT NULL, " +
				"description VARCHAR(100), " +
				"LastModifiedTime BIGINT NOT NULL" +
				")"
		);

		connection.createStatement().execute(
			"CREATE TABLE Department (" +
				"id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
				"name VARCHAR(255) NOT NULL, " +
				"companyId BIGINT NOT NULL, " +
				"LastModifiedTime BIGINT NOT NULL, " +
				"FOREIGN KEY (companyId) REFERENCES Company(id)" +
				")"
		);

		connection.createStatement().execute(
			"CREATE TABLE Employee (" +
				"id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
				"name VARCHAR(255) NOT NULL, " +
				"email VARCHAR(255) NOT NULL, " +
				"departmentId BIGINT NOT NULL, " +
				"status INT NOT NULL, " +
				"LastModifiedTime BIGINT NOT NULL, " +
				"FOREIGN KEY (departmentId) REFERENCES Department(id)" +
				")"
		);
	}

	private void registerSchema()
	{
		Schema.registerTable(
			"Company",
			new ColumnMetaData(
				"id",
				Types.BIGINT,
				"BIGINT",
				19,
				false,
				true,
				true,
				true,
				null,
				false,
				null,
				null
			),
			new ColumnMetaData(
				"name",
				Types.VARCHAR,
				"VARCHAR",
				100,
				false,
				false,
				false,
				false,
				null,
				false,
				null,
				null
			),
			new ColumnMetaData(
				"description",
				Types.VARCHAR,
				"VARCHAR",
				100,
				true,
				false,
				false,
				false,
				null,
				false,
				null,
				null
			),
			new ColumnMetaData(
				"LastModifiedTime",
				Types.BIGINT,
				"BIGINT",
				19,
				false,
				false,
				false,
				false,
				null,
				true,
				null,
				null
			)
		);

		Schema.registerTable(
			"Department",
			new ColumnMetaData(
				"id",
				Types.BIGINT,
				"BIGINT",
				19,
				false,
				true,
				true,
				true,
				null,
				false,
				null,
				null
			),
			new ColumnMetaData(
				"name",
				Types.VARCHAR,
				"VARCHAR",
				255,
				false,
				false,
				false,
				false,
				null,
				false,
				null,
				null
			),
			new ColumnMetaData(
				"companyId",
				Types.BIGINT,
				"BIGINT",
				19,
				false,
				false,
				false,
				false,
				null,
				false,
				"Company",
				"id"
			),
			new ColumnMetaData(
				"LastModifiedTime",
				Types.BIGINT,
				"BIGINT",
				19,
				false,
				false,
				false,
				false,
				null,
				true,
				null,
				null
			)
		);

		Schema.registerTable(
			"Employee",
			new ColumnMetaData(
				"id",
				Types.BIGINT,
				"BIGINT",
				19,
				false,
				true,
				true,
				true,
				null,
				false,
				null,
				null
			),
			new ColumnMetaData(
				"name",
				Types.VARCHAR,
				"VARCHAR",
				255,
				false,
				false,
				false,
				false,
				null,
				false,
				null,
				null
			),
			new ColumnMetaData(
				"email",
				Types.VARCHAR,
				"VARCHAR",
				255,
				false,
				false,
				false,
				false,
				null,
				false,
				null,
				null
			),
			new ColumnMetaData(
				"departmentId",
				Types.BIGINT,
				"BIGINT",
				19,
				false,
				false,
				false,
				false,
				null,
				false,
				"Department",
				"id"
			),
			new ColumnMetaData(
				"status",
				Types.INTEGER,
				"INTEGER",
				10,
				false,
				false,
				false,
				false,
				1,
				false,
				null,
				null
			),
			new ColumnMetaData(
				"LastModifiedTime",
				Types.BIGINT,
				"BIGINT",
				19,
				false,
				false,
				false,
				false,
				null,
				true,
				null,
				null
			)
		);
	}

	private void insertQueryTestData() throws Exception
	{
		connection.createStatement().executeUpdate(
			"INSERT INTO Company (name, LastModifiedTime) VALUES " +
				"('Zoho', 1000), " +
				"('Google', 1000)"
		);

		connection.createStatement().executeUpdate(
			"INSERT INTO Department " +
				"(name, companyId, LastModifiedTime) VALUES " +
				"('Payroll', 1, 1000), " +
				"('Books', 1, 1000), " +
				"('Search', 2, 1000)"
		);

		connection.createStatement().executeUpdate(
			"INSERT INTO Employee " +
				"(name, email, departmentId, status, LastModifiedTime) VALUES " +
				"('John', 'john@example.com', 1, 1, 1000), " +
				"('David', 'david@example.com', 2, 1, 1000), " +
				"('Mike', 'mike@example.com', 3, 0, 1000)"
		);
	}

	@Test
	void shouldExecuteSingleTableQuery() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.select("id")
			.select("name")
			.select("email")
			.where(
				new Criteria(
					"status",
					Criteria.EQUAL,
					1
				)
			);

		PersistenceObject persistenceObject = persistenceManager.get(query);

		List<Row> employees =
			persistenceObject.getRows("Employee");

		assertEquals(2, employees.size());

		Row employee =
			persistenceObject.getRow("Employee", null);

		assertNotNull(employee);
		assertEquals("John", employee.get("name"));
		assertEquals("john@example.com", employee.get("email"));
	}

	@Test
	void shouldExecuteEmployeeDepartmentJoin() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.join(
				Join.INNER,
				"Department",
				"Employee",
				"departmentId",
				"Department",
				"id"
			)
			.select("Employee", "id")
			.select("Employee", "name")
			.select("Department", "name")
			.where(
				new Criteria(
					"Employee.status",
					Criteria.EQUAL,
					1
				)
			);

		PersistenceObject persistenceObject =
			persistenceManager.get(query);

		List<Row> employees =
			persistenceObject.getRows("Employee");

		List<Row> departments =
			persistenceObject.getRows("Department");

		assertEquals(2, employees.size());
		assertEquals(2, departments.size());

		Row employee =
			persistenceObject.getRow("Employee", null);

		Row department =
			persistenceObject.getRow("Department", null);

		assertNotNull(employee);
		assertNotNull(department);

		assertEquals("John", employee.get("name"));
		assertEquals("Payroll", department.get("name"));
	}

	@Test
	void shouldExecuteMultipleTableJoin() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.join(
				Join.INNER,
				"Department",
				"Employee",
				"departmentId",
				"Department",
				"id"
			)
			.join(
				Join.INNER,
				"Company",
				"Department",
				"companyId",
				"Company",
				"id"
			)
			.select("Employee", "id")
			.select("Employee", "name")
			.select("Department", "name")
			.select("Company", "name")
			.where(
				Criteria.and(
					new Criteria(
						"Employee.status",
						Criteria.EQUAL,
						1
					),
					new Criteria(
						"Company.id",
						Criteria.EQUAL,
						1L
					)
				)
			);

		PersistenceObject persistenceObject =
			persistenceManager.get(query);

		assertEquals(
			2,
			persistenceObject.getRows("Employee").size()
		);

		assertEquals(
			2,
			persistenceObject.getRows("Department").size()
		);

		assertEquals(
			2,
			persistenceObject.getRows("Company").size()
		);

		Row employee =
			persistenceObject.getRow("Employee", null);

		Row department =
			persistenceObject.getRow("Department", null);

		Row company =
			persistenceObject.getRow("Company", null);

		assertNotNull(employee);
		assertNotNull(department);
		assertNotNull(company);

		assertEquals("John", employee.get("name"));
		assertEquals("Payroll", department.get("name"));
		assertEquals("Zoho", company.get("name"));
	}

	@Test
	void shouldOrderEmployeesByName() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.select("id")
			.select("name")
			.orderBy(
				"name",
				PersistenceQuery.ASC
			);

		PersistenceObject persistenceObject =
			persistenceManager.get(query);

		List<Row> employees =
			persistenceObject.getRows("Employee");

		assertEquals(3, employees.size());

		assertEquals(
			"David",
			employees.get(0).get("name")
		);

		assertEquals(
			"John",
			employees.get(1).get("name")
		);

		assertEquals(
			"Mike",
			employees.get(2).get("name")
		);
	}

	@Test
	void shouldLimitQueryResults() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.select("id")
			.select("name")
			.orderBy(
				"name",
				PersistenceQuery.ASC
			)
			.limit(2);

		PersistenceObject persistenceObject =
			persistenceManager.get(query);

		List<Row> employees =
			persistenceObject.getRows("Employee");

		assertEquals(2, employees.size());

		assertEquals(
			"David",
			employees.get(0).get("name")
		);

		assertEquals(
			"John",
			employees.get(1).get("name")
		);
	}

	@Test
	void shouldUseOffsetForPagination() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.select("id")
			.select("name")
			.orderBy(
				"name",
				PersistenceQuery.ASC
			)
			.limit(2)
			.offset(1);

		PersistenceObject persistenceObject =
			persistenceManager.get(query);

		List<Row> employees =
			persistenceObject.getRows("Employee");

		assertEquals(2, employees.size());

		assertEquals(
			"John",
			employees.get(0).get("name")
		);

		assertEquals(
			"Mike",
			employees.get(1).get("name")
		);
	}

	@Test
	void shouldPaginateJoinedResults() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.join(
				Join.INNER,
				"Department",
				"Employee",
				"departmentId",
				"Department",
				"id"
			)
			.join(
				Join.INNER,
				"Company",
				"Department",
				"companyId",
				"Company",
				"id"
			)
			.select("Employee", "name")
			.select("Department", "name")
			.select("Company", "name")
			.orderBy(
				"Employee.name",
				PersistenceQuery.ASC
			)
			.limit(1)
			.offset(1);

		PersistenceObject persistenceObject =
			persistenceManager.get(query);

		assertEquals(
			1,
			persistenceObject.getRows("Employee").size()
		);

		Row employee =
			persistenceObject.getRow("Employee", null);

		Row department =
			persistenceObject.getRow("Department", null);

		Row company =
			persistenceObject.getRow("Company", null);

		assertEquals(
			"John",
			employee.get("name")
		);

		assertEquals(
			"Payroll",
			department.get("name")
		);

		assertEquals(
			"Zoho",
			company.get("name")
		);
	}

	@Test
	void shouldExecuteCountQuery() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.count(
				"Employee",
				"id",
				"employeeCount"
			);

		PersistenceDataSet persistenceDataSet =
			persistenceManager.execute(query);

		assertEquals(1, persistenceDataSet.size());

		PersistenceDataSetRow row =
			persistenceDataSet.iterator().next();

		assertEquals(
			3L,
			row.getLong("employeeCount")
		);
	}

	@Test
	void shouldExecuteCountQueryWithGroupBy() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.select(
				"Employee",
				"departmentId"
			)
			.count(
				"Employee",
				"id",
				"employeeCount"
			)
			.groupBy(
				"Employee",
				"departmentId"
			)
			.orderBy(
				"Employee.departmentId",
				PersistenceQuery.ASC
			);

		PersistenceDataSet persistenceDataSet =
			persistenceManager.execute(query);

		assertEquals(3, persistenceDataSet.size());

		Iterator<PersistenceDataSetRow> iterator =
			persistenceDataSet.iterator();

		PersistenceDataSetRow firstRow =
			iterator.next();

		assertEquals(
			1L,
			firstRow.getLong("Employee__departmentId")
		);

		assertEquals(
			1L,
			firstRow.getLong("employeeCount")
		);

		PersistenceDataSetRow secondRow =
			iterator.next();

		assertEquals(
			2L,
			secondRow.getLong("Employee__departmentId")
		);

		assertEquals(
			1L,
			secondRow.getLong("employeeCount")
		);

		PersistenceDataSetRow thirdRow =
			iterator.next();

		assertEquals(
			3L,
			thirdRow.getLong("Employee__departmentId")
		);

		assertEquals(
			1L,
			thirdRow.getLong("employeeCount")
		);

		assertFalse(iterator.hasNext());
	}

	@Test
	void shouldExecuteSumQuery() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.sum(
				"Employee",
				"status",
				"statusTotal"
			);

		PersistenceDataSet persistenceDataSet =
			persistenceManager.execute(query);

		assertEquals(1, persistenceDataSet.size());

		PersistenceDataSetRow row =
			persistenceDataSet.iterator().next();

		assertEquals(
			2L,
			row.getLong("statusTotal")
		);
	}

	@Test
	void shouldExecuteAverageQuery() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.avg(
				"Employee",
				"status",
				"averageStatus"
			);

		PersistenceDataSet persistenceDataSet =
			persistenceManager.execute(query);

		assertEquals(1, persistenceDataSet.size());

		PersistenceDataSetRow row =
			persistenceDataSet.iterator().next();

		assertEquals(
			2.0 / 3.0,
			row.getDouble("averageStatus"),
			0.000001
		);
	}

	@Test
	void shouldExecuteMinMaxQuery() throws Exception
	{
		insertQueryTestData();

		PersistenceQuery query = new PersistenceQuery("Employee")
			.min(
				"Employee",
				"status",
				"minimumStatus"
			)
			.max(
				"Employee",
				"status",
				"maximumStatus"
			);

		PersistenceDataSet persistenceDataSet =
			persistenceManager.execute(query);

		assertEquals(1, persistenceDataSet.size());

		PersistenceDataSetRow row =
			persistenceDataSet.iterator().next();

		assertEquals(
			0L,
			row.getLong("minimumStatus")
		);

		assertEquals(
			1L,
			row.getLong("maximumStatus")
		);
	}

	@Test
	void shouldInsertRowAndPopulateGeneratedId() throws Exception
	{
		Row company =
			new Row("Company");

		company.set(
			"name",
			"Zoho"
		);

		PersistenceObject persistenceObject =
			new PersistenceObject();

		persistenceObject.addRowForInsert(
			company
		);

		persistenceManager.update(persistenceObject
		);

		assertNotNull(
			company.get("id")
		);

		assertNotNull(
			company.get("LastModifiedTime")
		);

		assertEquals(
			"Zoho",
			company.get("name")
		);

		assertEquals(
			1L,
			((Number) company.get("id")).longValue()
		);
	}

	@Test
	void shouldInsertMultipleRowsInOneTransaction() throws Exception
	{
		Row firstCompany =
			new Row("Company");

		firstCompany.set(
			"name",
			"Zoho"
		);

		Row secondCompany =
			new Row("Company");

		secondCompany.set(
			"name",
			"Google"
		);

		PersistenceObject persistenceObject =
			new PersistenceObject();

		persistenceObject.addRowForInsert(
			firstCompany
		);

		persistenceObject.addRowForInsert(
			secondCompany
		);

		persistenceManager.update(persistenceObject
		);

		assertNotNull(
			firstCompany.get("id")
		);

		assertNotNull(
			secondCompany.get("id")
		);

		assertEquals(
			1L,
			((Number) firstCompany.get("id")).longValue()
		);

		assertEquals(
			2L,
			((Number) secondCompany.get("id")).longValue()
		);
	}

	@Test
	void shouldUpdateRowUsingLastModifiedTime() throws Exception
	{
		Row company =
			new Row("Company");

		company.set(
			"name",
			"Zoho"
		);

		PersistenceObject insertObject =
			new PersistenceObject();

		insertObject.addRowForInsert(
			company
		);

		persistenceManager.update(
			insertObject
		);

		Long oldLastModifiedTime =
			((Number) company.get(
				"LastModifiedTime"
			)).longValue();

		company.set(
			"name",
			"Zoho Corporation"
		);

		PersistenceObject updateObject =
			new PersistenceObject();

		updateObject.updateRow(company);

		persistenceManager.update(
			updateObject
		);

		assertEquals(
			"Zoho Corporation",
			company.get("name")
		);

		Long newLastModifiedTime =
			((Number) company.get(
				"LastModifiedTime"
			)).longValue();

		assertTrue(
			newLastModifiedTime >=
				oldLastModifiedTime
		);
	}

	@Test
	void shouldThrowOptimisticLockExceptionWhenRowWasModified()
		throws Exception
	{
		Row company =
			new Row("Company");

		company.set(
			"name",
			"Zoho"
		);

		PersistenceObject insertObject =
			new PersistenceObject();

		insertObject.addRowForInsert(
			company
		);

		persistenceManager.update(
			insertObject
		);

		Long oldLastModifiedTime =
			((Number) company.get(
				"LastModifiedTime"
			)).longValue();

		long modifiedLastModifiedTime =
			oldLastModifiedTime + 1000;

		connection.createStatement().executeUpdate(
			"UPDATE Company " +
				"SET name = 'ChangedByAnotherTransaction', " +
				"LastModifiedTime = " +
				modifiedLastModifiedTime +
				" WHERE id = " +
				company.get("id")
		);

		company.set(
			"name",
			"MyUpdate"
		);

		PersistenceObject updateObject =
			new PersistenceObject();

		updateObject.updateRow(company);

		assertThrows(
			OptimisticLockException.class,
			() -> persistenceManager.update(
				updateObject
			)
		);

		assertEquals(
			oldLastModifiedTime,
			company.get(
				"LastModifiedTime"
			)
		);
	}

	@Test
	void shouldDeleteRow() throws Exception
	{
		Row company =
			new Row("Company");

		company.set(
			"name",
			"Zoho"
		);

		PersistenceObject insertObject =
			new PersistenceObject();

		insertObject.addRowForInsert(
			company
		);

		persistenceManager.update(
			insertObject
		);

		PersistenceObject deleteObject =
			new PersistenceObject();

		deleteObject.deleteRow(company);

		persistenceManager.update(
			deleteObject
		);

		var result =
			connection.createStatement().executeQuery(
				"SELECT COUNT(*) FROM Company WHERE id = " +
					company.get("id")
			);

		assertTrue(
			result.next()
		);

		assertEquals(
			0,
			result.getInt(1)
		);
	}

	@Test
	void shouldRollbackEntireTransactionWhenOneOperationFails()
		throws Exception
	{
		Row validCompany =
			new Row("Company");

		validCompany.set(
			"name",
			"Zoho"
		);

		Row invalidCompany =
			new Row("Company");

		PersistenceObject persistenceObject =
			new PersistenceObject();

		persistenceObject.addRowForInsert(
			validCompany
		);

		persistenceObject.addRowForInsert(
			invalidCompany
		);

		assertThrows(
			Exception.class,
			() -> persistenceManager.update(persistenceObject
			)
		);

		var result =
			connection.createStatement().executeQuery(
				"SELECT COUNT(*) FROM Company"
			);

		assertTrue(
			result.next()
		);

		assertEquals(
			0,
			result.getInt(1)
		);
	}

	@Test
	void shouldFindRowsWithNullValue()
	{
		Row company =
			new Row("Company");

		company.set(
			"name",
			"Zoho"
		);

		company.set(
			"description",
			null
		);

		PersistenceObject persistenceObject =
			new PersistenceObject();

		persistenceObject.addRowForInsert(
			company
		);

		persistenceManager.update(persistenceObject
		);

		PersistenceQuery query =
			new PersistenceQuery("Company")
				.select(
					"Company",
					"name"
				)
				.where(
					new Criteria(
						"description",
						Criteria.EQUAL,
						null
					)
				);

		PersistenceObject result =
			persistenceManager.get(query);

		Row resultRow =
			result.getRow(
				"Company",
				new Criteria(
					"name",
					Criteria.EQUAL,
					"Zoho"
				)
			);

		assertNotNull(resultRow);

		assertEquals(
			"Zoho",
			resultRow.get("name")
		);

		assertNull(
			resultRow.get("description")
		);
	}

	@Test
	void shouldFindRowsWithNonNullValue()
	{
		Row company =
			new Row("Company");

		company.set(
			"name",
			"Google"
		);

		company.set(
			"description",
			"Technology company"
		);

		PersistenceObject persistenceObject =
			new PersistenceObject();

		persistenceObject.addRowForInsert(
			company
		);

		persistenceManager.update(persistenceObject
		);

		PersistenceQuery query =
			new PersistenceQuery("Company")
				.select(
					"Company",
					"name"
				)
				.where(
					new Criteria(
						"description",
						Criteria.NOT_EQUAL,
						null
					)
				);

		PersistenceObject result =
			persistenceManager.get(query);

		Row resultRow =
			result.getRow(
				"Company",
				new Criteria(
					"name",
					Criteria.EQUAL,
					"Google"
				)
			);

		assertNotNull(resultRow);

		assertEquals(
			"Google",
			resultRow.get("name")
		);
	}

	@Test
	void shouldExposePrimaryKeyMetadata()
	{
		assertTrue(
			Schema.getTable("Company")
				.hasPrimaryKey()
		);

		assertEquals(
			"id",
			Schema.getTable("Company")
				.getPrimaryKeyColumn()
				.getName()
		);

		assertTrue(
			Schema.getTable("Company")
				.getPrimaryKeyColumn()
				.isAutoIncrement()
		);
	}

	@Test
	void shouldExposeForeignKeyMetadata()
	{
		ColumnMetaData companyId =
			Schema.getTable("Department")
				.getColumn("companyId");

		assertTrue(
			companyId.isForeignKey()
		);

		assertEquals(
			"Company",
			companyId.getReferencedTable()
		);

		assertEquals(
			"id",
			companyId.getReferencedColumn()
		);

		ColumnMetaData departmentId =
			Schema.getTable("Employee")
				.getColumn("departmentId");

		assertTrue(
			departmentId.isForeignKey()
		);

		assertEquals(
			"Department",
			departmentId.getReferencedTable()
		);

		assertEquals(
			"id",
			departmentId.getReferencedColumn()
		);
	}

	@Test
	void shouldExposeLastModifiedMetadata()
	{
		ColumnMetaData lastModified =
			Schema.getTable("Employee")
				.getLastModifiedTimeColumn();

		assertNotNull(lastModified);

		assertEquals(
			"LastModifiedTime",
			lastModified.getName()
		);

		assertTrue(
			lastModified.isLastModifiedTime()
		);
	}

	@Test
	void shouldExposeNullableAndUniqueMetadata()
	{
		ColumnMetaData description =
			Schema.getTable("Company")
				.getColumn("description");

		assertTrue(
			description.isNullable()
		);

		ColumnMetaData id =
			Schema.getTable("Company")
				.getColumn("id");

		assertTrue(
			id.isUnique()
		);

		assertTrue(
			id.isPrimaryKey()
		);
	}

	@Test
	void shouldRejectUpdateWhenPrimaryKeyValueIsMissing()
	{
		Row company = new Row("Company");
		company.set("name", "Zoho");
		company.set("LastModifiedTime", 1000L);

		PersistenceObject persistenceObject = new PersistenceObject();
		persistenceObject.updateRow(company);

		assertThrows(
			PersistenceException.class,
			() -> persistenceManager.update(persistenceObject)
		);
	}

	@Test
	void shouldRejectDeleteWhenPrimaryKeyValueIsMissing()
	{
		Row company = new Row("Company");
		company.set("name", "Zoho");

		PersistenceObject persistenceObject = new PersistenceObject();
		persistenceObject.deleteRow(company);

		assertThrows(
			PersistenceException.class,
			() -> persistenceManager.update(persistenceObject)
		);
	}

	@Test
	void shouldRejectDeleteWhenRowWasModifiedByAnotherTransaction()
		throws Exception
	{
		Row company = new Row("Company");
		company.set("name", "Zoho");

		PersistenceObject insertObject = new PersistenceObject();
		insertObject.addRowForInsert(company);

		persistenceManager.update(insertObject);

		Long oldLastModifiedTime =
			((Number) company.get("LastModifiedTime")).longValue();

		long modifiedLastModifiedTime =
			oldLastModifiedTime + 1000;

		connection.createStatement().executeUpdate(
			"UPDATE Company " +
				"SET name = 'ChangedByAnotherTransaction', " +
				"LastModifiedTime = " + modifiedLastModifiedTime +
				" WHERE id = " + company.get("id")
		);

		PersistenceObject deleteObject = new PersistenceObject();
		deleteObject.deleteRow(company);

		assertThrows(
			OptimisticLockException.class,
			() -> persistenceManager.update(deleteObject)
		);

		var result = connection.createStatement().executeQuery(
			"SELECT name, LastModifiedTime FROM Company WHERE id = "
				+ company.get("id")
		);

		assertTrue(result.next());
		assertEquals("ChangedByAnotherTransaction", result.getString("name"));
		assertEquals(modifiedLastModifiedTime, result.getLong("LastModifiedTime"));
	}

	@Test
	void shouldRollbackEntireTransactionWhenConcurrencyFails()
		throws Exception
	{
		Row firstCompany = new Row("Company");
		firstCompany.set("name", "Zoho");

		Row secondCompany = new Row("Company");
		secondCompany.set("name", "Google");

		PersistenceObject insertObject = new PersistenceObject();
		insertObject.addRowForInsert(firstCompany);
		insertObject.addRowForInsert(secondCompany);

		persistenceManager.update(insertObject);

		Long secondOldLastModifiedTime =
			((Number) secondCompany.get("LastModifiedTime")).longValue();

		long modifiedLastModifiedTime =
			secondOldLastModifiedTime + 1000;

		connection.createStatement().executeUpdate(
			"UPDATE Company " +
				"SET name = 'ChangedByAnotherTransaction', " +
				"LastModifiedTime = " + modifiedLastModifiedTime +
				" WHERE id = " + secondCompany.get("id")
		);

		firstCompany.set("name", "Zoho Corporation");
		secondCompany.set("name", "Google Corporation");

		PersistenceObject updateObject = new PersistenceObject();
		updateObject.updateRow(firstCompany);
		updateObject.updateRow(secondCompany);

		assertThrows(
			OptimisticLockException.class,
			() -> persistenceManager.update(updateObject)
		);

		var firstResult = connection.createStatement().executeQuery(
			"SELECT name FROM Company WHERE id = " +
				firstCompany.get("id")
		);

		assertTrue(firstResult.next());
		assertEquals("Zoho", firstResult.getString("name"));

		var secondResult = connection.createStatement().executeQuery(
			"SELECT name, LastModifiedTime FROM Company WHERE id = " +
				secondCompany.get("id")
		);

		assertTrue(secondResult.next());
		assertEquals(
			"ChangedByAnotherTransaction",
			secondResult.getString("name")
		);

		assertEquals(
			modifiedLastModifiedTime,
			secondResult.getLong("LastModifiedTime")
		);
	}

	@Test
	void shouldUpdateNullableColumnToNull() throws Exception
	{
		Row company = new Row("Company");
		company.set("name", "Zoho");
		company.set("description", "Technology Company");

		PersistenceObject insertObject = new PersistenceObject();
		insertObject.addRowForInsert(company);

		persistenceManager.update(insertObject);

		company.set("description", null);

		PersistenceObject updateObject = new PersistenceObject();
		updateObject.updateRow(company);

		persistenceManager.update(updateObject);

		var result = connection.createStatement().executeQuery(
			"SELECT description FROM Company WHERE id = " + company.get("id")
		);

		assertTrue(result.next());
		assertNull(result.getObject("description"));
	}

	@Test
	void shouldOnlyUpdateModifiedColumns() throws Exception
	{
		Row company = new Row("Company");
		company.set("name", "Zoho");
		company.set("description", "Technology Company");

		PersistenceObject insertObject = new PersistenceObject();
		insertObject.addRowForInsert(company);

		persistenceManager.update(insertObject);

		company.set("name", "Zoho Corporation");

		PersistenceObject updateObject = new PersistenceObject();
		updateObject.updateRow(company);

		persistenceManager.update(updateObject);

		var result = connection.createStatement().executeQuery(
			"SELECT name, description FROM Company WHERE id = " + company.get("id")
		);

		assertTrue(result.next());
		assertEquals("Zoho Corporation", result.getString("name"));
		assertEquals("Technology Company", result.getString("description"));
	}

	@Test
	void shouldRejectInsertWhenRequiredColumnIsMissing()
	{
		Row company = new Row("Company");

		PersistenceObject persistenceObject =
			new PersistenceObject();

		persistenceObject.addRowForInsert(
			company
		);

		PersistenceException exception =
			assertThrows(
				PersistenceException.class,
				() -> persistenceManager.update(persistenceObject
				)
			);

		assertTrue(
			exception.getMessage().contains(
				"Company.name"
			)
		);
	}

	@Test
	void shouldApplyDefaultValueDuringInsert()
	{
		Row company =
			new Row("Company");

		company.set(
			"name",
			"Zoho"
		);

		PersistenceObject companyObject =
			new PersistenceObject();

		companyObject.addRowForInsert(
			company
		);

		persistenceManager.update(
			companyObject
		);

		Row department =
			new Row("Department");

		department.set(
			"name",
			"Payroll"
		);

		department.set(
			"companyId",
			company.get("id")
		);

		PersistenceObject departmentObject =
			new PersistenceObject();

		departmentObject.addRowForInsert(
			department
		);

		persistenceManager.update(
			departmentObject
		);

		Row employee =
			new Row("Employee");

		employee.set(
			"name",
			"John"
		);

		employee.set(
			"email",
			"john@example.com"
		);

		employee.set(
			"departmentId",
			department.get("id")
		);

		PersistenceObject employeeObject =
			new PersistenceObject();

		employeeObject.addRowForInsert(
			employee
		);

		persistenceManager.update(
			employeeObject
		);

		assertEquals(
			1,
			((Number) employee.get(
				"status"
			)).intValue()
		);
	}

	@Test
	void shouldAllowNullableColumnDuringInsert() throws SQLException
	{
		Row company =
			new Row("Company");

		company.set(
			"name",
			"Zoho"
		);

		PersistenceObject persistenceObject =
			new PersistenceObject();

		persistenceObject.addRowForInsert(
			company
		);

		persistenceManager.update(persistenceObject
		);

		var result =
			assertDoesNotThrow(
				() -> connection
					.createStatement()
					.executeQuery(
						"SELECT description FROM Company WHERE id = "
							+ company.get("id")
					)
			);

		assertTrue(
			result.next()
		);

		assertNull(
			result.getObject(
				"description"
			)
		);
	}

	@Test
	void shouldRejectForeignKeyWithUnknownReferencedTable()
	{
		Schema.clear();

		try
		{
			Schema.registerTable(
				"Child",
				new ColumnMetaData(
					"id",
					Types.BIGINT,
					"BIGINT",
					19,
					false,
					true,
					true,
					true,
					null,
					false,
					null,
					null
				),
				new ColumnMetaData(
					"parentId",
					Types.BIGINT,
					"BIGINT",
					19,
					false,
					false,
					false,
					false,
					null,
					false,
					"Parent",
					"id"
				)
			);

			assertThrows(
				IllegalArgumentException.class,
				Schema::validate
			);
		}
		finally
		{
			Schema.clear();
			registerSchema();
			Schema.validate();
		}
	}

	@Test
	void shouldRejectForeignKeyWithUnknownReferencedColumn()
	{
		Schema.clear();

		try
		{
			Schema.registerTable(
				"Parent",
				new ColumnMetaData(
					"id",
					Types.BIGINT,
					"BIGINT",
					19,
					false,
					true,
					true,
					true,
					null,
					false,
					null,
					null
				)
			);

			Schema.registerTable(
				"Child",
				new ColumnMetaData(
					"id",
					Types.BIGINT,
					"BIGINT",
					19,
					false,
					true,
					true,
					true,
					null,
					false,
					null,
					null
				),
				new ColumnMetaData(
					"parentId",
					Types.BIGINT,
					"BIGINT",
					19,
					false,
					false,
					false,
					false,
					null,
					false,
					"Parent",
					"unknownId"
				)
			);

			IllegalArgumentException exception =
				assertThrows(
					IllegalArgumentException.class,
					Schema::validate
				);

			assertTrue(
				exception.getMessage().contains(
					"Parent.unknownId"
				)
			);
		}
		finally
		{
			Schema.clear();
			registerSchema();
			Schema.validate();
		}
	}

	@Test
	void shouldRejectForeignKeyWithDifferentJdbcType()
	{
		Schema.clear();

		try
		{
			Schema.registerTable(
				"Parent",
				new ColumnMetaData(
					"id",
					Types.BIGINT,
					"BIGINT",
					19,
					false,
					true,
					true,
					true,
					null,
					false,
					null,
					null
				)
			);

			Schema.registerTable(
				"Child",
				new ColumnMetaData(
					"id",
					Types.BIGINT,
					"BIGINT",
					19,
					false,
					true,
					true,
					true,
					null,
					false,
					null,
					null
				),
				new ColumnMetaData(
					"parentId",
					Types.VARCHAR,
					"VARCHAR",
					100,
					false,
					false,
					false,
					false,
					null,
					false,
					"Parent",
					"id"
				)
			);

			IllegalArgumentException exception =
				assertThrows(
					IllegalArgumentException.class,
					Schema::validate
				);

			assertTrue(
				exception.getMessage().contains(
					"Foreign key type mismatch"
				)
			);
		}
		finally
		{
			Schema.clear();
			registerSchema();
			Schema.validate();
		}
	}

	@Test
	void shouldRejectNullForNonNullableColumnDuringUpdate()
	{
		Row company = new Row("Company");
		company.set("name", "Zoho");

		PersistenceObject insertObject = new PersistenceObject();
		insertObject.addRowForInsert(company);

		persistenceManager.update(insertObject);

		company.set("name", null);

		PersistenceObject updateObject = new PersistenceObject();
		updateObject.updateRow(company);

		assertThrows(
			PersistenceException.class,
			() -> persistenceManager.update(updateObject)
		);
	}

	@Test
	void shouldAllowNullForNullableColumnDuringUpdate() throws Exception
	{
		Row company = new Row("Company");
		company.set("name", "Zoho");
		company.set("description", "Software company");

		PersistenceObject insertObject = new PersistenceObject();
		insertObject.addRowForInsert(company);

		persistenceManager.update(insertObject);

		company.set("description", null);

		PersistenceObject updateObject = new PersistenceObject();
		updateObject.updateRow(company);

		persistenceManager.update(updateObject);

		var result = connection.createStatement().executeQuery(
			"SELECT description FROM Company WHERE id = " + company.get("id")
		);

		assertTrue(result.next());
		assertNull(result.getObject("description"));
	}

	@Test
	void shouldRejectUpdateWhenNothingWasModified()
	{
		Row company = new Row("Company");
		company.set("name", "Zoho");

		PersistenceObject insertObject = new PersistenceObject();
		insertObject.addRowForInsert(company);

		persistenceManager.update(insertObject);

		PersistenceObject updateObject = new PersistenceObject();
		updateObject.updateRow(company);

		assertThrows(
			PersistenceException.class,
			() -> persistenceManager.update(updateObject)
		);
	}
}