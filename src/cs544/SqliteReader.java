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
	
	public enum Column {
		TITLE ("LIST_TITLE"),
		ARTIST ("LIST_ARTIST_MAKER"),
		CULTURE ("LIST_CULTURE"),
		DATE ("LIST_DATE"),
		MEDIUM ("LIST_MEDIUM"),
		DIM ("LIST_DIMENSIONS"),
		STORY ("LIST_STORY");
		
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
	 * @return null if no matching results are found; otherwise a String array of all matching results
	 */
	private String[] runQuery(String query, String target) {
		String[] results = null;

		try {
			
			ResultSet rs = statement.executeQuery(query);
			ArrayList<String> res = new ArrayList<String>();
			
			while (rs.next()) {
				res.add(rs.getString(target));
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
	 * @return null if no matching results are found; otherwise a String array of all matching results
	 */
	public String[] queryDB(String target, String key, String value) {
		String query = "SELECT \"" + clean(target) + "\" FROM MUSEUM_DB WHERE \"" + clean(key) + "\" = \"" + clean(value) + "\"";
		
		return runQuery(query, target);
	}
	
	/**
	 * Queries the database for a target column given a map of keys and values to compare
	 * Again, doesn't return multiple columns
	 * @param target The target column to get results from
	 * @param keys The column to compare the key value to
	 * @return null if no matching results are found; otherwise a String array of all matching results
	 */
	public String[] queryDB(String target, Map<String, String> keys) {
		String query = "SELECT \"" + clean(target) + "\" FROM MUSEUM_DB WHERE ";
		
		
		//Someone in the world is probably very unhappy that I'm concatenating
		//And not StringBuilding
		boolean first = true;
		for (String key : keys.keySet()) {
			if (!first) {
				query += "AND ";
			}
			else {
				first = false;
			}
			query += "\"" + clean(key) + "\" = \"" + clean(keys.get(key)) + "\"";
		}
		
		return runQuery(query, target);
	}
	
	public String[] getTitle(String key, String value) {
		return queryDB(Column.TITLE.key, key, value);
	}
	
	public String[] getArtist(String key, String value) {
		return queryDB(Column.ARTIST.key, key, value);
	}
	
	public String[] getCulture(String key, String value) {
		return queryDB(Column.CULTURE.key, key, value);
	}
	
	public String[] getDate(String key, String value) {
		return queryDB(Column.DATE.key, key, value);
	}
	
	public String[] getMedium(String key, String value) {
		return queryDB(Column.MEDIUM.key, key, value);
	}
	
	public String[] getDim(String key, String value) {
		return queryDB(Column.DIM.key, key, value);
	}
	
	public String[] getStory(String key, String value) {
		return queryDB(Column.STORY.key, key, value);
	}
	
	/**
	 * Returns a list of all the Cultures represented in the DB
	 * @return A String Array of cultures in the DB
	 */
	public String[] getCultures() {
		String query = "SELECT " + Column.CULTURE.key + " FROM museum_db GROUP BY " + Column.CULTURE.key;
		
		return runQuery(query, Column.CULTURE.key);
	}
	
	public static void main(String[] args) {
		SqliteReader reader = new SqliteReader("paintings.db");
		String[] output = reader.getCultures();//reader.getCulture(Column.ARTIST.key, "*");
		Map<String, String> query = new HashMap<String, String>();
		query.put(Column.CULTURE.key, output[0]);
		//query.put(Column.CULTURE.key, "Italian");
		String[] outs = reader.queryDB(Column.ARTIST.key, query);
		System.out.println(Arrays.toString(output));
		System.out.println(Arrays.toString(outs));
	}

}
