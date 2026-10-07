package com.sentinal.persistencefw.metadata;

public class ColumnMetaData
{
	private final String name;
	private final int jdbcType;
	private final String typeName;
	private final int size;

	private final boolean nullable;
	private final boolean autoIncrement;
	private final boolean primaryKey;
	private final boolean unique;
	private final Object defaultValue;
	private final boolean lastModifiedTime;

	private final String referencedTable;
	private final String referencedColumn;

	public ColumnMetaData(
		String name,
		int jdbcType,
		String typeName,
		int size,
		boolean nullable,
		boolean autoIncrement,
		boolean primaryKey,
		boolean unique,
		Object defaultValue,
		boolean lastModifiedTime,
		String referencedTable,
		String referencedColumn)
	{
		if (name == null || name.isEmpty())
		{
			throw new IllegalArgumentException(
				"Column name cannot be null or empty"
			);
		}

		if (typeName == null || typeName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Column type name cannot be null or empty"
			);
		}

		if (referencedTable != null && referencedColumn == null)
		{
			throw new IllegalArgumentException(
				"Referenced column cannot be null when referenced table is provided"
			);
		}

		if (referencedTable == null && referencedColumn != null)
		{
			throw new IllegalArgumentException(
				"Referenced table cannot be null when referenced column is provided"
			);
		}

		this.name = name;
		this.jdbcType = jdbcType;
		this.typeName = typeName;
		this.size = size;
		this.nullable = nullable;
		this.autoIncrement = autoIncrement;
		this.primaryKey = primaryKey;
		this.unique = unique;
		this.defaultValue = defaultValue;
		this.lastModifiedTime = lastModifiedTime;
		this.referencedTable = referencedTable;
		this.referencedColumn = referencedColumn;
	}

	public String getName()
	{
		return name;
	}

	public int getJdbcType()
	{
		return jdbcType;
	}

	public String getTypeName()
	{
		return typeName;
	}

	public int getSize()
	{
		return size;
	}

	public boolean isNullable()
	{
		return nullable;
	}

	public boolean isAutoIncrement()
	{
		return autoIncrement;
	}

	public boolean isPrimaryKey()
	{
		return primaryKey;
	}

	public boolean isUnique()
	{
		return unique;
	}

	public Object getDefaultValue()
	{
		return defaultValue;
	}

	public boolean isLastModifiedTime()
	{
		return lastModifiedTime;
	}

	public boolean isForeignKey()
	{
		return referencedTable != null &&
			referencedColumn != null;
	}

	public String getReferencedTable()
	{
		return referencedTable;
	}

	public String getReferencedColumn()
	{
		return referencedColumn;
	}
}