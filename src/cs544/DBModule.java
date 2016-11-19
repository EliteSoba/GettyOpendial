package cs544;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
	Map<Column, String> attributes;
	
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
		reader = new SqliteReader("paintings.db");
		
		cultures = reader.getAll(Column.CULTURE, true);
		attributes = new HashMap<Column, String>();
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
		
		if (updatedVars.contains("GetArtists")) {
			culture = state.queryProb("GetArtists").getBest().toString().trim();
			attributes.put(Column.CULTURE, culture);
			artists = reader.getArtist(Column.CULTURE, culture);
			artists = SqliteReader.removeDupes(SqliteReader.removeParens(artists));
			
			system.addContent("Artists", Arrays.toString(artists));
			
			system.addContent("u_m", "Here is a list of " + culture + " artists we have: " + join(artists, ", "));
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
