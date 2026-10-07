package com.sentinal.persistencefw.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TableMetaData
{
	private final String tableName;

	private final List<ColumnMetaData> columns =
		new ArrayList<>();

	private final Map<String, ColumnMetaData> columnMap =
		new LinkedHashMap<>();

	public TableMetaData(String tableName)
	{
		if (tableName == null || tableName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Table name cannot be null or empty"
			);
		}

		this.tableName = tableName;
	}

	public void addColumn(ColumnMetaData column)
	{
		if (column == null)
		{
			throw new IllegalArgumentException(
				"Column metadata cannot be null"
			);
		}

		if (columnMap.containsKey(column.getName()))
		{
			throw new IllegalArgumentException(
				"Duplicate column "
					+ column.getName()
					+ " in table "
					+ tableName
			);
		}

		if (column.isPrimaryKey() &&
			getPrimaryKeyColumn() != null)
		{
			throw new IllegalArgumentException(
				"Multiple primary keys are not supported for table "
					+ tableName
			);
		}

		columns.add(column);

		columnMap.put(
			column.getName(),
			column
		);
	}

	public String getTableName()
	{
		return tableName;
	}

	public List<ColumnMetaData> getColumns()
	{
		return columns;
	}

	public List<String> getColumnNames()
	{
		List<String> names =
			new ArrayList<>();

		for (ColumnMetaData column : columns)
		{
			names.add(column.getName());
		}

		return names;
	}

	public ColumnMetaData getColumn(
		String columnName)
	{
		ColumnMetaData column =
			columnMap.get(columnName);

		if (column == null)
		{
			throw new IllegalArgumentException(
				"Unknown column "
					+ columnName
					+ " in table "
					+ tableName
			);
		}

		return column;
	}

	public boolean hasColumn(
		String columnName)
	{
		return columnMap.containsKey(
			columnName
		);
	}

	public ColumnMetaData getPrimaryKeyColumn()
	{
		for (ColumnMetaData column : columns)
		{
			if (column.isPrimaryKey())
			{
				return column;
			}
		}

		return null;
	}

	public boolean hasPrimaryKey()
	{
		return getPrimaryKeyColumn() != null;
	}

	public ColumnMetaData getLastModifiedTimeColumn()
	{
		for (ColumnMetaData column : columns)
		{
			if (column.isLastModifiedTime())
			{
				return column;
			}
		}

		return null;
	}

	public List<ColumnMetaData> getForeignKeyColumns()
	{
		List<ColumnMetaData> foreignKeys =
			new ArrayList<>();

		for (ColumnMetaData column : columns)
		{
			if (column.isForeignKey())
			{
				foreignKeys.add(column);
			}
		}

		return foreignKeys;
	}
}