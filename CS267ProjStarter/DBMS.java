import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * CS 267 - Project - Implements create index, drop index, list table, and
 * exploit the index in select statements.
 */
public class DBMS {
	private static final String COMMAND_FILE_LOC = "Commands.txt";
	private static final String OUTPUT_FILE_LOC = "Output.txt";
	private ArrayList<Predicate> predicateFinal;
	private static final String TABLE_FOLDER_NAME = "tables";
	private static final String TABLE_FILE_EXT = ".tab";
	private static final String INDEX_FILE_EXT = ".idx";

	private DbmsPrinter out;
	private ArrayList<Table> tables;

	public DBMS() {
		tables = new ArrayList<Table>();
	}

	/**
	 * Main method to run the DBMS engine.
	 * 
	 * @param args
	 *            arg[0] is input file, arg[1] is output file.
	 */
	@SuppressWarnings("resource")
	public static void main(String[] args) {
		DBMS db = new DBMS();
		db.out = new DbmsPrinter();
		Scanner in = null;
		try {
			// set input file
			if (args.length > 0) {
				in = new Scanner(new File(args[0]));
			} else {  
				in = new Scanner(new File(COMMAND_FILE_LOC));
			}

			// set output files
			if (args.length > 1) {
				db.out.addPrinter(args[1]);
			} else {
				db.out.addPrinter(OUTPUT_FILE_LOC);
			}

			// Load data to memory
			db.loadTables();

			// Go through each line in the Command.txt file
			while (in.hasNextLine()) {
				String sql = in.nextLine();
				StringTokenizer tokenizer = new StringTokenizer(sql);

				// Evaluate the SQL statement
				if (tokenizer.hasMoreTokens()) {
					String command = tokenizer.nextToken();
					if (command.equalsIgnoreCase("CREATE")) {
						if (tokenizer.hasMoreTokens()) {
							command = tokenizer.nextToken();
							if (command.equalsIgnoreCase("TABLE")) {
								db.createTable(sql, tokenizer);
							} else if (command.equalsIgnoreCase("UNIQUE")) {
								// TODO your PART 1 code goes here
								command = tokenizer.nextToken();
								if (command.equalsIgnoreCase("INDEX")){
									db.CreateIndex(sql, tokenizer,true);
								}
							}
							else if (command.equalsIgnoreCase("INDEX")) {
								// TODO your PART 1 code goes here
									db.CreateIndex(sql, tokenizer,false);
							} else {
								throw new DbmsError("Invalid CREATE " + command
										+ " statement. '" + sql + "'.");
							}
						} else {
							throw new DbmsError("Invalid CREATE statement. '"
									+ sql + "'.");
						}
					} else if (command.equalsIgnoreCase("INSERT")) {
						db.insertInto(sql, tokenizer);
					} else if (command.equalsIgnoreCase("DROP")) {
						if (tokenizer.hasMoreTokens()) {
							command = tokenizer.nextToken();
							if (command.equalsIgnoreCase("TABLE")) {
								db.dropTable(sql, tokenizer);
							} else if (command.equalsIgnoreCase("INDEX")) {
								// TODO your PART 1 code goes here
								db.dropIndex(sql, tokenizer);
							} else {
								throw new DbmsError("Invalid DROP " + command
										+ " statement. '" + sql + "'.");
							}
						} else {
							throw new DbmsError("Invalid DROP statement. '"
									+ sql + "'.");
						}
					} else if (command.equalsIgnoreCase("RUNSTATS")) {
						// TODO your PART 1 code goes here
						String tableName = db.runStats(tokenizer);
						// TODO replace the table name below with the table name
						// in the command to print the RUNSTATS output
						db.printRunstats(tableName);
					} else if (command.equalsIgnoreCase("SELECT")) {
						// TODO your PART 2 code goes here
						db.predicateFinal= new ArrayList<Predicate>();
						db.selectQuery(sql,tokenizer);
					} else if (command.equalsIgnoreCase("--")) {
						// Ignore this command as a comment
					} else if (command.equalsIgnoreCase("COMMIT")) {
						try {
							// Check for ";"
							if (!tokenizer.nextElement().equals(";")) {
								throw new NoSuchElementException();
							}

							// Check if there are more tokens
							if (tokenizer.hasMoreTokens()) {
								throw new NoSuchElementException();
							}

							// Save tables to files
							for (Table table : db.tables) {
								db.storeTableFile(table);
							}
						} catch (NoSuchElementException ex) {
							throw new DbmsError("Invalid COMMIT statement. '"
									+ sql + "'.");
						}
					} else {
						throw new DbmsError("Invalid statement. '" + sql + "'.");
					}
				}
			}

			// Save tables to files
			for (Table table : db.tables) {
				db.storeTableFile(table);
			}
		} catch (DbmsError ex) {
			db.out.println("DBMS ERROR:  " + ex.getMessage());
			ex.printStackTrace();
		} catch (Exception ex) {
			db.out.println("JAVA ERROR:  " + ex.getMessage());
			ex.printStackTrace();
		} finally {
			// clean up
			try {
				in.close();
			} catch (Exception ex) {
			}

			try {
				db.out.cleanup();
			} catch (Exception ex) {
			}
		}
	}

	/**
	 * Loads tables to memory
	 * 
	 * @throws Exception
	 */
	private void loadTables() throws Exception {
		// Get all the available tables in the "tables" directory
		File tableDir = new File(TABLE_FOLDER_NAME);
		if (tableDir.exists() && tableDir.isDirectory()) {
			for (File tableFile : tableDir.listFiles()) {
				// For each file check if the file extension is ".tab"
				String tableName = tableFile.getName();
				int periodLoc = tableName.lastIndexOf(".");
				String tableFileExt = tableName.substring(tableName
						.lastIndexOf(".") + 1);
				if (tableFileExt.equalsIgnoreCase("tab")) {
					// If it is a ".tab" file, create a table structure
					Table table = new Table(tableName.substring(0, periodLoc));
					Scanner in = new Scanner(tableFile);

					try {
						// Read the file to get Column definitions
						int numCols = Integer.parseInt(in.nextLine());

						for (int i = 0; i < numCols; i++) {
							StringTokenizer tokenizer = new StringTokenizer(
									in.nextLine());
							String name = tokenizer.nextToken();
							String type = tokenizer.nextToken();
							boolean nullable = Boolean.parseBoolean(tokenizer
									.nextToken());
							switch (type.charAt(0)) {
							case 'C':
								table.addColumn(new Column(i + 1, name,
										Column.ColType.CHAR, Integer
												.parseInt(type.substring(1)),
										nullable));
								break;
							case 'I':
								table.addColumn(new Column(i + 1, name,
										Column.ColType.INT, 4, nullable));
								break;
							default:
								break;
							}
						}

						// Read the file for index definitions
						int numIdx = Integer.parseInt(in.nextLine());
						for (int i = 0; i < numIdx; i++) {
							StringTokenizer tokenizer = new StringTokenizer(
									in.nextLine());
							Index index = new Index(tokenizer.nextToken());
							index.setIsUnique(Boolean.parseBoolean(tokenizer
									.nextToken()));

							int idxColPos = 1;
							while (tokenizer.hasMoreTokens()) {
								String colDef = tokenizer.nextToken();
								Index.IndexKeyDef def = index.new IndexKeyDef();
								def.idxColPos = idxColPos;
								def.colId = Integer.parseInt(colDef.substring(
										0, colDef.length() - 1));
								switch (colDef.charAt(colDef.length() - 1)) {
								case 'A':
									def.descOrder = false;
									break;
								case 'D':
									def.descOrder = true;
									break;
								default:
									break;
								}

								index.addIdxKey(def);
								idxColPos++;
							}

							table.addIndex(index);
							loadIndex(table, index);
						}

						// Read the data from the file
						int numRows = Integer.parseInt(in.nextLine());
						for (int i = 0; i < numRows; i++) {
							table.addData(in.nextLine());
						}
						
						// Read RUNSTATS from the file
						while(in.hasNextLine()) {
							String line = in.nextLine();
							StringTokenizer toks = new StringTokenizer(line);
							if(toks.nextToken().equals("STATS")) {
								String stats = toks.nextToken();
								if(stats.equals("TABCARD")) {
									table.setTableCard(Integer.parseInt(toks.nextToken()));
								} else if (stats.equals("COLCARD")) {
									Column col = table.getColumns().get(Integer.parseInt(toks.nextToken()));
									col.setColCard(Integer.parseInt(toks.nextToken()));
									col.setHiKey(toks.nextToken());
									col.setLoKey(toks.nextToken());
								} else {
									throw new DbmsError("Invalid STATS.");
								}
							} else {
								throw new DbmsError("Invalid STATS.");
							}
						}
					} catch (DbmsError ex) {
						throw ex;
					} catch (Exception ex) {
						throw new DbmsError("Invalid table file format.");
					} finally {
						in.close();
					}
					tables.add(table);
				}
			}
		} else {
			throw new FileNotFoundException(
					"The system cannot find the tables directory specified.");
		}
	}

	/**
	 * Loads specified table to memory
	 * 
	 * @throws DbmsError
	 */
	private void loadIndex(Table table, Index index) throws DbmsError {
		try {
			Scanner in = new Scanner(new File(TABLE_FOLDER_NAME,
					table.getTableName() + index.getIdxName() + INDEX_FILE_EXT));
			String def = in.nextLine();
			String rows = in.nextLine();

			while (in.hasNext()) {
				String line = in.nextLine();
				Index.IndexKeyVal val = index.new IndexKeyVal();
				val.rid = Integer.parseInt(new StringTokenizer(line)
						.nextToken());
				val.value = line.substring(line.indexOf("'") + 1,
						line.lastIndexOf("'"));
				index.addKey(val);
			}
			in.close();
		} catch (Exception ex) {
			throw new DbmsError("Invalid index file format.");
		}
	}

	/**
	 * CREATE TABLE
	 * <table name>
	 * ( <col name> < CHAR ( length ) | INT > <NOT NULL> ) ;
	 * 
	 * @param sql
	 * @param tokenizer
	 * @throws Exception
	 */
	private void createTable(String sql, StringTokenizer tokenizer)
			throws Exception {
		try {
			// Check the table name
			String tok = tokenizer.nextToken().toUpperCase();
			if (Character.isAlphabetic(tok.charAt(0))) {
				// Check if the table already exists
				for (Table tab : tables) {
					if (tab.getTableName().equals(tok) && !tab.delete) {
						throw new DbmsError("Table " + tok
								+ "already exists. '" + sql + "'.");
					}
				}

				// Create a table instance to store data in memory
				Table table = new Table(tok.toUpperCase());

				// Check for '('
				tok = tokenizer.nextToken();
				if (tok.equals("(")) {
					// Look through the column definitions and add them to the
					// table in memory
					boolean done = false;
					int colId = 1;
					while (!done) {
						tok = tokenizer.nextToken();
						if (Character.isAlphabetic(tok.charAt(0))) {
							String colName = tok;
							Column.ColType colType = Column.ColType.INT;
							int colLength = 4;
							boolean nullable = true;

							tok = tokenizer.nextToken();
							if (tok.equalsIgnoreCase("INT")) {
								// use the default Column.ColType and colLength

								// Look for NOT NULL or ',' or ')'
								tok = tokenizer.nextToken();
								if (tok.equalsIgnoreCase("NOT")) {
									// look for NULL after NOT
									tok = tokenizer.nextToken();
									if (tok.equalsIgnoreCase("NULL")) {
										nullable = false;
									} else {
										throw new NoSuchElementException();
									}

									tok = tokenizer.nextToken();
									if (tok.equals(",")) {
										// Continue to the next column
									} else if (tok.equalsIgnoreCase(")")) {
										done = true;
									} else {
										throw new NoSuchElementException();
									}
								} else if (tok.equalsIgnoreCase(",")) {
									// Continue to the next column
								} else if (tok.equalsIgnoreCase(")")) {
									done = true;
								} else {
									throw new NoSuchElementException();
								}
							} else if (tok.equalsIgnoreCase("CHAR")) {
								colType = Column.ColType.CHAR;

								// Look for column length
								tok = tokenizer.nextToken();
								if (tok.equals("(")) {
									tok = tokenizer.nextToken();
									try {
										colLength = Integer.parseInt(tok);
									} catch (NumberFormatException ex) {
										throw new DbmsError(
												"Invalid table column length for "
														+ colName + ". '" + sql
														+ "'.");
									}

									// Check for the closing ')'
									tok = tokenizer.nextToken();
									if (!tok.equals(")")) {
										throw new DbmsError(
												"Invalid table column definition for "
														+ colName + ". '" + sql
														+ "'.");
									}

									// Look for NOT NULL or ',' or ')'
									tok = tokenizer.nextToken();
									if (tok.equalsIgnoreCase("NOT")) {
										// Look for NULL after NOT
										tok = tokenizer.nextToken();
										if (tok.equalsIgnoreCase("NULL")) {
											nullable = false;

											tok = tokenizer.nextToken();
											if (tok.equals(",")) {
												// Continue to the next column
											} else if (tok
													.equalsIgnoreCase(")")) {
												done = true;
											} else {
												throw new NoSuchElementException();
											}
										} else {
											throw new NoSuchElementException();
										}
									} else if (tok.equalsIgnoreCase(",")) {
										// Continue to the next column
									} else if (tok.equalsIgnoreCase(")")) {
										done = true;
									} else {
										throw new NoSuchElementException();
									}
								} else {
									throw new DbmsError(
											"Invalid table column definition for "
													+ colName + ". '" + sql
													+ "'.");
								}
							} else {
								throw new NoSuchElementException();
							}

							// Everything is ok. Add the column to the table
							table.addColumn(new Column(colId, colName, colType,
									colLength, nullable));
							colId++;
						} else {
							// if(colId == 1) {
							throw new DbmsError(
									"Invalid table column identifier " + tok
											+ ". '" + sql + "'.");
							// }
						}
					}

					// Check for the semicolon
					tok = tokenizer.nextToken();
					if (!tok.equals(";")) {
						throw new NoSuchElementException();
					}

					// Check if there are more tokens
					if (tokenizer.hasMoreTokens()) {
						throw new NoSuchElementException();
					}

					if (table.getNumColumns() == 0) {
						throw new DbmsError(
								"No column descriptions specified. '" + sql
										+ "'.");
					}

					// The table is stored into memory when this program exists.
					tables.add(table);

					out.println("Table " + table.getTableName()
							+ " was created.");
				} else {
					throw new NoSuchElementException();
				}
			} else {
				throw new DbmsError("Invalid table identifier " + tok + ". '"
						+ sql + "'.");
			}
		} catch (NoSuchElementException ex) {
			throw new DbmsError("Invalid CREATE TABLE statement. '" + sql
					+ "'.");
		}
	}

	/**
	 * INSERT INTO
	 * <table name>
	 * VALUES ( val1 , val2, .... ) ;
	 * 
	 * @param sql
	 * @param tokenizer
	 * @throws Exception
	 */
	private void insertInto(String sql, StringTokenizer tokenizer)
			throws Exception {
		try {
			String tok = tokenizer.nextToken();
			if (tok.equalsIgnoreCase("INTO")) {
				tok = tokenizer.nextToken().trim().toUpperCase();
				Table table = null;
				for (Table tab : tables) {
					if (tab.getTableName().equals(tok)) {
						table = tab;
						break;
					}
				}

				if (table == null) {
					throw new DbmsError("Table " + tok + " does not exist.");
				}

				tok = tokenizer.nextToken();
				if (tok.equalsIgnoreCase("VALUES")) {
					tok = tokenizer.nextToken();
					if (tok.equalsIgnoreCase("(")) {
						tok = tokenizer.nextToken();
						String values = String.format("%3s", table.getData()
								.size() + 1)
								+ " ";
						int colId = 0;
						boolean done = false;
						while (!done) {
							if (tok.equals(")")) {
								done = true;
								break;
							} else if (tok.equals(",")) {
								// Continue to the next value
							} else {
								if (colId == table.getNumColumns()) {
									throw new DbmsError(
											"Invalid number of values were given.");
								}

								Column col = table.getColumns().get(colId);

								if (tok.equals("-") && !col.isColNullable()) {
									throw new DbmsError(
											"A NOT NULL column cannot have null. '"
													+ sql + "'.");
								}

								if (col.getColType() == Column.ColType.INT) {
									try {
										if(!tok.equals("-")) {
											int temp = Integer.parseInt(tok);
										}
									} catch (Exception ex) {
										throw new DbmsError(
												"An INT column cannot hold a CHAR. '"
														+ sql + "'.");
									}

									tok = String.format("%10s", tok.trim());
								} else if (col.getColType() == Column.ColType.CHAR) {
									int length = tok.length();
									if (length > col.getColLength()) {
										throw new DbmsError(
												"A CHAR column cannot exceede its length. '"
														+ sql + "'.");
									}

									tok = String.format(
											"%-" + col.getColLength() + "s",
											tok.trim());
								}

								values += tok + " ";
								colId++;
							}
							tok = tokenizer.nextToken().trim();
						}

						if (colId != table.getNumColumns()) {
							throw new DbmsError(
									"Invalid number of values were given.");
						}

						// Check for the semicolon
						tok = tokenizer.nextToken();
						if (!tok.equals(";")) {
							throw new NoSuchElementException();
						}

						// Check if there are more tokens
						if (tokenizer.hasMoreTokens()) {
							throw new NoSuchElementException();
						}
						// insert the value to table and update index
						for(Index index: table.getIndexes())
						{
								String[] val = values.split("\\s+");
								String tempval="", tempKey= "",tempval1 = "";
								int colLength = 0;
								for(Index.IndexKeyDef def:index.getIdxKey())
								{
									if(index.getIsUnique())
									{
										checkUnique(tempval, table, index,"INSERT");
									}
									else if(!val[def.colId+1].equals("-")){
									if(!def.descOrder)
									{
										if(table.getColumns().get(def.colId -1).getColType()==Column.ColType.CHAR)
										{
											colLength = table.getColumns().get(def.colId -1).getColLength();
											tempKey = String.format("%1$-" + colLength +"s", val[def.colId+1]);														
										}
										else
										{
											tempKey = String.format("%010d",Integer.parseInt(val[def.colId+1])) ;
										}
									}
									else
									{
										tempKey = findCompliment(val[def.colId+1].toString(),table.getColumns().get(def.colId -1));
									}
									tempval = tempval.concat(tempKey);
									//tempval1 = tempval1.concat(val[def.colId+1]);
									}
									else
									{
										if(table.getColumns().get(def.colId -1).getColType()==Column.ColType.CHAR)
										{
											colLength = table.getColumns().get(def.colId -1).getColLength();
											tempKey = String.format("%1$-" + colLength +"s", "~");
										}
										else
										{
											tempKey =String.format("%10s", "~");
										}
										tempval = tempval.concat(tempKey);
									}
								}
								addIndexVal(index,tempval);
						}					
						table.addData(values);	
						out.println("One line was saved to the table. "
								+ table.getTableName() + ": " + values);
					} else {
						throw new NoSuchElementException();
					}
				} else {
					throw new NoSuchElementException();
				}
			} else {
				throw new NoSuchElementException();
			}
		} catch (NoSuchElementException ex) {
			throw new DbmsError("Invalid INSERT INTO statement. '" + sql + "'.");
		}
	}

	/**
	 * DROP TABLE
	 * <table name>
	 * ;
	 * 
	 * @param sql
	 * @param tokenizer
	 * @throws Exception
	 */
	private void dropTable(String sql, StringTokenizer tokenizer)
			throws Exception {
		try {
			// Get table name
			String tableName = tokenizer.nextToken();

			// Check for the semicolon
			String tok = tokenizer.nextToken();
			if (!tok.equals(";")) {
				throw new NoSuchElementException();
			}

			// Check if there are more tokens
			if (tokenizer.hasMoreTokens()) {
				throw new NoSuchElementException();
			}

			// Delete the table if everything is ok
			boolean dropped = false;
			for (Table table : tables) {
				if (table.getTableName().equalsIgnoreCase(tableName)) {
					table.delete = true;
					dropped = true;
					//delete the index after deleting the table
					for(Index indexes:table.getIndexes())
					{
						indexes.delete = true;
					}
					break;
				}
			}

			if (dropped) {
				out.println("Table " + tableName + " was dropped.");
			} else {
				throw new DbmsError("Table " + tableName + "does not exist. '" + sql + "'."); 
			}
		} catch (NoSuchElementException ex) {
			throw new DbmsError("Invalid DROP TABLE statement. '" + sql + "'.");
		}

	}

	private void printRunstats(String tableName) {
		for (Table table : tables) {
			if (table.getTableName().equals(tableName)) {
				out.println("TABLE CARDINALITY: " + table.getTableCard());
				for (Column column : table.getColumns()) {
					out.println(column.getColName());
					out.println("\tCOLUMN CARDINALITY: " + column.getColCard());
					out.println("\tCOLUMN HIGH KEY: " + column.getHiKey());
					out.println("\tCOLUMN LOW KEY: " + column.getLoKey());
				}
				break;
			}
		}
	}

	private void storeTableFile(Table table) throws FileNotFoundException {
		File tableFile = new File(TABLE_FOLDER_NAME, table.getTableName()
				+ TABLE_FILE_EXT);

		// Delete the file if it was marked for deletion
		if (table.delete) {
			try {
				tableFile.delete();
			} catch (Exception ex) {
				out.println("Unable to delete table file for "
						+ table.getTableName() + ".");
			}
			
			// Delete the index files too
			for (Index index : table.getIndexes()) {
				File indexFile = new File(TABLE_FOLDER_NAME, table.getTableName()
						+ index.getIdxName() + INDEX_FILE_EXT);
				
				try {
					indexFile.delete();
				} catch (Exception ex) {
					out.println("Unable to delete table file for "
							+ indexFile.getName() + ".");
				}
			}
		} else {
			// Create the table file writer
			PrintWriter out = new PrintWriter(tableFile);

			// Write the column descriptors
			out.println(table.getNumColumns());
			for (Column col : table.getColumns()) {
				if (col.getColType() == Column.ColType.INT) {
					out.println(col.getColName() + " I " + col.isColNullable());
				} else if (col.getColType() == Column.ColType.CHAR) {
					out.println(col.getColName() + " C" + col.getColLength()
							+ " " + col.isColNullable());
				}
			}

			// Write the index info		
			out.println(table.getNumIndexes());
			for (Index index : table.getIndexes()) {
				if(!index.delete) {
					String idxInfo = index.getIdxName() + " " + index.getIsUnique()
							+ " ";

					for (Index.IndexKeyDef def : index.getIdxKey()) {
						idxInfo += def.colId;
						if (def.descOrder) {
							idxInfo += "D ";
						} else {
							idxInfo += "A ";
						}
					}
					out.println(idxInfo);
				}
			}

			// Write the rows of data
			out.println(table.getData().size());
			for (String data : table.getData()) {
				out.println(data);
			}

			// Write RUNSTATS
			out.println("STATS TABCARD " + table.getTableCard());
			for (int i = 0; i < table.getColumns().size(); i++) {
				Column col = table.getColumns().get(i);
				if(col.getHiKey() == null)
					col.setHiKey("-");
				if(col.getLoKey() == null)
					col.setLoKey("-");
				out.println("STATS COLCARD " + i + " " + col.getColCard() + " " + col.getHiKey() + " " + col.getLoKey());
			}
			
			out.flush();
			out.close();
		}

		// Save indexes to file
		for (Index index : table.getIndexes()) {

			File indexFile = new File(TABLE_FOLDER_NAME, table.getTableName()
					+ index.getIdxName() + INDEX_FILE_EXT);

			// Delete the file if it was marked for deletion
			if (index.delete) {
				try {
					indexFile.delete();
				} catch (Exception ex) {
					out.println("Unable to delete index file for "
							+ indexFile.getName() + ".");
				}
			} else {
				PrintWriter out = new PrintWriter(indexFile);
				String idxInfo = index.getIdxName() + " " + index.getIsUnique()
						+ " ";

				// Write index definition
				for (Index.IndexKeyDef def : index.getIdxKey()) {
					idxInfo += def.colId;
					if (def.descOrder) {
						idxInfo += "D ";
					} else {
						idxInfo += "A ";
					}
				}
				out.println(idxInfo);

				// Write index keys
				out.println(index.getKeys().size());
				for (Index.IndexKeyVal key : index.getKeys()) {
					String rid = String.format("%3s", key.rid);
					out.println(rid + " '" + key.value + "'");
				}

				out.flush();
				out.close();

			}
		}
	}
private  void CreateIndex(String sql,StringTokenizer tokenizer,Boolean isunique) throws Exception{
	try{
		String tok = tokenizer.nextToken().toUpperCase();
		String indexName = tok;
		String tableName = null;
		Table table =null;
		Boolean countTable = false, countColumn = false, done = false;
		int idxposition =1;
		tok = tokenizer.nextToken().toUpperCase();
		Index index = new Index(indexName);
		if(tok.equals("ON"))
		{
			tok = tokenizer.nextToken().toUpperCase();
			tableName = tok;
			for(Table tab:tables)
			{
				if(tab.getTableName().equals(tableName) && !tab.delete)
				{
					countTable =true;
					table = tab;
					for(Index indexes:tab.getIndexes())
					{
						if(indexes.getIdxName().equalsIgnoreCase(indexName) && !indexes.delete)
						{
							throw new DbmsError("Index " + indexName + "already exist on table "+tableName+
								" '"	+ sql+".'");
						}
					}
					break;
				}
			}
			if(countTable)
			{
				tok = tokenizer.nextToken();
				if(tok.equals("("))
				{					
					while(!done)
					{	
						Index.IndexKeyDef idxDef = index.new IndexKeyDef();
						tok = tokenizer.nextToken().toUpperCase();
						for(Column col:table.getColumns())
						{
							if(col.getColName().equalsIgnoreCase(tok))
							{								
								idxDef.colId = col.getColId();
								idxDef.idxColPos = idxposition++;
								countColumn = true;
							}
						}
						if(!countColumn)
						{
							throw new DbmsError("Index cannot be created. Column does not exist. '" + sql+".'");
						}
						tok = tokenizer.nextToken().toUpperCase();
						if(tok.equalsIgnoreCase("DESC") || tok.equalsIgnoreCase("ASC"))
						{
							switch (tok){
							case "DESC" :
								idxDef.descOrder = true;
								break;
							case "ASC" :
								idxDef.descOrder = false;
								break;
							default:	
								idxDef.descOrder = false;
								break;
							}
							tok = tokenizer.nextToken().toUpperCase();
							if(tok.equals(")"))
							{
								done = true;
								index.addIdxKey(idxDef);
								break;
							}
						}else if(tok.equals(","))
						{
							//Proceed to next column in the create index statement
						}else if(tok.equals(")"))
						{
							done = true;
							index.addIdxKey(idxDef);
							break;
						}
						index.addIdxKey(idxDef);
					}
						if(tok.equals(")"))
						{
						// End of SQL
						}
						else if(tok.equals(";"))
						{
							//sql ended
						}
						else
						{
							throw new NoSuchElementException();
						}

				}
				else 
				{
					throw new NoSuchElementException();
				}				
			}
			else
			{
				throw new DbmsError("Index cannot be created. Table "+tableName+"does not exist '" + sql+".'");
			}
		}
		else{
			throw new NoSuchElementException();
		}
		tok = tokenizer.nextToken().toUpperCase();
		if(!tok.equals(";"))
		{
			throw new NoSuchElementException();
		}
		if(tokenizer.hasMoreElements())
		{
			throw new NoSuchElementException();
		}
		if(isunique)
		{
			index.setIsUnique(true);
		}
		createIndexKeyVal(index, table);
		table.addIndex(index);
		out.println("Index " + indexName
							+ " was created on table " + tableName + ".");
		}catch(NoSuchElementException ex){
		throw new DbmsError("Invalid CREATE INDEX statement. '" + sql + "'.");
	}
}
private void dropIndex(String sql, StringTokenizer tokenizer)
		throws Exception {
	try {
		// Get Index name
		String indexName = tokenizer.nextToken();

		// Check for the semicolon
		String tok = tokenizer.nextToken();
		if (!tok.equals(";")) {
			throw new NoSuchElementException();
		}

		// Check if there are more tokens
		if (tokenizer.hasMoreTokens()) {
			throw new NoSuchElementException();
		}

		// Delete the index if everything is ok
		boolean dropped = false;
		for (Table table : tables) {
			for(Index index: table.getIndexes()){
			if (index.getIdxName().equalsIgnoreCase(indexName)) {
				index.delete = true;
				dropped = true;
				table.setNumIndexes(table.getNumIndexes()-1);
				break;
			}
			}
		}

		if (dropped) {
			out.println("Index " + indexName + " was dropped.");
		} else {
			throw new DbmsError("Index " + indexName + "does not exist. '" + sql + "'."); 
		}
	} catch (NoSuchElementException ex) {
		throw new DbmsError("Invalid DROP Index statement. '" + sql + "'.");
	}

}
private void createIndexKeyVal(Index index,Table table) throws Exception
{
		String[] data = null;
		int  rid=0, colLength=0;
		String tempKey=null,tempval="",uniqueCol="";
		Column col = null;
		Map<Integer,String> keyval = new TreeMap<>();
		ArrayList<Integer> ridList = new ArrayList<>();
		for(String row : table.getData())
		{
			data = row.split("\\s+");
			tempval="";			
				for(Index.IndexKeyDef idxDef:index.getIdxKey())
				{
					col = table.getColumns().get(idxDef.colId -1);
					rid = Integer.parseInt(data[1]);
					if(data[col.getColId()+1].equals("-"))
					{
						tempKey = String.format("%1$-" + colLength +"s", "~");
					}
					else
					{
						if(!idxDef.descOrder)
						{
							
							tempKey=null;
							if(col.getColType()==Column.ColType.CHAR)
							{
								colLength = col.getColLength();
								tempKey = String.format("%1$-" + colLength +"s", data[col.getColId()+1]);														
							}
							else
							{
								tempKey = String.format("%010d",Integer.parseInt(data[col.getColId()+1])) ;
							}
						}
						else
						{
							tempKey = findCompliment(data[col.getColId()+1].toString(),col);
						}
					}
					uniqueCol = uniqueCol.concat(data[col.getColId()+1]);
					tempval = tempval.concat(tempKey);
			}
			if(index.getIsUnique())
			{
				checkUnique(uniqueCol,table,index,"CREATE");
			}
			keyval.put(rid,tempval);
		}	
		for(Entry<Integer, String> val:keyval.entrySet())
		{

				Index.IndexKeyVal key = index.new IndexKeyVal();
				key.rid = val.getKey();
				key.value = val.getValue();
				index.addKey(key);
		}
		resortIndex(index);
}
private String findCompliment(String Data,Column col1)
{
	String returnData = "", temp=null;
	char character = ' ',char1;
	char charCapStart ='A', charCapEnd = 'Z';
	char charSmallStart ='a', charSmallEnd = 'z';
	char numStart = '0', numEnd = '9';
	if(col1.getColType()==Column.ColType.CHAR)
	{
		temp= String.format("%1$-" + col1.getColLength() +"s",Data);
		for(int i=0;i < temp.length();i++)
		{
			char1 = temp.charAt(i);
			if(char1>=65 && char1<=90)
			{
				character = (char) (charCapStart - char1 + charCapEnd);
			}
			else if(char1>=97 && char1<=122) 
			{
				character = (char) (charSmallStart - char1 + charSmallEnd);
			}
			else
			{
				character = ' ';
			}
			returnData = returnData + character;
		}
	}
	else
	{
		temp = String.format("%010d",Integer.parseInt(Data));
		for(int i=0;i < temp.length();i++)
		{
			character = (char) (numEnd - temp.charAt(i) + numStart);
			returnData = returnData + character;
		}
	}
	return returnData;
}
private void checkUnique(String val,Table table,Index index,String command) throws Exception{
		Map<String,Integer> duplicateVal= new TreeMap<>();
		String tempData = "";
		if(table.getData().size() > 0)
		{
			if(command.equals("INSERT"))
			{
				ArrayList<String> data = table.getData();
				for(String rowData: data )
				{
					String[] temp = rowData.split("\\s+");
					tempData = "";
					for(Index.IndexKeyDef idxdef: index.getIdxKey())
					{
						tempData = tempData.concat(temp[idxdef.colId + 1]);
					}
					duplicateVal.put(tempData, 1);
					if(duplicateVal.containsKey(val))
					{
						throw new DbmsError("Unique Index cannot be created. Table "+table.getTableName()+" has duplicate value");
					}
				}
				
			}
			else
			{
				ArrayList<String> data = table.getData();
				for(String rowData: data )
				{
					String[] temp = rowData.split("\\s+");
					tempData = "";
					for(Index.IndexKeyDef idxdef: index.getIdxKey())
					{
						tempData = tempData.concat(temp[idxdef.colId + 1]);
					}
					if(duplicateVal.containsKey(tempData))
					{
						throw new DbmsError("Unique Index cannot be created. Table "+table.getTableName()+" has duplicate value");
					}
					else
					{
						duplicateVal.put(tempData, 1);
					}
				}
			}
		}
	}
private String runStats(StringTokenizer command) throws Exception {
	
	
	String token = command.nextToken().toUpperCase();		
	int count = 0 ;
	if(token.equals(";"))
	{
		throw new DbmsError("Invalid Runstats Statement");
	}
	String tableName = token;
	for(Table table:tables)
	{
		if(table.getTableName().equalsIgnoreCase(tableName))
		{
			count++;
			table.setTableCard(table.getData().size());	
			for(Column col:table.getColumns())
			{
				if(col.getColType() == Column.ColType.CHAR)
				{
					ArrayList<String> colData = new ArrayList<>();
					for(String data:table.getData())
					{
						String[] rowData = data.split("\\s+");
						if(!(rowData[col.getColId() + 1].equals("-"))){
						if((!colData.contains(rowData[col.getColId() + 1])))
						{

							colData.add(rowData[col.getColId() + 1]);
						}
					}
					}
					col.setColCard(colData.size());
					Collections.sort(colData,new Comparator<String>() {

						@Override
						public int compare(String s1, String s2) {
							// TODO Auto-generated method stub
							return s1.compareToIgnoreCase(s2);
						}

					});	
					String[] sortArray = new String[colData.size()];
					int i =0;
					for(String tmp:colData)
					{
						sortArray[i] = tmp ;
						i++;
					}
					i = 0 ;
					for(int j=0;j<sortArray.length;j++)
					{
						for(int k=j+1;k<sortArray.length;k++)
						{
							if (k!=j && sortArray[k].equalsIgnoreCase(sortArray[j]))
							{
								i++;
							}
						}
					}
					if(i>0){col.setColCard(sortArray.length - i);}else{col.setColCard(sortArray.length- i-1);}
					col.setHiKey(sortArray[colData.size()-1]);
					col.setLoKey(sortArray[0]);
					col.setColCard(sortArray.length);
				}
				else
				{
					ArrayList<Integer> colData = new ArrayList<>();
					for(String data:table.getData())
					{
						String[] rowData = data.split("\\s+");
						if((!(rowData[col.getColId() + 1].equals("-"))))
						{
							Integer temp = Integer.parseInt(rowData[col.getColId() + 1]);
							if((!colData.contains(temp)))
							{
								colData.add(Integer.parseInt(rowData[col.getColId() + 1]));
							}
						}

					}
					col.setColCard(colData.size());
					Collections.sort(colData);
					col.setHiKey(Integer.toString(Collections.max(colData)));
					col.setLoKey(Integer.toString(Collections.min(colData)));
				}
			}
			
		}
	}
	if(count == 0)
	{
		throw new DbmsError("Table " + tableName + " does not exist");
	}
	if(command.hasMoreElements())
	{
		if(!command.nextElement().equals(";"))
		{
			throw new DbmsError("Invalid Runstats Statement");
		}
	}
	else
	{
		throw new DbmsError("Invalid Runstats Statement");
	}
	out.println("Rustats has been performed on table " +tableName);
	return tableName;
}

private void addIndexVal(Index index,String val) {

	Index.IndexKeyVal key = index.new IndexKeyVal();
	key.rid = index.getKeys().size() + 1;
	key.value = val;
	index.addKey(key);
	resortIndex(index);
}
private void resortIndex(Index index)
{
	Collections.sort(index.getKeys(),new Comparator<Index.IndexKeyVal>() {

		@Override
		public int compare(Index.IndexKeyVal o1, Index.IndexKeyVal o2) {
			// TODO Auto-generated method stub
			return o1.value.compareToIgnoreCase(o2.value);
		}

	});
}
private void selectQuery(String sql, StringTokenizer tokenizer) throws Exception
{
	try{
		ArrayList<String> columnList = new ArrayList<String>();
		ArrayList<String> tableList = new ArrayList<String>();
		ArrayList<String> predicateList = new ArrayList<String>();
		ArrayList<String> andList = new ArrayList<String>();
		ArrayList<String> orList = new ArrayList<String>();
		ArrayList<Table> tableUsed=new ArrayList<>();
		ArrayList<Integer> colId = new ArrayList<>();
		HashMap<String,Boolean>  orderByList = new HashMap<String,Boolean>();
		HashMap<Integer,Boolean> orderBy=new HashMap<Integer,Boolean>();
		String tempTokenVal = null;
		String tempToken = null;
		StringTokenizer tempTokenizer=new StringTokenizer(sql.substring(7));
		ArrayList<Predicate> predicates = new ArrayList<Predicate>();
		boolean orderByOrder=false;
		//create the select column list
		do
		{	
			tempToken=tokenizer.nextToken();
			if(tempTokenizer.nextToken().equals(",") && !tempTokenizer.nextToken().contains("."))
			{
				throw new DbmsError("INVALID SELECT STATEMENT: ILLEGAL SYMBOL \",\" '" + sql + " .'");
			}
			else if(tempToken.equals("*"))
			{
				tempToken=tokenizer.nextToken();
			}
			else
			{
				columnList.add(tempToken);
				tempToken=tokenizer.nextToken();			
			}
		}while((!(tempToken.equalsIgnoreCase("FROM"))&& tokenizer.hasMoreElements()));
		if(!tempToken.equalsIgnoreCase("FROM")||(!tokenizer.hasMoreElements()))
		{
			throw new DbmsError("INVALID SELECT STATEMENT: TOKEN FROM INTO WAS EXPECTED. '" + sql + " .'");
		}
		//create the table list from the select query
		do
		{
				tempToken = tokenizer.nextToken();
				if(tempToken.equals(","))
				{
					tempToken=tokenizer.nextToken();
				}
				if(tempToken.equalsIgnoreCase("WHERE")||tempToken.equalsIgnoreCase("ORDER"))
				{
					break;
				}
				tableList.add(tempToken);
				//tempToken=tokenizer.nextToken();		
		}while(!(tempToken.equalsIgnoreCase("WHERE")||tempToken.equalsIgnoreCase("ORDER")) && tokenizer.hasMoreElements());
		if((!(tempToken.equalsIgnoreCase("WHERE") || tempToken.equalsIgnoreCase("ORDER"))) && tokenizer.hasMoreElements())
		{
			throw new DbmsError("INVALID SELECT STATEMENT. '" + sql + " .'");
		}
		tableUsed = checkTableExist(tableList,sql);
		colId=checkColumnList(columnList,tableUsed);
		if(tokenizer.hasMoreElements() && tempToken.equalsIgnoreCase("WHERE"))
		{	
			String predicateToken="",previous="";
			do
			{
				predicateToken="";
				ArrayList<String> predicate=new ArrayList<String>();
				int i=0;
				do{
					
					tempToken = tokenizer.nextToken();
					if(tempToken.equalsIgnoreCase("IN"))
					{	predicateToken="";
						predicate.add(tempToken);
						tempToken = tokenizer.nextToken();
						if(!(tempToken.equals("(")))
						{
							throw new DbmsError("INVALID SELECT STATEMENT: IN Missing ("+ sql);
						}
						while(!tempToken.equals(")"))
						{
							
							predicateToken=predicateToken+tempToken;
							tempToken = tokenizer.nextToken();
						}
						predicateToken=predicateToken+tempToken;
						tempToken=predicateToken;
						//predicate.add(predicateToken);
					}
					if(tempToken.equals(","))
					{
						throw new DbmsError("INVALID SELECT STATEMENT: ILLEGAL SYMBOL \",\" '" + sql + " .'");
					}

					if(tempToken.equalsIgnoreCase("AND"))
					{
						andList.add(predicateToken);
						previous="AND";
						break;
					}
					if(tempToken.equalsIgnoreCase("OR"))
					{
						if(previous.equalsIgnoreCase("AND"))
						{
							andList.add(predicateToken);
							previous="OR";
						}
						else
						{
							orList.add(predicateToken);
							previous="OR";
						}
						break;
					}
					predicate.add(tempToken.toUpperCase());	
					predicateToken=predicateToken+tempToken.toUpperCase();
					if(!tokenizer.hasMoreElements())
					{
						break;
					}
				}while(!(tempToken.equalsIgnoreCase("AND")||tempToken.equalsIgnoreCase("OR")));
				if(predicate.size()!=3 || !(predicate.contains("=")||predicate.contains(">")||predicate.contains("<")||predicate.contains("IN")))
				{
					throw new DbmsError("INVALID SELECT STATEMENT. INVALID PREDICATE USAGE'" + sql + " .'");
				}

				predicateToken="";
				while(i<predicate.size() && (predicate.contains("=")||predicate.contains(">")||predicate.contains("<")||predicate.contains("IN")))
				{
					predicateToken=predicateToken.concat(predicate.get(i));
					i++;
				}
				predicateList.add(predicateToken);
			}while(tokenizer.hasMoreElements()&& (!(tempToken.equalsIgnoreCase("ORDER"))));
			if(previous.equalsIgnoreCase("AND"))
			{
				andList.add(predicateToken);
			}
			else if(previous.equalsIgnoreCase("OR"))
			{
				orList.add(predicateToken);
			}
/*			do
			{
				tempToken = tokenizer.nextToken();
				if(tempToken.equals(";"))
				{
					throw new DbmsError("INVALID SELECT STATEMENT. WHERE MISSING PREDICATES '" + sql + " .'");
				}
				if(tempToken.equalsIgnoreCase("AND")||tempToken.equalsIgnoreCase("OR"))
				{	
					//move to next predicate
				}
				else if(tempToken.equals(","))
				{
					throw new DbmsError("INVALID SELECT STATEMENT: ILLEGAL SYMBOL \",\" '" + sql + " .'");
				}
				else
				{
					predicateList.add(tempToken);
					tempToken = tokenizer.nextToken();
				}
			}while((tokenizer.hasMoreElements() && (!tempToken.equalsIgnoreCase("ORDER"))||!tempToken.equals(";")));*/
			if(tokenizer.hasMoreElements())
			{
				if(!tempToken.equalsIgnoreCase("ORDER"))
				{
					throw new DbmsError("INVALID SELECT STATEMENT. '" + sql + " .'");
				}
			}
			predicates = checkPredicateSemantics(predicateList,tableList,andList,orList,"");
		}
		if(tokenizer.hasMoreElements() && tempToken.equalsIgnoreCase("ORDER"))
		{
			tempToken=tokenizer.nextToken();
			if(tempToken.equalsIgnoreCase("BY"))
			{
				tempTokenizer = new StringTokenizer(sql.substring(sql.toUpperCase().indexOf("BY")));
				String temp=tempTokenizer.nextToken();
				temp=tempTokenizer.nextToken();
				do
				{
					//tempTokenizer = tokenizer;
					//System.out.println(sql.toUpperCase().indexOf("BY"));
					
					tempToken=tokenizer.nextToken();
					if(tempTokenizer.hasMoreElements())
					{
						temp=tempTokenizer.nextToken();
					}
					
					if(tempToken.equalsIgnoreCase("D"))
					{
						orderByOrder = true;
						orderByList.put(tempTokenVal,orderByOrder);
					}
					else if(tempToken.equals(",") && (!tokenizer.hasMoreElements()))
					{
						throw new DbmsError("INVALID SELECT STATEMENT: ILLEGAL SYMBOL \",\" '" + sql + " .'");
					}
					else if(tempToken.equals(",") && (tokenizer.hasMoreElements()))
					{
						//move to next
					}
					else
					{
						orderByList.put(tempToken,orderByOrder);
						tempTokenVal=tempToken;
					}
					orderByOrder = false;
				}while(tokenizer.hasMoreElements());
			}
			else
			{
				throw new DbmsError("INVALID SELECT STATEMENT. ORDER MISSING BY '" + sql + " .'");
			}
			orderBy = OrderByListValid(orderByList,tableUsed);
		}
		printIndexList(tableUsed);
		if(tableUsed.size()==1)
		{
			predicateAnalysisSingleTable(predicates,tableUsed,andList,orList,orderBy,colId);
		}
		else if(tableUsed.size()==2)
		{
			predicateAnalysistwoTable(predicates,tableUsed,andList,orList,orderBy,colId);
			
		}
		
		
}catch(NoSuchElementException ex)
{
	throw new DbmsError("INVALID SELECT STATEMENT. '" + sql + " .'");
}
	
}
private ArrayList<Table> checkTableExist(ArrayList<String> tableName,String sql) throws Exception{
	ArrayList<Table> tableList = new ArrayList<>();
	int count=0;
	for(String selectTable:tableName)
	{
		for(Table table:tables)
		{
			if(table.getTableName().equalsIgnoreCase(selectTable))
			{
				tableList.add(table);
				count++;
			}
		}
	}
	if(count==tableName.size())
	{
		return tableList;
	}
	else 
	{
		throw new DbmsError("TABLE IN THE SELECT STATEMENT DOES NOT EXISTS '" + sql + " .'");
	}
}
private ArrayList<Integer> checkColumnList(ArrayList<String> columnList,ArrayList<Table> tableList) throws Exception {
	int columnCount=0,tableCount;
	String tableName=null,columnName=null;
	ArrayList<Integer> columnId=new ArrayList<>();
 	for(String selectColumn:columnList)
	{
 		tableCount=0;
		String[] temp = selectColumn.trim().split("\\.");
		tableName=temp[0];
		columnName=temp[1];
		for(Table table:tableList)
		{
			if(table.getTableName().equalsIgnoreCase(tableName))
			{
				tableCount++;
				columnCount=0;
				for(Column column:table.getColumns())
				{
					if(column.getColName().equalsIgnoreCase(columnName))
					{
						columnCount++;
						columnId.add(column.getColId());
					}
					if(columnCount>0)
					{
						break;
					}
				}
			}
			if(tableCount>0)
			{
				break;
			}
		}
		if(tableCount==0)
		{
			throw new DbmsError("COLUMN NAME "+columnName+" IS NOT VALID IN THE CONTEXT WHERE IT IS USED."); 
		}
		if(tableCount!=0 &&columnCount==0)
		{
			throw new DbmsError("COLUMN "+ columnName + "IS NOT PRESENT IN THE TABLE "+ tableName + "."); 
		}
	}
 	return columnId;
}
private void printIndexList(ArrayList<Table> tableList) {
	IndexList indexList = new IndexList();
		for(Table table:tableList)
		{
			for(Index index:table.getIndexes())
			{
				indexList.list.add(index);
			}
		}
	indexList.printTable(out);
}
private HashMap<Integer,Boolean> OrderByListValid(HashMap<String,Boolean> orderByList,ArrayList<Table> tableList) throws Exception {
	String tableName=null,columnName=null;
	HashMap<Integer,Boolean> order=new HashMap<Integer,Boolean>();
	int columnCount=0,tableCount;int count=0;
	for(Entry<String, Boolean> orderBy:orderByList.entrySet())
	{
		tableCount=0;
		String[] temp = orderBy.getKey().trim().split("\\.");
		tableName=temp[0];
		columnName=temp[1];
		
		for(Table table:tableList)
		{
			if(table.getTableName().equalsIgnoreCase(tableName))
			{
				count++;
			}
		}
		if(count!=tableList.size())
		{
			throw new DbmsError("TABLE " +tableName+" USED IN ORDER BY IS NOT PRESENT IN FROM");
		}
		for(Table table:tableList)
		{
			if(table.getTableName().equalsIgnoreCase(tableName))
			{
				tableCount++;
				columnCount=0;
				for(Column column:table.getColumns())
				{
					if(column.getColName().equalsIgnoreCase(columnName))
					{
						columnCount++;
						order.put(column.getColId(), orderBy.getValue());
					}
					if(columnCount>0)
					{
						break;
					}
				}
				if(tableCount>0)
				{
					break;
				}
			}
		}
		if(tableCount==0)
		{
			throw new DbmsError("TABLE " +tableName+" USED IN ORDER BY IS NOT PRESENT IN FROM"); 
		}
		if(tableCount!=0 &&columnCount==0)
		{
			throw new DbmsError("COLUMN "+columnName+" IN ORDER BY IS NOT PRESENT IN TABLE "+tableName); 
		}
	}
	return order;
}
private ArrayList<Predicate> checkPredicateSemantics(ArrayList<String> predicateList,ArrayList<String> tableList, 
		ArrayList<String> andList,ArrayList<String> orList,String description) throws Exception
{
	ArrayList<Predicate> predicates = new ArrayList<Predicate>();
	String colType1 = null,colType2=null;
	String lhs=null,rhs=null;
	String pct="";
	String actualPredicateText="";
	boolean predicateChanged = false,predicateAdded = false;
	int charColumnLength=0;
	ArrayList<String> predicateLhsRhs=new ArrayList<>();
	double filterFactor;
	Boolean col1=false,col2= false;
	String value="",desc=description;
	if(predicateList.size()>8)
	{
		throw new DbmsError("The optimizer can currently handle only 8 predicates");
	}
	for(String predicate:predicateList)
	{
		if(!(predicate.contains("=")||predicate.contains(">")||predicate.contains("<")||predicate.contains("IN")))
		{
			throw new DbmsError("INVALID OPERATOR UASAGE IN PREDICATE " +predicate);
		}
	}
	if((orList.size()==(predicateList.size()))&&orList.size()>0)
	{
		boolean orListAdded=false;
		HashMap<String,Integer> tempList = new HashMap<String,Integer>();
		ArrayList<String> templist2 = new ArrayList<String>();
		String lhsOR =""; int order = 1,i=1;
		tempList.put(predicateList.get(0).split("=")[0],order);
		for(String predicate:predicateList)
		{
			if(predicate.contains("=")&&tempList.containsKey(predicate.split("=")[0]))
			{
				lhsOR = predicate.split("=")[0];	
				tempList.put(lhsOR,order);
				value=value.concat(predicate.trim().split("=")[1].concat(","));
				actualPredicateText=actualPredicateText.concat(predicate.concat(" OR "));
				orListAdded=true;
			}
			else if(!tempList.containsKey(predicate.split("=")[0]))
			{
				templist2.add(predicate);
				order++;
			}
		}
		if(orListAdded&&tempList.size()==1)
		{
			
			if(order>1)
			{
				predicateList.clear();
				while(i<=order)
				{
					if(tempList.entrySet().iterator().next().getValue()==i)
					{
						value=value.substring(0,value.lastIndexOf(','));
						actualPredicateText=actualPredicateText.substring(0,actualPredicateText.lastIndexOf('O'));
						String concat = tempList.entrySet().iterator().next().getKey().concat(" IN ".concat(" ( ".concat(value.concat(" )"))));					
						predicateChanged = true;
						predicateList.add(concat);
						desc=concat;
					}
					else if(templist2.size()>0)
					{
						predicateList.addAll(templist2);
					}
					i++;
				}
			}
			else
			{
				value=value.substring(0,value.lastIndexOf(','));
				actualPredicateText=actualPredicateText.substring(0,actualPredicateText.lastIndexOf('O'));
				String concat = predicateList.get(0).split("=")[0].concat(" IN ".concat(" ( ".concat(value.concat(" )"))));					
				predicateChanged = true;
				predicateList.clear();
				predicateList.add(concat);
				desc=concat;
			}
		}
	}
	boolean pctflag=false;
	for(String predicate:predicateList)
	{
		if(!predicateChanged)
		{
			desc=description;
			actualPredicateText="";
			predicateChanged = false;
		}
		ArrayList<Integer> columnId = new ArrayList<Integer>();
		if(predicateLhsRhs.size()==2)
		{
			if(predicate.trim().split("=")[0].equalsIgnoreCase(predicateLhsRhs.get(0))||predicate.trim().split("=")[0].equalsIgnoreCase(predicateLhsRhs.get(1)))
			{
				if(predicate.trim().split("=")[0].equalsIgnoreCase(predicateLhsRhs.get(0)))
				{
					pct=predicateLhsRhs.get(1)+"="+predicate.trim().split("=")[1];
					pctflag=true;
				}
				else
				{
					pct=predicateLhsRhs.get(0)+"="+predicate.trim().split("=")[1];
					pctflag=true;
				}			
			}
			else if(predicate.trim().split(">")[0].equalsIgnoreCase(predicateLhsRhs.get(0))||predicate.trim().split(">")[0].equalsIgnoreCase(predicateLhsRhs.get(1)))
			{
				if(predicate.trim().split("=")[0]==predicateLhsRhs.get(0))
				{
					pct=predicateLhsRhs.get(1)+">"+predicate.trim().split(">")[1];
					pctflag=true;
				}
				else
				{
					pct=predicateLhsRhs.get(0)+">"+predicate.trim().split(">")[1];
					pctflag=true;
				}
			}
			else if(predicate.trim().split("<")[0].equalsIgnoreCase(predicateLhsRhs.get(0))||predicate.trim().split("<")[0].equalsIgnoreCase(predicateLhsRhs.get(1)))
			{
				if(predicate.trim().split("=")[0]==predicateLhsRhs.get(0))
				{
					pct=predicateLhsRhs.get(1)+"<"+predicate.trim().split("<")[1];
					pctflag=true;
				}
				else
				{
					pct=predicateLhsRhs.get(0)+"<"+predicate.trim().split("<")[1];
					pctflag=true;
				}
			}
			if(pctflag)
			{
				actualPredicateText = pct;
				predicateAdded = true;
				//desc="TCP";
			}
		}
		if(predicate.contains("IN"))
		{
			if(predicate.trim().split("IN")[1].length()<4 && !predicateChanged)
			{
				predicateChanged=true;
				//predicateList.remove(predicate);
				actualPredicateText=predicate;
				String temp=predicate.trim().split("IN")[0]+"="+
				predicate.trim().split("IN")[1].substring(predicate.trim().split("IN")[1].indexOf('(')+1,predicate.trim().split("IN")[1].lastIndexOf(')'));
				//predicateList.add(temp);
				predicate=temp;
				desc=temp;
			}
		}
		Predicate predicateObj = new Predicate();
		String[] tempPredicate=null;
		if(predicate.contains("="))
		{
			tempPredicate = predicate.trim().split("=");
			predicateObj.setType('E');
		}
		else if(predicate.contains(">"))
		{
			tempPredicate = predicate.trim().split(">");
			predicateObj.setType('R');
		}
		else if(predicate.contains("<"))
		{
			tempPredicate = predicate.trim().split("<");
			predicateObj.setType('R');
		}
		else if(predicate.contains("IN"))
		{
			tempPredicate = predicate.trim().split("IN");
			predicateObj.setType('I');
		}
		else
		{
			throw new DbmsError("INVALID OPERATOR UASAGE IN PREDICATE " +predicate);
		}
		lhs=tempPredicate[0];
		rhs=tempPredicate[1];
		if(!lhs.contains("."))
		{
			if(!checkliteral(lhs))
			{
				throw new DbmsError("LHS OF PREDICATE MISSING TABLE NAME");
			}			
		}
		String[] tempTable1 = lhs.trim().split("\\.");
		if(lhs.contains("."))
		{
			predicateObj.setTable1(tempTable1[0]);
		}
		
		if(!tableList.contains(tempTable1[0].toUpperCase()))
		{
			if(!checkliteral(lhs))
			{
				throw new DbmsError("TABLE USED IN WHERE CLAUSE PREDICATE LHS " + predicate +  " IS NOT PRESENT IN FROM");
			}
		}
		String[] tempTable2=null;
		if(rhs.contains("."))
		{
			
			tempTable2 = rhs.trim().split("\\.");
			predicateObj.setTable2(tempTable2[0]);
			if(!tableList.contains(tempTable2[0].toUpperCase()))
			{
				throw new DbmsError("TABLE USED IN WHERE CLAUSE PREDICATE RHS " + predicate +  " IS NOT PRESENT IN FROM");
			}
			if(tempTable1[0].equalsIgnoreCase(tempTable2[0]))
			{
				throw new DbmsError("Join predicae cannot be on same table");
			}
		}
		int colCard1=0,colCard2=0;
		String colHikey="",colLowKey="";
		for(Table table:tables)
		{		
			if(table.getTableName().equalsIgnoreCase(tempTable1[0]))
			{
				for(Column column:table.getColumns())
				{
					if(column.getColName().equalsIgnoreCase(tempTable1[1]))
					{
						col1=true;
						predicateObj.setColid1(column.getColId());
						//remove this
						columnId.add(column.getColId());
						if(column.getColType() == Column.ColType.CHAR)
						{
							colType1="CHAR";
							charColumnLength=column.getColLength();
						}
						else
						{
							colType1="INT";
						}
						colCard1=column.getColCard();
						predicateObj.setCard1(colCard1);
						predicateObj.setFf1((double)1/colCard1);
						colHikey=column.getHiKey();
						colLowKey=column.getLoKey();
						if(predicateObj.getType()=='E' && !(predicate.split("=")[1].contains(".")))
						{
							if(colType1.equalsIgnoreCase("INT")&& Integer.parseInt(rhs)<Integer.parseInt(colLowKey))
							{
								predicateObj.setFf1(0);
							}
							else if(colType1.equalsIgnoreCase("CHAR") && (rhs.compareTo(colLowKey)<0||rhs.compareTo(colHikey)>0))
							{
								predicateObj.setFf1(0);
							}
							else
							{
								predicateObj.setFf1((double)1/colCard1);
							}
						}
						break;
					}				
				}		
				if(!col1)
				{
					throw new DbmsError("PREDICATE COLUMN "+ tempTable1[1] +" IN WHERE IS NOT PRESENT IN TABLE " +tempTable1[0]+".");
				}
			}
			}
	if(rhs.contains("."))
	{
		predicateObj.setJoin(true);
		predicateLhsRhs.add(lhs);
		predicateLhsRhs.add(rhs);	
		for(Table table:tables)
		{		
			if(table.getTableName().equalsIgnoreCase(tempTable2[0]))
			{
				for(Column column:table.getColumns())
				{
					if(column.getColName().equalsIgnoreCase(tempTable2[1]))
					{
						col2=true;
						predicateObj.setColid2(column.getColId());
						//remove this
						columnId.add(column.getColId());
						if(column.getColType() == Column.ColType.CHAR)
						{
							colType2="CHAR";
						}
						else
						{
							colType2="INT";
						}
						colCard2=column.getColCard();
						predicateObj.setCard2(colCard2);
						predicateObj.setFf2((double)1/colCard2);
						break;
					}
				}
				if(!col2)
				{
					throw new DbmsError("PREDICATE COLUMN "+ tempTable2[1] +" IN WHERE IS NOT PRESENT IN TABLE " +tempTable2[0]+".");
				}
			}
		}
		if(!colType1.equals(colType2))
		{
			throw new DbmsError("COLUMN IN WHERE HAS MISMATCH DATATYPE.");
		}
		
	}
	else
	{
		if(predicate.contains("=") && lhs.contains("."))
		{
			if(colType1.equals("CHAR"))
			{
				if(rhs.length() > charColumnLength)
				{
					throw new DbmsError("PREDICATE LHS "+predicate+" IS OUT OF BOUND");
				}
				
			}
			else
			{
				if(Integer.parseInt(rhs) < -2147483648 || Integer.parseInt(rhs) > 2147483647)
				{
					throw new DbmsError("PREDICATE LHS "+predicate+" IS OUT OF BOUND");
				}
			}
		} 
		else if(predicate.contains("IN"))
		{					
			if(!(rhs.contains("(")&&rhs.contains(")")))
			{
				throw new DbmsError("SYNTAX ERROR: IN MISSING BRACKET ) or("+predicate); 
			}
			String inValue = predicate.substring(predicate.indexOf("(")+1, predicate.indexOf(")"));
			if(colType1.equals("CHAR"))
			{
				String[] val = inValue.trim().split(",");
				for(String temp:val)
				{
					if(temp.length()>charColumnLength)
					{
						throw new DbmsError("PREDICATE LHS "+predicate+" IS OUT OF BOUND");
					}
				}
				predicateObj.setFf2((double)val.length/colCard1);
			}
			else
			{
				String[] val = inValue.trim().split(",");
				for(String temp:val)
				{
					if(Integer.parseInt(temp) < -2147483648 || Integer.parseInt(temp) > 2147483647)
					{
						throw new DbmsError("PREDICATE LHS "+predicate+" IS OUT OF BOUND");
					}
				}
				predicateObj.setFf1((double)val.length/colCard1);
			}
		}
		else if(predicate.contains(">") && lhs.contains("."))
		{
			if(colType1.equals("CHAR"))
			{
				if(rhs.length() > charColumnLength)
				{
					throw new DbmsError("PREDICATE LHS "+predicate+" IS OUT OF BOUND");
				}
				
			}
			else
			{
				if(Integer.parseInt(rhs) < -2147483648 || Integer.parseInt(rhs) > 2147483647)
				{
					throw new DbmsError("PREDICATE LHS "+predicate+" IS OUT OF BOUND");
				}
			}
			filterFactor=charCoversion(colHikey,colLowKey,rhs,colType1,'>');
			predicateObj.setFf1(filterFactor);
		}
		else if(predicate.contains("<") && lhs.contains("."))
		{
			if(colType1.equals("CHAR"))
			{
				if(rhs.length() > charColumnLength)
				{
					throw new DbmsError("PREDICATE LHS "+predicate+" IS OUT OF BOUND");
				}
			}
			else
			{
				if(Integer.parseInt(rhs) < -2147483648 || Integer.parseInt(rhs) > 2147483647)
				{
					throw new DbmsError("PREDICATE LHS "+predicate+" IS OUT OF BOUND");
				}
			}
			filterFactor=charCoversion(colHikey,colLowKey,rhs,colType1,'<');
			predicateObj.setFf1(filterFactor);
		}
	}
	predicateObj.setDescription(desc);
	if(predicateChanged)
	{
		predicateObj.setText(actualPredicateText);
		predicateObj.setDescription(desc);
		predicateChanged=false;
	}
	else
	{
		predicateObj.setText(predicate);
	}

	predicates.add(predicateObj);
	}

	predicateFinal.addAll(predicates);
	if(predicateAdded)
	{
		
		predicateList.clear();
		predicateList.add(pct);
		checkPredicateSemantics(predicateList, tableList, andList, orList,"TCP");
	}
return predicateFinal;
}
private void predicateAnalysisSingleTable(ArrayList<Predicate> predicates,ArrayList<Table> tableList,ArrayList<String> andList,
		ArrayList<String> orList,HashMap<Integer, Boolean> orderByList,ArrayList<Integer> columnList)
{
	HashMap<Index,HashMap<Character,ArrayList<Predicate>>> indexes = new HashMap<Index,HashMap<Character,ArrayList<Predicate>>>();	
	//Single Table Access
	int matchCols =0; boolean literalCheck = false;
	HashMap<Predicate, Double> predicateToFF = new HashMap<Predicate, Double>(); 
	int i=1;
	boolean indexOnly = false,orderBy=false; String IndexName1 = "",IndexName2 = "";
	PlanTable planTable = new PlanTable();
	ArrayList<Predicate> predicateList=new ArrayList<>();
	//If no index on the table
	planTable.setTable1Card(tableList.get(0).getTableCard());
	sequenceForLiteral(predicates);
	for(Predicate predicate:predicates)
	{
		if(predicate.getTable1().length()==0&&predicate.getFf1()==0)
		{
			literalCheck=true;
		}
	}
	if(!literalCheck)
	{
		if(tableList.get(0).getIndexes().size()==0)
		{
			planTable.setAccessType('R');
			planTable.setPrefetch('S');
			planTable.setIndexOnly(' ');
			
			if(orderByList.isEmpty())
			{
				planTable.setSortC_orderBy('N');
			}
			else
			{
				planTable.setSortC_orderBy('Y');
			}
			if(!predicates.isEmpty())
			{
				for(Predicate predicate:predicates)
				{
					if(predicate.getTable1().length()!=0)
					{
						predicateToFF.put(predicate, predicate.getFf1());
					}
					predicateList.add(predicate);
				}
				entriesSortedByValues(predicateToFF);
				i=predicateToFF.size();
				for(Entry<Predicate, Double> entry:predicateToFF.entrySet())
				{
					Predicate key = entry.getKey();
					key.setSequence(i);
					i--;
				}
			}
		}
		else
		{
			if(predicates.isEmpty())
			{
				IndexName1=indexOnly(tableList, columnList);
				IndexName2=sortAvoidance(tableList, orderByList);
				if(IndexName1.length()>1)
				{
					indexOnly=true;
				}
				if(IndexName2.length()>1)
				{
					orderBy=true;
				}	
				if(IndexName1.length()<1 && IndexName2.length()<1)
				{
					planTable.setAccessType('R');
					planTable.setPrefetch('S');
					planTable.setTable1Card(tableList.get(0).getTableCard());
					if(orderByList.size()>0)
					{
						planTable.setSortC_orderBy('Y');
					}
				}
				if(orderBy)
				{
					planTable.setSortC_orderBy('N');
					planTable.setAccessType('I');
					planTable.setAccessName(IndexName2);
					matchCols=orderByList.size();
					if(indexOnly)
					{
						if(IndexName1.equalsIgnoreCase(IndexName2))
						{
							planTable.setIndexOnly('Y');
						}
					}
					else
					{
							planTable.setIndexOnly('N');
					}
				}
				else
				{
					if(indexOnly)
					{
						planTable.setIndexOnly('Y');
						planTable.setAccessType('I');
						planTable.setAccessName(IndexName1);
					}
				}				
			}
			else
			{
				boolean inList = false;
				for(Predicate predicateObj:predicates)
				{
					if(predicateObj.getType()=='I')
					{
						inList=true;
					}
				}
				
				for(Index index:tableList.get(0).getIndexes())
				{
					char phase ='S', type=' ';
					ArrayList<Predicate> screening = new ArrayList<Predicate>();
					ArrayList<Predicate> matching = new ArrayList<Predicate>();
					for(Index.IndexKeyDef def:index.getIdxKey())
					{
						for(Predicate predicateObj:predicates)
						{				
							if(orList.isEmpty()||inList)
							{						
								if(def.colId==predicateObj.getColid1() && def.idxColPos==1)
								{
									phase = 'M';
									type=	predicateObj.getType();	
									for(Predicate p:matching)
									{
										if(!p.getTable1().equalsIgnoreCase(predicateObj.getTable1()))
										{
											matching.add(predicateObj);
										}
									}
									if(matching.size()<1)
									{
										matching.add(predicateObj);
									}
								}
								else if(def.colId==predicateObj.getColid1() &&phase=='M'&&type=='E')
								{
									phase = 'M';
									type=	predicateObj.getType();
									matching.add(predicateObj);
				
									//predicateObj.setSequence(sequence);
								}
								else if(def.colId==predicateObj.getColid1()&&phase=='M'&&type=='R')
								{
									phase = 'S';
									type=	predicateObj.getType();
									for(Predicate p:screening)
									{
										if(!p.getTable1().equalsIgnoreCase(predicateObj.getTable1()))
										{
											screening.add(predicateObj);
										}
									}
									if(screening.size()<1)
									{
										screening.add(predicateObj);
									}
									//predicateObj.getKey().setSequence(sequence);
									
								}
								else if(def.colId==predicateObj.getColid1() && phase=='S')
								{
									phase = 'S';
									type=	predicateObj.getType();

										screening.add(predicateObj);
									//predicateObj.getKey().setSequence(sequence);
								}
							}
							else
							{
								break;
							}
						}
					}				
					HashMap<Character,ArrayList<Predicate>> tempList = new HashMap<Character,ArrayList<Predicate>>();
					if(matching.size()>0)
					{
						tempList.put('M', matching);
					}
					if(screening.size()>0)
					{
						tempList.put('S', screening);
					}
					if(screening.size()>0||matching.size()>0)
					{
						indexes.put(index, tempList);
					}
				}
				String indexName=""; double ff1=1,ff2=0;
				
				if(!indexes.isEmpty())
				{
					int countMatching = 0,countScreening=0,countMatching1 = 0,countScreening1=0,totalMatching=0,totalMatching1=0;
					for(Entry<Index, HashMap<Character, ArrayList<Predicate>>> list:indexes.entrySet())
					{
						
						for(Entry<Character, ArrayList<Predicate>> findMatching:list.getValue().entrySet())
						{
							if(findMatching.getKey()=='M')
							{
								countMatching=findMatching.getValue().size();
							}
							else if(findMatching.getKey()=='S')
							{
								countScreening=findMatching.getValue().size();  
								
							}
						}
						totalMatching=countMatching+countScreening;
						if(countMatching>0&&countMatching>countMatching1&&totalMatching>totalMatching1)
						{
							indexName=list.getKey().getIdxName();
							countMatching1=countMatching;
							matchCols=countMatching;
							totalMatching1=totalMatching;						
						}
						else if(countMatching==0&&countScreening>0&&countScreening>countScreening1&&totalMatching>totalMatching1)
						{
							indexName=list.getKey().getIdxName();
							countScreening1=countScreening;
							totalMatching1=totalMatching;
						}	
						else if(countMatching>0&&countMatching==countMatching1&&totalMatching==totalMatching1)
						{
							matchCols=countMatching;
							totalMatching1=totalMatching;
							if(countScreening>countScreening1)
							{
								indexName=list.getKey().getIdxName();
								countScreening1=countScreening;
							}
							else
							{
								for(Entry<Index, HashMap<Character, ArrayList<Predicate>>> list1
										:indexes.entrySet())
								{
									ff2=0;ff1=1;
									for(Entry<Character, ArrayList<Predicate>> findMatching:list1.getValue().entrySet())
									{
										for(Predicate p:findMatching.getValue())
										{
											ff1=ff1*p.getFf1();
										}
										if(ff1>ff2)
										{
											indexName=list.getKey().getIdxName();
											ff2=ff1;
										}
									}
								}
							}
						}
						else if(countMatching==0)
						{
							if(countScreening>1&&countScreening>countScreening1)
							{
								indexName=list.getKey().getIdxName();
								countScreening1=countScreening;
							}	
						}
					}
					if(indexName.equals(""))
					{
						planTable.setAccessType('R');
						planTable.setPrefetch('S');
					}
					else
					{
						planTable.setAccessName(indexName);
						planTable.setMatchCols(matchCols);
						if(inList)
						{
							planTable.setAccessType('N');
						}
						else
						{
							planTable.setAccessType('I');
						}
						IndexName1=indexOnly(tableList, columnList);
						IndexName2=sortAvoidance(tableList, orderByList);
						if(IndexName2.equalsIgnoreCase(indexName))
						{
							planTable.setSortC_orderBy('N');
							if(IndexName1.equalsIgnoreCase(indexName))
							{
								planTable.setIndexOnly('Y');
							}
						}
						else if(IndexName1.equalsIgnoreCase(indexName))
						{
							planTable.setIndexOnly('Y');
						}
						for(Index index:tableList.get(0).getIndexes())
						{
							if(index.getIdxName().equalsIgnoreCase(indexName))
							{
								setSequences(predicates,index,orList);
								
							}
						}
					}

				}
				else{
					planTable.setAccessType('R');
					planTable.setPrefetch('S');
					HashMap<Predicate,String> predicatesequence = new HashMap<Predicate,String>();
					ArrayList<Character> condition = new ArrayList<Character>();
					double ff=0; char type =' ';
					for(Predicate predicate:predicates)
					{
						if(predicate.getTable1().length()!=0)
						{
							ff=predicate.getFf1();
							predicateToFF.put(predicate,ff);
						}				
						if(predicate.getText().contains("=")&&predicate.getTable1().length()!=0)
						{
							predicatesequence.put(predicate, (predicate.getText().split("=")[1]));
							condition.add(predicate.getText().charAt(predicate.getText().indexOf("=")));
						}
						else if(predicate.getText().contains(">")&&predicate.getTable1().length()!=0)
						{
							predicatesequence.put(predicate, (predicate.getText().split(">")[1]));
							condition.add(predicate.getText().charAt(predicate.getText().indexOf(">")));
							type='>';
						}
						else if(predicate.getText().contains("<")&&predicate.getTable1().length()!=0)
						{
							predicatesequence.put(predicate, (predicate.getText().split("<")[1]));
							condition.add(predicate.getText().charAt(predicate.getText().indexOf("<")));
							type='<';
						}
						//predicateList.add(predicate.getKey());
					}
					//entriesSortedByValues(predicateToFF);
					Set<Character> uniqueVCond = new HashSet<Character>(condition);
					if(!orList.isEmpty())
					{
						String val=""; int sequence=1;
						if(!andList.isEmpty()&&predicateToFF.containsValue(0.0))
						{
							for(Entry<Predicate, Double> entry:predicateToFF.entrySet())
							{
								Predicate key = entry.getKey();
									key.setSequence(0);
							}
						}
						else if(!andList.isEmpty())
						{
							for(Entry<Predicate, Double> entry:predicateToFF.entrySet())
							{
								Predicate key = entry.getKey();
								if(key.getTable1().length()!=0)
								{
									for(String or:orList)
									{
										if(!key.getText().equalsIgnoreCase(or))
										{
											key.setSequence(i);
											i++;
										}
									}										
								}
							}
							for(Entry<Predicate, Double> entry:predicateToFF.entrySet())
							{
								Predicate key = entry.getKey();
								if(key.getTable1().length()!=0)
								{
									for(String or:orList)
									{
										if(key.getText().equalsIgnoreCase(or))
										{
											key.setSequence(i);
											i++;
										}
									}										
								}
							}
						}
						else if(uniqueVCond.size()==1)
						{
							for(Entry<Predicate, String> entry:predicatesequence.entrySet())
							{
								if(type=='>' )
								{
									if(entry.getValue().compareToIgnoreCase(val) > 0 && (entry.getValue().charAt(0)>=65 || entry.getValue().charAt(0)<=90))
									{
										val = entry.getValue();
										
									}
									else if(entry.getValue().compareToIgnoreCase(val) > 0 && (entry.getValue().charAt(0)>=48 || entry.getValue().charAt(0)<=57))
									{
										val = entry.getValue();
									}
								}
								else if(type=='<')
								{
									if(entry.getValue().compareToIgnoreCase(val) > 0 && (entry.getValue().charAt(0)>=65 || entry.getValue().charAt(0)<=90))
									{
										val = entry.getValue();
										
									}
									else if(entry.getValue().compareToIgnoreCase(val) > 0 && (entry.getValue().charAt(0)>=48 || entry.getValue().charAt(0)<=57))
									{
										val = entry.getValue();
									}
								}
							}
							for(Entry<Predicate, String> entry:predicatesequence.entrySet())
							{
								if(type=='>' )
								{
									if(entry.getValue().compareToIgnoreCase(val)<0)
									{
										val = entry.getValue();
										entry.getKey().setSequence(sequence);
									}
									else
									{
										entry.getKey().setSequence(0);
									}
								}
								else if(type=='<')
								{
									if(entry.getValue().compareToIgnoreCase(val)>=0)
									{
										val = entry.getValue();
										entry.getKey().setSequence(sequence);
									}
									else
									{
										entry.getKey().setSequence(0);
									}
									
								}
							}
						}
						else
						{
							entriesSortedByValues(predicateToFF);
							i=predicateToFF.size();
							for(Entry<Predicate, Double> entry:predicateToFF.entrySet())
							{
								Predicate key = entry.getKey();
								if(key.getTable1().length()!=0)
								{
									key.setSequence(i);
									i--;									
								}
							}
						}
					}
					else
					{
						entriesSortedByValues(predicateToFF);
						i=1;
						if(predicateToFF.containsValue(0.0))
						{
							for(Entry<Predicate, Double> entry:predicateToFF.entrySet())
							{
								Predicate key = entry.getKey();
									key.setSequence(0);
							}
						}
						else
						{
							for(Entry<Predicate, Double> entry:predicateToFF.entrySet())
							{
								Predicate key = entry.getKey();
								if(key.getTable1().length()!=0)
								{
									key.setSequence(i);
									i++;
								}
							}
						}
					}
				}
			
			}
		}
	}
	else
	{
		planTable.setAccessType('R');
		planTable.setPrefetch('S');
	}
	planTable.printTable(out);
	Predicate p = new Predicate();
	p.printTable(out, predicates);
}
private double charCoversion(String hiKey,String lowKey,String literal,String colType,char condition)
{
	double ff =0;
	char[] alphabetCap ={'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z'};
	char[] alphabetSmall ={'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z'};
	int val=0;
	int conversionhi=0,conversionlo=0,conversionlit=0;
	if(colType.equalsIgnoreCase("CHAR"))
	{
		if(hiKey.length()<2)
		{
			if(condition=='>')
			{
				if(literal.compareToIgnoreCase(lowKey) < 0||literal.compareToIgnoreCase(hiKey)> 0)
				{
					ff=0;
				}
				else
					ff=(double)(hiKey.charAt(0)-literal.charAt(0))/(hiKey.charAt(0)-lowKey.charAt(0));
			}
			else if(condition=='<')
			{
				System.out.println(literal.compareToIgnoreCase(lowKey));
				if(literal.compareToIgnoreCase(lowKey) < 0||literal.compareToIgnoreCase(hiKey)> 0)
				{
					ff=0;
				}
				else
					ff=(double)(literal.charAt(0)-lowKey.charAt(0))/(hiKey.charAt(0)-lowKey.charAt(0));
			}
		}
		else{
			char[] highKey = {hiKey.charAt(0),hiKey.charAt(1)};
			char[] loKey = {lowKey.charAt(0),lowKey.charAt(1)};
			char[] lit = {literal.charAt(0),literal.charAt(1)};
			int power = 1;
			for(int i=0;i<2;i++)
			{
				if(highKey[i]>=65 && highKey[i]<91)
				{
					for(int j=0;j<alphabetCap.length;j++)
					{
						if(highKey[i]==alphabetCap[j])
						{
							val=j+1;
							break;
						}
					}
					
				}
				else if(highKey[i]>=97 && highKey[i]<122)
				{
					for(int j=0;j<alphabetSmall.length;j++)
					{
						if(highKey[i]==alphabetSmall[j])
						{
							val=j+1;
							break;
						}
					}
				}
				conversionhi=conversionhi+((int)(Math.pow(26,power)))*val;
				power--;
			}
			power =1;
			for(int i=0;i<2;i++)
			{
				if(loKey[i]>=65 && loKey[i]<91)
				{
					for(int j=0;j<alphabetCap.length;j++)
					{
						if(loKey[i]==alphabetCap[j])
						{
							val=j+1;
							break;
						}
					}
				}
				else if(highKey[i]>=97 && highKey[i]<122)
				{
					for(int j=0;j<alphabetSmall.length;j++)
					{
						if(loKey[i]==alphabetSmall[j])
						{
							val=j+1;
							break;
						}
					}
				}
				conversionlo=conversionlo+((int)(Math.pow(26,power)))*val;
				power--;
			}
			power =1;
			for(int i=0;i<2;i++)
			{
				if(lit[i]>=65 && lit[i]<91)
				{
					for(int j=0;j<alphabetCap.length;j++)
					{
						if(lit[i]==alphabetCap[j])
						{
							val=j+1;
							break;
						}
					}
				}
				else if(lit[i]>=97 && lit[i]<122)
				{
					for(int j=0;j<alphabetSmall.length;j++)
					{
						if(lit[i]==alphabetSmall[j])
						{
							val=j+1;
							break;
						}
					}
				}
				conversionlit=conversionlit+((int)(Math.pow(26,power)))*val;
				power--;
			}
			if(condition=='>')
			{
				if(conversionlit<conversionlo||conversionlit>conversionhi)
				{
					ff=0;
				}
				else
					ff=(double)(conversionhi-conversionlit)/(conversionhi-conversionlo);
			}
			else if(condition=='<')
			{
				if(conversionlit<conversionlo||conversionlit>conversionhi)
				{
					ff=0;
				}
				else
					ff=(double)(conversionlit-conversionlo)/(conversionhi-conversionlo);
			}
		}
	}
	else
	{
		if(condition=='>')
		{
			if(Integer.parseInt(literal)>Integer.parseInt(hiKey)||Integer.parseInt(literal)<Integer.parseInt(lowKey))
			{
					ff=0;
			}
			else
			{
				ff=(double)(Integer.parseInt(hiKey)-Integer.parseInt(literal))/(Integer.parseInt(hiKey)-Integer.parseInt(lowKey));
			}
		}
		else if(condition=='<')
		{
			if(Integer.parseInt(literal)<Integer.parseInt(lowKey)||Integer.parseInt(literal)>Integer.parseInt(hiKey))
			{
					ff=0;
			}
			else
			{
				ff=(double)(Integer.parseInt(literal)-Integer.parseInt(lowKey))/(Integer.parseInt(hiKey)-Integer.parseInt(lowKey));
			}	
		}
		
	}
	return ff ;
}
private String indexOnly(ArrayList<Table> tableList,ArrayList<Integer> columnList) {
	int countIndexOnly=0;
	for(Index index:tableList.get(0).getIndexes())
	{		
		countIndexOnly=0;
		for(Index.IndexKeyDef def:index.getIdxKey())
		{
			for(Integer id:columnList)
			{
				if(def.colId==id)
				countIndexOnly++;
			}
		}
		if(countIndexOnly==columnList.size())
		{
			return index.getIdxName();
		}
	}

	return "";
}
private String sortAvoidance(ArrayList<Table> tableList,HashMap<Integer,Boolean> orderByList) {
	int countOrderBy=0;
	if(!orderByList.isEmpty())
	{
		for(Index index:tableList.get(0).getIndexes())
		{		
			countOrderBy=0;
			for(Index.IndexKeyDef def:index.getIdxKey())
			{
				int orderByPos=1;
				for(Entry<Integer,Boolean> orderby:orderByList.entrySet())
				{		
					if(orderby.getKey()==def.colId && orderby.getValue()==def.descOrder
							&& orderByPos==def.idxColPos)
					{
						countOrderBy++;
					}
					orderByPos++;
				}
			}
			if(countOrderBy==orderByList.size())
			{
				return index.getIdxName();
			}
		}
	}

	return "";
}
private void predicateAnalysistwoTable(ArrayList<Predicate> predicates,ArrayList<Table> tableList,ArrayList<String> andList,
		ArrayList<String> orList,HashMap<Integer, Boolean> orderByList,ArrayList<Integer> columnList) throws Exception {
	PlanTable planTable = new PlanTable();
	String leadingTable = "";String innerTable="";
	String indexString="",indexString2="";
	if(tableList.get(0).getIndexes().size()==0&&tableList.get(1).getIndexes().size()==0)
	{
		planTable.setTable1Card(tableList.get(0).getTableCard());
		planTable.setTable2Card(tableList.get(1).getTableCard());
		planTable.setAccessType('R');
		planTable.setPrefetch('S');
		if(predicates.size()==1)
		{
			if(predicates.get(0).join)
			{
				if(tableList.get(0).getTableCard()>tableList.get(1).getTableCard())
				{
					leadingTable=tableList.get(0).getTableName();
					innerTable=tableList.get(1).getTableName();
				}
				else
				{
					leadingTable=tableList.get(1).getTableName();
					innerTable=tableList.get(0).getTableName();
				}
				planTable.setLeadTable(leadingTable);
				planTable.setInnerTable(innerTable);
				setPredicateSequence(predicates,planTable);
			}
			else
			{
				throw new DbmsError("Query Missing join predicate");
			}
		}
		else
		{
			leadingTable=findLeadingTable(predicates,tableList,false);
			for(Table table:tableList)
			{
				if(!table.getTableName().equalsIgnoreCase(leadingTable))
				{
					innerTable=table.getTableName();
				}
			}
			planTable.setLeadTable(leadingTable);
			planTable.setInnerTable(innerTable);
			setPredicateSequence(predicates,planTable);
		}
		
	}
	else if(tableList.get(0).getIndexes().size()==0&&tableList.get(1).getIndexes().size()>0)
	{
		planTable.setTable1Card(tableList.get(0).getTableCard());
		planTable.setTable2Card(tableList.get(1).getTableCard());
		indexString=findIndexUsed(tableList.get(1),predicates);
		if(indexString.length()>1)
		{
			leadingTable=tableList.get(0).getTableName();
			innerTable=tableList.get(1).getTableName();
			planTable.setLeadTable(leadingTable);
			planTable.setInnerTable(innerTable);
			planTable.setAccessType('I');
			planTable.setAccessName(indexString);
			planTable.setPrefetch('S');
			setPredicateSequence(predicates,planTable);
		}
	}
	else if(tableList.get(0).getIndexes().size()>0&&tableList.get(1).getIndexes().size()==0)
	{
		planTable.setTable1Card(tableList.get(0).getTableCard());
		planTable.setTable2Card(tableList.get(1).getTableCard());
		indexString=findIndexUsed(tableList.get(0),predicates);
		if(indexString.length()>1)
		{
			leadingTable=tableList.get(1).getTableName();
			innerTable=tableList.get(0).getTableName();
			planTable.setLeadTable(leadingTable);
			planTable.setMatchCols(1);
			planTable.setInnerTable(innerTable);
			planTable.setInnerTable(tableList.get(1).getTableName());
			planTable.setAccessType('I');
			planTable.setAccessName(indexString);
			planTable.setPrefetch('S');
		}
		setPredicateSequence(predicates,planTable);
	}
	else if(tableList.get(0).getIndexes().size()>0&&tableList.get(1).getIndexes().size()>0)
	{
		int matchcol1=0,matchcol2=0;
		planTable.setTable1Card(tableList.get(0).getTableCard());
		planTable.setTable2Card(tableList.get(1).getTableCard());
		indexString=findIndexUsed(tableList.get(0),predicates);
		indexString2=findIndexUsed(tableList.get(1),predicates);
		if(indexString.length()>1 && indexString2.length()>1)
		{
			matchcol1=findMatchcols(predicates,tableList.get(0).getIndexes());
			matchcol2=findMatchcols(predicates,tableList.get(1).getIndexes());
			if(matchcol1>matchcol2)
			{
				planTable.setMatchCols(matchcol1);
				planTable.setInnerTable(tableList.get(0).getTableName());
				planTable.setLeadTable(tableList.get(1).getTableName());
				planTable.setAccessType('I');
				planTable.setAccessName(indexString);
				planTable.setPrefetch('S');
				setPredicateSequence(predicates,planTable);
			}
			else if(matchcol1<matchcol2)
			{
				planTable.setMatchCols(matchcol2);
				planTable.setInnerTable(tableList.get(1).getTableName());
				planTable.setLeadTable(tableList.get(0).getTableName());
				planTable.setAccessType('I');
				planTable.setAccessName(indexString2);
				planTable.setPrefetch('S');
				setPredicateSequence(predicates,planTable);
			}
			else if(matchcol1==matchcol2)
			{
				if(predicates.size()==1)
				{
					planTable.setPrefetch('S');
					planTable.setMatchCols(1);
					if(predicates.get(0).join)
					{
						if(tableList.get(0).getTableCard()>tableList.get(1).getTableCard())
						{
							leadingTable=tableList.get(1).getTableName();
							innerTable=tableList.get(0).getTableName();
							planTable.setAccessType('I');
							planTable.setAccessName(indexString);
						}
						else
						{
							leadingTable=tableList.get(0).getTableName();
							innerTable=tableList.get(1).getTableName();
							planTable.setAccessType('I');
							planTable.setAccessName(indexString2);
						}
						planTable.setLeadTable(leadingTable);
					}
					else
					{
						throw new DbmsError("Query Missing join predicate");
					}
				}
				else
				{
					leadingTable=findLeadingTable(predicates, tableList,true);
					if(tableList.get(0).getTableName().equalsIgnoreCase(leadingTable))
					{
						innerTable=tableList.get(1).getTableName();
					}
					else
					{
						innerTable=tableList.get(0).getTableName();
					}
				}
				if(innerTable.equalsIgnoreCase(tableList.get(1).getTableName()))
				{
					planTable.setAccessName(indexString2);
					planTable.setMatchCols(matchcol2);
				}
				else
				{
					planTable.setAccessName(indexString);
					planTable.setMatchCols(matchcol1);
				}
				planTable.setInnerTable(innerTable);
				planTable.setLeadTable(leadingTable);
				planTable.setAccessType('I');
				
				planTable.setPrefetch('S');
				setPredicateSequence(predicates,planTable);
			}		
		}
		else if(indexString.length()>1 && !(indexString2.length()>1))
		{
			leadingTable=tableList.get(1).getTableName();
			planTable.setLeadTable(leadingTable);
			planTable.setMatchCols(findMatchcols(predicates, tableList.get(0).getIndexes()));
			planTable.setInnerTable(tableList.get(0).getTableName());
			planTable.setAccessType('I');
			planTable.setAccessName(indexString);
			planTable.setPrefetch('S');
			setPredicateSequence(predicates,planTable);
		}
		else if(!(indexString.length()>1) && indexString2.length()>1)
		{
			leadingTable=tableList.get(0).getTableName();
			planTable.setLeadTable(leadingTable);
			planTable.setInnerTable(tableList.get(1).getTableName());
			planTable.setAccessType('I');
			planTable.setAccessName(indexString2);
			planTable.setPrefetch('S');
			setPredicateSequence(predicates,planTable);
			planTable.setMatchCols(1);
		}
		else
		{
			planTable.setPrefetch('S');
			planTable.setAccessType('R');
			planTable.setTable1Card(tableList.get(0).getTableCard());
			planTable.setTable2Card(tableList.get(1).getTableCard());
			if(predicates.size()==1)
			{
				predicates.get(0).setSequence(1);
				if(predicates.get(0).join)
				{
					if(tableList.get(0).getTableCard()>tableList.get(1).getTableCard())
					{
						leadingTable=tableList.get(0).getTableName();
						innerTable=tableList.get(1).getTableName();
					}
					else
					{
						leadingTable=tableList.get(1).getTableName();
						innerTable=tableList.get(0).getTableName();
					}
					planTable.setLeadTable(leadingTable);
					planTable.setInnerTable(innerTable);
					setPredicateSequence(predicates,planTable);
				}
				else
				{
					throw new DbmsError("Query Missing join predicate");
				}
			}
			else
			{
				leadingTable=findLeadingTable(predicates,tableList,false);
				planTable.setLeadTable(leadingTable);
				if(tableList.get(0).getTableName().equalsIgnoreCase(leadingTable))
				{
					innerTable=tableList.get(1).getTableName();
				}
				else
				{
					innerTable=tableList.get(0).getTableName();
				}
				planTable.setInnerTable(innerTable);
				setPredicateSequence(predicates,planTable);
			}
		}
	}
	planTable.printTable(out);
	Predicate p = new Predicate();
	p.printTable(out, predicates);
}
private String findLeadingTable(ArrayList<Predicate> predicates ,ArrayList<Table> tableList,boolean noIndex) 
{
	String localPredicate="",leftJoin="",rightJoin="";
	double ff1=1,ff2=1;
	
	for(Predicate predicate:predicates)
	{
		if(predicate.join)
		{
			ff1=predicate.getFf1();
			ff2=predicate.getFf2();
			leftJoin=predicate.getText().split("=")[0];
			rightJoin=predicate.getText().split("=")[1];
		}
		else if(predicate.getType()=='E')
		{
			localPredicate = predicate.getText().split("=")[0];
			if(leftJoin.equalsIgnoreCase(localPredicate))
			{
				ff1=ff1*predicate.getFf1();
			}
			else if(rightJoin.equalsIgnoreCase(localPredicate))
			{
				ff2=ff2*predicate.getFf1();
			}
		}
		else if(predicate.getType()=='R')
		{
			if(predicate.getText().contains(">"))
			{
				localPredicate = predicate.getText().split(">")[0];
				if(leftJoin.equalsIgnoreCase(localPredicate))
				{
					ff1=ff1*predicate.getFf1();
				}
				else if(rightJoin.equalsIgnoreCase(localPredicate))
				{
					ff2=ff2*predicate.getFf1();
				}
			}
			else
			{
				localPredicate = predicate.getText().split("<")[0];
				if(leftJoin.equalsIgnoreCase(localPredicate))
				{
					ff1=ff1*predicate.getFf1();
				}
				else if(rightJoin.equalsIgnoreCase(localPredicate))
				{
					ff2=ff2*predicate.getFf1();
				}
			}
		}
		else if(predicate.getType()=='I')
		{
			localPredicate = predicate.getText().split("IN")[0];
			if(leftJoin.equalsIgnoreCase(localPredicate))
			{
				ff1=ff1*predicate.getFf1();
			}
			else if(rightJoin.equalsIgnoreCase(localPredicate))
			{
				ff2=ff2*predicate.getFf1();
			}
		}
	}
	if(!noIndex)
	{
		if(ff1*tableList.get(0).getTableCard()>ff2*tableList.get(1).getTableCard())
		{
			return leftJoin.split("\\.")[0]; 
		}
		else
		{
			return rightJoin.split("\\.")[0];
		}
	}
	if(ff1>ff2)
	{
		return leftJoin.split("\\.")[0];
	}
	else
	{
		return rightJoin.split("\\.")[0];
	}
}
private String findIndexUsed(Table table,ArrayList<Predicate> predicates)
{	
	String returnVal ="";
	if(predicates.size()==1)
	{	
		for(Index indexex:table.getIndexes())
		{
			for(Index.IndexKeyDef def:indexex.getIdxKey())
			{
				if(def.colId==predicates.get(0).getColid1()&&def.idxColPos==1
						&&predicates.get(0).getTable1().equalsIgnoreCase(table.getTableName()))
				{
					returnVal= indexex.getIdxName();
				}
				else if(def.colId==predicates.get(0).getColid2()&&def.idxColPos==1 &&
						predicates.get(0).getTable2().equalsIgnoreCase(table.getTableName()))
				{
					returnVal= indexex.getIdxName();
				}
			}
		}
	}
	else
	{
		for(Predicate pred:predicates)
		{
			if(pred.join)
			{
				for(Index indexex:table.getIndexes())
				{
					for(Index.IndexKeyDef def:indexex.getIdxKey())
					{
						if(def.colId==predicates.get(0).getColid1()&&def.idxColPos==1 
								&&predicates.get(0).getTable1().equalsIgnoreCase(table.getTableName()))
						{
							returnVal= indexex.getIdxName();
						}
						else if(def.colId==predicates.get(0).getColid2()&&def.idxColPos==1 &&
								predicates.get(0).getTable2().equalsIgnoreCase(table.getTableName()))
						{
							returnVal= indexex.getIdxName();
						}
					}
				}
			}
			else
			{
				for(Index indexex:table.getIndexes())
				{
					for(Index.IndexKeyDef def:indexex.getIdxKey())
					{
						if(def.colId==predicates.get(0).getColid1()
								&&def.idxColPos==1 &&predicates.get(0).getTable1().equalsIgnoreCase(table.getTableName()))
						{
							returnVal= indexex.getIdxName();
						}
					}
				}
			}
		}
	}
	return returnVal;
}
private void setPredicateSequence(ArrayList<Predicate> predicates,PlanTable planTable)
{
	String innerColumn = "";
	HashMap<Predicate, Double> leadPredicate = new HashMap<Predicate, Double>();
	HashMap<Predicate, Double> innerPredicate = new HashMap<Predicate, Double>();
	int sequence=1;
	if(predicates.size()==1)
	{
		predicates.get(0).setSequence(1);
	}
	else if(predicates.size()>1)
	{
		for(Predicate predicate:predicates)
		{
			if(!predicate.join)
			{
				if(predicate.getType()=='E')
				{
					if((predicate.getText().split("=")[0]).split("\\.")[0].equalsIgnoreCase(planTable.getLeadTable()))
					{
						leadPredicate.put(predicate, predicate.getFf1());
					}
					if((predicate.getText().split("=")[0]).split("\\.")[0].equalsIgnoreCase(planTable.getInnerTable()))
					{
						innerPredicate.put(predicate, predicate.getFf1());
					}
				}
				else if(predicate.getType()=='R')
				{
					if(predicate.getText().contains(">"))
					{
						if((predicate.getText().split(">")[0]).split("\\.")[0].equalsIgnoreCase(planTable.getLeadTable()))
						{
							leadPredicate.put(predicate, predicate.getFf1());
						}
						if((predicate.getText().split(">")[0]).split("\\.")[0].equalsIgnoreCase(planTable.getInnerTable()))
						{
							innerPredicate.put(predicate, predicate.getFf1());
						}
					}
					else
					{
						if((predicate.getText().split("<")[0]).split("\\.")[0].equalsIgnoreCase(planTable.getLeadTable()))
						{
							leadPredicate.put(predicate, predicate.getFf1());
						}
						if((predicate.getText().split("<")[0]).split("\\.")[0].equalsIgnoreCase(planTable.getInnerTable()))
						{
							innerPredicate.put(predicate, predicate.getFf1());
						}
					}
				}
				else
				{
					if((predicate.getText().split("IN")[0]).split("\\.")[0].equalsIgnoreCase(planTable.getLeadTable()))
					{
						leadPredicate.put(predicate, predicate.getFf1());
					}
					if((predicate.getText().split("IN")[0]).split("\\.")[0].equalsIgnoreCase(planTable.getInnerTable()))
					{
						innerPredicate.put(predicate, predicate.getFf1());
					}
					
				}
			}
		}
		entriesSortedByValues(leadPredicate);
		for(Entry<Predicate,Double> setSequences:leadPredicate.entrySet())
		{
			setSequences.getKey().setSequence(sequence);
			sequence++;
		}
		for(Predicate predicate:predicates)
		{
			if(predicate.join)
			{
				predicate.setSequence(sequence);
				if(predicate.getText().split("=")[1].split("\\.")[0].equalsIgnoreCase(planTable.getInnerTable()))
				{
					innerColumn=(predicate.getText().split("=")[1]);
				}
				else
				{
					innerColumn=(predicate.getText().split("=")[0]);
				}
				
				sequence++;
				break;
			}
		}
		entriesSortedByValues(innerPredicate);
		for(Entry<Predicate,Double> setSequences:innerPredicate.entrySet())
		{
			if(!setSequences.getKey().getDescription().equalsIgnoreCase("TCP"))
			{
				if(setSequences.getKey().getText().split("=")[0].equalsIgnoreCase(innerColumn))
				{
					setSequences.getKey().setSequence(0);
				}
				else
				{
					setSequences.getKey().setSequence(sequence);
					sequence++;
				}
			}
			else
			{
				setSequences.getKey().setSequence(0);
			}
		}
	}
}
private boolean checkliteral(String literal)
{
	if(literal.charAt(0)>=65 && literal.charAt(0)<=90)
	{
		return true;
	}
	else if(literal.charAt(0)>=97 && literal.charAt(0)<=122)
	{
		return true;
	}
	else if(literal.charAt(0)>=48 && literal.charAt(0)<=57)
	{
		return true;
	}
	else 
	{
		return false;
	}
}
static <K,V extends Comparable<? super V>> SortedSet<Map.Entry<K,V>> entriesSortedByValues(Map<K,V> map) {
    SortedSet<Map.Entry<K,V>> sortedEntries = new TreeSet<Map.Entry<K,V>>(
        new Comparator<Map.Entry<K,V>>() {
            @Override public int compare(Map.Entry<K,V> e1, Map.Entry<K,V> e2) {
                int res = e1.getValue().compareTo(e2.getValue());
                return res != 0 ? res : 1; // Special fix to preserve items with equal values
            }
        }
    );
    sortedEntries.addAll(map.entrySet());
    return sortedEntries;
}
private void setSequences(ArrayList<Predicate> predicates,Index index,ArrayList<String> orlist)
{
	int sequence = 1; char phase ='S',type=' ';int i=0;
	HashMap<Predicate,Double> predicateToFF= new HashMap<Predicate,Double>();
	ArrayList<String> predicateText = new ArrayList<String>();
	ArrayList<String> predicateVal = new ArrayList<String>();
	ArrayList<Character> condition = new ArrayList<Character>();
	for(Predicate p:predicates)
	{
		//condition.add(p.getText().charAt(p.getText().indexOf("=")));
		if(p.getText().contains("="))
		{
			predicateText.add(p.getText().split("=")[0]);
			predicateVal.add((p.getText().split("=")[1]));
			condition.add(p.getText().charAt(p.getText().indexOf("=")));
		}
		else if(p.getText().contains(">"))
		{
			predicateText.add(p.getText().split(">")[0]);
			predicateVal.add((p.getText().split(">")[1]));
			condition.add(p.getText().charAt(p.getText().indexOf(">")));
		}
		else if(p.getText().contains("<"))
		{
			predicateText.add(p.getText().split("<")[0]);
			predicateVal.add((p.getText().split("<")[1]));
			condition.add(p.getText().charAt(p.getText().indexOf("<")));
		}	
		else if(p.getText().contains("IN"))
		{
			predicateText.add(p.getText().split("IN")[0]);
			predicateVal.add((p.getText().split("IN")[1]));
			condition.add(p.getText().charAt(p.getText().indexOf("IN")));
		}
	}
	Set<String> uniqueText = new HashSet<String>(predicateText);
	Set<String> uniqueVal = new HashSet<String>(predicateVal);
	Set<Character> uniqueVCond = new HashSet<Character>(condition);
	if(uniqueText.size()==1 && predicates.size()>1)
	{
		if(orlist.isEmpty())
		{
			if(uniqueVal.size()>1 && uniqueVCond.size()==1)
			{
				for(Predicate p:predicates)
				{
					p.setSequence(0);
				}
			}
			else if(uniqueVal.size()==1 && uniqueVCond.size()==1)
			{
				for(Predicate p:predicates)
				{
					p.setSequence(sequence);
					sequence=0;
				}
			}
			else if(uniqueVal.size()>1 && uniqueVCond.size()>1)
			{
				for(Predicate p:predicates)
				{
					p.setSequence(sequence);
					sequence++;
				}
			}
		}
		else if(!orlist.isEmpty() && uniqueVal.size()!=1)
		{
			if(uniqueVCond.size()==1)
			{
				for(Predicate p:predicates)
				{
					if(p.getText().contains(">")||p.getText().contains("<")&&sequence==1)
					{
						p.setSequence(sequence);
						sequence=0;
					}
					else
					{
						p.setSequence(0);
					}
				}
			}
			else
			{
				for(Predicate predicate:predicates)
				{
					predicateToFF.put(predicate, predicate.getFf1());
				}
				entriesSortedByValues(predicateToFF);
				i=predicateToFF.size();
				for(Entry<Predicate, Double> entry:predicateToFF.entrySet())
				{
					Predicate key = entry.getKey();
					if(key.getSequence()==0)
					{
						key.setSequence(i);
						i--;
					}
				}
			}
		}
	}
	else{
	
	for(Index.IndexKeyDef def:index.getIdxKey())
	{
		for(Predicate predicateObj:predicates)
		{
			if(def.colId==predicateObj.getColid1() && def.idxColPos==1)
			{
				phase = 'M';
				type=predicateObj.getType();	
				predicateObj.setSequence(sequence);
				sequence++;
			}
			else if(def.colId==predicateObj.getColid1()&&phase=='M'&&type=='E')
			{
				phase = 'M';
				type=	predicateObj.getType();
				predicateObj.setSequence(sequence);
				sequence++;
			}
			else if(def.colId==predicateObj.getColid1()&&phase=='M'&&type=='R')
			{
				phase = 'S';
				type=	predicateObj.getType();
				predicateObj.setSequence(sequence);
				sequence++;
			}
			else if(def.colId==predicateObj.getColid1()&&phase=='S')
			{
				phase = 'S';
				type=	predicateObj.getType();
				predicateObj.setSequence(sequence);
				sequence++;
			}
		}
	}
	if((sequence-1)!=predicates.size())
	{
		for(Predicate predicate:predicates)
		{
			predicateToFF.put(predicate, predicate.getFf1());
		}
		entriesSortedByValues(predicateToFF);
		i=predicateToFF.size();
		for(Entry<Predicate, Double> entry:predicateToFF.entrySet())
		{
			Predicate key = entry.getKey();
			if(key.getSequence()==0)
			{
				key.setSequence(i);
				i--;
			}
		}
	}
	}
}
private int findMatchcols(ArrayList<Predicate> predicate,ArrayList<Index> index)
{
	int matchcol=0;char phase = 'M',type='E';
	ArrayList<Integer> returnMatchCol = new ArrayList<>();
	String predicateUsed=""; 
	for(Index indexes:index)
	{
		matchcol=0;
		for(Index.IndexKeyDef def:indexes.getIdxKey())
		{
			for(Predicate p : predicate)
			{
				if(p.join)
				{
					if(def.colId==p.getColid1()&&def.idxColPos==1)
					{
						matchcol++;
						if(p.getText().contains("="))
						{
							predicateUsed=p.getText().split("=")[0];
						}
						else if(p.getText().contains(">"))
						{
							predicateUsed=p.getText().split(">")[0];
						}
						else if(p.getText().contains("<"))
						{
							predicateUsed=p.getText().split("<")[0];
					}
					else if(def.colId==p.getColid2()&&def.idxColPos==1)
					{
						matchcol++;
						if(p.getText().contains("="))
						{
							predicateUsed=p.getText().split("=")[0];
						}
						else if(p.getText().contains(">"))
						{
							predicateUsed=p.getText().split(">")[0];
						}
						else if(p.getText().contains("<"))
						{
							predicateUsed=p.getText().split("<")[0];
						}
						
					}
				}
			}
			else
			{
				if(def.colId==p.getColid1() && def.idxColPos==1 && !(p.getText().split("=")[0].equalsIgnoreCase(predicateUsed)||
						p.getText().split(">")[0].equalsIgnoreCase(predicateUsed)||p.getText().split("<")[0].equalsIgnoreCase(predicateUsed)))
				{
					phase = 'M';
					type=p.getType();	
					matchcol++;
				}
				else if(def.colId==p.getColid1()&&phase=='M'&&type=='E' && !(p.getText().split("=")[0].equalsIgnoreCase(predicateUsed)||
						p.getText().split(">")[0].equalsIgnoreCase(predicateUsed)||p.getText().split("<")[0].equalsIgnoreCase(predicateUsed)))
				{
					phase = 'M';
					type=p.getType();	
					matchcol++;
				}
				if(def.colId==p.getColid2() && def.idxColPos==1&& !(p.getText().split("=")[0].equalsIgnoreCase(predicateUsed)||
						p.getText().split(">")[0].equalsIgnoreCase(predicateUsed)||p.getText().split("<")[0].equalsIgnoreCase(predicateUsed)))
				{
					phase = 'M';
					type=p.getType();	
					matchcol++;
				}
				else if(def.colId==p.getColid2()&&phase=='M'&&type=='E'&&!(p.getText().split("=")[0].equalsIgnoreCase(predicateUsed)||
						p.getText().split(">")[0].equalsIgnoreCase(predicateUsed)||p.getText().split("<")[0].equalsIgnoreCase(predicateUsed)) )
				{
					phase = 'M';
					type=p.getType();	
					matchcol++;
				}
			}
		}

	}
		returnMatchCol.add(matchcol);
}
	//Collections.sort(returnMatchCol);
	return Collections.max(returnMatchCol);
}
private void sequenceForLiteral(ArrayList<Predicate> predicates) 
{
	String lhs="",rhs="";
	for(Predicate p:predicates)
	{
		if(p.getTable1().length()==0)
		{
			if(p.getText().contains("="))
			{
				lhs = p.getText().split("=")[0];
				rhs=p.getText().split("=")[1];
			}
			else if(p.getText().contains(">"))
			{
				lhs = p.getText().split(">")[0];
				rhs=p.getText().split(">")[1];
			}
			else if(p.getText().contains("<"))
			{
				lhs = p.getText().split("<")[0];
				rhs=p.getText().split("<")[1];
			}
			if(lhs.charAt(0)>=65 && lhs.charAt(0)<=90 || lhs.charAt(0)>=97 && lhs.charAt(0)<=122 )
			{
				if(rhs.charAt(0)>=65 && rhs.charAt(0)<=90 || rhs.charAt(0)>=97 && rhs.charAt(0)<=122 )
				{
					if(lhs.equalsIgnoreCase(rhs))
					{
						p.setFf1(1);
						p.setSequence(0);
					}
					else
					{
						p.setFf1(0);
						p.setSequence(0);
					}
				}
			}
			else if(lhs.charAt(0)>=48 && lhs.charAt(0)<=57)
			{
				if(p.getText().contains("="))
				{
					if(Integer.parseInt(lhs)==Integer.parseInt(rhs))
					{
						p.setFf1(1);
						p.setSequence(0);
					}
					else
					{
						p.setFf1(0);
						p.setSequence(0);
					}
				}
				else if(p.getText().contains(">"))
				{
					if(Integer.parseInt(lhs)>Integer.parseInt(rhs))
					{
						p.setFf1(1);
						p.setSequence(0);
					}
					else
					{
						p.setFf1(0);
						p.setSequence(0);
					}
				}
				else if(p.getText().contains("<"))
				{
					if(Integer.parseInt(lhs)<Integer.parseInt(rhs))
					{
						p.setFf1(1);
						p.setSequence(0);
					}
					else
					{
						p.setFf1(0);
						p.setSequence(0);
					}
				}
			}
		}
	}
}

}

