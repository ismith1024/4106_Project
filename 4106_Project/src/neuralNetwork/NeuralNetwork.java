package neuralNetwork;

import java.util.*;

import featureEngineering.DailyData;

import java.io.*;
import java.sql.Connection;
import java.sql.Statement;

public class NeuralNetwork{
	
	////////////////////////
	//   Instructions:
	// - Set the TICKER_TO_EVAL manually
	// - Set the FIVEFOLD_SET manually
	// - For fundamental analysis, comment out the economic method in main() and vice versa
	
	//////////////// PARAMETERS - can be tweaked based on nature of problem
	static double LEARNING_RATE = 0.01;
	static double MOMENTUM = 0.01;
	static double MIN_ERROR = 0.5;
	static double TRAINING_STEPS = 555;
	static int NUMBER_LAYERS = 3;
	static final int HIDDEN_LAYER_SIZE = 16;
	
	static int NUMFEATURES;
	static String TICKER_TO_EVAL = "CP";
	
	static final int FIVEFOLD_SET = 4;
	
	NeuronLayer outputLayer;
	NeuronLayer hiddenLayer;
	NeuronLayer inputLayer;

	double totError;
	double[][] trainingOutputs;
	double[][] trainingInputs;
	int	numSamples;
	int	sample;
	double[][] nnOutputs;
	
	
	//Creates a three-layer neural network given the neuron counts
	public NeuralNetwork(int[] numNeurons, double[][] dataIn, double[][] dataOut) {

		numSamples = dataIn.length;
		
		// create neuron layers
		inputLayer = new NeuronLayer(numNeurons[0], numNeurons[0]);
		hiddenLayer = new NeuronLayer(numNeurons[1], numNeurons[0]);
		outputLayer = new NeuronLayer(numNeurons[2], numNeurons[1]);
		
		trainingInputs = new double[numSamples][inputLayer.neurons.length];
		trainingOutputs = new double[numSamples][outputLayer.neurons.length];
		nnOutputs = new double[numSamples][outputLayer.neurons.length];

		// Assign input set
		for (int i = 0; i < numSamples; i++) for (int j = 0; j < inputLayer.neurons.length; j++){
			trainingInputs[i][j] = dataIn[i][j];
		}

		// Assign output set
		for (int i = 0; i < numSamples; i++) for (int j = 0; j < outputLayer.neurons.length; j++){
			trainingOutputs[i][j] = dataOut[i][j];
		}
	}

	/////// Control methods
	public static void main(String[] args){
		/////////////////////////////////////////
		//comment out the section not being used
		String ticker = TICKER_TO_EVAL;
		classifyFundamental(ticker);
		//classifyEconomic(ticker);
	}
	
	///////// Training and Testing methods
	
	//classifies a dataset for fundamental analysis
	public static void classifyFundamental(String tick){
		NUMFEATURES = 7;
		Dataset ds = new Dataset();
		ds.setupDB();
		System.out.println("Get fundamental data");
		ds.getFundamentalData();
		System.out.println("Get economic data");
		ds.getEconomicData();
		System.out.println("Get benchmark data");
		ds.getBenchmarkData();
		ds.normalize();
		
		ArrayList<DailyData> training = new ArrayList<>();
		ArrayList<DailyData> testing = new ArrayList<>();
		
		//////Classify on fundamentals
		int j = 0;
		for(DailyData d: ds.dailyData){
			if(d.volume.get(tick) != null && d.volume.get(tick) != 0.0 && d.classes.get(tick) != null){
				if(j % 5 == FIVEFOLD_SET) testing.add(d); 
				else training.add(d);
				j++;
			}
		}
		
		double[][] inputSet = new double[training.size()][NUMFEATURES];
		double[][] targetSet = new double[training.size()][1];
		for(int i = 0; i < training.size(); ++i){
			DailyData dat = training.get(i);
			inputSet[i][0] = dat.p_e.get(tick);
			inputSet[i][1] = dat.div_yld.get(tick);
			inputSet[i][2] = dat.ma200.get(tick);
			inputSet[i][3] = dat.ma20.get(tick);
			inputSet[i][4] = dat.beta.get(tick);
			inputSet[i][5] = dat.volume.get(tick);
			inputSet[i][6] = dat.relStr.get(tick);
			
			switch(dat.classes.get(tick)){
			case "w1": 
				targetSet[i][0] = 0.125; 
				break;
			case "w2": 
				targetSet[i][0] = 0.275; 
				break;
			case "w3": 
				targetSet[i][0] = 0.625; 
				break;
			case "w4": 
				targetSet[i][0] = 0.875; 
				break;
			}
		}
		
		int[] layers = {NUMFEATURES, HIDDEN_LAYER_SIZE, 1};
		
		NeuralNetwork net = new NeuralNetwork(layers, inputSet, targetSet);
		net.train();
		
		int[][] confusionMatrix = new int[4][4];
		
		for(int i = 0; i < testing.size(); ++i){
			double[] testingData = new double[NUMFEATURES];
			DailyData dat = testing.get(i);
			testingData[0] = dat.p_e.get(tick);
			testingData[1] = dat.div_yld.get(tick);
			testingData[2] = dat.ma200.get(tick);
			testingData[3] = dat.ma20.get(tick);
			testingData[4] = dat.beta.get(tick);
			testingData[5] = dat.volume.get(tick);
			testingData[6] = dat.relStr.get(tick);
			
			double result = net.test(testingData);
			
			if(dat.classes.get(tick) != null){
				String res = "";
				if(result < 0.25) res = "w1";
				else if(result < 0.5) res = "w2";
				else if(result < 0.75) res = "w3";
				else res = "w4";
				
				ds.writeFundToDB(dat.year, dat.month, dat.day, tick, dat.classes.get(tick), res);
			
			switch(dat.classes.get(tick)){
				
			
				case "w1":
					if(result < 0.25) confusionMatrix[0][0]++;
					else if(result < 0.5) confusionMatrix[0][1]++;
					else if(result < 0.75) confusionMatrix[0][2]++;
					else confusionMatrix[0][3]++;
					break;
				case "w2":
					if(result < 0.25) confusionMatrix[1][0]++;
					else if(result < 0.5) confusionMatrix[1][1]++;
					else if(result < 0.75) confusionMatrix[1][2]++;
					else confusionMatrix[1][3]++;
					break;
				case "w3":
					if(result < 0.25) confusionMatrix[2][0]++;
					else if(result < 0.5) confusionMatrix[2][1]++;
					else if(result < 0.75) confusionMatrix[2][2]++;
					else confusionMatrix[2][3]++;
					break;
				case "w4":
					if(result < 0.25) confusionMatrix[3][0]++;
					else if(result < 0.5) confusionMatrix[3][1]++;
					else if(result < 0.75) confusionMatrix[3][2]++;
					else confusionMatrix[3][3]++;
					break;
				}
			}
			
			
			//System.out.println("Testing sample: known class -- " + d.classes.get(tick) + " Classifies as: " + result);
		}
		
		System.out.println("Confusion Matrix\n.................\n");
		for(int i = 0; i < 4; ++i){
			System.out.print("| ");
			for(int k = 0; k < 4; ++k){
				System.out.print(confusionMatrix[i][k] + " |");
			}
			System.out.println();
		}
		
	}
	
	//classifies a dataset for economic analysis
	public static void classifyEconomic(String tick){
		Dataset ds = new Dataset();
		ds.setupDB();
		System.out.println("Get fundamental data");
		ds.getFundamentalData();
		System.out.println("Get economic data");
		ds.getEconomicData();
		ds.normalize();
				
		ArrayList<DailyData> training = new ArrayList<>();
		ArrayList<DailyData> testing = new ArrayList<>();
		
		int[][] confusionMatrix = new int[4][4];
		
		//////Classify on economics
		NUMFEATURES = 8;
		int j = 0;
		for(DailyData d: ds.dailyData){
			//if(d.classes.get(tick)!= null && d.classes.get(tick).equals("w4")) System.out.println("Have w4");
			
			if(d.volume.get(tick) != null && d.volume.get(tick) != 0.0 && d.classes.get(tick) != null){
				if(j % 5 == FIVEFOLD_SET) testing.add(d); 
				else training.add(d);
				j++;
			}
		}
		
		double[][] inputSet = new double[training.size()][NUMFEATURES];
		double[][] targetSet = new double[training.size()][1];
		for(int i = 0; i < training.size(); ++i){
			DailyData dat = training.get(i);
			inputSet[i][0] = dat.bankRate;
			inputSet[i][1] = dat.cpi;
			inputSet[i][2] = dat.cadUSD;
			inputSet[i][3] = dat.oilUSD;
			inputSet[i][4] = dat.goldUSD;
			inputSet[i][5] = dat.employment;
			inputSet[i][6] = dat.householdGDP;
			inputSet[i][7] = dat.indExpGDP;
			
			switch(dat.classes.get(tick)){
				case "w1": 
					targetSet[i][0] = 0.125; 
					break;
				case "w2": 
					targetSet[i][0] = 0.275; 
					break;
				case "w3": 
					targetSet[i][0] = 0.625; 
					break;
				case "w4": 
					targetSet[i][0] = 0.875; 
					break;
			}
		}
		
		int[] layerSizes = {NUMFEATURES, HIDDEN_LAYER_SIZE, 1}; 
		
		NeuralNetwork net = new NeuralNetwork(layerSizes, inputSet, targetSet);
		net.train();
		
		for(int i = 0; i < testing.size(); ++i){
						
			double[] testingData = new double[NUMFEATURES];
			DailyData dat = testing.get(i);
			testingData[0] = dat.bankRate;
			testingData[1] = dat.cpi;
			testingData[2] = dat.cadUSD;
			testingData[3] = dat.oilUSD;
			testingData[4] = dat.goldUSD;
			testingData[5] = dat.employment;
			testingData[6] = dat.householdGDP;
			testingData[7] = dat.indExpGDP;
			
			double result = net.test(testingData);
			
			if(dat.classes.get(tick) != null){
				
				String res = "";
				if(result < 0.25) res = "w1";
				else if(result < 0.5) res = "w2";
				else if(result < 0.75) res = "w3";
				else res = "w4";
				
				ds.writeEconToDB(dat.year, dat.month, dat.day, tick, dat.classes.get(tick), res);
				
				switch(dat.classes.get(tick)){
					case "w1":
						if(result < 0.25) confusionMatrix[0][0]++;
						else if(result < 0.5) confusionMatrix[0][1]++;
						else if(result < 0.75) confusionMatrix[0][2]++;
						else confusionMatrix[0][3]++;
						break;
					case "w2":
						if(result < 0.25) confusionMatrix[1][0]++;
						else if(result < 0.5) confusionMatrix[1][1]++;
						else if(result < 0.75) confusionMatrix[1][2]++;
						else confusionMatrix[1][3]++;
						break;
					case "w3":
						if(result < 0.25) confusionMatrix[2][0]++;
						else if(result < 0.5) confusionMatrix[2][1]++;
						else if(result < 0.75) confusionMatrix[2][2]++;
						else confusionMatrix[2][3]++;
						break;
					case "w4":
						if(result < 0.25) confusionMatrix[3][0]++;
						else if(result < 0.5) confusionMatrix[3][1]++;
						else if(result < 0.75) confusionMatrix[3][2]++;
						else confusionMatrix[3][3]++;
						break;
				}
			}
			
		}
		
		System.out.println("Confusion Matrix\n.................\n");
		for(int i = 0; i < 4; ++i){
			System.out.print("| ");
			for(int k = 0; k < 4; ++k){
				System.out.print(confusionMatrix[i][k] + " |");
			}
			System.out.println();
		}
		
	}
	
	/////////// Training and testing methods
	
	//Trains the neural network according to its input and target datasets
	public void train() {		
		long k = 0;
		
		do{
			for (sample = 0; sample < numSamples; sample++) {
        		for (int i = 0; i < inputLayer.neurons.length; i++){
        			inputLayer.inputs[i] = trainingInputs[sample][i];
        		}

				forwardPropagate();
									
				for (int i = 0; i < outputLayer.neurons.length; i++){
					nnOutputs[sample][i] = outputLayer.neurons[i].output;
				}
				
				updateWeights();
			}
			k++;
			getTotError();
		} while ((totError > MIN_ERROR) && (k < TRAINING_STEPS));

	}

	public double test(double[] input) {
		int max = 0;
		Neuron[] outputs;

		for (int i = 0; i < inputLayer.neurons.length; i++)
			inputLayer.inputs[i] = input[i];

		forwardPropagate();

		outputs = outputLayer.neurons;

		return outputLayer.neurons[0].output;

	} 
	
	//Forward propagation method
	public void forwardPropagate(){

		for (int i = 0; i < inputLayer.neurons.length; i++){
			inputLayer.neurons[i].output = inputLayer.inputs[i];
		}

		hiddenLayer.inputs = inputLayer.inputs;
		
		inputLayer.forwardPropagate();
		hiddenLayer.inputs = inputLayer.getOutput();
		hiddenLayer.forwardPropagate();
		outputLayer.inputs = hiddenLayer.getOutput();
		outputLayer.forwardPropagate();		

	} 

	//Updates the weights of the neurons
	public void updateWeights() {
		getErrors();
		backPropagate();
	}


	//determines the deltas for the neuron outputs
	public void getErrors() {

		double sum = 0.0;

		for (int i = 0; i < outputLayer.neurons.length; i++){ 
			outputLayer.neurons[i].error = (trainingOutputs[sample][i] - outputLayer.neurons[i].output) * outputLayer.neurons[i].output * (1 - outputLayer.neurons[i].output);
		}
		
		for (int j = 0; j < hiddenLayer.neurons.length; j++) {
			sum = 0;

			for (int k = 0; k < outputLayer.neurons.length; k++) {
				sum += outputLayer.neurons[k].weights[j] * outputLayer.neurons[k].error;
			}
			
			hiddenLayer.neurons[j].error = hiddenLayer.neurons[j].output * (1 - hiddenLayer.neurons[j].output) * sum;
		}

	}


	//Backpropagation method
	public void backPropagate() {
		
		//backpropagate error to the output layer weights
		for (int i = 0; i < outputLayer.neurons.length; ++i) {
			outputLayer.neurons[i].deltaThreshold = LEARNING_RATE * outputLayer.neurons[i].error + MOMENTUM * outputLayer.neurons[i].deltaThreshold;
			outputLayer.neurons[i].threshold = outputLayer.neurons[i].threshold + outputLayer.neurons[i].deltaThreshold;

			for (int j = 0; j < outputLayer.inputs.length; j++) {
				outputLayer.neurons[i].deltaWt[j] = LEARNING_RATE * outputLayer.neurons[i].error * hiddenLayer.neurons[j].output + MOMENTUM * outputLayer.neurons[i].deltaWt[j];
				outputLayer.neurons[i].weights[j] = outputLayer.neurons[i].weights[j] + outputLayer.neurons[i].deltaWt[j];
			}
		}
		
		//backpropagate error to the hidden layer weights
		for (int j = 0; j < hiddenLayer.neurons.length; ++j) {
			hiddenLayer.neurons[j].deltaThreshold = LEARNING_RATE * hiddenLayer.neurons[j].error + MOMENTUM * hiddenLayer.neurons[j].deltaThreshold;
			hiddenLayer.neurons[j].threshold = hiddenLayer.neurons[j].threshold + hiddenLayer.neurons[j].deltaThreshold;

			for (int k = 0; k < hiddenLayer.inputs.length; k++) {
				hiddenLayer.neurons[j].deltaWt[k] = LEARNING_RATE * hiddenLayer.neurons[j].error * inputLayer.neurons[k].output + MOMENTUM * hiddenLayer.neurons[j].deltaWt[k];
				hiddenLayer.neurons[j].weights[k] = hiddenLayer.neurons[j].weights[k] + hiddenLayer.neurons[j].deltaWt[k];
			}
		}
		
	}


	//Determines the error of the neuron layer -- used by backpropagation method
	private void getTotError() {
		totError = 0;
   	
		for (int i = 0; i < numSamples; i++)
			for (int j = 0; j < outputLayer.neurons.length; j++) {
        			totError = totError + 0.5*( Math.pow(trainingOutputs[i][j] - nnOutputs[i][j],2) );
		}
	}

	
	public double get_error() { 
		getTotError();
		return totError;
	} 

	//demo training data (XOR) to test the system
		//static int[] NN = {2, 3, 1};
		//static double[][] IS = {{0,0}, {0,1}, {1,0}, {1,1}};
		//static double[][] OS = {{0},{1},{1},{0}};
		
}
