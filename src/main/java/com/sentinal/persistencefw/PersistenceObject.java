package com.sentinal.persistencefw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PersistenceObject
{
	private HashMap<String, List<Row>> tableToRowList = new HashMap<>();
	private HashMap<Integer, PersistenceOperations> operTypeVsOperTables = new HashMap<>();
	private List<PersistenceActions> operations = new ArrayList<>();

	public void addRow(Row row)
	{
		if (row == null)
		{
			throw new IllegalArgumentException("Row cannot be null");
		}

		String tableName = row.getTableName();

		if (tableName == null || tableName.isEmpty())
		{
			throw new IllegalArgumentException("Row table name cannot be null or empty");
		}

		List<Row> rowList = tableToRowList.computeIfAbsent(tableName, key -> new ArrayList<>());

		rowList.add(row);
	}

	public List<Row> getRows(String tableName)
	{
		return tableToRowList.getOrDefault(tableName, new ArrayList<>());
	}

	public Row getRow(String tableName, Criteria criteria)
	{
		List<Row> rowList = tableToRowList.get(tableName);

		if (rowList == null)
		{
			return null;
		}

		for (Row row : rowList)
		{
			if (criteria == null || criteria.matches(row))
			{
				return row;
			}
		}

		return null;
	}

	public void addRowForInsert(Row row)
	{
		addRow(row);
		addOperation(PersistenceActions.INSERT, row);
	}

	public void updateRow(Row row)
	{
		if (row == null)
		{
			throw new IllegalArgumentException("Row cannot be null");
		}

		addOperation(PersistenceActions.UPDATE, row);
	}

	public void deleteRow(Row row)
	{
		if (row == null)
		{
			throw new IllegalArgumentException("Row cannot be null");
		}

		addOperation(PersistenceActions.DELETE, row);
	}

	private void addOperation(int operation, Row row)
	{
		PersistenceOperations persistenceOperations =
			operTypeVsOperTables.computeIfAbsent(
				operation,
				key -> new PersistenceOperations()
			);

		PersistenceActions persistenceActions =
			persistenceOperations.addActionInfo(operation, row);

		operations.add(persistenceActions);
	}

	public HashMap<String, List<Row>> getTableToRowList()
	{
		return tableToRowList;
	}

	public HashMap<Integer, PersistenceOperations> getOperTypeVsOperTables()
	{
		return operTypeVsOperTables;
	}

	public List<Row> getAllRows()
	{
		List<Row> rows = new ArrayList<>();

		for (List<Row> rowList : tableToRowList.values())
		{
			rows.addAll(rowList);
		}

		return rows;
	}

	public boolean isEmpty()
	{
		return tableToRowList.isEmpty();
	}

	public boolean hasOperations()
	{
		return !operTypeVsOperTables.isEmpty();
	}

	public void clearOperations()
	{
		operTypeVsOperTables.clear();
		operations.clear();
	}

	public List<PersistenceActions> getOperations()
	{
		return operations;
	}
}