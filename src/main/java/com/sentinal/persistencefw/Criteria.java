package com.sentinal.persistencefw;

import java.util.ArrayList;
import java.util.List;

public class Criteria
{
	public static final int EQUAL = 1;
	public static final int NOT_EQUAL = 2;
	public static final int GREATER_THAN = 3;
	public static final int GREATER_THAN_OR_EQUAL = 4;
	public static final int LESS_THAN = 5;
	public static final int LESS_THAN_OR_EQUAL = 6;
	public static final int LIKE = 7;
	public static final int IN = 8;

	private String columnName;
	private int comparator;
	private Object value;

	private List<Criteria> criteriaList;
	private boolean andOperator = true;

	public Criteria(String columnName, int comparator, Object value)
	{
		if (columnName == null || columnName.isEmpty())
		{
			throw new IllegalArgumentException("Column name cannot be null or empty");
		}

		this.columnName = columnName;
		this.comparator = comparator;
		this.value = value;
	}

	private Criteria(boolean andOperator, Criteria first, Criteria second)
	{
		this.andOperator = andOperator;
		this.criteriaList = new ArrayList<>();
		this.criteriaList.add(first);
		this.criteriaList.add(second);
	}

	public static Criteria and(Criteria first, Criteria second)
	{
		return new Criteria(true, first, second);
	}

	public static Criteria or(Criteria first, Criteria second)
	{
		return new Criteria(false, first, second);
	}

	public Criteria and(Criteria criteria)
	{
		return and(this, criteria);
	}

	public Criteria or(Criteria criteria)
	{
		return or(this, criteria);
	}

	public boolean matches(Row row)
	{
		if (criteriaList != null)
		{
			if (andOperator)
			{
				for (Criteria criteria : criteriaList)
				{
					if (!criteria.matches(row))
					{
						return false;
					}
				}

				return true;
			}

			for (Criteria criteria : criteriaList)
			{
				if (criteria.matches(row))
				{
					return true;
				}
			}

			return false;
		}

		Object rowValue = row.get(columnName);

		return compare(rowValue);
	}

	@SuppressWarnings("unchecked")
	private boolean compare(Object rowValue)
	{
		switch (comparator)
		{
			case EQUAL:
				return rowValue == null ? value == null : rowValue.equals(value);

			case NOT_EQUAL:
				return rowValue == null ? value != null : !rowValue.equals(value);

			case GREATER_THAN:
				return compareValues(rowValue, value) > 0;

			case GREATER_THAN_OR_EQUAL:
				return compareValues(rowValue, value) >= 0;

			case LESS_THAN:
				return compareValues(rowValue, value) < 0;

			case LESS_THAN_OR_EQUAL:
				return compareValues(rowValue, value) <= 0;

			case LIKE:
				return rowValue != null && value != null && rowValue.toString().contains(value.toString());

			case IN:
				if (!(value instanceof List))
				{
					throw new IllegalArgumentException("IN criteria requires a List value");
				}

				return ((List<Object>) value).contains(rowValue);

			default:
				throw new IllegalArgumentException("Unknown criteria comparator: " + comparator);
		}
	}

	@SuppressWarnings("unchecked")
	private int compareValues(Object first, Object second)
	{
		if (first == null || second == null)
		{
			throw new IllegalArgumentException("Cannot compare null values");
		}

		if (!(first instanceof Comparable))
		{
			throw new IllegalArgumentException("Value is not comparable: " + first.getClass().getName());
		}

		return ((Comparable<Object>) first).compareTo(second);
	}

	public String getColumnName()
	{
		return columnName;
	}

	public int getComparator()
	{
		return comparator;
	}

	public Object getValue()
	{
		return value;
	}

	public List<Criteria> getCriteriaList()
	{
		return criteriaList;
	}

	public boolean isAndOperator()
	{
		return andOperator;
	}
}