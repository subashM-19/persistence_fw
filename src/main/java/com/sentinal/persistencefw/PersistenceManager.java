package com.sentinal.persistencefw;

import com.sentinal.persistencefw.exception.OptimisticLockException;
import com.sentinal.persistencefw.exception.PersistenceException;
import com.sentinal.persistencefw.metadata.ColumnMetaData;
import com.sentinal.persistencefw.metadata.Schema;
import com.sentinal.persistencefw.metadata.TableMetaData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.sql.DriverManager.getConnection;

public class PersistenceManager
{
	private static String url;
	private static String username;
	private static String password;
	private static boolean initialized;

	private PersistenceManager()
	{
	}

	public static synchronized void initialize(String databaseUrl, String databaseUsername, String databasePassword)
	{
		if (databaseUrl == null || databaseUrl.isEmpty())
		{
			throw new IllegalArgumentException("Database URL cannot be null or empty");
		}

		if (initialized)
		{
			if (url.equals(databaseUrl) && java.util.Objects.equals(username, databaseUsername) && java.util.Objects.equals(password, databasePassword))
			{
				return;
			}

			throw new IllegalStateException("PersistenceManager is already initialized");
		}

		url = databaseUrl;
		username = databaseUsername;
		password = databasePassword;
		initialized = true;
	}

	private static void validateInitialized()
	{
		if (!initialized)
		{
			throw new IllegalStateException("PersistenceManager is not initialized");
		}
	}

	public static void update(PersistenceObject persistenceObject)
	{
		validateInitialized();
		if (persistenceObject == null)
		{
			throw new IllegalArgumentException("PersistenceObject cannot be null");
		}

		if (!persistenceObject.hasOperations())
		{
			return;
		}

		try
		{
			try (Connection connection = getConnection(url, username, password))
			{
				connection.setAutoCommit(false);

				try
				{
					executeOperation(connection, persistenceObject);

					connection.commit();

					clearModifiedColumns(persistenceObject);
				}
				catch (Exception e)
				{
					try
					{
						connection.rollback();
					}
					catch (Exception rollbackException)
					{
						e.addSuppressed(rollbackException);
					}

					if (e instanceof OptimisticLockException)
					{
						throw (OptimisticLockException) e;
					}

					if (e instanceof PersistenceException)
					{
						throw (PersistenceException) e;
					}

					throw new PersistenceException("Transaction failed and was rolled back", e);
				}
			}
		}
		catch (PersistenceException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new PersistenceException("Unable to execute database transaction", e);
		}
	}

	private static void executeOperation(Connection connection, PersistenceObject persistenceObject) throws Exception
	{
		for (PersistenceActions persistenceActions : persistenceObject.getOperations())
		{
			switch (persistenceActions.getOperation())
			{
				case PersistenceActions.INSERT:
					executeInsert(connection, persistenceActions.getValue());
					break;

				case PersistenceActions.UPDATE:
					executeUpdate(connection, persistenceActions.getValue());
					break;

				case PersistenceActions.DELETE:
					executeDelete(connection, persistenceActions.getValue());
					break;

				default:
					throw new PersistenceException("Unsupported operation: " + persistenceActions.getOperation());
			}
		}
	}

	private static void executeInsert(Connection connection, Row row) throws Exception
	{
		String tableName = row.getTableName();

		TableMetaData tableMetaData = Schema.getTable(tableName);

		List<String> columns = new ArrayList<>();

		List<Object> values = new ArrayList<>();

		for (ColumnMetaData column : tableMetaData.getColumns())
		{
			String columnName = column.getName();

			if (column.isAutoIncrement())
			{
				continue;
			}

			Object value = row.get(columnName);

			if (column.isLastModifiedTime())
			{
				value = System.currentTimeMillis();

				row.setLoadedValue(columnName, value);
			}
			else if (value == null && column.getDefaultValue() != null)
			{
				value = column.getDefaultValue();

				row.setLoadedValue(columnName, value);
			}

			if (value == null)
			{
				if (!column.isNullable())
				{
					throw new PersistenceException("Required column " + tableName + "." + columnName + " cannot be null");
				}

				continue;
			}

			columns.add(columnName);
			values.add(value);
		}

		if (columns.isEmpty())
		{
			throw new PersistenceException("No values available for insert into table " + tableName);
		}

		StringBuilder sql = new StringBuilder();

		sql.append("INSERT INTO ").append(tableName).append(" (");

		for (int i = 0; i < columns.size(); i++)
		{
			if (i > 0)
			{
				sql.append(", ");
			}

			sql.append(columns.get(i));
		}

		sql.append(") VALUES (");

		for (int i = 0; i < values.size(); i++)
		{
			if (i > 0)
			{
				sql.append(", ");
			}

			sql.append("?");
		}

		sql.append(")");

		try (PreparedStatement statement = connection.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS))
		{
			for (int i = 0; i < values.size(); i++)
			{
				statement.setObject(i + 1, values.get(i));
			}

			int affectedRows = statement.executeUpdate();

			if (affectedRows != 1)
			{
				throw new PersistenceException("Expected one row to be inserted into table " + tableName + " but inserted " + affectedRows);
			}

			ColumnMetaData primaryKeyColumn = tableMetaData.getPrimaryKeyColumn();

			if (primaryKeyColumn != null && primaryKeyColumn.isAutoIncrement())
			{
				try (ResultSet generatedKeys = statement.getGeneratedKeys())
				{
					if (!generatedKeys.next())
					{
						throw new PersistenceException("Generated primary key was not returned for table " + tableName);
					}

					row.setLoadedValue(primaryKeyColumn.getName(), generatedKeys.getObject(1));
				}
			}
		}
	}

	private static void executeUpdate(Connection connection, Row row) throws SQLException
	{
		String tableName = row.getTableName();
		TableMetaData tableMetadata = Schema.getTable(tableName);

		ColumnMetaData primaryKeyColumn = getPrimaryKeyColumn(row);
		Object primaryKeyValue = getPrimaryKeyValue(row, primaryKeyColumn);

		validateConcurrency(connection, row);

		List<String> columns = new ArrayList<>();
		List<Object> values = new ArrayList<>();

		ColumnMetaData lastModifiedTimeColumn = tableMetadata.getLastModifiedTimeColumn();

		Long newLastModifiedTime = null;

		for (ColumnMetaData column : tableMetadata.getColumns())
		{
			String columnName = column.getName();

			if (column.isPrimaryKey() || column.isAutoIncrement())
			{
				continue;
			}

			if (column.isLastModifiedTime())
			{
				continue;
			}

			if (!row.isModified(columnName))
			{
				continue;
			}

			Object value = row.get(columnName);

			if (value == null && !column.isNullable())
			{
				throw new PersistenceException("Column " + tableName + "." + columnName + " cannot be null");
			}

			columns.add(columnName);
			values.add(value);
		}

		if (columns.isEmpty())
		{
			throw new PersistenceException("No modified values available for update on table " + tableName);
		}

		if (lastModifiedTimeColumn != null)
		{
			newLastModifiedTime = System.currentTimeMillis();

			columns.add(lastModifiedTimeColumn.getName());

			values.add(newLastModifiedTime);
		}

		StringBuilder sql = new StringBuilder();

		sql.append("UPDATE ").append(tableName).append(" SET ");

		for (int i = 0; i < columns.size(); i++)
		{
			if (i > 0)
			{
				sql.append(", ");
			}

			sql.append(columns.get(i)).append(" = ?");
		}

		sql.append(" WHERE ").append(primaryKeyColumn.getName()).append(" = ?");

		values.add(primaryKeyValue);

		try (PreparedStatement statement = connection.prepareStatement(sql.toString()))
		{
			for (int i = 0; i < values.size(); i++)
			{
				statement.setObject(i + 1, values.get(i));
			}

			int affectedRows = statement.executeUpdate();

			if (affectedRows != 1)
			{
				throw new PersistenceException("Expected one row to be updated in table " + tableName + " but updated " + affectedRows);
			}

			if (lastModifiedTimeColumn != null)
			{
				row.setLoadedValue(lastModifiedTimeColumn.getName(), newLastModifiedTime);
			}
		}
	}

	private static void clearModifiedColumns(PersistenceObject persistenceObject)
	{
		for (PersistenceActions persistenceActions : persistenceObject.getOperations())
		{
			persistenceActions.getValue().clearModifiedColumns();
		}
	}

	private static void executeDelete(Connection connection, Row row) throws Exception
	{
		String tableName = row.getTableName();

		ColumnMetaData primaryKeyColumn = getPrimaryKeyColumn(row);
		Object primaryKeyValue = getPrimaryKeyValue(row, primaryKeyColumn);

		validateConcurrency(connection, row);

		String sql = "DELETE FROM " + tableName + " WHERE " + primaryKeyColumn.getName() + " = ?";

		try (PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setObject(1, primaryKeyValue);

			int affectedRows = statement.executeUpdate();

			if (affectedRows != 1)
			{
				throw new PersistenceException("Expected one row to be deleted from table " + tableName + " but deleted " + affectedRows);
			}
		}
	}

	private static void validateConcurrency(Connection connection, Row row) throws SQLException
	{
		String tableName = row.getTableName();
		TableMetaData tableMetadata = Schema.getTable(tableName);

		ColumnMetaData lastModifiedTimeColumn = tableMetadata.getLastModifiedTimeColumn();

		if (lastModifiedTimeColumn == null)
		{
			return;
		}

		Object currentLastModifiedTime = row.get(lastModifiedTimeColumn.getName());

		if (currentLastModifiedTime == null)
		{
			throw new PersistenceException("LastModifiedTime cannot be null for update on table " + tableName);
		}

		ColumnMetaData primaryKeyColumn = getPrimaryKeyColumn(row);
		Object primaryKeyValue = getPrimaryKeyValue(row, primaryKeyColumn);

		String sql = "SELECT " + lastModifiedTimeColumn.getName() + " FROM " + tableName + " WHERE " + primaryKeyColumn.getName() + " = ? FOR UPDATE";

		try (PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setObject(1, primaryKeyValue);

			try (ResultSet resultSet = statement.executeQuery())
			{
				if (!resultSet.next())
				{
					throw new OptimisticLockException("Row no longer exists for update on table " + tableName);
				}

				Object databaseLastModifiedTime = resultSet.getObject(1);

				if (!currentLastModifiedTime.equals(databaseLastModifiedTime))
				{
					throw new OptimisticLockException("Concurrency check failed. The row was modified by another transaction");
				}
			}
		}
	}

	private static void appendCriteria(StringBuilder sql, String tableName, Criteria criteria, List<Object> values)
	{
		if (criteria.getCriteriaList() != null)
		{
			sql.append("(");

			List<Criteria> criteriaList = criteria.getCriteriaList();

			for (int i = 0; i < criteriaList.size(); i++)
			{
				if (i > 0)
				{
					if (criteria.isAndOperator())
					{
						sql.append(" AND ");
					}
					else
					{
						sql.append(" OR ");
					}
				}

				appendCriteria(sql, tableName, criteriaList.get(i), values);
			}

			sql.append(")");
			return;
		}

		String columnName = criteria.getColumnName();

		if (columnName.contains("."))
		{
			String[] parts = columnName.split("\\.");

			if (parts.length != 2)
			{
				throw new PersistenceException("Invalid qualified column: " + columnName);
			}

			Schema.validateColumn(parts[0], parts[1]);

			sql.append(parts[0]);
			sql.append(".");
			sql.append(parts[1]);
		}
		else
		{
			Schema.validateColumn(tableName, columnName);

			sql.append(tableName);
			sql.append(".");
			sql.append(columnName);
		}

		switch (criteria.getComparator())
		{
			case Criteria.EQUAL:
				if (criteria.getValue() == null)
				{
					sql.append(" IS NULL");
				}
				else
				{
					sql.append(" = ?");
					values.add(criteria.getValue());
				}
				break;

			case Criteria.NOT_EQUAL:
				if (criteria.getValue() == null)
				{
					sql.append(" IS NOT NULL");
				}
				else
				{
					sql.append(" <> ?");
					values.add(criteria.getValue());
				}
				break;

			case Criteria.GREATER_THAN:
				sql.append(" > ?");
				values.add(criteria.getValue());
				break;

			case Criteria.GREATER_THAN_OR_EQUAL:
				sql.append(" >= ?");
				values.add(criteria.getValue());
				break;

			case Criteria.LESS_THAN:
				sql.append(" < ?");
				values.add(criteria.getValue());
				break;

			case Criteria.LESS_THAN_OR_EQUAL:
				sql.append(" <= ?");
				values.add(criteria.getValue());
				break;

			case Criteria.LIKE:
				sql.append(" LIKE ?");
				values.add("%" + criteria.getValue() + "%");
				break;

			case Criteria.IN:
				if (!(criteria.getValue() instanceof List))
				{
					throw new PersistenceException("IN criteria requires a List value");
				}

				List<?> list = (List<?>) criteria.getValue();

				if (list.isEmpty())
				{
					throw new PersistenceException("IN criteria cannot contain an empty list");
				}

				sql.append(" IN (");

				for (int i = 0; i < list.size(); i++)
				{
					if (i > 0)
					{
						sql.append(", ");
					}

					sql.append("?");
					values.add(list.get(i));
				}

				sql.append(")");
				break;

			default:
				throw new PersistenceException("Unsupported criteria comparator: " + criteria.getComparator());
		}
	}

	private String findGeneratedColumn(TableMetaData tableMetaData)
	{
		for (ColumnMetaData column : tableMetaData.getColumns())
		{
			if (column.isAutoIncrement())
			{
				return column.getName();
			}
		}

		return null;
	}

	private static void buildSelectQuery(StringBuilder sql, List<Object> values, PersistenceQuery query)
	{
		sql.append("SELECT ");

		boolean firstColumn = true;

		for (int i = 0; i < query.getColumns().size(); i++)
		{
			if (!firstColumn)
			{
				sql.append(", ");
			}

			String column = query.getColumns().get(i);

			String[] parts = column.split("\\.");

			if (parts.length != 2)
			{
				throw new PersistenceException("Invalid column: " + column);
			}

			Schema.validateColumn(parts[0], parts[1]);

			sql.append(parts[0]);
			sql.append(".");
			sql.append(parts[1]);
			sql.append(" AS ");
			sql.append(query.getColumnAliases().get(i));

			firstColumn = false;
		}

		for (int i = 0; i < query.getAggregateExpressions().size(); i++)
		{
			if (!firstColumn)
			{
				sql.append(", ");
			}

			sql.append(query.getAggregateExpressions().get(i));

			sql.append(" AS ");

			sql.append(query.getAggregateAliases().get(i));

			firstColumn = false;
		}

		if (firstColumn)
		{
			appendAllColumns(sql, query);
		}

		sql.append(" FROM ");
		sql.append(query.getTableName());

		for (Join join : query.getJoins())
		{
			appendJoin(sql, join);
		}

		if (query.getCriteria() != null)
		{
			sql.append(" WHERE ");

			appendCriteria(sql, query.getTableName(), query.getCriteria(), values);
		}

		if (!query.getGroupByColumns().isEmpty())
		{
			sql.append(" GROUP BY ");

			for (int i = 0; i < query.getGroupByColumns().size(); i++)
			{
				if (i > 0)
				{
					sql.append(", ");
				}

				String column = query.getGroupByColumns().get(i);

				String[] parts = column.split("\\.");

				if (parts.length != 2)
				{
					throw new PersistenceException("Invalid GROUP BY column: " + column);
				}

				Schema.validateColumn(parts[0], parts[1]);

				sql.append(parts[0]);
				sql.append(".");
				sql.append(parts[1]);
			}
		}

		appendOrderBy(sql, query);

		if (query.getLimit() != null)
		{
			sql.append(" LIMIT ");
			sql.append(query.getLimit());
		}

		if (query.getOffset() != null)
		{
			sql.append(" OFFSET ");
			sql.append(query.getOffset());
		}

		if (query.isForUpdate())
		{
			sql.append(" FOR UPDATE");
		}
	}

	private static void appendOrderBy(StringBuilder sql, PersistenceQuery query)
	{
		if (query.getOrderByColumns().isEmpty())
		{
			return;
		}

		sql.append(" ORDER BY ");

		for (int i = 0; i < query.getOrderByColumns().size(); i++)
		{
			if (i > 0)
			{
				sql.append(", ");
			}

			String columnName = query.getOrderByColumns().get(i);

			String tableName;

			if (columnName.contains("."))
			{
				String[] parts = columnName.split("\\.");

				if (parts.length != 2)
				{
					throw new PersistenceException("Invalid ORDER BY column: " + columnName);
				}

				tableName = parts[0];
				columnName = parts[1];
			}
			else
			{
				tableName = query.getTableName();
			}

			Schema.validateColumn(tableName, columnName);

			sql.append(tableName);
			sql.append(".");
			sql.append(columnName);

			if (query.getOrderByTypes().get(i) == PersistenceQuery.ASC)
			{
				sql.append(" ASC");
			}
			else
			{
				sql.append(" DESC");
			}
		}
	}

	private static void appendJoin(StringBuilder sql, Join join)
	{
		if (!Schema.hasTable(join.getTableName()))
		{
			throw new PersistenceException("Unknown join table: " + join.getTableName());
		}

		Schema.validateColumn(join.getLeftTable(), join.getLeftColumn());

		Schema.validateColumn(join.getRightTable(), join.getRightColumn());

		switch (join.getJoinType())
		{
			case Join.INNER:
				sql.append(" INNER JOIN ");
				break;

			case Join.LEFT:
				sql.append(" LEFT JOIN ");
				break;

			case Join.RIGHT:
				sql.append(" RIGHT JOIN ");
				break;

			default:
				throw new PersistenceException("Unsupported join type: " + join.getJoinType());
		}

		sql.append(join.getTableName());
		sql.append(" ON ");
		sql.append(join.getLeftTable());
		sql.append(".");
		sql.append(join.getLeftColumn());
		sql.append(" = ");
		sql.append(join.getRightTable());
		sql.append(".");
		sql.append(join.getRightColumn());
	}

	private static void addTableRow(PersistenceObject persistenceObject, ResultSet resultSet, PersistenceQuery query, String tableName) throws Exception
	{
		TableMetaData tableMetadata = Schema.getTable(tableName);

		Row row = new Row(tableName);

		for (String columnName : tableMetadata.getColumnNames())
		{
			String selectedColumn = tableName + "." + columnName;

			if (!query.getColumns().isEmpty() && !query.getColumns().contains(selectedColumn))
			{
				continue;
			}

			String resultColumn = tableName + "__" + columnName;

			Object value = resultSet.getObject(resultColumn);

			row.setLoadedValue(columnName, value);
		}

		persistenceObject.addRow(row);
	}

	private static void addSelectedColumns(Map<String, Object> row, ResultSet resultSet, PersistenceQuery query) throws Exception
	{
		List<String> columns = query.getColumns();

		List<String> aliases = query.getColumnAliases();

		for (int i = 0; i < columns.size(); i++)
		{
			String alias = aliases.get(i);

			Object value = resultSet.getObject(alias);

			row.put(alias, value);
		}
	}

	private static void addAggregateValues(Map<String, Object> row, ResultSet resultSet, PersistenceQuery query) throws Exception
	{
		List<String> aliases = query.getAggregateAliases();

		for (String alias : aliases)
		{
			Object value = resultSet.getObject(alias);

			row.put(alias, value);
		}
	}

	private static void appendAllColumns(StringBuilder sql, PersistenceQuery query)
	{
		boolean firstColumn = true;

		for (String tableName : query.getTables())
		{
			TableMetaData tableMetadata = Schema.getTable(tableName);

			for (String columnName : tableMetadata.getColumnNames())
			{
				if (!firstColumn)
				{
					sql.append(", ");
				}

				sql.append(tableName);
				sql.append(".");
				sql.append(columnName);
				sql.append(" AS ");
				sql.append(tableName);
				sql.append("__");
				sql.append(columnName);

				firstColumn = false;
			}
		}
	}

	public static PersistenceObject get(PersistenceQuery query)
	{
		validateInitialized();
		try (Connection connection = getConnection(url, username, password))
		{
			return executeGet(connection, query);
		}
		catch (PersistenceException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new PersistenceException("Failed to execute get query", e);
		}
	}

	public static PersistenceDataSet execute(PersistenceQuery query)
	{
		validateInitialized();
		try (Connection connection = getConnection(url, username, password))
		{
			return executeQuery(connection, query);
		}
		catch (PersistenceException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new PersistenceException("Failed to execute aggregate query", e);
		}
	}

	static PersistenceObject executeGet(Connection connection, PersistenceQuery query)
	{
		if (query == null)
		{
			throw new IllegalArgumentException("PersistenceQuery cannot be null");
		}

		if (!query.getAggregateExpressions().isEmpty())
		{
			throw new IllegalArgumentException("Aggregate query must use execute()");
		}

		StringBuilder sql = new StringBuilder();

		List<Object> values = new ArrayList<>();

		buildSelectQuery(sql, values, query);

		PersistenceObject persistenceObject = new PersistenceObject();

		try
		{
			try (PreparedStatement statement = connection.prepareStatement(sql.toString()))
			{
				for (int i = 0; i < values.size(); i++)
				{
					statement.setObject(i + 1, values.get(i));
				}

				try (ResultSet resultSet = statement.executeQuery())
				{
					while (resultSet.next())
					{
						for (String tableName : query.getTables())
						{
							addTableRow(persistenceObject, resultSet, query, tableName);
						}
					}
				}
			}

			return persistenceObject;
		}
		catch (PersistenceException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new PersistenceException("Failed to execute get query", e);
		}
	}

	static PersistenceDataSet executeQuery(Connection connection, PersistenceQuery query)
	{
		if (query == null)
		{
			throw new IllegalArgumentException("PersistenceQuery cannot be null");
		}

		if (query.getAggregateExpressions().isEmpty())
		{
			throw new IllegalArgumentException("execute() requires an aggregate query");
		}

		if (query.isForUpdate())
		{
			throw new PersistenceException("FOR UPDATE is not supported for aggregate queries");
		}

		StringBuilder sql = new StringBuilder();

		List<Object> values = new ArrayList<>();

		buildSelectQuery(sql, values, query);

		PersistenceDataSet persistenceDataSet = new PersistenceDataSet();

		try
		{
			try (PreparedStatement statement = connection.prepareStatement(sql.toString()))
			{
				for (int i = 0; i < values.size(); i++)
				{
					statement.setObject(i + 1, values.get(i));
				}

				try (ResultSet resultSet = statement.executeQuery())
				{
					while (resultSet.next())
					{
						Map<String, Object> row = new java.util.LinkedHashMap<>();

						addSelectedColumns(row, resultSet, query);

						addAggregateValues(row, resultSet, query);

						persistenceDataSet.addRow(row);
					}
				}
			}

			return persistenceDataSet;
		}
		catch (PersistenceException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new PersistenceException("Failed to execute aggregate query", e);
		}
	}

	private static ColumnMetaData getPrimaryKeyColumn(Row row)
	{
		TableMetaData tableMetaData = Schema.getTable(row.getTableName());
		ColumnMetaData primaryKeyColumn = tableMetaData.getPrimaryKeyColumn();

		if (primaryKeyColumn == null)
		{
			throw new PersistenceException("Primary key is not defined for table " + row.getTableName());
		}

		return primaryKeyColumn;
	}

	private static Object getPrimaryKeyValue(Row row, ColumnMetaData primaryKeyColumn)
	{
		Object primaryKeyValue = row.get(primaryKeyColumn.getName());

		if (primaryKeyValue == null)
		{
			throw new PersistenceException("Primary key value cannot be null for table " + row.getTableName());
		}

		return primaryKeyValue;
	}
}