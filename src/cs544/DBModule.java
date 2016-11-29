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
	String[] cultures, artists, media, dims, titles, holder, keywords;
	String culture, artist, medium, size, dim, title, date, story;
	Map<Column, String[]> attributes;
	ArrayList<Column> queries;
	boolean debug = true;
	
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
		artists = reader.getAll(Column.ARTIST, true);
		titles = reader.getAll(Column.TITLE, false);
		media = reader.getAll(Column.MEDIUM, true);
		keywords = reader.getAll(Column.KEYWORDS, true);
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
	 * Also removes duplicates
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
	
	/**
	 * From any grounding, give suggestions for the next thing to look into
	 * @param state DialogueState to provide system content. Perhaps unnecessary if all I do is add u_m
	 * @param bootleg Sad bootleg attempt to make things easier on myself for changing the message for rejections
	 * @return true if we got any results, false otherwise
	 */
	private boolean nextStep(DialogueState state, boolean bootleg) {
		//We're only making these suggestions if title hasn't been filled in yet
		//If title has been filled in, uh, we probably shouldn't be here...
		//I wonder if it'd be better to say artists first even if only very few titles...
		if (!queries.contains(Column.TITLE) && attributes.size() != 0) {
			titles = reader.queryDB(Column.TITLE, attributes, true, true);
			//Running list of titles
			
			if (titles != null) {
				system.addContent("Titles", Arrays.toString(split(titles, " ")));
			}
			if (titles == null || titles.length == 0) {
				system.addContent("u_m", "Oh dear, it seems we don't have any paintings that fit your restrictions.");
				return false;
			}
			else if (titles.length == 1) {
				//Only one option, so we'll force it.
				system.addContent("u_m", "Only one title matches your restrictions: " + titles[0]);
				system.addContent("u_m", "So let's just jump right into it.");
				system.addContent("TitleOfArtwork", titles[0]);
				system.addContent("TitleOfArtworkStatus", "confirmed");
				system.addContent("a_m", "Ground(TitleOfArtwork)");
				return true;
			}
			else if (titles.length <= 5 || queries.size() >= 5) {
				//Provide list of titles b/c its short
				//Alternatively, out of things to query for
				system.addContent("u_m", "Okay, here is a list of paintings that fit your criteria: " + join(titles, "; "));
				system.addContent("u_m", "Any of these titles pique your interest?");
				system.addContent("current_step", "ChooseTitle");
				system.addContent("TitlesPretty", join(titles, "; "));
				system.addContent("current_prompt", "TitleOfArtwork");
				return true;
			}
			//If we're here, title hasn't been set and we have too many title choices
			else if (!queries.contains(Column.ARTIST)) {
				//If we haven't filled in artist yet, we can probably give some artist suggestions
				artists = reader.queryDB(Column.ARTIST, attributes, true, true);
				//Running list of artists
				//I should just make a function to get a dupeless, parenless artists
				if (artists != null) {
					system.addContent("Artists", Arrays.toString(split(SqliteReader.removeDupesAndParens(artists), " ")));
				}
				if (artists != null && artists.length <= 5 || queries.size() >= 3) {
					//Provide list of artists b/c its short
					system.addContent("u_m", "All right, here are some artists that fit your criteria: " + join(SqliteReader.removeDupesAndParens(artists), "; "));
					system.addContent("u_m", "Is there any artist in particular that you're interested in?");
					system.addContent("ArtistsPretty", join(SqliteReader.removeParens(artists), "; "));
					system.addContent("current_prompt", "NameOfArtist");
					return true;
				}
			}
		}
		//We fall down here if nothing else worked	
		
		String message = "Hmm... There seem to be a lot of paintings meeting your criteria so perhaps ";
		if (bootleg) {
			message = "Okay then. Well there are still other ways to limit your search, so perhaps ";
		}
		String message2 = "";
		//Might be worth also putting a list of options for each suggested choice
		if (!queries.contains(Column.KEYWORDS)) {
			message += "you're looking for a particular subject or theme for your search?";
			message2 = "Some examples would include \"Christianity\", or \"Impressionism\"";
			system.addContent("current_prompt", "ChooseKeywords");
		}
		else if (!queries.contains(Column.CULTURE)) {
			message += "you'd care to restrict your search to only a particular culture?";
			cultures = reader.queryDB(Column.CULTURE, attributes, true, true);
			if (cultures != null) {
				message2 = "The cultures available to choose from are: " + join(SqliteReader.removeDupesAndParens(cultures), ", ");
				system.addContent("CulturesPretty", join(cultures, ", "));
			}
			system.addContent("current_prompt", "NameOfCulture");
		}
		else if (!queries.contains(Column.SIZE)) {
			message += "you'd like to narrow down your options by size?";
			message2 = "Currently, our selection is divided into big, medium, and small paintings";
			system.addContent("current_prompt", "SizeOfArt");
		}
		else if (!queries.contains(Column.MEDIUM)) {
			message += "you'd be interested in a particular medium?";
			media = reader.queryDB(Column.MEDIUM, attributes, true, true);
			if (media != null) {
				message2 = "The media represented here are: " + join(media, ", ");
				system.addContent("MediaPretty", join(media, ", "));
			}
			system.addContent("current_prompt", "NameOfMedium");
		}
		else {
			//This only happens if we have no queries because a single query will be caught higher up
			system.addContent("u_m", "I see how it is. If you don't want to play along, then fine. I'm leaving.");
			system.addContent("current_step", "Exit");
			return true;
		}
		system.addContent("u_m", message);
		if (!"".equals(message2)) {
			system.addContent("u_m", message2);
		}
		return true;
	}

	@Override
	public void trigger(DialogueState state, Collection<String> updatedVars) {
		if (debug) {
			for (String s : updatedVars) {
				System.out.print(s);
				System.out.print(" ");
				System.out.print(state.queryProb(s).getBest().toString());
				System.out.print(" ");
			}
			System.out.println();
		}
		
		//Note to self: Not a big fan of all these strings.
		//Should probably make an enum at the top or something
		
		if (updatedVars.contains("init")) {
			system.addContent("Cultures", Arrays.toString(cultures));
			system.addContent("CulturesPretty", join(cultures, ", "));
			
			system.addContent("Titles", Arrays.toString(split(titles, " ")));
			system.addContent("TitlesPretty", join(titles, "; "));
			system.addContent("Artists", Arrays.toString(split(SqliteReader.removeDupesAndParens(artists), " ")));
			system.addContent("ArtistsPretty", join(SqliteReader.removeParens(artists), "; "));
			system.addContent("Media", Arrays.toString(split(media, " ")));
			system.addContent("MediaPretty", join(media, ", "));
			system.addContent("Keywords", Arrays.toString(split(SqliteReader.massKeywordsToArray(keywords), " ")));
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
				system.addContent("a_m", "Ground(NameOfCulture,"+results[0]+")");
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
				system.addContent("a_m", "Ground(NameOfCulture,"+results[0]+")");
			}
		}
		if (updatedVars.contains("GroundNameOfCulture")) {
			culture = state.queryProb("GroundNameOfCulture").getBest().toString().trim();
			
			attributes.put(Column.CULTURE, new String[]{culture});
			//If we're replacing, it'll replace automatically with maps, but not with arraylists
			queries.remove(Column.CULTURE);
			queries.add(Column.CULTURE);
			
			//Ground with user and ask for next step
			system.addContent("u_m", "All right, then let's look at " + culture + " paintings.");
			if (!nextStep(state, false)) {
				system.addContent("u_m", "Let's just go back a bit and stop looking for " + culture + " paintings in particular.");
				attributes.remove(Column.CULTURE);
				queries.remove(Column.CULTURE);
			}
		}
		
		if (updatedVars.contains("ResolveSize")) {
			//This one is unfortunately a little less interesting
			String s = state.queryProb("ResolveSize").getBest().toString().trim();
			size = s;
			
			//Ground size with user
			system.addContent("SizeOfArt", s);
			//For now, let's just add no uncertainty and we can add confirmations later. The infrastructure is already there
			system.addContent("SizeOfArtStatus", "confirmed");
			system.addContent("a_m", "Ground(SizeOfArt,"+size+")");
		}
		if (updatedVars.contains("GroundSizeOfArt")) {
			size = state.queryProb("GroundSizeOfArt").getBest().toString().trim();
			
			attributes.put(Column.SIZE, new String[]{size});
			queries.remove(Column.SIZE);
			queries.add(Column.SIZE);

			//Ground with user and ask for next step
			system.addContent("u_m", size + " paintings? Sounds good.");
			if (!nextStep(state, false)) {
				system.addContent("u_m", "We'll remove this size filter, so maybe pick a different size you're interested in.");
				attributes.remove(Column.SIZE);
				queries.remove(Column.SIZE);
			}
		}
		
		if (updatedVars.contains("ResolveArtist")) {
			//To be honest, this is going to be iffy. I'll need a running list of artists/titles throughout the process
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
				//Let's just always be tentative with artist for fun
				system.addContent("NameOfArtistStatus", "tentative");
				//system.addContent("NameOfArtistStatus", "confirmed");
				//system.addContent("a_m", "Ground(NameOfArtist)");
			}
			else {
				system.addContent("NameOfArtist", SqliteReader.removeParens(results[0]));
				system.addContent("NameOfArtistStatus", "tentative");
			}
		}
		if (updatedVars.contains("GroundNameOfArtist")) {
			//tbh I'm pretty upset at the amount of redundancy in these if blocks but there's not really an elegant solution
			//Well there is, but it's more trouble than it's worth.
			artist = state.queryProb("GroundNameOfArtist").getBest().toString().trim();
			attributes.put(Column.ARTIST, new String[]{artist});
			queries.remove(Column.ARTIST);
			queries.add(Column.ARTIST);
			
			system.addContent("u_m", "Okay, we'll get you paintings by " + SqliteReader.removeParens(artist));
			//nextStep will either prompt for title or warn that there are no works
			//the latter should never happen because an artist with no matching works won't be an option
			nextStep(state, false);
		}
		
		if (updatedVars.contains("ResolveMedium")) {
			String[] m = state.queryProb("ResolveMedium").getBest().toString().trim().split("#");
			Map<Column, String[]> query = new HashMap<Column, String[]>();
			query.putAll(attributes);
			query.put(Column.MEDIUM, m);
			
			String[] results = reader.queryDB(Column.MEDIUM, query, true, true);
			
			//No results. AskRepeat
			if (results == null || results.length == 0 || results[0] == null || "null".equalsIgnoreCase(results[0])) {
				system.addContent("a_m", "AskRepeatMedium");
			}
			else {
				//Medium is a bit difficult because of "oil" or "canvas" or "panel" matching multiple results
				//This is actually the exact same problem as story so if I solve this I solve story
				//So it's simple enough to manage the query, but I need to ground it with the user in some
				//way that doesn't look horrible
				media = m;
				
				//Confirmation message is difficult for this, so we'll manage it here in code instead of in the nlg
				String on = "";
				for (String med : media) {
					if (reader.queryDB(Column.MEDIUM, Column.MEDIUM, "on " + med, true, true) != null) {
						on = med;
						break;
					}
				}
				
				boolean first = true;
				String confMessage = "So you're looking for ";
				
				for (String med : media) {
					if (!on.equals(med)) {
						if (first) {
							confMessage += "works painted with " + med;
							first = false;
						}
						else {
							confMessage += ", " + med;
						}
					}
				}
				//No results other than on
				if (first) {
					confMessage += "a painting done on " + on;
				}
				else if (!on.equals("")) {
					confMessage += " on " + on;
				}
				system.addContent("u_m", confMessage + ". Is that correct?");
				system.addContent("NameOfMedium", "FILLER");
				system.addContent("NameOfMediumStatus", "tentative");
			}
		}
		if (updatedVars.contains("GroundNameOfMedium")) {
			//Media set from resolve
			//media = media
			attributes.put(Column.MEDIUM, media);
			queries.remove(Column.MEDIUM);
			queries.add(Column.MEDIUM);
			
			system.addContent("u_m", "Okay, we'll look for paintings like that.");
			if (!nextStep(state, false)) {
				system.addContent("u_m", "Maybe you'll find more success with another medium.");
				attributes.remove(Column.MEDIUM);
				queries.remove(Column.MEDIUM);
			}
		}
		
		if (updatedVars.contains("ResolveKeywords")) {
			String[] ks = state.queryProb("ResolveKeywords").getBest().toString().trim().split("#");
			Map<Column, String[]> query = new HashMap<Column, String[]>();
			query.putAll(attributes);
			query.put(Column.KEYWORDS, ks);
			
			String[] results = reader.queryDB(Column.KEYWORDS, query, true, true);
			
			//No results. AskRepeat
			if (results == null || results.length == 0 || results[0] == null || "null".equalsIgnoreCase(results[0])) {
				system.addContent("a_m", "AskRepeatKeywords");
			}
			else {
				keywords = ks;
				
				String confMessage = "There were a few key words I picked up there that hold relevance with the selection we have on display."
						+ " Here are the terms I'll restrict by: " + join(keywords, "; ");
				
				system.addContent("u_m", confMessage + ". Is that correct?");
				system.addContent("ChooseKeywords", "FILLER");
				system.addContent("ChooseKeywordsStatus", "tentative");
				//system.addContent("a_m", "Ground(ChooseKeywords, FILLER)");
			}
		}
		if (updatedVars.contains("GroundChooseKeywords")) {
			attributes.put(Column.KEYWORDS, keywords);
			queries.remove(Column.KEYWORDS);
			queries.add(Column.KEYWORDS);
			
			system.addContent("u_m", "Okay, we'll look for paintings like that.");
			if (!nextStep(state, false)) {
				system.addContent("u_m", "How about you suggest some ideas for what other topics you'd like to see");
				attributes.remove(Column.KEYWORDS);
				queries.remove(Column.KEYWORDS);
			}
		}
		
		if (updatedVars.contains("ResolveTitle")) {
			String[] ts = state.queryProb("ResolveTitle").getBest().toString().trim().split("#");
			Map<Column, String[]> query = new HashMap<Column, String[]>();
			query.putAll(attributes);
			query.put(Column.TITLE, ts);
			
			String[] results = reader.queryDB(Column.TITLE, query, true, true);
			
			//Right now it seems - WHAT DOES IT SEEM? WHY DID I CUT OFF THIS COMMENT MIDSENTENCE? WHAT STILL NEEDS WORK HERE?
			
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
		if (updatedVars.contains("GroundTitleOfArtwork")) {
			//wew
			system.addContent("a_m", "Ground(TitleOfArtwork)");
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
			else if ("Artist".equals(category)) {
				holder = reader.queryDB(Column.ARTIST, attributes, true, false);
				
				if (holder.length == 0 || holder[0] == null || "null".equalsIgnoreCase(holder[0])) {
					system.addContent("u_m", "Sorry, we don't have any details about the artist of the work.");
				}
				else {
					if (holder.length > 1){
						System.out.println("Warning. Got multiple matches given filters.");
					}
					artist = holder[0];
					system.addContent("u_m", "The creator of this work is " + artist);
				}
			}
			system.addContent("u_m", "Is there anything else you'd like to know about this piece?");
		}
		
		//This needs to be updated. Pretty much it should just be popping the last query from the stack
		//and if we're in explaining go back to slotfilling
		if (updatedVars.contains("GoBack")) {
			String curStep = state.queryProb("GoBack").getBest().toString().trim();
			
			if ("Explain".equalsIgnoreCase(curStep)) {
				attributes.remove(Column.TITLE);
				system.addContent("current_step", "ChooseTitle");
				system.addContent("TitleOfArtwork", "None");
				system.addContent("TitleOfArtworkState", "empty");
				system.addContent("u_m", "Okay, feel free to pick another piece to investigate: " + join(titles, "; ") + ".");
			}
			else if (queries.size() >= 1) {
				Column c = queries.remove(queries.size()-1);
				attributes.remove(c);
				
				String current_step = "";
				String message = "All right, we'll just retract your previous statement, then.";
				
				switch (c) {
				case ARTIST: current_step = "NameOfArtist"; message += " Got any other artists you're interested in?"; break;
				case CULTURE: current_step = "NameOfCulture"; message += " Any other cultures you'd like to try?"; break;
				case SIZE: current_step = "SizeOfArt"; message += " Are you prehaps interested in another size of painting?"; break;
				case MEDIUM: current_step = "NameOfMedium"; message += " Perhaps a different medium would interest you?"; break;
				case KEYWORDS: current_step = "ChooseKeywords"; message += " What other themes would you like to look for?"; break;
				//Default should never happen so let's just assume it's title
				default: current_step = "TitleOfArtwork"; break;
				}
				
				system.addContent("current_step", current_step);
				system.addContent("current_prompt", current_step);
				system.addContent(current_step, "None");
				system.addContent(current_step + "Status", "empty");
				system.addContent("u_m", message);
			}
			else {
				//Probably should just end at this point and essentially quit out (stop accepting input)
				//Maybe ask for a quitting confirmation?
				//Maybe we should also give the option to do so at an earlier point
				system.addContent("u_m", "Sorry. We're at the farthest back we can go. We can only go forward from here!");
			}
		}
		
		if (updatedVars.contains("CycleOptions")) {
			//We give suggestions to the user and they can reject the suggestions and ask for new suggestions
			String prompt = state.queryProb("CycleOptions").getBest().toString().trim();
			
			if ("NameOfCulture".equals(prompt)) {
				queries.add(Column.CULTURE);
			}
			else if ("ChooseKeywords".equals(prompt)) {
				queries.add(Column.KEYWORDS);
			}
			else if ("SizeOfArt".equals(prompt)) {
				queries.add(Column.SIZE);
			}
			else if ("NameOfMedium".equals(prompt)) {
				queries.add(Column.MEDIUM);
			}
			else if ("NameOfArtist".equals(prompt)) {
				queries.add(Column.ARTIST);
			}
			
			nextStep(state, true);
		}
		
	}

}
