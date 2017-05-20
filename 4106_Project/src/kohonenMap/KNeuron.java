package kohonenMap;

import java.util.ArrayList;
import java.util.Random;

public class KNeuron {

	double[] features;
	int theClass;
	String stringClass;
	KNeuron parent;
	ArrayList<KNeuron> children;
	
	public KNeuron(KNeuron par){
		parent = par;
		children = new ArrayList<>();
		features = new double[KMap.NUMFEATURES];
		long seed = System.nanoTime();
		Random rand = new Random(seed);
		for(double d: features){			
			d = rand.nextFloat() * KMap.FEATURESCALE;
		}
	}
	
	public void addChild(KNeuron c){
		children.add(c);
	}
	
	public void clearChildren(){children.clear();}
	
	@Override
	public String toString(){
		String ret = "Neuron || Class: " + theClass + "||";
		for(int i = 0; i < features.length; ++i) ret += String.format("[%2.2f]", features[i]);
		ret += " Children: " + children.size();
		return ret;
	}
	
}
