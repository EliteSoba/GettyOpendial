package cs544;

import java.util.List;
import java.util.function.Function;

import opendial.bn.values.Value;
import opendial.bn.values.ValueFactory;

/**
 * Takes the u_u and a list of valid filter tokens
 * and determines which tokens are relevant. If there are relevant tokens,
 * then this returns 1.0. Else it'll return 0.0
 * @author Tobias Lee
 *
 */
public class FuzzyMatch implements Function<List<String>, Value>{

	/**
	 * arg0[0] is u_u
	 * arg0[1] is list of filter tokens
	 */
	@Override
	public Value apply(List<String> arg0) {
		String utterance = arg0.get(0);
		String filter = arg0.get(1);
		
		String[] fils = filter.substring(1, filter.length()-1).split(", ");
		for (int i = 0; i < fils.length; ++i) {
			fils[i] = fils[i].replaceAll("(,|\\.|\\?|!|\\[|\\])", "");
		}
		
		//Checks if there is a single matching token
		for (String token : utterance.split(" ")) {
			String tok = token.replaceAll("(,|\\.|\\?|!|\\[|\\])", "");
			for (String fil : fils) {
				if (fil.equalsIgnoreCase(tok)) {
					return ValueFactory.create(1.0);
				}
			}
		}
		
		return ValueFactory.create(0.0);
	}

}
