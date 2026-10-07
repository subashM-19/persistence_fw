package com.sentinal.persistencefw;

import java.util.ArrayList;
import java.util.List;

public class PersistenceQuery
{
	public static final int ASC = 1;
	public static final int DESC = 2;

	public static final int COUNT = 1;
	public static final int SUM = 2;
	public static final int AVG = 3;
	public static final int MIN = 4;
	public static final int MAX = 5;

	private final String tableName;
	private final List<String> tables = new ArrayList<>();
	private final List<String> columns = new ArrayList<>();
	private final List<String> columnAliases = new ArrayList<>();
	private final List<String> aggregateExpressions = new ArrayList<>();
	private final List<String> aggregateAliases = new ArrayList<>();
	private final List<Join> joins = new ArrayList<>();
	private final List<String> orderByColumns = new ArrayList<>();
	private final List<Integer> orderByTypes = new ArrayList<>();
	private final List<String> groupByColumns = new ArrayList<>();

	private Criteria criteria;
	private Integer limit;
	private Integer offset;
	private boolean forUpdate;

	public PersistenceQuery(String tableName)
	{
		if (tableName == null || tableName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Table name cannot be null or empty"
			);
		}

		this.tableName = tableName;
		tables.add(tableName);
	}

	public PersistenceQuery select(
		String tableName,
		String columnName)
	{
		if (tableName == null || tableName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Table name cannot be null or empty"
			);
		}

		if (columnName == null || columnName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Column name cannot be null or empty"
			);
		}

		columns.add(
			tableName + "." + columnName
		);

		columnAliases.add(
			tableName + "__" + columnName
		);

		return this;
	}

	public PersistenceQuery select(String columnName)
	{
		if (columnName == null || columnName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Column name cannot be null or empty"
			);
		}

		columns.add(
			tableName + "." + columnName
		);

		columnAliases.add(
			tableName + "__" + columnName
		);

		return this;
	}

	public PersistenceQuery count(
		String tableName,
		String columnName,
		String alias)
	{
		addAggregate(
			COUNT,
			tableName,
			columnName,
			alias
		);

		return this;
	}

	public PersistenceQuery sum(
		String tableName,
		String columnName,
		String alias)
	{
		addAggregate(
			SUM,
			tableName,
			columnName,
			alias
		);

		return this;
	}

	public PersistenceQuery avg(
		String tableName,
		String columnName,
		String alias)
	{
		addAggregate(
			AVG,
			tableName,
			columnName,
			alias
		);

		return this;
	}

	public PersistenceQuery min(
		String tableName,
		String columnName,
		String alias)
	{
		addAggregate(
			MIN,
			tableName,
			columnName,
			alias
		);

		return this;
	}

	public PersistenceQuery max(
		String tableName,
		String columnName,
		String alias)
	{
		addAggregate(
			MAX,
			tableName,
			columnName,
			alias
		);

		return this;
	}

	private void addAggregate(
		int aggregateType,
		String tableName,
		String columnName,
		String alias)
	{
		if (tableName == null || tableName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Table name cannot be null or empty"
			);
		}

		if (columnName == null || columnName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Column name cannot be null or empty"
			);
		}

		if (alias == null || alias.isEmpty())
		{
			throw new IllegalArgumentException(
				"Aggregate alias cannot be null or empty"
			);
		}

		String function;

		switch (aggregateType)
		{
			case COUNT:
				function = "COUNT";
				break;

			case SUM:
				function = "SUM";
				break;

			case AVG:
				function = "AVG";
				break;

			case MIN:
				function = "MIN";
				break;

			case MAX:
				function = "MAX";
				break;

			default:
				throw new IllegalArgumentException(
					"Unsupported aggregate type: " +
						aggregateType
				);
		}

		aggregateExpressions.add(
			function +
				"(" +
				tableName +
				"." +
				columnName +
				")"
		);

		aggregateAliases.add(alias);
	}

	public PersistenceQuery join(
		int joinType,
		String tableName,
		String leftTable,
		String leftColumn,
		String rightTable,
		String rightColumn)
	{
		if (tableName == null || tableName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Join table name cannot be null or empty"
			);
		}

		joins.add(
			new Join(
				joinType,
				tableName,
				leftTable,
				leftColumn,
				rightTable,
				rightColumn
			)
		);

		if (!tables.contains(tableName))
		{
			tables.add(tableName);
		}

		return this;
	}

	public PersistenceQuery where(Criteria criteria)
	{
		this.criteria = criteria;
		return this;
	}

	public PersistenceQuery groupBy(
		String tableName,
		String columnName)
	{
		if (tableName == null || tableName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Table name cannot be null or empty"
			);
		}

		if (columnName == null || columnName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Column name cannot be null or empty"
			);
		}

		groupByColumns.add(
			tableName + "." + columnName
		);

		return this;
	}

	public PersistenceQuery orderBy(
		String columnName,
		int orderType)
	{
		if (columnName == null || columnName.isEmpty())
		{
			throw new IllegalArgumentException(
				"Column name cannot be null or empty"
			);
		}

		if (orderType != ASC && orderType != DESC)
		{
			throw new IllegalArgumentException(
				"Invalid order type: " + orderType
			);
		}

		orderByColumns.add(columnName);
		orderByTypes.add(orderType);

		return this;
	}

	public PersistenceQuery limit(int limit)
	{
		if (limit < 0)
		{
			throw new IllegalArgumentException(
				"Limit cannot be negative"
			);
		}

		this.limit = limit;

		return this;
	}

	public PersistenceQuery offset(int offset)
	{
		if (offset < 0)
		{
			throw new IllegalArgumentException(
				"Offset cannot be negative"
			);
		}

		this.offset = offset;

		return this;
	}

	public String getTableName()
	{
		return tableName;
	}

	public List<String> getTables()
	{
		return tables;
	}

	public List<String> getColumns()
	{
		return columns;
	}

	public List<String> getColumnAliases()
	{
		return columnAliases;
	}

	public List<String> getAggregateExpressions()
	{
		return aggregateExpressions;
	}

	public List<String> getAggregateAliases()
	{
		return aggregateAliases;
	}

	public List<Join> getJoins()
	{
		return joins;
	}

	public Criteria getCriteria()
	{
		return criteria;
	}

	public List<String> getGroupByColumns()
	{
		return groupByColumns;
	}

	public List<String> getOrderByColumns()
	{
		return orderByColumns;
	}

	public List<Integer> getOrderByTypes()
	{
		return orderByTypes;
	}

	public Integer getLimit()
	{
		return limit;
	}

	public Integer getOffset()
	{
		return offset;
	}

	public PersistenceQuery forUpdate()
	{
		this.forUpdate = true;
		return this;
	}

	public boolean isForUpdate()
	{
		return forUpdate;
	}
}