package cs544;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import cs544.SqliteReader.Column;
import opendial.DialogueState;
import opendial.DialogueSystem;
import opendial.modules.Module;

/**
 * Module to interface with the database, extracting relevant information
 * based on the triggers. Acts as sort of a combination between dm and nlg.
 * It does a lot of nlg because for some reason the opendial xmls have some
 * trouble dealing with variables with paranthetical values (ex: Jeanne (Spring))
 * @author Tobias Lee
 *
 */
public class DBModule implements Module{

	boolean paused = false;
	DialogueSystem system;
	SqliteReader reader;
	String[] cultures, artists, media, dims, titles, holder;
	String culture, artist, medium, size, dim, title, date, story;
	Map<Column, String[]> attributes;
	ArrayList<Column> queries;
	
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
		queries = new ArrayList<Column>();
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
		
		//Note to self: Not a big fan of all these strings.
		//Should probably make an enum at the top or something
		
		if (updatedVars.contains("init")) {
			system.addContent("Cultures", Arrays.toString(cultures));
			system.addContent("CulturesPretty", join(cultures, ", "));
		}
		
		if (updatedVars.contains("ResolveCulture")) {
			String[] cults = state.queryProb("ResolveCulture").getBest().toString().trim().split("#");
			Map<Column, String[]> query = new HashMap<Column, String[]>();
			
			query.put(Column.CULTURE, cults);
			
			String[] results = reader.queryDB(Column.CULTURE, query, true, true);
			
			//No results. AskRepeat
			if (results == null || results.length == 0 || results[0] == null || "null".equalsIgnoreCase(results[0])) {
				system.addContent("a_m", "AskRepeatCulture");
			}
			else if (results.length == 1) {
				system.addContent("NameOfCulture", results[0]);
				system.addContent("NameOfCultureStatus", "confirmed");
				system.addContent("a_m", "Ground(NameOfCulture)");
			}
			else {
				//The only time we get here on culture is the one "French or German" and all the "Italian (...)"s
				//In which case, "French", "German", or "Italian" are all fine
				//Sort by length. Ensures we get "French", "German", or "Italian"
				Arrays.sort(results, new Comparator<String>() {
					@Override
					public int compare(String arg0, String arg1) {
						return arg0.length() - arg1.length();
					}
					
				});
				system.addContent("NameOfCulture", results[0]);
				//system.addContent("NameOfCultureStatus", "tentative");
				system.addContent("NameOfCultureStatus", "confirmed");
				system.addContent("a_m", "Ground(NameOfCulture)");
			}
		}
		
		if (updatedVars.contains("GroundCulture")) {
			culture = state.queryProb("GroundCulture").getBest().toString().trim();
			
			attributes.put(Column.CULTURE, new String[]{culture});
			queries.add(Column.CULTURE);
			
			//We're only making these suggestions if title hasn't been filled in yet
			//If title has been filled in, uh, we probably shouldn't be here...
			if (!queries.contains(Column.TITLE)) {
				titles = reader.queryDB(Column.TITLE, attributes, true, true);
				if (titles != null && titles.length <= 5) {
					//Provide list of titles b/c its short
					System.out.println("We hit the title block. Cool");
				}
				//If we're here, title hasn't been set and we have too many title choices
				else if (!queries.contains(Column.ARTIST)) {
					//If we haven't filled in artist yet, we can probably give some artist suggestions
					artists = reader.queryDB(Column.ARTIST, attributes, true, true);
					if (artists != null && titles.length <= 5) {
						//Provide list of artists b/c its short
						System.out.println("We hit the artist block. Cool");
					}
				}
			}
			// Eventually we fall down here.
			String message = "All right, we'll look for " + culture + " paintings, then. There seem to be a lot of these so perhaps ";
			if (!queries.contains(Column.SIZE)) {
				message += "you'd like to narrow down your options by size?";
			}
			else if (!queries.contains(Column.CULTURE)) {
				message += "you'd care to restrict your search to only a particular culture?";
			}
			else if (!queries.contains(Column.STORY)) {
				message += "you're looking for a particular subject or theme for your search?";
			}
			else if (!queries.contains(Column.MEDIUM)) {
				message += "you'd be interested in a particular medium?";
			}
			system.addContent("u_m", message);
		}
		
		if (updatedVars.contains("ResolveSize")) {
			//This one is unfortunately a little less interesting
			String s = state.queryProb("ResolveSize").getBest().toString().trim();
			size = s;
			
			//Ground size with user
			system.addContent("SizeOfArt", s);
			//For now, let's just add no uncertainty and we can add confirmations later. The infrastructure is already there
			system.addContent("SizeOfArtStatus", "confirmed");
			system.addContent("a_m", "Ground(SizeOfArt)");
		}
		
		if (updatedVars.contains("ResolveArtist")) {
			String[] arts = state.queryProb("ResolveArtist").getBest().toString().trim().split("#");
			Map<Column, String[]> query = new HashMap<Column, String[]>();
			query.putAll(attributes);
			query.put(Column.ARTIST, arts);
			
			String[] results = reader.queryDB(Column.ARTIST, query, true, true);
			
			//No results. AskRepeat
			if (results == null || results.length == 0 || results[0] == null || "null".equalsIgnoreCase(results[0])) {
				system.addContent("a_m", "AskRepeatArtist");
			}
			else if (results.length == 1) {
				system.addContent("NameOfArtist", SqliteReader.removeParens(results[0]));
				system.addContent("NameOfArtistStatus", "confirmed");
				system.addContent("a_m", "Ground(NameOfArtist)");
			}
			else {
				system.addContent("NameOfArtist", SqliteReader.removeParens(results[0]));
				system.addContent("NameOfArtistStatus", "tentative");
			}
		}
		
		if (updatedVars.contains("ResolveTitle")) {
			String[] ts = state.queryProb("ResolveTitle").getBest().toString().trim().split("#");
			Map<Column, String[]> query = new HashMap<Column, String[]>();
			query.putAll(attributes);
			query.put(Column.TITLE, ts);
			
			String[] results = reader.queryDB(Column.TITLE, query, true, true);
			
			//Right now it seems 
			
			//No results. AskRepeat
			if (results == null || results.length == 0 || results[0] == null || "null".equalsIgnoreCase(results[0])) {
				system.addContent("a_m", "AskRepeat");
			}
			else if (results.length == 1) {
				system.addContent("TitleOfArtwork", results[0]);
				system.addContent("TitleOfArtworkStatus", "confirmed");
				system.addContent("a_m", "Ground(TitleOfArtwork)");
			}
			else {
				system.addContent("TitleOfArtwork", results[0]);
				system.addContent("TitleOfArtworkStatus", "tentative");
			}
		}
		
		if (updatedVars.contains("GetArtists")) {
			culture = state.queryProb("GetArtists").getBest().toString().trim();
			attributes.put(Column.CULTURE, new String[]{culture});
			artists = reader.queryDB(Column.ARTIST, Column.CULTURE, culture, true, true);
			if (artists != null) {
				artists = SqliteReader.removeDupes(SqliteReader.removeParens(artists));
				
				system.addContent("Artists", Arrays.toString(split(artists, " ")));
				
				system.addContent("u_m", "Here is a list of " + culture + " artists we have: " + join(artists, ", ") + ".");
			}
			else {
				system.addContent("u_m", "Oops, we don't have any works by " + culture + " artists!");
			}
		}
		
		if (updatedVars.contains("GetTitles")) {
			artist = state.queryProb("GetTitles").getBest().toString().trim();
			//Opendial works poorly with double spaces, which our database has plenty of
			attributes.put(Column.ARTIST, artist.split(" "));
			titles = reader.queryDB(Column.TITLE, attributes, true, true);
			
			if (titles != null) {
				system.addContent("Titles", Arrays.toString(split(titles, " ")));
				
				system.addContent("u_m", "Okay then. These are the works we have by " + SqliteReader.removeParens(artist) + ": " + join(titles, "; ") + ".");
			}
			else {
				system.addContent("u_m", "Oops, we don't seem to have any works by that artist!");
			}
		}
		
		if (updatedVars.contains("GetData")) {
			title = state.queryProb("GetData").getBest().toString().trim();
			attributes.put(Column.TITLE, new String[]{title});
			
			//Sanity check
			holder = reader.queryDB(Column.TITLE, attributes, true, false);
			
			if (holder.length == 0 || holder[0] == null || "null".equalsIgnoreCase(holder[0])) {
				//Uh oh no matches...
				system.addContent("u_m", "Oops, something went wrong! We can't seem to find any paintings like that!");
			}
			else {
				if (holder.length > 1){
					System.out.println("Warning. Got multiple matches given filters.");
				}
				title = holder[0];
				system.addContent("u_m", "All right, what would you like to know about " + title + "?");
			}
		}
		
		if (updatedVars.contains("Lookup")) {
			String category = state.queryProb("Lookup").getBest().toString().trim();
			if ("Date".equals(category)) {
				holder = reader.queryDB(Column.DATE, attributes, true, false);
				
				if (holder.length == 0 || holder[0] == null || "null".equalsIgnoreCase(holder[0])) {
					system.addContent("u_m", "Sorry, we currently don't know when this work is dated to.");
				}
				else {
					if (holder.length > 1){
						System.out.println("Warning. Got multiple matches given filters.");
					}
					date = holder[0];
					system.addContent("u_m", "This work is dated to " + date);
				}
			}
			else if ("Size".equals(category)) {
				holder = reader.queryDB(Column.DIM, attributes, true, false);
				
				if (holder.length == 0 || holder[0] == null || "null".equalsIgnoreCase(holder[0])) {
					system.addContent("u_m", "Sorry, we don't have the measurements recorded.");
				}
				else {
					if (holder.length > 1){
						System.out.println("Warning. Got multiple matches given filters.");
					}
					dim = holder[0];
					system.addContent("u_m", "The dimensions of this work are " + dim);
				}
			}
			else if ("Story".equals(category)) {
				holder = reader.queryDB(Column.STORY, attributes, true, false);
				
				if (holder.length == 0 || holder[0] == null || "null".equalsIgnoreCase(holder[0])) {
					system.addContent("u_m", "Sorry, we don't have any details about the story of the work.");
				}
				else {
					if (holder.length > 1){
						System.out.println("Warning. Got multiple matches given filters.");
					}
					story = holder[0];
					system.addContent("u_m", story);
				}
			}
			else if ("Medium".equals(category)) {
				holder = reader.queryDB(Column.MEDIUM, attributes, true, false);
				
				if (holder.length == 0 || holder[0] == null || "null".equalsIgnoreCase(holder[0])) {
					system.addContent("u_m", "Sorry, we don't have any details about the medium of the work.");
				}
				else {
					if (holder.length > 1){
						System.out.println("Warning. Got multiple matches given filters.");
					}
					medium = holder[0];
					system.addContent("u_m", "This work was created using " + medium);
				}
			}
		}
		
		if (updatedVars.contains("GoBack")) {
			String curStep = state.queryProb("GoBack").getBest().toString().trim();
			
			if ("Explain".equalsIgnoreCase(curStep)) {
				attributes.remove(Column.TITLE);
				system.addContent("current_step", "TitleOfArtwork");
				system.addContent("TitleOfArtwork", "None");
				system.addContent("TitleOfArtworkState", "empty");
				system.addContent("u_m", "Okay, feel free to pick another piece to investigate: " + join(titles, "; ") + ".");
			}
			else if ("TitleOfArtwork".equalsIgnoreCase(curStep)) {
				attributes.remove(Column.ARTIST);
				system.addContent("current_step", "NameOfArtist");
				system.addContent("NameOfArtist", "None");
				system.addContent("NameOfArtistStatus", "empty");
				system.addContent("u_m", "Well then. Any other artists you'd like to look at? In case you forgot, here's a list of "
						+ culture + " ones: " + join(artists, ", ") + ".");
			}
			else if ("NameOfArtist".equalsIgnoreCase(curStep)) {
				attributes.remove(Column.CULTURE);
				system.addContent("current_step", "NameOfCulture");
				system.addContent("NameOfCulture", "None");
				system.addContent("NameOfCultureStatus", "empty");
				system.addContent("u_m", "So which cultures do you want to explore? You can choose " + join(cultures, ", ") + ".");
			}
			else {
				system.addContent("u_m", "Sorry. We're at the farthest back we can go. We can only go forward form here!");
			}
		}
		
	}

}
