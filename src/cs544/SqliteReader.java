package cs544;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.sqlite.Function;

/**
 * Class to interface with the sqlite db
 * @author Tobias Lee
 *
 */
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
		STORY ("STORY"),
		SIZE ("SIZE(DIMENSIONS)"),
		PLACE ("PLACE"),
		KEYWORDS("KEYWORDS");
		
		public String key;
		Column(String key) {
			this.key = key;
		}
	}
	
	/**
	 * Constructor. Requires database to be in src folder
	 * @param db The name of the database to connect to
	 */
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
		
		//Create custom SIZE function
		try {
			Function.create(connection, "SIZE", new Function() {

				@Override
				protected void xFunc() throws SQLException {
					String sizeString = value_text(0);
					
					sizeString = removeParens(sizeString);
					
					double total = 1.0;
					for (String i : sizeString.split(" ")) {
						if (i.matches("[0-9]+(\\.[0-9]+)?")) {
							//Shouldn't fail with this regex
							total *= Double.parseDouble(i);
						}
					}
					result(total);
				}
				
			});
		} catch (SQLException e) {
			e.printStackTrace();
		}
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

		if (target == Column.ARTIST) {
			if (query.contains("WHERE")) {
				query += " AND " + target.key + " NOT LIKE \"%Unknown%\"";
			}
			else {
				query += " WHERE " + target.key + " NOT LIKE \"%Unknown%\"";
			}
		}
		
		try {
			ResultSet rs = statement.executeQuery(query + (unique ? (" GROUP BY " + target.key) : "") + " COLLATE NOCASE" + (unique ? (" ORDER BY COUNT(" + target.key + ") DESC") : ""));
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
		
		if (keys.size() == 0) {
			return null;
		}
		
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
				
				if (key == Column.SIZE) {
					if (k.equalsIgnoreCase("Big")) {
						query += key.key + " >= 10000";
					}
					else if (k.equalsIgnoreCase("Medium")) {
						query += key.key + " >= 3600 AND " + key.key + " < 10000";
					}
					else if (k.equalsIgnoreCase("Small")) {
						query += key.key + " < 3600";
					}
				}
				else {
					query += "\"" + clean(key.key);
					if (like) {
						query += "\" LIKE \"%" + clean(k) + "%\"";
					}
					else {
						query += "\" = \"" + clean(k) + "\"";
					}
				}
			}
		}
		
		return runQuery(query, target, unique);
	}
	
	/**
	 * Removes parenthetical values from a single String
	 * @param word The string to remove parentheticals from
	 * @return The word with all parenthetical values removed
	 */
	public static String removeParens(String word) {
		StringBuilder res = new StringBuilder();
		int depth = 0;
		for (int j = 0; j < word.length(); j++) {
			char ch = word.charAt(j);
			if (ch == '(') {
				++depth;
			}
			else if (ch == ')') {
				--depth;
				if (depth == 0 && word.substring(j).indexOf('(') != -1) {
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
		
		String result = res.toString().trim();
		if (result.charAt(result.length()-1) == '.') {
			result = result.substring(0, result.length()-2);
			result = result.trim();
		}
		
		//Lot of cleaning up
		while (result.indexOf("  ") != -1) {
			result = result.replace("  ", " ");
		}
		result = result.replace(" .", ".");
		result = result.replace(". and", " and");
		return result;
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
			newResults[i] = removeParens(results[i]);
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
	 * Shortcut for removeDupes(removeParens(String[]))
	 * @param results the array to remove dupes and parens from
	 * @return the array without dupes and parens
	 */
	public static String[] removeDupesAndParens(String[] results) {
		return removeDupes(removeParens(results));
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
			results = removeDupesAndParens(results);
		}
		
		return results;
	}
	
	/**
	 * Keywords aren't in the best format, so I make it into a map
	 * @param keywords The keywords column from the table
	 * @return A map of keyword-confidence pairs
	 */
	public static Map<String, Double> keywordsToMap(String keywords) {
		Map<String, Double> keymap = new HashMap<String, Double>();
		if (keywords == null || keywords.length() <= 2) {
			return keymap;
		}
		String keys = keywords.substring(2, keywords.length()-2);
		String[] ks = keys.split("\". \"");
		
		for (String k : ks) {
			//Super optimism
			String key = k.split("=")[0];
			double d = Double.parseDouble(k.split("=")[1].replaceAll("\"", ""));
			
			keymap.put(key, d);
		}
		
		return keymap;
	}
	
	/**
	 * Converts an array of keywords into a bigger array of those keywords
	 * @param keywordss The list of keyword lists
	 * @return An array containing all the keywords sans confidence
	 */
	public static String[] massKeywordsToArray(String[] keywordss) {
		Set<String> tokens = new HashSet<String>();
		for (String keywords : keywordss) {
			Map<String, Double> keymap = keywordsToMap(keywords);
			tokens.addAll(keymap.keySet());
		}
		
		String[] newList = new String[tokens.size()];
		
		int i = 0;
		for (String token : tokens) {
			newList[i++] = token;
		}
		
		return newList;
	}
	
	public static void main(String[] args) {
		SqliteReader reader = new SqliteReader("getty.db");
		
		try {
			ResultSet rs = reader.statement.executeQuery("SELECT " + Column.TITLE + " FROM PAINTINGS WHERE SIZE(DIMENSIONS) < 3600 order by SIZE(DIMENSIONS)");
			
			while (rs.next()) {
				//System.out.println(rs.getString(1));
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		Map<Column, String[]> attributes = new HashMap<Column, String[]>();
		attributes.put(Column.CULTURE, new String[]{""});
		//attributes.put(Column.ARTIST, new String[]{"After  Hyacinthe Rigaud"});
		String[] output = reader.queryDB(Column.PLACE, attributes, true, true);
		//output = DBModule.split(output, " ");
		//output = reader.getAll(Column.KEYWORDS, true);
		/*String[] outputs = new String[0];
		//System.out.println(Arrays.toString(massKeywordsToArray(output)));
		for (String o : output) {
			//System.out.println(Arrays.toString(keywordsToMap(o).keySet().toArray(outputs)));
		}*/
		//System.out.println(DBModule.join(output, "\n"));
		//output = reader.filterSize(Column.DIM, attributes, true, true, Size.MEDIUM);
		System.out.println(Arrays.toString(output));
		/*System.out.println(Arrays.toString(output));
		System.out.println(output[0].equals("null"));
		output = reader.getAll(Column.CULTURE, true);
		System.out.println(Arrays.toString(output));
		output = reader.getAll(Column.DATE, true);
		System.out.println(Arrays.toString(output));
		output = reader.getAll(Column.MEDIUM, true);
		System.out.println(Arrays.toString(output));
		output = reader.getAll(Column.DIM, true);
		System.out.println(Arrays.toString(output));
		output = reader.getAll(Column.STORY, true);
		System.out.println(Arrays.toString(output));
		output = reader.getAll(Column.ARTIST, true);
		System.out.println(Arrays.toString(output));*/
		Map<Column, String[]> query = new HashMap<Column, String[]>();
		query.put(Column.CULTURE, new String[]{""});
		String[] outs = reader.queryDB(Column.DIM, query, true, true);
		outs = removeParens(outs);
		double[] totals = new double[outs.length];
		int j = 0;
		for (String s : outs) {
			double total = 1.0;
			for (String i : s.split(" ")) {
				if (i.matches("[0-9]+(\\.[0-9]+)?")) {
					//Shouldn't fail with this regex
					total *= Double.parseDouble(i);
				}
			}
			//System.out.println(s + ": " + total);
			totals[j++] = total;
		}
		
		Arrays.sort(totals);
		
		//Sizes: 1 <= Small <= 3600 <= Medium <= 10000 <= Large
		/*System.out.println(Arrays.toString(totals));
		System.out.println(Arrays.toString(output));
		System.out.println(Arrays.toString(outs));*/
	}

}
