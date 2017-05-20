package kohonenMap;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import featureEngineering.DailyData;

public class KMap {

	////////////
	//  NOTE:  SET THE NUMFEATURES AND TICKER MANUALLY!!
	////////////
	//Set this to 10 to evaluate random data, 7 for financials, and 8 for macroeconomics 
	public static int NUMFEATURES = 8;
	public static final int FEATURESCALE = 100;
	public static final int TRAININGSTEPS = 15;
	public static final int MAPSIZE = 100;
	public static final int MAP_BRANCHING_FACTOR = 2;
	public static final int NUMDATA = 10000;
	public static final int NEURON_COUNT = 256;
	public static final double SUCCESS_THRESHOLD = 0.5;
	
	public static final double INIT_LAMBDA = 0.75;
	
	public static final double LAMBDA_DECAY_RATE = 0.9999;
	public static final double NEIGHBORHOOD_DECAY_RATE = 0.999;
	
	public static final int FIVEFOLD_SET = 0;
	
	public static final String TICKER_TO_EVAL = "BMO";
	
	//// For datafeed
	static Connection database;
	static Statement stat;
	
	ArrayList<DailyData> dailyData;
	ArrayList<DailyData> testing;
	HashMap<Long, DailyData> indexData;
	
	static long seed = System.nanoTime();
	static Random rand = new Random(seed);
	
	public ArrayList<KNeuron> neurons;
	public ArrayList<KNeuron> allNeurons;
	public ArrayList<KNeuron> map;
	public HashMap<double[], Integer> data;

	HashSet<String> tickers;
	
	public KNeuron root;
	
	public KMap(){
		neurons = new ArrayList<>();
		map = new ArrayList<>();
		data = new HashMap<>();
		neurons = new ArrayList<>();
		allNeurons = new ArrayList<>();
		
		dailyData = new ArrayList<>();
		indexData = new HashMap<>();
		tickers = new HashSet<>();
		
	}
	
	//////////////// execution functions
	
	public static void main(String[] args){		
		KMap theMap = new KMap();
		theMap.run();	
	}
	
	public void run(){
		System.out.println("Open DB Connection");
		setupDB();
		System.out.println("Get corporate data");
		getFundamentalData();
		System.out.println("Get economic data");
		getEconomicData();
		System.out.println("Get benchmark data");
		getBenchmarkData();
		
		//data needs to be properly scaled -- in this implementation all features range from 0 to 100
		System.out.println("Normalize data to map dimensions");
		normalize();
				
		//generate the neurons
		generateMap();
		boolean success = false;
		
		
		System.out.println("Create map");
		
		while(!success){
			///////////////////////////////////////////////////////////////
			////////train and evaluate:
			//Uncomment the classifier you want to use
			//System.out.println("Initialize");
			//trainOnFundamentals(TICKER_TO_EVAL);
			//success = testOnFundamentals(TICKER_TO_EVAL);
			trainOnEconomics(TICKER_TO_EVAL);
			success = testOnEconomics(TICKER_TO_EVAL);
			//////////////////////////////////////////////////////////////
		}
	}
		
	//starts the map building process
	public void generateMap(){
		root = new KNeuron(null);
		map.add(root);
		int parentIndex = 0;
		
		for(int i = 1; i < NEURON_COUNT; ++i){
			if(map.get(parentIndex).children.size() >= MAP_BRANCHING_FACTOR) parentIndex++; 
			KNeuron newNode = new KNeuron(map.get(parentIndex));
			map.get(parentIndex).addChild(newNode);
			map.add(newNode);
			allNeurons.add(newNode);
		}		
	}
		
	//recursively builds the neuron network
	public void rAdd(KNeuron k){
		if(k == null) return;
		map.add(k);
		for(KNeuron m: k.children) rAdd(m);
	}
	
	///////////////////// TRAINING AND TESTING METHODS
	
	//Determines if two vectors are close given a distance threshold
	public boolean isNeighbor(KNeuron source, KNeuron test, double dist){
		return (getDist(source.features, test.features) < dist);
	}
	
	//Returns the closest neuron to some vector
	public KNeuron getBMU(double[] sample){
		KNeuron ret = map.get(0);
		double closestDist = Double.MAX_VALUE;
		
		for(int i = 0; i < map.size(); ++i){
			double d = getDist(sample, map.get(i).features);
			if(d < closestDist){
				closestDist = d;
				ret = map.get(i);
			}
		}
		
		return ret;
	}
	
	//Moves one vector towards another by a specific factor 0.0 - 1.0
	public void moveTo(double[] source, double[] target, double amount){
		
		for(int i = 0; i < source.length; ++i){
			double movement = (source[i] - target[i]) * amount;
			source[i] -= movement;
		}		
	}
	
	
	//Euclidian distance between two vectors
	public double getDist(double[] a, double[] b){
		double dist = 0.0;
		
		for(int i = 0; i < a.length; ++i){
			dist += Math.pow((a[i] - b[i]), 2);
		}
		
		return Math.sqrt(dist);
	}
	
	//classifies a data sample vector
	public int classify(double[] sample){
		KNeuron closest = getBMU(sample);
		return closest.theClass;
	}
	
	//classifies a DailyData object
	public String classify(DailyData sample){
		KNeuron closest = getBMU(sample.features);
		return closest.stringClass;
	}
	
	//Five-fold cross validation for fundamentals analysis, prints confusion matrix
	public boolean testOnFundamentals(String tick){
		
		boolean success = true;
		
		int[][] confusionMatrix = new int[4][4];
		
		for(DailyData d: testing){			
			d.features = new double[NUMFEATURES];
				
			d.features[0] = d.p_e.get(tick);
			d.features[1] = d.div_yld.get(tick);
			d.features[2] = d.ma200.get(tick);
			d.features[3] = d.ma20.get(tick);
			d.features[4] = d.beta.get(tick);
			d.features[5] = d.volume.get(tick);
			d.features[6] = d.relStr.get(tick);
		
			String result = classify(d);
			
			if(d.classes.get(tick) != null && result != null){
				
			writeFundToDB(d.year, d.month, d.day, tick, d.classes.get(tick), result);
				
			switch(d.classes.get(tick)){
				case "w1":
					if(result.equals("w1")) confusionMatrix[0][0]++;
					else if(result.equals("w2")) confusionMatrix[0][1]++;
					else if(result.equals("w3")) confusionMatrix[0][2]++;
					else if(result.equals("w4")) confusionMatrix[0][3]++;
					break;
				case "w2":
					if(result.equals("w1")) confusionMatrix[1][0]++;
					else if(result.equals("w2")) confusionMatrix[1][1]++;
					else if(result.equals("w3")) confusionMatrix[1][2]++;
					else if(result.equals("w4")) confusionMatrix[1][3]++;
					break;
				case "w3":
					if(result.equals("w1")) confusionMatrix[2][0]++;
					else if(result.equals("w2")) confusionMatrix[2][1]++;
					else if(result.equals("w3")) confusionMatrix[2][2]++;
					else if(result.equals("w4")) confusionMatrix[2][3]++;
					break;
				case "w4":
					if(result.equals("w1")) confusionMatrix[3][0]++;
					else if(result.equals("w2")) confusionMatrix[3][1]++;
					else if(result.equals("w3")) confusionMatrix[3][2]++;
					else if(result.equals("w4")) confusionMatrix[3][3]++;
					break;
				}
			}
		}
		
		for(int i = 0; i < 4; ++i){
			double tot = 0.0;
			for(int j = 0; j < 4; ++j){
				tot += confusionMatrix[i][j];
			}
			if((confusionMatrix[i][i] == 0.0 && tot > 0.0) || (confusionMatrix[i][i]) / tot < SUCCESS_THRESHOLD){
				success = false;
			}
		}

		if(!success) return false;
		
		System.out.println("Confusion Matrix\n.................\n");
		for(int i = 0; i < 4; ++i){
			System.out.print("| ");
			for(int j = 0; j < 4; ++j){
				System.out.print(confusionMatrix[i][j] + " |");
			}
			System.out.println();
		}
		
		return success;
		
	}
	
	//Five-fold cross validation for economic analysis, prints confusion matrix
	public boolean testOnEconomics(String tick){
		
		boolean success = true;
		
		int[][] confusionMatrix = new int[4][4];
		
		for(DailyData d: testing){			
			d.features = new double[NUMFEATURES];
				
			d.features[0] = d.bankRate;
			d.features[1] = d.cpi;
			d.features[2] = d.cadUSD;
			d.features[3] = d.oilUSD;
			d.features[4] = d.goldUSD;
			d.features[5] = d.employment;
			d.features[6] = d.householdGDP;
			d.features[7] = d.indExpGDP;
		
			String result = classify(d);
			
			if(d.classes.get(tick) != null && result != null){
				
				writeEconToDB(d.year, d.month, d.day, tick, d.classes.get(tick), result);
				
			switch(d.classes.get(tick)){
				case "w1":
					if(result.equals("w1")) confusionMatrix[0][0]++;
					else if(result.equals("w2")) confusionMatrix[0][1]++;
					else if(result.equals("w3")) confusionMatrix[0][2]++;
					else if(result.equals("w4")) confusionMatrix[0][3]++;
					break;
				case "w2":
					if(result.equals("w1")) confusionMatrix[1][0]++;
					else if(result.equals("w2")) confusionMatrix[1][1]++;
					else if(result.equals("w3")) confusionMatrix[1][2]++;
					else if(result.equals("w4")) confusionMatrix[1][3]++;
					break;
				case "w3":
					if(result.equals("w1")) confusionMatrix[2][0]++;
					else if(result.equals("w2")) confusionMatrix[2][1]++;
					else if(result.equals("w3")) confusionMatrix[2][2]++;
					else if(result.equals("w4")) confusionMatrix[2][3]++;
					break;
				case "w4":
					if(result.equals("w1")) confusionMatrix[3][0]++;
					else if(result.equals("w2")) confusionMatrix[3][1]++;
					else if(result.equals("w3")) confusionMatrix[3][2]++;
					else if(result.equals("w4")) confusionMatrix[3][3]++;
					break;
				}
			}
		}
			
		for(int i = 0; i < 4; ++i){
			double tot = 0.0;
			for(int j = 0; j < 4; ++j){
				tot += confusionMatrix[i][j];
			}
			if((confusionMatrix[i][i] == 0.0 && tot > 0.0) || (confusionMatrix[i][i]) / tot < SUCCESS_THRESHOLD){
				success = false;
			}
		}
			
		if(!success) return false;
		
		System.out.println("Confusion Matrix\n.................\n");
		for(int i = 0; i < 4; ++i){
			System.out.print("| ");
			for(int j = 0; j < 4; ++j){
				System.out.print(confusionMatrix[i][j] + " |");
			}
			System.out.println();
		}
		
		return success;	
		
	}
	
	
	//Training method for fundamentals analysis
	public void trainOnFundamentals(String tick){
		NUMFEATURES = 7;
		double dist = MAPSIZE;
		double lambda = INIT_LAMBDA;
		
		ArrayList<DailyData> training = new ArrayList<>();
		testing = new ArrayList<>();
		
		//need to filter out the invalid data
		int j = 0;
		for(DailyData d: dailyData){
			if(d.p_e.get(tick) != null && d.p_e.get(tick) != 0.0){
				if(j % 5 == FIVEFOLD_SET) testing.add(d); 
				else training.add(d);
				j++;
			}
		}
		
		Collections.shuffle(training);
		
		for(int i = 0; i < TRAININGSTEPS; ++i){
		
			//1. Each node's weights are initialized.
			for(KNeuron n: allNeurons){
				for(double d: n.features){
					d = rand.nextDouble() * MAPSIZE;
				}
			}
			//2. A vector is chosen at random from the set of training data and presented to the lattice.
			for(DailyData d: training){
				//DailyData d = dailyData.get(j);
				//3. Every node is examined to calculate which one's weights are most like the input vector. The winning node is commonly known as the Best Matching Unit (BMU).
				d.features = new double[NUMFEATURES];
			
				d.features[0] = d.p_e.get(tick);
				d.features[1] = d.div_yld.get(tick);
				d.features[2] = d.ma200.get(tick);
				d.features[3] = d.ma20.get(tick);
				d.features[4] = d.beta.get(tick);
				d.features[5] = d.volume.get(tick);
				d.features[6] = d.relStr.get(tick);
				
				KNeuron bmu = getBMU(d.features);
				//bmu.theClass = data.get(d);
				//4. The radius of the neighbourhood of the BMU is now calculated. This is a value that starts large, typically set to the 'radius' of the lattice,  but diminishes each time-step. Any nodes found within this radius are deemed to be inside the BMU's neighbourhood.
				//5. Each neighbouring node's (the nodes found in step 4) weights are adjusted to make them more like the input vector. The closer a node is to the BMU, the more its weights get altered.
				for(KNeuron k: map){
					if(k == bmu) continue;
					if(isNeighbor(bmu, k, dist)){
						moveTo(k.features, d.features, lambda);
					}
				}
				lambda *= LAMBDA_DECAY_RATE;
				dist *= NEIGHBORHOOD_DECAY_RATE;
				
				//6. Repeat step 2 for N iterations.
			}
		}
		
		for(KNeuron k: map){
			DailyData best = null; 
			double bestdist = Double.MAX_VALUE;
			for(DailyData d: training){
				double distance = getDist(d.features, k.features);
				if(distance < bestdist){
					bestdist = distance;
					best = d;
				}
			}
			k.stringClass = best.classes.get(tick);
		}
	}
	
	//Training method for economic analysis
	public void trainOnEconomics(String tick){
		NUMFEATURES = 8;
		double dist = MAPSIZE;
		double lambda = INIT_LAMBDA;
		
		ArrayList<DailyData> training = new ArrayList<>();
		testing = new ArrayList<>();
		
		//need to filter out the invalid data
		int j = 0;
		for(DailyData d: dailyData){
			if(d.p_e.get(tick) != null && d.p_e.get(tick) != 0.0){
				if(j % 5 == FIVEFOLD_SET) testing.add(d); 
				else training.add(d);
				j++;
			}
		}
		
		Collections.shuffle(training);
		
		for(int i = 0; i < TRAININGSTEPS; ++i){
		
			//1. Each node's weights are initialized.
			for(KNeuron n: allNeurons){
				for(double d: n.features){
					d = rand.nextDouble() * MAPSIZE;
				}
			}
			//2. A vector is chosen at random from the set of training data and presented to the lattice.
			for(DailyData d: training){
				//DailyData d = dailyData.get(j);
				//3. Every node is examined to calculate which one's weights are most like the input vector. The winning node is commonly known as the Best Matching Unit (BMU).
				d.features = new double[NUMFEATURES];
			
				d.features[0] = d.bankRate;
				d.features[1] = d.cpi;
				d.features[2] = d.cadUSD;
				d.features[3] = d.oilUSD;
				d.features[4] = d.goldUSD;
				d.features[5] = d.employment;
				d.features[6] = d.householdGDP;
				d.features[7] = d.indExpGDP;
				
				KNeuron bmu = getBMU(d.features);
				//bmu.theClass = data.get(d);
				//4. The radius of the neighbourhood of the BMU is now calculated. This is a value that starts large, typically set to the 'radius' of the lattice,  but diminishes each time-step. Any nodes found within this radius are deemed to be inside the BMU's neighbourhood.
				//5. Each neighbouring node's (the nodes found in step 4) weights are adjusted to make them more like the input vector. The closer a node is to the BMU, the more its weights get altered.
				for(KNeuron k: map){
					if(k == bmu) continue;
					if(isNeighbor(bmu, k, dist)){
						moveTo(k.features, d.features, lambda);
					}
				}
				lambda *= LAMBDA_DECAY_RATE;
				dist *= NEIGHBORHOOD_DECAY_RATE;
				
				//6. Repeat step 2 for N iterations.
			}
		}
		
		for(KNeuron k: map){
			DailyData best = null; 
			double bestdist = Double.MAX_VALUE;
			for(DailyData d: training){
				double distance = getDist(d.features, k.features);
				if(distance < bestdist){
					bestdist = distance;
					best = d;
				}
			}
			k.stringClass = best.classes.get(tick);
		}
	}
	
		
	///////////////// DB MANAGEMENT METHODS	
		
	//Queries the SQLite DB for 'fundamental' data
	public void getFundamentalData(){
		String sqlQueryString;
		ResultSet rs;
		
		try{			
			sqlQueryString = "SELECT * FROM corporate";
			rs = stat.executeQuery(sqlQueryString);			
			Date dt;
			
	        while (rs.next()) {
	        	long theDate = new Date(rs.getInt("year"), rs.getInt("month") -1, rs.getInt("day")).getTime();
	        	DailyData dp;
	        	if(!indexData.keySet().contains(theDate)){	        	
	        		dp = new DailyData(rs.getInt("year"), rs.getInt("month") -1, rs.getInt("day"));
	        		indexData.put(theDate, dp);
	        		dailyData.add(dp);
	        	} else dp = indexData.get(theDate);
	        	
	        	String ticker = rs.getString("ticker");
	        	tickers.add(ticker);
	    		dp.p_e.put(ticker, rs.getDouble("p_e"));
	    		dp.div_yld.put(ticker, rs.getDouble("div_yld"));
	    		dp.ma200.put(ticker, rs.getDouble("ma200"));
	    		dp.ma20.put(ticker, rs.getDouble("ma20"));
	    		dp.beta.put(ticker, rs.getDouble("beta"));
	    		dp.volume.put(ticker, rs.getDouble("volume"));
	    		dp.relStr.put(ticker, rs.getDouble("relStr"));
	    		dp.price.put(ticker, rs.getDouble("price"));
	    		dp.earnings.put(ticker, rs.getDouble("eps"));
	    		dp.divs.put(ticker, rs.getDouble("div_yld"));
	    		dp.classes.put(ticker, rs.getString("knownClass"));
	        }
	        rs.close();
		}
		catch(SQLException e){
			e.printStackTrace();
		}
		
		dailyData.sort((DailyData d1, DailyData d2) -> {return d1.date.compareTo(d2.date);} );
	}
		
	//Queries the SQLite DB for economic data
	public void getEconomicData(){
		String sqlQueryString;
		ResultSet rs;
		
		try{			
			sqlQueryString = "SELECT * FROM macroeconomics";
			rs = stat.executeQuery(sqlQueryString);			
			Date dt;
			
	        while (rs.next()) {
	        	long theDate = new Date(rs.getInt("year"), rs.getInt("month") -1, 1).getTime();
	        	DailyData dp;
	        	if(!indexData.keySet().contains(theDate)){	        	
	        		dp = new DailyData(rs.getInt("year"), rs.getInt("month") -1, 1);
	        		indexData.put(theDate, dp);
	        		dailyData.add(dp);
	        	} else dp = indexData.get(theDate);	        	
	        	
	        	//dp.tsx = rs.getDouble("tsx");
	        	dp.bankRate = rs.getDouble("bankRate");
	        	dp.cpi = rs.getDouble("cpi");
	        	dp.cadUSD = rs.getDouble("cadUsd");
	        	dp.oilUSD = rs.getDouble("oilUsd");
	        	dp.goldUSD = rs.getDouble("goldUsd");
	        	dp.employment = rs.getDouble("employment");
	        	dp.householdGDP = rs.getDouble("householdGDP");
	        	dp.indExpGDP = rs.getDouble("indExpGDP");
	        	
	        	//these data only applicable by month - so fill them in for all days of that month
	        	//for(DailyData d: indexData.values() ){
	        		for(int i = 1; i < 31; ++i){
	        			long newDate = new Date(rs.getInt("year"), rs.getInt("month") -1, i).getTime();
	        			//if(indexData.containsKey(newDate)){
	        				DailyData dd = indexData.get(newDate);
	        				if(dd != null){
		        	        	dd.bankRate = rs.getDouble("bankRate");
		        	        	dd.cpi = rs.getDouble("cpi");
		        	        	dd.cadUSD = rs.getDouble("cadUsd");
		        	        	dd.oilUSD = rs.getDouble("oilUsd");
		        	        	dd.goldUSD = rs.getDouble("goldUsd");
		        	        	dd.employment = rs.getDouble("employment");
		        	        	dd.householdGDP = rs.getDouble("householdGDP");
		        	        	dd.indExpGDP = rs.getDouble("indExpGDP");
	        	        	}
	        			//}//if	        			
	        		}//for
	        	//}//for
	        }//while
	        rs.close();
		}//try
		catch(SQLException e){
			e.printStackTrace();
		}
	}
	
	//Queries the SQLite DB for benchmark data
	public void getBenchmarkData(){
		String sqlQueryString;
		ResultSet rs;
		
		try{			
			sqlQueryString = "SELECT * FROM benchmarks";
			rs = stat.executeQuery(sqlQueryString);			
			Date dt;
			
	        while (rs.next()) {
	        	long theDate = new Date(rs.getInt("year"), rs.getInt("month") -1, rs.getInt("day")).getTime();
	        	DailyData dp;
	        	if(!indexData.keySet().contains(theDate)){	        	
	        		dp = new DailyData(rs.getInt("year"), rs.getInt("month") -1, 1);
	        		indexData.put(theDate, dp);
	        		dailyData.add(dp);
	        	} else dp = indexData.get(theDate);	        	
	        	
	        	dp.tsx = rs.getDouble("tsx");
	        }
	        rs.close();
		}
		catch(SQLException e){
			e.printStackTrace();
		}
		
	}
	
	public void writeFundToDB(int y, int m, int d, String tick, String realClass, String classifierClass){
		String sqlQueryString;
		
		/*try{			
			sqlQueryString = "INSERT INTO classifierResults(year, month, day, ticker, knownClass) VALUES(" + y + ", " + m + ", " + d + ", '" + tick + "', '" + realClass + "');";
			System.out.println(sqlQueryString);
			stat.executeUpdate(sqlQueryString);			
		}
		catch(SQLException e){
			//e.printStackTrace();
		}*/
		
		try{			
			sqlQueryString = "UPDATE classifierResults SET kmFundamentals = '" + classifierClass + "' where year = " + y + " AND month = " + m + " AND day = " + d + " AND ticker = '" + tick + "';";
			//System.out.println(sqlQueryString);
			stat.executeUpdate(sqlQueryString);			
		}
		catch(SQLException e){
			//e.printStackTrace();
		}
	}
	
	public void writeEconToDB(int y, int m, int d, String tick, String realClass, String classifierClass){
		String sqlQueryString;
		
		/*try{			
			sqlQueryString = "INSERT INTO classifierResults(year, month, day, ticker, knownClass) VALUES(" + y + ", " + m + ", " + d + ", '" + tick + "', '" + realClass + "');";
			//System.out.println(sqlQueryString);
			stat.executeUpdate(sqlQueryString);			
		}
		catch(SQLException e){
			//e.printStackTrace();
		}*/
		
		try{			
			sqlQueryString = "UPDATE classifierResults SET kmEconomics = '" + classifierClass + "' where year = " + y + " AND month = " + m + " AND day = " + d + " AND ticker = '" + tick + "';";
			//System.out.println(sqlQueryString);
			stat.executeUpdate(sqlQueryString);			
		}
		catch(SQLException e){
			//e.printStackTrace();
		}
	}
	
	
	
	//establishes the SQLite DB connection
	public void setupDB(){
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
	}
		
	//Normalizes the features to range from 0 to 100
	public void normalize(){
		//there are 7 features to normalize for the tickers
		/* 0 : p_e;
		   1 : div_yld;
		   2 : ma200;
		   3 : ma20;
		   4 : beta;
		   5 : volume;
		   6 : relStr;
		   7 : divs;*/
		
		for(String tick: tickers){
			double[] maxs = new double[10];
			double[] mins = new double[10];
			for(int i = 0; i < 10; ++i){
				maxs[i] = Double.MIN_VALUE;
				mins[i] = Double.MAX_VALUE;
			}
			for(DailyData d: dailyData){
				if(d.p_e.get(tick) != null && d.p_e.get(tick) > maxs[0]) maxs[0] = d.p_e.get(tick);
				if(d.p_e.get(tick) != null && d.p_e.get(tick) < mins[0]) mins[0] = d.p_e.get(tick);
				if(d.div_yld.get(tick) != null && d.div_yld.get(tick) > maxs[1]) maxs[1] = d.div_yld.get(tick);
				if(d.div_yld.get(tick) != null && d.div_yld.get(tick) < mins[1]) mins[1] = d.div_yld.get(tick);
				if(d.ma200.get(tick) != null && d.ma200.get(tick) > maxs[2]) maxs[2] = d.ma200.get(tick);
				if(d.ma200.get(tick) != null && d.ma200.get(tick) < mins[2]) mins[2] = d.ma200.get(tick);
				if(d.ma20.get(tick) != null && d.ma20.get(tick) > maxs[3]) maxs[3] = d.ma20.get(tick);
				if(d.ma20.get(tick) != null && d.ma20.get(tick) < mins[3]) mins[3] = d.ma20.get(tick);
				if(d.beta.get(tick) != null && d.beta.get(tick) > maxs[4]) maxs[4] = d.beta.get(tick);
				if(d.beta.get(tick) != null && d.beta.get(tick) < mins[4]) mins[4] = d.beta.get(tick);
				if(d.volume.get(tick) != null && d.volume.get(tick) > maxs[5]) maxs[5] = d.volume.get(tick);
				if(d.volume.get(tick) != null && d.volume.get(tick) < mins[5]) mins[5] = d.volume.get(tick);
				if(d.relStr.get(tick) != null && d.relStr.get(tick) > maxs[6]) maxs[6] = d.relStr.get(tick);
				if(d.relStr.get(tick) != null && d.relStr.get(tick) < mins[6]) mins[6] = d.relStr.get(tick);
				//if(d.divs.get(tick) != null && d.divs.get(tick) > maxs[7]) maxs[7] = d.divs.get(tick);
				//if(d.divs.get(tick) != null && d.divs.get(tick) < mins[7]) mins[7] = d.divs.get(tick);				
			}
			
			//subtract the minimum value from each data point.
			//divide each data point by [max]-[min] and multiply by 100
			
			for	(DailyData d: dailyData){
				if(d.p_e.get(tick) != null){
					double pe = MAPSIZE * ((d.p_e.get(tick)) - mins[0]) / (maxs[0] - mins[0]);
					d.p_e.put(tick,  pe);
				}
				
				if(d.div_yld.get(tick) != null){
					double dy = MAPSIZE * ((d.div_yld.get(tick)) - mins[1]) / (maxs[1] - mins[1]);
					d.div_yld.put(tick,  dy);
				}				
				
				if(d.ma200.get(tick) != null){
					double m200 = MAPSIZE * ((d.ma200.get(tick)) - mins[2]) / (maxs[2] - mins[2]);
					d.ma200.put(tick,  m200);
				}
				
				if(d.ma20.get(tick) != null){
					double m20 = MAPSIZE * ((d.ma20.get(tick)) - mins[3]) / (maxs[3] - mins[3]);
					d.ma20.put(tick,  m20);
				}
				
				if(d.beta.get(tick) != null){
					double bt = MAPSIZE * ((d.beta.get(tick)) - mins[4]) / (maxs[4] - mins[4]);
					d.beta.put(tick,  bt);
				}
				
				if(d.volume.get(tick) != null){
					double v = MAPSIZE * ((d.volume.get(tick)) - mins[5]) / (maxs[5] - mins[5]);
					d.volume.put(tick,  v);
				}
				
				if(d.relStr.get(tick) != null){
					double rst = MAPSIZE * ((d.relStr.get(tick)) - mins[6]) / (maxs[6] - mins[6]);
					d.relStr.put(tick,  rst);
				}
				
				/*if(d.divs.get(tick) != null){
					double dv = MAPSIZE * ((d.divs.get(tick)) - mins[7]) / (maxs[7] - mins[7]);
					d.divs.put(tick,  dv);
				}*/
			}			
		} //for tickers
		
		//normalize the economic and benchmark data
		
		double[] mins = new double[8];
		double[] maxs = new double[8];
		for(int i = 0; i < 8; ++i){
			maxs[i] = Double.MIN_VALUE;
			mins[i] = Double.MAX_VALUE;
		}
		
		for	(DailyData d: dailyData){
			//there are 8 macroeconomic features to normalize
			/*	bankRate
		        cpi
		        cadUSD
		        oilUSD
		        goldUSD
		        employment
		        householdGDP
		        indExpGDP
			 * */
			if(d.bankRate > maxs[0]) maxs[0] = d.bankRate;
			if(d.bankRate < mins[0] && d.bankRate != 0.0) mins[0] = d.bankRate;
			if(d.cpi > maxs[1]) maxs[1] = d.cpi;
			if(d.cpi < mins[1] && d.cpi != 0.0) mins[1] = d.cpi;
			if(d.cadUSD > maxs[2]) maxs[2] = d.cadUSD;
			if(d.cadUSD < mins[2] && d.cadUSD != 0.0) mins[2] = d.cadUSD;
			if(d.oilUSD > maxs[3]) maxs[3] = d.oilUSD;
			if(d.oilUSD < mins[3] && d.oilUSD != 0.0 ) mins[3] = d.oilUSD;
			if(d.goldUSD > maxs[4]) maxs[4] = d.goldUSD;
			if(d.goldUSD < mins[4] && d.goldUSD != 0.0) mins[4] = d.goldUSD;
			if(d.employment > maxs[5]) maxs[5] = d.employment;
			if(d.employment < mins[5] && d.employment != 0.0) mins[5] = d.employment;
			if(d.householdGDP > maxs[6]) maxs[6] = d.householdGDP;
			if(d.householdGDP < mins[6] && d.householdGDP != 0.0) mins[6] = d.householdGDP;
			if(d.indExpGDP > maxs[7]) maxs[7] = d.indExpGDP;
			if(d.indExpGDP < mins[7] && d.indExpGDP != 0.0) mins[7] = d.indExpGDP;
		}
		
		for	(DailyData d: dailyData){
			if(d.bankRate != 0.0) d.bankRate = MAPSIZE * (d.bankRate - mins[0]) / (maxs[0] - mins[0]);
			if(d.cpi != 0.0) d.cpi = MAPSIZE * (d.cpi - mins[1]) / (maxs[1] - mins[1]);
			if(d.cadUSD != 0.0) d.cadUSD = MAPSIZE * (d.cadUSD - mins[2]) / (maxs[2] - mins[2]);
			if(d.oilUSD != 0.0) d.oilUSD = MAPSIZE * (d.oilUSD - mins[3]) / (maxs[3] - mins[3]);
			if(d.goldUSD != 0.0) d.goldUSD = MAPSIZE * (d.goldUSD - mins[4]) / (maxs[4] - mins[4]);
			if(d.employment != 0.0) d.employment = MAPSIZE * (d.employment - mins[5]) / (maxs[5] - mins[5]);
			if(d.householdGDP != 0.0) d.householdGDP = MAPSIZE * (d.householdGDP - mins[6]) / (maxs[6] - mins[6]);
			if(d.indExpGDP != 0.0) d.indExpGDP = MAPSIZE * (d.indExpGDP - mins[7]) / (maxs[7] - mins[7]);		
		}
		
	}	
	
	//////////////////// RANDOM GAUSSIAN DATA TESTING
	public void generateGaussianData(){
		for(int i = 0; i < NUMDATA; ++i){
			double count = 0.0;
			double[] d = new double[NUMFEATURES];
			for(int j = 0; j < NUMFEATURES; ++j){
				d[j] = Math.max(0.0,  Math.min(((rand.nextGaussian() * 15) + MAPSIZE/2), MAPSIZE-1));
				count += d[j];
			}
			count /= NUMFEATURES;
			int theClass;
			if(count < MAPSIZE/6) theClass = 0;
			else if(count < MAPSIZE/4) theClass = 1;
			else if(count < MAPSIZE/2) theClass = 2;
			else theClass = 3;
			data.put(d, theClass);
		}
	}
	
	//This was used for testing and creates a crude 2-dimensional image of the mapdata and neurons
	public void printmap(){
		double[][] mapdensity = new double[MAPSIZE][MAPSIZE];
		char[][] mapimage = new char[MAPSIZE][MAPSIZE];
		for(KNeuron n: map){
			int i = (int) n.features[0];
			int j = (int) n.features[1];
			mapdensity[i][j]++;
		}
		
		for(int i = 0; i < MAPSIZE; ++i) for(int j = 0; j < MAPSIZE; ++j) mapimage[i][j] = '.';
		
		for(double[] d: data.keySet()){
			int i = (int) d[0];
			int j = (int) d[1];
			if(data.get(d) == 1) mapimage[i][j] = '/';
			if(data.get(d) == 2) mapimage[i][j] = '\\';
			if(data.get(d) == 3) mapimage[i][j] = '*';
			else mapimage[i][j] = 'o';
		}
		
		for(int i = 0; i < MAPSIZE; ++i){ 
			for(int j = 0; j < MAPSIZE; ++j){
				if(mapdensity[i][j] > 5) mapimage[i][j] = '█';//System.out.print("█");
				else if(mapdensity[i][j] > 3) mapimage[i][j] = '▒';//System.out.print("▒");
				else if(mapdensity[i][j] > 0) mapimage[i][j] = '░';//System.out.print("░");
				//else mapimage[i][j] = //System.out.print(".");
				System.out.print(mapimage[i][j]);
			}
			System.out.println(":");	
		}
	}
	
	//restructures the neuron tree -- note that I am using a small enough number of neurons that it is feasible to just
	//search them all.
	public void restructure(){
		double[] allmin = new double[NUMFEATURES];
		double[] allmax = new double[NUMFEATURES];
		for(int i = 0; i < NUMFEATURES; ++i){
			allmin[i] = 0.0;
			allmax[i] = 0.0 + MAPSIZE;
		}
				
		KNeuron newRoot = restructGet(allmin, allmax);
		map.remove(newRoot);
		root = newRoot;
		
		//topologically sort in map
		rAdd(root);				
	}
	
	public KNeuron restructGet(double[] mins, double[] maxs){
		if(map.isEmpty()) return null;
		
		double[] mid = new double[mins.length];
		for(int i = 0; i < mins.length; ++i){
			mid[i] = 0.5 * (mins[i] + maxs[i]);
		}
		
		KNeuron ret = getBMU(mid);
		map.remove(ret);
		
		ret.children.add(restructGet(mid, maxs));
		ret.children.add(restructGet(mins, mid));
				
		return ret;
	}
	
	//prototype training method for arbitrary data
	public void train(){
		double dist = MAPSIZE;
		double lambda = INIT_LAMBDA;
		
		ArrayList<double[]> indexVals = new ArrayList<>();
		for(double[] d: data.keySet()) indexVals.add(d);
		Collections.shuffle(indexVals);
		
		for(int i = 0; i < TRAININGSTEPS; ++i){
		
			//1. Each node's weights are initialized.
			for(KNeuron n: allNeurons){
				for(double d: n.features){
					d = rand.nextDouble() * MAPSIZE;
				}
			}
			//2. A vector is chosen at random from the set of training data and presented to the lattice.
			for(double[] d: indexVals){
				//3. Every node is examined to calculate which one's weights are most like the input vector. The winning node is commonly known as the Best Matching Unit (BMU).
				KNeuron bmu = getBMU(d);
				//bmu.theClass = data.get(d);
				//4. The radius of the neighbourhood of the BMU is now calculated. This is a value that starts large, typically set to the 'radius' of the lattice,  but diminishes each time-step. Any nodes found within this radius are deemed to be inside the BMU's neighbourhood.
				//5. Each neighbouring node's (the nodes found in step 4) weights are adjusted to make them more like the input vector. The closer a node is to the BMU, the more its weights get altered.
				for(KNeuron k: map){
					if(k == bmu) continue;
					if(isNeighbor(bmu, k, dist)){
						moveTo(k.features, d, lambda);
					}
				}
				lambda *= LAMBDA_DECAY_RATE;
				dist *= NEIGHBORHOOD_DECAY_RATE;
				
				//6. Repeat step 2 for N iterations.
			}
		}
		
		for(KNeuron k: map){
			double[] best = indexVals.get(0);
			double bestdist = Double.MAX_VALUE;
			for(double[] d: data.keySet()){
				double distance = getDist(d, k.features);
				if(distance < bestdist){
					bestdist = distance;
					best = d;
				}
			}
			k.theClass = data.get(best);
		}				
	}

	
	
}


//////////////Scrap from main() for testing with random gaussian data
//for(DailyData d: dailyData){
//System.out.println(d.cpi);
//System.out.println(d.divs.get("BMO"));
//}

/*
//System.out.println("Generate random data");
generateGaussianData();

//verify the random data
for(double[] d: data.keySet()){
for(int i = 0; i < d.length; ++i){
	System.out.print(String.format("[%2.2f] ", d[i]));
}
System.out.print(" ===> [" + data.get(d) + "]");
System.out.println();
}*/

////////////// This is for testing with random gaussian data
/*
train();
for(KNeuron n: map) System.out.println(n);
printmap();
test();
*/

//////SCRAP FROM TESTING WITH RANDOM GAUSSIAN DATA
	/*int score = 0;
	for(int i = 0; i < 100; ++i){
		double[] sample = new double[NUMFEATURES];
		double count = 0.0;
		for(int j = 0; j < NUMFEATURES; ++j){
			sample[j] = Math.max(0.0,  Math.min(((rand.nextGaussian() * 15) + MAPSIZE/2), MAPSIZE-1));
			count += sample[j];
		}
		count /= NUMFEATURES;
		int theClass;
		if(count < MAPSIZE/6) theClass = 0;
		else if(count < MAPSIZE/4) theClass = 1;
		else if(count < MAPSIZE/2) theClass = 2;
		else theClass = 3;
		int result = classify(sample);
		if(result == theClass) score++;
	
	}
	
	System.out.println("Got " + score + "% correct!");*/
