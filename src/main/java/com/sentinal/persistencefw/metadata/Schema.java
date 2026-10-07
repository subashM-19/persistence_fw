package com.sentinal.persistencefw.metadata;

import java.util.LinkedHashMap;
import java.util.Map;

public class Schema
{
	private static final Map<String, TableMetaData> tables =
		new LinkedHashMap<>();

	private Schema()
	{
	}

	public static void registerTable(
		String tableName,
		ColumnMetaData... columns)
	{
		if (tableName == null || tableName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Table name cannot be null or empty"
			);
		}

		if (tables.containsKey(tableName))
		{
			throw new IllegalArgumentException(
				"Table already registered: " + tableName
			);
		}

		TableMetaData tableMetaData =
			new TableMetaData(tableName);

		if (columns != null)
		{
			for (ColumnMetaData column : columns)
			{
				tableMetaData.addColumn(column);
			}
		}

		tables.put(
			tableName,
			tableMetaData
		);
	}

	public static void validate()
	{
		for (TableMetaData tableMetaData : tables.values())
		{
			validateTable(tableMetaData);
		}
	}

	private static void validateTable(
		TableMetaData tableMetaData)
	{
		for (ColumnMetaData column :
			tableMetaData.getColumns())
		{
			if (column.isForeignKey())
			{
				validateForeignKey(
					tableMetaData,
					column
				);
			}
		}
	}

	private static void validateForeignKey(
		TableMetaData tableMetaData,
		ColumnMetaData column)
	{
		String referencedTableName =
			column.getReferencedTable();

		String referencedColumnName =
			column.getReferencedColumn();

		TableMetaData referencedTable =
			tables.get(referencedTableName);

		if (referencedTable == null)
		{
			throw new IllegalArgumentException(
				"Referenced table "
					+ referencedTableName
					+ " does not exist for column "
					+ tableMetaData.getTableName()
					+ "."
					+ column.getName()
			);
		}

		if (!referencedTable.hasColumn(
			referencedColumnName))
		{
			throw new IllegalArgumentException(
				"Referenced column "
					+ referencedTableName
					+ "."
					+ referencedColumnName
					+ " does not exist"
			);
		}

		ColumnMetaData referencedColumn =
			referencedTable.getColumn(
				referencedColumnName
			);

		if (column.getJdbcType() !=
			referencedColumn.getJdbcType())
		{
			throw new IllegalArgumentException(
				"Foreign key type mismatch between "
					+ tableMetaData.getTableName()
					+ "."
					+ column.getName()
					+ " and "
					+ referencedTableName
					+ "."
					+ referencedColumnName
			);
		}
	}

	public static TableMetaData getTable(
		String tableName)
	{
		TableMetaData tableMetaData =
			tables.get(tableName);

		if (tableMetaData == null)
		{
			throw new IllegalArgumentException(
				"Unknown table: " + tableName
			);
		}

		return tableMetaData;
	}

	public static boolean hasTable(
		String tableName)
	{
		return tables.containsKey(
			tableName
		);
	}

	public static void validateColumn(
		String tableName,
		String columnName)
	{
		TableMetaData tableMetaData =
			getTable(tableName);

		if (!tableMetaData.hasColumn(
			columnName))
		{
			throw new IllegalArgumentException(
				"Unknown column "
					+ tableName
					+ "."
					+ columnName
			);
		}
	}

	public static void clear()
	{
		tables.clear();
	}
}