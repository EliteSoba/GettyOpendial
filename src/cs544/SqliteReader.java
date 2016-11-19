package cs544;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SqliteReader {

	Connection connection;
	Statement statement;
	
	public static final String TABLE = "PAINTINGS";
	
	public enum Column {
		TITLE ("TITLE"),
		ARTIST ("ARTIST"),
		CULTURE ("CULTURE"),
		DATE ("DATE"),
		MEDIUM ("MEDIUM"),
		DIM ("DIMENSIONS"),
		STORY ("STORY");
		
		public String key;
		Column(String key) {
			this.key = key;
		}
	}
	
	public SqliteReader(String db) {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			System.out.println("Error. Please ensure dqlite-jdbc.jar is present");
			e.printStackTrace();
		}
		
		try {
			connection = DriverManager.getConnection("jdbc:sqlite::resource:" + db);
			statement = connection.createStatement();
		} catch (SQLException e) {
			System.out.println("Error connecting to database");
			e.printStackTrace();
		}
		
		//Connection established successfully
		System.out.println("Database Connection Established Successfully");
	}
	
	/**
	 * Sanitizes query input strings to replace "s with ""s,
	 * the escape character in SQLite for "
	 * @param input The string to clean
	 * @return The string with "s replaced with ""s
	 */
	private String clean(String input) {
		return input.replace("\"", "\"\"");
	}
	
	/**
	 * Runs the query against the DB, and returns the desired
	 * column as a String array
	 * @param query The query to run against the DB
	 * @param target The column of interest
	 * @param unique Whether or not to return only unique results
	 * @return null if no matching results are found; otherwise a String array of all matching results
	 */
	private String[] runQuery(String query, Column target, boolean unique) {
		String[] results = null;

		try {
			ResultSet rs = statement.executeQuery(query + (unique ? (" GROUP BY " + target.key) : "") + " COLLATE NOCASE");
			ArrayList<String> res = new ArrayList<String>();
			
			while (rs.next()) {
				res.add(rs.getString(target.key));
			}
			
			if (res.size() == 0) {
				//No results
				return null;
			}
			results = new String[res.size()];
			for (int i = 0; i < res.size(); ++i) {
				results[i] = res.get(i);
			}
			
			return results;
		} catch (SQLException e) {
			System.err.println("Error executing query: " + query);
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Queries the database for a target column given a key column and a value.
	 * No function for getting multiple values at once because its unnecessary at present
	 * @param target The column to get results from
	 * @param key The column to compare the key value to
	 * @param value The value to compare
	 * @param like Determine whether or not to use %Wildcards%
	 * @param unique Whether or not only unique values are desirable
	 * @return null if no matching results are found; otherwise a String array of all matching results
	 */
	public String[] queryDB(Column target, Column key, String value, boolean like, boolean unique) {
		String query = "SELECT \"" + clean(target.key) + "\" FROM " + TABLE + " WHERE \"" + clean(key.key);
		if (like) {
			query += "\" LIKE \"%" + clean(value) + "%\"";
		}
		else {
			query += "\" = \"" + clean(value) + "\"";
		}
		
		return runQuery(query, target, unique);
	}
	
	/**
	 * Queries the database for a target column given a map of keys and values to compare
	 * Again, doesn't return multiple columns
	 * @param target The target column to get results from
	 * @param keys The column to compare the key value to
	 * @param like Determine whether or not to use %Wildcards%
	 * @param unique Whether or not only unique values are desirable
	 * @return null if no matching results are found; otherwise a String array of all matching results
	 */
	public String[] queryDB(Column target, Map<Column, String[]> keys, boolean like, boolean unique) {
		String query = "SELECT \"" + clean(target.key) + "\" FROM " + TABLE + " WHERE ";
		
		
		//Someone in the world is probably very unhappy that I'm concatenating
		//And not StringBuilding
		boolean first = true;
		for (Column key : keys.keySet()) {
			for (String k : keys.get(key)){
				if (!first) {
					query += " AND ";
				}
				else {
					first = false;
				}
				query += "\"" + clean(key.key);
				if (like) {
					query += "\" LIKE \"%" + clean(k) + "%\"";
				}
				else {
					query += "\" = \"" + clean(k) + "\"";
				}
			}
		}
		
		return runQuery(query, target, unique);
	}
	
	/**
	 * Removes parenthetical values from a list of Strings
	 * @param results The array to remove parentheticals from
	 * @return An array with all parenthetical values removed
	 */
	public static String[] removeParens(String[] results) {
		//It'd be more efficient to just modify the original array, I guess
		//Just not a fan of modifying parameters
		String[] newResults = new String[results.length];
		
		for (int i = 0; i < results.length; ++i) {
			String result = results[i];
			
			StringBuilder res = new StringBuilder();
			int depth = 0;
			for (int j = 0; j < result.length(); j++) {
				char ch = result.charAt(j);
				if (ch == '(') {
					++depth;
				}
				else if (ch == ')') {
					--depth;
					if (depth == 0 && result.substring(j).indexOf('(') != -1) {
						res.append('.');
					}
				}
				else if (depth == 0) {
					if (ch == ',') {
						ch = '.';
					}
					res.append(ch);
				}
			}
			
			newResults[i] = res.toString().trim();
			if (newResults[i].charAt(newResults[i].length()-1) == '.') {
				newResults[i] = newResults[i].substring(0, newResults[i].length()-2);
				newResults[i] = newResults[i].trim();
			}
			
			//Lot of cleaning up
			while (newResults[i].indexOf("  ") != -1) {
				newResults[i] = newResults[i].replace("  ", " ");
			}
			newResults[i] = newResults[i].replace(" .", ".");
			newResults[i] = newResults[i].replace(". and", " and");
		}
		return newResults;
	}
	
	/**
	 * Removes duplicate entries in an array
	 * @param results The array to remove duplicates from
	 * @return String Array with all duplicates removed
	 */
	public static String[] removeDupes(String[] results) {
		ArrayList<String> newResults = new ArrayList<String>();
		
		//O(N^2) removal of duplicates. results is probably sorted
		//and even if not, can be sorted in O(N*log(N)) and duplicate
		//removal on sorted list can be done in O(N), but eh.
		for (String result : results) {
			if (!newResults.contains(result)) {
				newResults.add(result);
			}
		}
		
		//Convert ArrayList back to String Array
		String[] res = new String[newResults.size()];
		for (int i = 0; i < res.length; ++i) {
			res[i] = newResults.get(i);
		}
		return res;
	}
	
	/**
	 * Get all the unique values of a certain column
	 * @param column The column of interest
	 * @param parens Whether or not to remove parenthetical values
	 * @return A String array of unique values in that column
	 */
	public String[] getAll(Column column, boolean parens) {
		String query = "SELECT " + column.key + " FROM " + TABLE;
		String[] results = runQuery(query, column, true);
		
		if (parens) {
			results = removeParens(results);
			//Entries were unique before parens were removed
			//Now, we're not so sure
			results = removeDupes(results);
		}
		
		return results;
	}
	
	public static void main(String[] args) {
		SqliteReader reader = new SqliteReader("getty.db");
//		String[] output = reader.getAll(Column.TITLE, true);
//		System.out.println(Arrays.toString(output));
//		output = reader.getAll(Column.CULTURE, true);
//		System.out.println(Arrays.toString(output));
//		output = reader.getAll(Column.DATE, true);
//		System.out.println(Arrays.toString(output));
//		output = reader.getAll(Column.MEDIUM, true);
//		System.out.println(Arrays.toString(output));
//		output = reader.getAll(Column.DIM, true);
//		System.out.println(Arrays.toString(output));
//		output = reader.getAll(Column.STORY, true);
//		System.out.println(Arrays.toString(output));
//		output = reader.getAll(Column.ARTIST, true);
//		System.out.println(Arrays.toString(output));
		Map<Column, String[]> query = new HashMap<Column, String[]>();
		query.put(Column.CULTURE, new String[]{"french"});
		String[] outs = reader.queryDB(Column.CULTURE, query, true, true);
		//System.out.println(Arrays.toString(output));
		System.out.println(Arrays.toString(outs));
	}

}
