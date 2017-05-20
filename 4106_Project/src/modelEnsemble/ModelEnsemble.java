package modelEnsemble;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

/*This code is recycled from Assignment 3 and is intended to demonstrate the concept
 * of the Model Ensemble.
 * 
 * For the purposes of an academic project, the neural network and kohonen map classifiers 
 * are intended to fulfil the requirement to build two classifiers not previously encountered 
 * 
 * */
public class ModelEnsemble {
	
	static final int FIVEFOLD_SET = 3;
	
	static final String TICKER_TO_EVAL = "BNS";
	
	static Connection database;
	static Statement stat;
	
	static final int NUMSAMPLES = 2000;
	static final int NUMFEATURES = 16;
	
	static final double DT_THRESH = 0.97;
	
	//estimated and cross-validation count for the computed feature dependencies
	float[][] w1vals;
	float[][] w2vals;
	float[][] w3vals;
	float[][] w4vals;

	//confusion matrices
	float[][] confusion1;
	float[][] confusion2;
	float[][] confusion3;
	float[][] confusion4;
	
	float confusionIndependent[][];
	
	static ArrayList<MEData> data;
	static ArrayList<MEData> training;
	ArrayList<MEData> testing;
	
	public ModelEnsemble(){
		data = new ArrayList<>();
		testing = new ArrayList<>();
		training = new ArrayList<>();
		
		w1vals = new float[NUMFEATURES][5];
		w2vals = new float[NUMFEATURES][5];
		w3vals = new float[NUMFEATURES][5];
		w4vals = new float[NUMFEATURES][5];
		
		confusion1 = new float[4][4];
		confusion2 = new float[4][4];
		confusion3 = new float[4][4];
		confusion4 = new float[4][4];
		
		confusionIndependent = new float[4][4];	

	}
	
	public static void main(String[] args) {
		
		ModelEnsemble classifier = new ModelEnsemble();		
		classifier.run();
	}
	
	public void run(){
		//get the data from the database
		getData();
		
		//independent baeysian classification
		System.out.println("Independent Bayesian Classification:");
		independentBayes();

		
	}
		
	public void independentBayes() {
		//five-fold cross validation
		float[] w1probs = new float[NUMFEATURES];
		float[] w2probs = new float[NUMFEATURES];
		float[] w3probs = new float[NUMFEATURES];
		float[] w4probs = new float[NUMFEATURES];
		
		for(float[]i: confusionIndependent) for(float j: i) j = 0;
		
		sortForTraining(FIVEFOLD_SET);
		
		int training1 = 0;
		int training2 = 0;
		int training3 = 0;
		int training4 = 0; 
		
		for(MEData p: training){
		
			switch(p.theClass){
			case 1:
				for(int j = 0; j < NUMFEATURES; ++j) w1probs[j] += p.features[j];
				training1++;
				break;
			case 2:
				for(int j = 0; j < NUMFEATURES; ++j) w2probs[j] += p.features[j];
				training2++;
				break;
			case 3:
				for(int j = 0; j < NUMFEATURES; ++j) w3probs[j] += p.features[j];
				training3++;
				break;
			case 4:
				for(int j = 0; j < NUMFEATURES; ++j) w4probs[j] += p.features[j];
				training4++;
				break;				
			} 
		}
		
		for(int i = 0; i < NUMFEATURES; ++i){
			w1probs[i] /= training1;
			w2probs[i] /= training2;
			w3probs[i] /= training3;
			w4probs[i] /= training4;
		}
		
		//test w1:
		
		for(MEData p: testing){ //(int i = 1600; i < 2000; ++i){
			float p1 = 1.0f;
			float p2 = 1.0f;
			float p3 = 1.0f;
			float p4 = 1.0f;
			
			for(int j = 0; j < NUMFEATURES; ++j){
				if(p.features[j] == 1) p1 *= w1probs[j];
				else p1 *= (1-w1probs[j]);
				if(p.features[j] == 1) p2 *= w2probs[j];
				else p2 *= (1-w2probs[j]);
				if(p.features[j] == 1) p3 *= w3probs[j];
				else p3 *= (1-w3probs[j]);
				if(p.features[j] == 1) p4 *= w2probs[j];
				else p4 *= (1-w4probs[j]);
			}
			
			if(Float.isNaN(p1)) p1 = 0.0f;
			if(Float.isNaN(p2)) p2 = 0.0f;
			if(Float.isNaN(p3)) p3 = 0.0f;
			if(Float.isNaN(p4)) p4 = 0.0f;
			
			//make pairwise
			if(p1 >= p2 && p1 >= p3 && p1 >= p4){
				p.classifierGuess = 1;
			} else if(p2 >= p1 && p2 >= p3 && p2 >= p4){
				p.classifierGuess = 2;
			} else if(p3 >= p1 && p3 >= p2 && p3 >= p4){
				p.classifierGuess = 3;
			} else if(p4 >= p1 && p4 >= p2 && p4 >= p3){
				p.classifierGuess = 4;
			} else p.classifierGuess = 9999;
			
			//print(data.get(i));
			confusionIndependent[p.theClass-1][p.classifierGuess-1] ++;
		}
		
////scrap comes from here
		
		System.out.println("\n..................\nConfusion matrix:");
		for(float[]i: confusionIndependent){
			System.out.print("\n| ");
			float rowTot = 0.0f;
			for(float j: i){
				rowTot += j;
			}
			
			for(float j: i){
				//j /= 500;
				if(rowTot == 0) System.out.print("" + String.format("%2.3f", 0.0f) + "% |");
				else
				System.out.print("" + String.format("%2.3f", /*100 **/(0.0f + j)/*/rowTot*/) + " |");
			}			
		}
		
		System.out.println("\n..................");
		
	}
	
	
	

	public void getData(){
		/////////////////// Set up database
		//Connect to database
		try {
	
			//direct java to the sqlite-jdbc driver jar code
			// load the sqlite-JDBC driver using the current class loader
			Class.forName("org.sqlite.JDBC");
			System.out.println("Open Database Connection");
	
			//HARD CODED DATABASE NAME:
			database = DriverManager.getConnection("jdbc:sqlite:4106");
		     //create a statement object which will be used to relay a
		     //sql query to the database
			stat = database.createStatement();
		
	
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		//go do some stuff with the DB
		
		MEData pt;
			
		try{
			String sqlQueryString = "SELECT * FROM classifierResults WHERE ticker = '" + TICKER_TO_EVAL + "'";
			//System.out.println(sqlQueryString);			
			ResultSet rs = stat.executeQuery(sqlQueryString);
			 
	        while (rs.next()) {
	        	String s1 = rs.getString("nnFundamentals");
	        	String s2 = rs.getString("nnEconomics");
	        	String s3 = rs.getString("kmFundamentals");
	        	String s4 = rs.getString("kmEconomics");
	        	String s5 = rs.getString("knownClass");
	        	
	        	
	        	if(s1 != null && s2 != null && s3 != null && s4 != null && s5 != null && s1 != "" && s2 != "" && s3 != "" && s4 != "" && s5 != ""){
	        		pt = new MEData(s1, s2, s3, s4, s5);	        	
	        		data.add(pt);
	        	}
	        }
			
		}
		catch(SQLException e){
			e.printStackTrace();			
		}
			
	
	}
	
	
	public void print(MEData p){
		String ret = ("Class: " + p.theClass + " " + "   Classifier guess: " + p.classifierGuess);
		for(int i: p.features){
			ret += "[" + i + "] ";
		}
		System.out.println(ret);
	}
	
	public void sortForTraining(int set){
			
		training.clear();
		testing.clear();
		for(int i = 0; i < data.size(); ++i){
			if(i % 5 == set){
				testing.add(data.get(i));
			} else training.add(data.get(i));
		}
	}
	
}



	
	


