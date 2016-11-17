package cs544;
import java.util.Arrays;
import java.util.Collection;

import cs544.SqliteReader.Column;

import opendial.DialogueState;
import opendial.DialogueSystem;
import opendial.modules.Module;


public class DBModule implements Module{

	boolean paused = false;
	DialogueSystem system;
	SqliteReader reader;
	String[] cultures;
	
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
		
		cultures = reader.getCultures();
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
		if (updatedVars.contains("a_u")) {
			String action = state.queryProb("a_u").getBest().toString();
			System.out.println(action);
			if (action.contains("Reroute")) {
				//System.out.println(action);
				//system.addContent("X", "third");
				//system.addContent("Y", cultures[0]);
				String floor = state.queryProb("floor").getBest().toString();
				system.addContent("a_m", "GoTo(" + floor + ")");
			}
		}
		
		if (updatedVars.contains("NameOfCulture")) {
			String culture = state.queryProb("NameOfCulture").getBest().toString().trim();
			culture = culture.toLowerCase();
			culture = culture.substring(0, 1).toUpperCase() + culture.substring(1);
			String[] artists = reader.getArtist(Column.CULTURE.key, culture);
			system.addContent("Artists", Arrays.toString(artists));
			String output = culture + ", eh? Well here's a list of artists:\n";
			
			for (String artist : artists) {
				if (artist.indexOf('(') != -1) {
					output += artist.substring(0, artist.indexOf('(')).trim() + ", ";
				}
				else {
					output += artist.trim();
				}
			}
			
			system.addContent("u_m", output);
		}
		
	}

}
