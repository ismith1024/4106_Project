package neuralNetwork;

import java.util.*;
import java.io.*;

public class NeuronLayer {
	
	static final double e = 2.7182818284;
	
	public double[] inputs;		
	public Neuron[]	neurons;
	
	public NeuronLayer (int neur, int inp) {
		neurons = new Neuron[neur];

		for (int i = 0; i < neur; i++)
			neurons[i] = new Neuron(inp);

		inputs = new double[inp];
	}

	public void forwardPropagate() {
		double nn;
		
		for (int i = 0; i < neurons.length; i++) {
			nn = neurons[i].threshold;

			for (int j = 0; j < neurons[i].weights.length; j++)
				nn = nn + inputs[j] * neurons[i].weights[j];

			neurons[i].output = sigmoid(nn);
		}
	}

	public static double sigmoid (double x) {
		return 1.0 / (1.0 + Math.pow(e, -1 * x));
	}
	
	public static double sigmoidPrime(double x){
		return sigmoid(x) * (1.0 - sigmoid(x));
	}
	
	public double[] getOutput() {
		double[] ret = new double[neurons.length];
		for (int i=0; i < neurons.length; i++) ret[i] = neurons[i].output;
		return (ret);
	}

};
