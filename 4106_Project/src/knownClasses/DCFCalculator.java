package knownClasses;

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

public class DCFCalculator {
	/*This class will calculate the "real value" in the present time based on discounted cash flow calculations.
	*  The formula is:
	*  
	*  v(0) = [Sigma(t=0 : t=T) [d(t) /(1+R)^n]] + (P (1 + G)^n/(1 + R)n]
	*    
	*  v(0) = value at current time
	*  t = time variable
	*  R = discount rate (target rate of return)
	*  n = number of discrete time periods in the future (e.g. months) t is, for compounding purposes
	*  T = time at which position is sold
	*  P = "fair" P/E (calculated emperically or estimated)
	*  G = "fair" growth (calculated emperically or estimated)
	*  
	*  Ref:
	*  http://www.moneychimp.com/articles/finworks/fmvaluation.htm
	*  
	*  Note: The accuracy by which P and G can be estimated affects this computation.  For this reason, 
	*  DCF fails as a valuation tool in cases where the business is not predictable, explaining why 
	*  this project limits itself to boring business sectors!
	*/
	static final double TARGET_RETURN = 0.2; //if the ROR can meet this, buy more
	static final double STOP_LOSS = 0.30; //if we lose this much, we sell
	static final double CALL_LIMIT = 0.15; //if the ROR does not exceed this, we can sell calls
	static final int PERIOD_START = 700;
	static final int PERIOD_END = 1000;
	static Connection database;
	static Statement stat;
	
	ArrayList<DailyData> data;
	HashMap<Long, DailyData> indexData;
	HashMap<String, Double> estGrowth;
	HashMap<String, Double> estP_E;
	HashSet<String> tickers;
	
	
	public DCFCalculator(){
		data = new ArrayList<>();
		indexData = new HashMap<>();
		estGrowth = new HashMap<>();
		estP_E = new HashMap<>();
		tickers = new HashSet<>();
	}
	
	public static void main(String[] args){
		DCFCalculator theCalc = new DCFCalculator();
		theCalc.run();
	}
	
	public void run(){
		setupDB();
		getData();
		findKnownClasses();
		writeData();
	}
	
	/* DCF Calculation to find value at a time based on known future data.
	 * Time will be measured in number of business days.  We will work with 250 working days per year.
	 * Interest is divided by 250 to find daily interest.
	 * 
	 * */
	public void findKnownClasses(){
		//get the "fair" growth and p-e for each ticker
		double estGrowth = 0.0;
		double estPE = 0.0;
		int count = 0;
		
		for(String tick: tickers){
			estGrowth = 0.0;
			estPE = 0.0;
			count = 0;
			for(DailyData d: data){
				if(d.p_e.get(tick) != null && d.p_e.get(tick) > 0.0){
					count ++;
					estPE += d.p_e.get(tick);
				}
			}
			estP_E.put(tick, (estPE/count));
			//System.out.println("Estimate " + tick + " P-E at: "+ (estPE/count) + " sum: " + estPE);
		}
		
		double dailyInterest = 1 + (TARGET_RETURN/250);
		System.out.println("Daily interest: " + dailyInterest);
		//go through all dailydata: 
		//look at each ticker:
		//look at all future days over the target holding period and check price thresholds
		//assign the ticker a class in classes depending on the rules
		//Rule 1: If at least one trading day in the holding period's DCF exceeds the (aggressive) target ROR, w1 (buy more)
		//Rule 2: Else, if any future price drops by STOP_LOSS, w4 (sell)
		//Rule 3: Else, if no future price exceeds CALL_LIMIT, w3 (sell calls)
		//Else, w2 (sit around and reinvest dividends)
		
		System.out.println("DAta size: " + data.size());
		
		for(int i = 0; i < data.size(); ++i){
			DailyData dp = data.get(i);
			
			//for each price data point we have for that day
			for(String tick: dp.price.keySet()){
				//figure out the DCF for holding period
				double discounted_div = 0.0;
				for(int j = i + PERIOD_START; j < i + PERIOD_END && j < data.size(); ++j){
					DailyData futureData = data.get(j);
					//System.out.println("look at " + futureData.year + "." + futureData.month + "." + futureData.day);
					
					//add the future dividend payment at the discount rate
					if(futureData.divs.get(tick) != null && futureData.divs.get(tick) != 0 ){
						discounted_div += futureData.divs.get(tick) * 1.0/Math.pow(dailyInterest, (j-i));
					}
					
					double futurePrice = 0.0;
					double non_disc = 0.0;
					int extraday = 0;
					DailyData d = null;
					if(futureData.price.get(tick) != null){
						futurePrice = futureData.price.get(tick) * 1.0/Math.pow(dailyInterest, (j-i)) + discounted_div;
						non_disc =  futureData.price.get(tick);
					} else{
						while(data.get(j + extraday).price.get(tick) == null){
							extraday--;
						}
						futurePrice = data.get(j + extraday).price.get(tick) * 1.0/Math.pow(dailyInterest, (j-i)) + discounted_div;
						non_disc = data.get(j + extraday).price.get(tick);
					}
					
					if(futurePrice >= dp.price.get(tick)){
						dp.classes.put(tick, "w1");
						
						//System.out.println("Found: " + tick + " " + dp.year + "-" + dp.month + "-" + dp.day + "at: " + dp.price.get(tick) +"   Sell on -" + data.get(j + extraday).year + "-" + data.get(j + extraday).month + "-" + data.get(j + extraday).day + " at " + data.get(j + extraday).price.get(tick) + " -- discount factor " + 1.0/Math.pow(dailyInterest, (j-i)) + "Return: " +  futurePrice + "/" + dp.price.get(tick) + "=" + (futurePrice + discounted_div)/dp.price.get(tick));
						j = data.size();
						//TODO: STOP_LOSS NEEDS TO BE DAILY?
					} else if(non_disc < dp.price.get(tick) * (1-STOP_LOSS)){
						dp.classes.put(tick, "w4");
						j = data.size();
					} else if(non_disc >= dp.price.get(tick) * (1 + CALL_LIMIT)){
						dp.classes.put(tick, "w2");
					} else if(!dp.classes.keySet().contains(tick)){
						dp.classes.put(tick, "w3");
					}					
				} //end for future data
				
				//if(dp.classes.get(tick) != null && dp.year < 2010 && tick.equals("BNS")) System.out.println("Classified: " + tick + " " + DailyData.df.format(dp.date) + dp.classes.get(tick)); 
			}//end for each ticker in day	
			
		} //end for each day		
		

	}
	
	public void writeData(){
		try {
			database.setAutoCommit(false);
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		for(DailyData d: data){
			for(String s: d.classes.keySet()){
				try{
						if(d.classes.get(s) != null){
							String sqlString = "UPDATE corporate SET knownClass = '" + d.classes.get(s) + "' WHERE ticker = '" + s + "' AND year = " + d.year + " AND month = " + (d.month +1) + " AND day = " + d.day + ";";
							System.out.println(sqlString);
							stat.executeUpdate(sqlString);
						}
					}				
					
					catch(SQLException e){
						e.printStackTrace();
						//System.exit(1);
					}
			}
		}
		
		try {
			database.setAutoCommit(true);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			database.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
	
	}
	
	public void getData(){
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
	        		data.add(dp);
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
	    		dp.divs.put(ticker, rs.getDouble("dividend"));
	        }
	        rs.close();
		}
		catch(SQLException e){
			e.printStackTrace();
		}
		
		data.sort((DailyData d1, DailyData d2) -> {return d1.date.compareTo(d2.date);} );
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

}
