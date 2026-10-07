package com.sentinal.persistencefw;

import java.util.Map;

public class PersistenceDataSetRow
{
	private final Map<String, Object> values;

	PersistenceDataSetRow(Map<String, Object> values)
	{
		this.values = values;
	}

	public Object get(String columnName)
	{
		return values.get(columnName);
	}

	public String getString(String columnName)
	{
		Object value = get(columnName);

		if (value == null)
		{
			return null;
		}

		return value.toString();
	}

	public Integer getInteger(String columnName)
	{
		Object value = get(columnName);

		if (value == null)
		{
			return null;
		}

		if (value instanceof Number)
		{
			return ((Number) value).intValue();
		}

		return Integer.valueOf(value.toString());
	}

	public Long getLong(String columnName)
	{
		Object value = get(columnName);

		if (value == null)
		{
			return null;
		}

		if (value instanceof Number)
		{
			return ((Number) value).longValue();
		}

		return Long.valueOf(value.toString());
	}

	public Double getDouble(String columnName)
	{
		Object value = get(columnName);

		if (value == null)
		{
			return null;
		}

		if (value instanceof Number)
		{
			return ((Number) value).doubleValue();
		}

		return Double.valueOf(value.toString());
	}

	public boolean contains(String columnName)
	{
		return values.containsKey(columnName);
	}
}