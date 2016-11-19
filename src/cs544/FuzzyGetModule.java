package cs544;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import opendial.DialogueState;
import opendial.DialogueSystem;
import opendial.modules.Module;

/**
 * FuzzyGet Module. Called by "FuzzyGet = X # Y # ACTION"
 * Will ultimately set a_u = ACTION({X Union Y})
 * The union elements will be separated by "#"
 * @author Tobias Lee
 *
 */
public class FuzzyGetModule implements Module{

	boolean paused = false;
	DialogueSystem system;
	
	@Override
	public boolean isRunning() {
		return paused;
	}

	@Override
	public void pause(boolean arg0) {
		paused = arg0;
	}

	@Override
	public void start() {
		//No need to do anything here
	}

	public FuzzyGetModule(DialogueSystem system) {
		this.system = system;
	}

	@Override
	public void trigger(DialogueState state, Collection<String> updatedVars) {
		if (updatedVars.contains("FuzzyGet")) {
			String action = state.queryProb("FuzzyGet").getBest().toString();
			String[] args = action.substring(1, action.length()-1).split(" # ");
			if (args.length != 3) {
				//We got a bad call
				return;
			}
			String u_u = args[0], filter = args[1], act = args[2];
			
			//Filter is of the form [X, Y] aka Arrays.toString form
			String[] fils = filter.substring(1, filter.length()-1).split(", ");
			
			//Remove all .'s and ,'s
			for (int i = 0; i < fils.length; ++i) {
				fils[i] = fils[i].replaceAll("(,|\\.|\\?|!|\\[|\\])", "");
			}
			
			Set<String> tokens = new HashSet<String>();
			
			//Checks if there is a single matching token
			for (String token : u_u.split(" ")) {
				String tok = token.replaceAll("(,|\\.|\\?|!|\\[|\\])", "");
				for (String fil : fils) {
					if (fil.equalsIgnoreCase(tok)) {
						tokens.add(tok);
					}
				}
			}
			
			String[] toks = new String[tokens.size()];
			
			int i = 0;
			for (String token : tokens) {
				toks[i++] = token;
			}
			
			//Set a_u = ACTION(a|b|c)
			system.addContent("a_u", act + "(" + DBModule.join(toks, "#") + ")");
		}
	}

}
