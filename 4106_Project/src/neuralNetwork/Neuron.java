package neuralNetwork;

import java.util.*;
import java.io.*;

public class Neuron {
	public double output;		
	public double weights[];		
	public double threshold;	
	public double deltaWt[];	
	public double deltaThreshold;	
	public double error;	
	
	public Neuron (int neurons) {
		weights = new double[neurons];		
		deltaWt = new double[neurons];	
		reset();				
	}

	public void reset() {
		long seed = System.nanoTime();
		Random rand = new Random(seed);		
		threshold = -1 + 2 * rand.nextDouble();	    	
		deltaThreshold = 0;				

     	for(int i = 0; i < weights.length; i++) {
			weights[i]= -1 + 2 * rand.nextDouble();	
			deltaWt[i] = 0;			
		}
	}

	public double[] get_weights() { return weights; }
	public double get_output() { return output; }
};