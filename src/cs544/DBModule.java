package cs544;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import opendial.DialogueState;
import opendial.DialogueSystem;
import opendial.modules.Module;
import cs544.SqliteReader.Column;


public class DBModule implements Module{

	boolean paused = false;
	DialogueSystem system;
	SqliteReader reader;
	String[] cultures, artists, media, sizes, titles;
	String culture, artist, medium, size, title, date, story;
	Map<Column, String[]> attributes;
	
	public static String[] stopwords = {"the", "de", "van", "der", "to", "attributed", "of", "le", "di", "el", "possibly",
			"la", "y", "ter", "and", "by", "workshop", "with", "for", "in", "an", "a", "at", "as", "du", "et", "other", "new",
			"me", "on", "about", "open", "un", "please", "aux", "not", "lot", "los", "from"};
	
	@Override
	public boolean isRunning() {
		return !paused;
	}

	@Override
	public void pause(boolean arg0) {
		paused = arg0;
		
	}

	public DBModule(DialogueSystem system) {
		this.system = system;
	}
	
	@Override
	public void start() {
		reader = new SqliteReader("getty.db");
		
		cultures = reader.getAll(Column.CULTURE, true);
		attributes = new HashMap<Column, String[]>();
	}
	
	/**
	 * Combines elements of a list into a single String
	 * @param list The list to join
	 * @param joiner How to separate each element
	 * @return The combined String, with elements separated by joiners
	 */
	public static String join(String[] list, String joiner) {
		if (list.length == 0) {
			return "";
		}
		if (list.length == 1) {
			return list[0];
		}
		StringBuilder s = new StringBuilder(list[0]);
		
		for (int i = 1; i < list.length; ++i) {
			s.append(joiner);
			s.append(list[i]);
		}
		
		return s.toString();
	}
	
	/**
	 * Splits a list of strings into a larger list of string containing
	 * all the elements of the list split by the divisor
	 * @param list The list of words to split
	 * @param divisor The sequence to split by
	 * @return The newly formed list
	 */
	public static String[] split(String[] list, String divisor) {
		Set<String> tokens = new HashSet<String>();
		
		for (String s : list) {
			for (String token : s.split(divisor)) {
				//Also remove some punctuation
				tokens.add(token.toLowerCase().replaceAll("(,|\\.|\\?|!|\\[|\\]|\\(|\\)|\\\"|\\:)", ""));
			}
		}
		
		for (String word : stopwords) {
			tokens.remove(word);
		}
		
		String[] newList = new String[tokens.size()];
		
		int i = 0;
		for (String token : tokens) {
			newList[i++] = token;
		}
		
		return newList;
	}
	
	/**
	 * Return a String array of the arguments of an action
	 * @param action The whole action(args, ...)
	 * @return A String array of the args
	 */
	public static String[] getArgs(String action) {
		String args = action.substring(action.indexOf('(')+1, action.length()-1);
		String[] arguments = args.split(",");
		
		for (int i = 0; i < arguments.length; ++i) {
			arguments[i] = arguments[i].trim();
		}
		
		return arguments;
	}

	@Override
	public void trigger(DialogueState state, Collection<String> updatedVars) {
		for (String s : updatedVars) {
			System.out.print(s);
			System.out.print(" ");
			System.out.print(state.queryProb(s).getBest().toString());
			System.out.print(" ");
		}
		System.out.println();
		
		if (updatedVars.contains("init")) {
			system.addContent("Cultures", Arrays.toString(cultures));
		}
		
		if (updatedVars.contains("ResolveCulture")) {
			String[] cults = state.queryProb("ResolveCulture").getBest().toString().trim().split("#");
			Map<Column, String[]> query = new HashMap<Column, String[]>();
			
			query.put(Column.CULTURE, cults);
			
			String[] results = reader.queryDB(Column.CULTURE, query, true, true);
			
			//No results. AskRepeat
			if (results == null || results.length == 0) {
				system.addContent("a_m", "AskRepeatCulture");
			}
			else if (results.length == 1) {
				system.addContent("NameOfCulture", results[0]);
				system.addContent("NameOfCultureStatus", "confirmed");
				system.addContent("a_m", "Ground(NameOfCulture, " + results[0] + ")");
			}
			else {
				system.addContent("NameOfCulture", results[0]);
				//system.addContent("NameOfCultureStatus", "tentative");
				//The only time we get here on culture is the one "French or German" and all the "Italian (...)"s
				//In which case, "French", "German", or "Italian" are all fine
				system.addContent("NameOfCultureStatus", "confirmed");
				system.addContent("a_m", "Ground(NameOfCulture, " + results[0] + ")");
			}
		}
		
		if (updatedVars.contains("GetArtists")) {
			culture = state.queryProb("GetArtists").getBest().toString().trim();
			attributes.put(Column.CULTURE, new String[]{culture});
			artists = reader.queryDB(Column.ARTIST, Column.CULTURE, culture, true, true);
			if (artists != null) {
				artists = SqliteReader.removeDupes(SqliteReader.removeParens(artists));
				
				system.addContent("Artists", Arrays.toString(split(artists, " ")));
				
				system.addContent("u_m", "Here is a list of " + culture + " artists we have: " + join(artists, ", "));
			}
			else {
				system.addContent("u_m", "Oops, we don't have any works by " + culture + " artists!");
			}
		}
		
		if (updatedVars.contains("ResolveArtist")) {
			String[] arts = state.queryProb("ResolveArtist").getBest().toString().trim().split("#");
			//attributes.put(Column.CULTURE, new String[]{culture});
			Map<Column, String[]> query = new HashMap<Column, String[]>();
			query.putAll(attributes);
			query.put(Column.ARTIST, arts);
			
			String[] results = reader.queryDB(Column.ARTIST, query, true, true);
			
			//No results. AskRepeat
			if (results == null || results.length == 0) {
				system.addContent("a_m", "AskRepeat");
			}
			else if (results.length == 1) {
				system.addContent("NameOfArtist", results[0]);
				system.addContent("NameOfArtistStatus", "confirmed");
				system.addContent("a_m", "Ground(NameOfArtist, " + results[0] + ")");
			}
			else {
				system.addContent("NameOfArtist", results[0]);
				system.addContent("NameOfArtistStatus", "tentative");
			}
		}
		
		if (updatedVars.contains("GetTitles")) {
			artist = state.queryProb("GetTitles").getBest().toString().trim();
			attributes.put(Column.ARTIST, new String[]{artist});
			titles = reader.queryDB(Column.TITLE, attributes, true, true);
			
			if (titles != null) {
				system.addContent("Titles", Arrays.toString(split(titles, " ")));
				
				system.addContent("u_m", "Okay then. These are the works we have by " + SqliteReader.removeParens(artist) + ": " + join(titles, "; "));
			}
			else {
				system.addContent("u_m", "Oops, we don't seem to have any works by that artist!");
			}
		}
		
		
		//if (updatedVars.contains("NameOfCultureStatus")) {
			/*String culture = state.queryProb("NameOfCulture").getBest().toString().trim();
			culture = culture.toLowerCase();
			culture = culture.substring(0, 1).toUpperCase() + culture.substring(1);
			String[] artists = reader.getArtist(Column.CULTURE, culture);
			system.addContent("Artists", Arrays.toString(artists));
			/*String output = culture + ", eh? Well here's a list of artists:\n";
			
			for (String artist : artists) {
				if (artist.indexOf('(') != -1) {
					output += artist.substring(0, artist.indexOf('(')).trim() + ", ";
				}
				else {
					output += artist.trim();
				}
			}
			
			system.addContent("u_m", output);*/
		//}
		
	}

}
