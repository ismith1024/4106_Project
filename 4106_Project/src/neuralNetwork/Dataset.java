package neuralNetwork;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import featureEngineering.DailyData;

public class Dataset{
	ArrayList<DailyData> dailyData;
	ArrayList<DailyData> testing;
	HashMap<Long, DailyData> indexData;
	HashSet<String> tickers;
		
	//// For datafeed
	static Connection database;
	static Statement stat;
	
	public static final int MAPSIZE = 100;
	
	public static final int FIVEFOLD_SET = 4;
	
	public Dataset(){
		dailyData = new ArrayList<>();
		indexData = new HashMap<>();
		tickers = new HashSet<>();
	}
	
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
	    		//if(rs.getString("knownClass") != null && rs.getString("knownClass").equals("w4")) System.out.println("Got w4");
	        }
	        rs.close();
		}
		catch(SQLException e){
			e.printStackTrace();
		}
		
		dailyData.sort((DailyData d1, DailyData d2) -> {return d1.date.compareTo(d2.date);} );
	}
	
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
			//System.out.println(sqlQueryString);
			stat.executeUpdate(sqlQueryString);			
		}
		catch(SQLException e){
			e.printStackTrace();
		}*/
		
		try{			
			sqlQueryString = "UPDATE classifierResults SET nnFundamentals = '" + classifierClass + "' where year = " + y + " AND month = " + m + " AND day = " + d + " AND ticker = '" + tick + "';";
			//System.out.println(sqlQueryString);
			stat.executeUpdate(sqlQueryString);			
		}
		catch(SQLException e){
			e.printStackTrace();
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
			e.printStackTrace();
		}*/
		
		try{			
			sqlQueryString = "UPDATE classifierResults SET nnEconomics = '" + classifierClass + "' where year = " + y + " AND month = " + m + " AND day = " + d + " AND ticker = '" + tick + "'";
			//System.out.println(sqlQueryString);
			stat.executeUpdate(sqlQueryString);			
		}
		catch(SQLException e){
			e.printStackTrace();
		}
	}
	
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
	
	//Normalizes the data to range of 0-100
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
}