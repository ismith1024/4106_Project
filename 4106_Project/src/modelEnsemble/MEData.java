package modelEnsemble;

public class MEData {
	public int theClass;
	public int classifierGuess;
	public int[] features;
	
	public MEData(String guess1, String guess2, String guess3, String guess4, String actualClass){
		switch(actualClass){
		case "w1": theClass = 1; break;
		case "w2": theClass = 2; break;
		case "w3": theClass = 3; break;
		case "w4": theClass = 4; break;		
		}

		features = new int[16];
		for(int i = 0; i < 16; ++i) features[i] = 0;
		
		switch(guess1){
		case "w1": features[0] = 1; break;
		case "w2": features[1] = 1; break;
		case "w3": features[2] = 1; break;
		case "w4": features[3] = 1; break;		
		}
		
		switch(guess2){
		case "w1": features[4] = 1; break;
		case "w2": features[5] = 1; break;
		case "w3": features[6] = 1; break;
		case "w4": features[7] = 1; break;		
		}
		
		switch(guess3){
		case "w1": features[8] = 1; break;
		case "w2": features[9] = 1; break;
		case "w3": features[10] = 1; break;
		case "w4": features[11] = 1; break;		
		}
		
		switch(guess4){
		case "w1": features[12] = 1; break;
		case "w2": features[13] = 1; break;
		case "w3": features[14] = 1; break;
		case "w4": features[15] = 1; break;		
		}
	
	}
	
	public MEData(int c){
		theClass = c;
		classifierGuess = 0;
		features = new int[16];

	}
	
	@Override
	public String toString(){
		return String.format("ADSamplePoint: Class - %d Guess - %d Features:[%d][%d][%d][%d][%d][%d][%d][%d][%d][%d][%d][%d][%d][%d][%d][%d] ", theClass, classifierGuess, features[0], features[1],features[2],features[3],features[4],features[5],features[6],features[7],features[8],features[9], features[10], features[11], features[12], features[13], features[14], features[15]);
	}
	

}
