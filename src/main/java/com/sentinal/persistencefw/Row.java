package com.sentinal.persistencefw;

import com.sentinal.persistencefw.metadata.Schema;
import com.sentinal.persistencefw.metadata.TableMetaData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Row
{
	private String tableName;
	private Object[] values;
	private transient List<String> tableColumns;
	private transient Set<String> modifiedColumns = new HashSet<>();

	public Row(String tableName)
	{
		if (tableName == null || tableName.isEmpty())
		{
			throw new IllegalArgumentException("Table name cannot be null or empty");
		}

		this.tableName = tableName;

		initializeColumns();
	}

	private void initializeColumns()
	{
		TableMetaData tableMetadata = Schema.getTable(tableName);
		tableColumns = new ArrayList<>(tableMetadata.getColumnNames());

		values = new Object[tableColumns.size()];
	}

	public String getTableName()
	{
		return tableName;
	}

	public Object get(String columnName)
	{
		return get(findColumn(columnName));
	}


	public Object get(int index)
	{
		if (values == null)
		{
			return null;
		}

		return values[index - 1];
	}


	public void set(String columnName, Object value)
	{
		int index = findColumn(columnName);

		values[index - 1] = value;
		modifiedColumns.add(columnName);
	}


	public void set(int index, Object value)
	{
		ensureValues();

		values[index - 1] = value;
	}


	public int findColumn(String columnName)
	{
		if (columnName == null)
		{
			throw new IllegalArgumentException("Column name cannot be null");
		}

		int index = getColumns().indexOf(columnName);

		if (index == -1)
		{
			throw new IllegalArgumentException("Unknown column " + columnName + " in table " + tableName);
		}

		return index + 1;
	}


	public List<String> getColumns()
	{
		if (tableColumns == null)
		{
			tableColumns = new ArrayList<>();
		}

		return tableColumns;
	}


	public void setColumns(List<String> columns)
	{
		this.tableColumns = new ArrayList<>(columns);

		this.values = new Object[columns.size()];
	}


	private void ensureValues()
	{
		if (values == null)
		{
			values = new Object[getColumns().size()];
		}
	}

	public boolean isModified(String columnName)
	{
		return modifiedColumns.contains(columnName);
	}

	public Set<String> getModifiedColumns()
	{
		return new HashSet<>(modifiedColumns);
	}

	public void clearModifiedColumns()
	{
		modifiedColumns.clear();
	}

	void setLoadedValue(String columnName, Object value)
	{
		int index = findColumn(columnName);
		values[index - 1] = value;
	}
}