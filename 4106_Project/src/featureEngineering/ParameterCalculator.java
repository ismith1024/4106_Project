package featureEngineering;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class ParameterCalculator {
	/* This class calculates the features as follows:
	 * 
	 *  P/E: Price-to-earnings; this is the share price divided by the earnings over the most recently concluded 12-month period.
	 *  Dividend yield: Most recent dividends paid out over the last 12-month period divided by share price  
	 *  MA200: Share price minus the 200-day moving average price
	 *  MA20: Share price minus the 20-day moving average
	 *  Beta: Measure of volatility of the stock relative to benchmark (TSX here)
	 *  	Beta(t) = cov(S(t) I(t)) / var(I(t)
	 *  			Where S(t) is the vector containing daily % changes of the security
	 *  			And I(t) is the vector containing the daily % changes of the index
	 *  		ref: 
	 *  		https://www.fool.com/knowledge-center/how-to-calculate-the-beta-coefficient-for-a-single.aspx
	 *  Volume: Number of shares traded in a given day
	 *  Relative strength: S(t) / I(t)
	 *  	Where S(t) is percent change in the security over a period of time
	 *  	I(t) is the percent change in the index over the same period
	 *  	ref:
	 *  	http://www.investopedia.com/articles/trading/08/relative-strength.asp
	 *  	 
	 *  Annual growth is not considered, as this is provides the same data as Annual Growth 
	 * 
	 * 
	 * */
	
	ArrayList<DailyData> data;
	HashMap<Long, DailyData> indexData;
	static Connection database;
	static Statement stat;
	
	public ParameterCalculator(){
		
		data = new ArrayList<>();
		indexData = new HashMap<>();
	}
	
	public static void main(String[] args){
		ParameterCalculator theCalc = new ParameterCalculator();
		theCalc.run();		
	}
	
	public void run(){
		getData();
		
		calcFeatures();
		
		for(DailyData d: data){
			System.out.println(d);
		}
		
		writeFeatures();		
	}
	
	public void getReturn(String ticker){
		
		for(int i = 0; i < (data.size() - 200); ++i){
			double[] x = new double[200];
			double[] y = new double[200];
			for(int j = 0; j < 200; ++j){
				x[j] = j;
				y[j] = Math.log(data.get(i + j).price.get(ticker));
				LinearRegression reg = new LinearRegression(x, y);
				double val = Math.exp(reg.predict(199));
				data.get(i+j).returns.put(ticker,  val);
			}
			
		}
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
		
		//ADSamplePoint pt;
			
		try{
			//first get and load the TSX information
			
			String sqlQueryString = "SELECT * FROM benchmarks";
			//System.out.println(sqlQueryString);			
			ResultSet rs = stat.executeQuery(sqlQueryString);
			
			DailyData pt;
			
	        while (rs.next()) {
	        	pt = new DailyData(rs.getInt("year"), rs.getInt("month"), rs.getInt("day"));
	        	pt.setTsx(rs.getDouble("tsx"));
	        	data.add(pt);
	        	indexData.put(pt.date.getTime(),  pt);
	        }
	        
	        data.sort((DailyData a, DailyData b) -> {return a.CompareTo(b);});
			
		}
		catch(SQLException e){
			e.printStackTrace();			
		}
		
		//get the macroeconomic data
		try{
		
			String sqlQueryString = "SELECT * FROM macroeconomics";
			//System.out.println(sqlQueryString);			
			ResultSet rs2 = stat.executeQuery(sqlQueryString);
			
			Date dt;
			
	        while (rs2.next()) {
	        	for(DailyData d: data){
	        	//long theDate = new Date(rs2.getInt("year") - 1900, rs2.getInt("month"), rs2.getInt("day")).getTime();
	        	//DailyData d = indexData.get(theDate);
	        		if(d.year == rs2.getInt("year") && d.month == rs2.getInt("month")){
			        	d.setBankRate(rs2.getDouble("bankRate"));
			        	d.setCpi(rs2.getDouble("cpi"));
			        	d.setCadUSD(rs2.getDouble("cadUsd"));
			        	d.setOilUSD(rs2.getDouble("oilUsd"));
			        	d.setGoldUSD(rs2.getDouble("goldUsd"));
			        	d.setEmployment(rs2.getDouble("employment"));
			        	d.setHouseholdGDP(rs2.getDouble("householdGDP"));
			        	d.setIndExpGDP(rs2.getDouble("indExpGDP"));
		        	}
	        	}
	        }
	        
	        //data.sort((DailyData a, DailyData b) -> {return a.CompareTo(b);});
			
		}
		catch(SQLException e){
			e.printStackTrace();			
		}
	
		
		//get the financial data
		try{
			
			String sqlQueryString = "SELECT * FROM corporate";

			ResultSet rs3 = stat.executeQuery(sqlQueryString);
			
			Date dt;
			
	        while (rs3.next()) {
	        	long theDate = new Date(rs3.getInt("year") - 1900, rs3.getInt("month") -1, rs3.getInt("day")).getTime();
	        	DailyData d = indexData.get(theDate);
	        	if(d == null){
	        		DailyData d2 = null;
	        		int i = 0;
	        		do{
	        			i++;
	        			long yesterday = new Date(rs3.getInt("year") - 1900, rs3.getInt("month") -1, rs3.getInt("day") -i).getTime();
	        			d2 = indexData.get(yesterday);
	        		} while(d2 == null);
	        		DailyData newData = new DailyData(rs3.getInt("year") - 1900, rs3.getInt("month") -1, rs3.getInt("day"));
	        		newData.tsx = d2.tsx;
	        		newData.bankRate = d2.bankRate;
	        		newData.cpi = d2.cpi;
	        		newData.cadUSD = d2.cadUSD;
	        		newData.oilUSD = d2.oilUSD;
	        		newData.goldUSD = d2.goldUSD;
	        		newData.employment = d2.employment;
	        		newData.householdGDP = d2.householdGDP;
	        		newData.indExpGDP = d2.indExpGDP;
	        		indexData.put(theDate, newData);
	        		data.add(data.indexOf(d2), newData);
	        		System.out.println("Adding data for " + newData);
	        		d = newData;
	        	} 
	        	String tick = rs3.getString("ticker");
	        	d.price.put(tick, rs3.getDouble("price"));
	        	d.volume.put(tick, rs3.getDouble("volume"));
	        	
	        	try{d.earnings.put(tick, rs3.getDouble("eps"));} catch( SQLException e){System.out.println(e.getMessage());}
	        	try{d.divs.put(tick, rs3.getDouble("dividend"));} catch( SQLException e){System.out.println(e.getMessage());}

	        	
	        	/*switch(rs3.getString("ticker"){
	        		case StringRef.bmo: break;
	        		case StringRef.bns: break;
	        		case StringRef.
	        	}*/
	        	

	        	//}
	        	
	        }
	        
			
		}
		catch(SQLException e){
			e.printStackTrace();			
		}
	
	}
	
	public void calcFeatures(){
		data.sort((DailyData a, DailyData b) -> {return a.CompareTo(b);});
		
		//HashMap<String, Double> recentDiv = new HashMap<>();
		//HashMap<String, Double> recentEarn = new HashMap<>();
		
		//////////////// Compute EPS and P-E
		
		ArrayList<Double> recentDivBMO = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentDivBMO.add(0.0);
		ArrayList<Double> recentEarnBMO = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentEarnBMO.add(0.0);
		ArrayList<Double> recentDivBNS = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentDivBNS.add(0.0);
		ArrayList<Double> recentEarnBNS = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentEarnBNS.add(0.0);
		ArrayList<Double> recentDivENB = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentDivENB.add(0.0);
		ArrayList<Double> recentEarnENB = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentEarnENB.add(0.0);
		ArrayList<Double> recentDivTRP = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentDivTRP.add(0.0);
		ArrayList<Double> recentEarnTRP = new ArrayList<>();
		for(int i = 0; i < 4; ++i)recentEarnTRP.add(0.0);
		ArrayList<Double> recentDivCNR = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentDivCNR.add(0.0);
		ArrayList<Double> recentEarnCNR = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentEarnCNR.add(0.0);
		ArrayList<Double> recentDivCP = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentDivCP.add(0.0);
		ArrayList<Double> recentEarnCP = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentEarnCP.add(0.0);
		ArrayList<Double> recentDivSLF = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentDivSLF.add(0.0);
		ArrayList<Double> recentEarnSLF = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentEarnSLF.add(0.0);
		ArrayList<Double> recentDivMFC = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentDivMFC.add(0.0);
		ArrayList<Double> recentEarnMFC = new ArrayList<>();
		for(int i = 0; i < 4; ++i) recentEarnMFC.add(0.0);
		
		double rdBMO = 0.0;
		double reBMO = 0.0;
		double rdBNS = 0.0;
		double reBNS = 0.0;
		double rdENB = 0.0;
		double reENB = 0.0;
		double rdTRP = 0.0;
		double reTRP = 0.0;
		double rdCNR = 0.0;
		double reCNR = 0.0;
		double rdCP = 0.0;
		double reCP = 0.0;
		double rdSLF = 0.0;
		double reSLF = 0.0;
		double rdMFC = 0.0;
		double reMFC = 0.0;
				
		for(DailyData d: data){
			if(d.divs.get("BMO") != null && d.divs.get("BMO") != 0.0) {
				//System.out.println("Add div : " + d.divs.get("BMO"));
				recentDivBMO.add(0, d.divs.get("BMO"));
				//System.out.println("Remove div: " + recentDivBMO.get(4));
				recentDivBMO.remove(4);
				double s = 0.0;
				for(double dd: recentDivBMO){
					s += dd; // / 4.0;
				}
				rdBMO = s;
				//System.out.println("rdBMO " + rdBMO );
			}
			if(d.earnings.get("BMO") != null && d.earnings.get("BMO") != 0.0){
				recentEarnBMO.add(0, d.earnings.get("BMO"));
				recentEarnBMO.remove(4);
				double s = 0.0;
				for(double dd: recentEarnBMO){
					s += dd; // / 4.0;
				}
				reBMO = s;
			}
			if(d.divs.get("BNS") != null && d.divs.get("BNS") != 0.0){
				recentDivBNS.add(0, d.divs.get("BNS"));
				recentDivBNS.remove(4);
				double s = 0.0;
				for(double dd: recentDivBNS){
					s += dd; // / 4.0;
				}
				rdBNS = s;
			}
			if(d.earnings.get("BNS") != null && d.earnings.get("BNS") != 0.0){
				recentEarnBNS.add(0, d.earnings.get("BNS"));
				recentEarnBNS.remove(4);
				double s = 0.0;
				for(double dd: recentEarnBNS){
					s += dd; // / 4.0;
				}
				reBNS = s;
			}
			if(d.divs.get("ENB") != null && d.divs.get("ENB") != 0.0){
				recentDivENB.add(0, d.divs.get("ENB"));
				recentDivENB.remove(4);
				double s = 0.0;
				for(double dd: recentDivENB){
					s += dd; // / 4.0;
				}
				rdENB = s;
			}
			if(d.earnings.get("ENB") != null && d.earnings.get("ENB") != 0.0){
				recentEarnENB.add(0, d.earnings.get("ENB"));
				recentEarnENB.remove(4);
				double s = 0.0;
				for(double dd: recentEarnENB){
					s += dd; // / 4.0;
				}
				reENB = s;
			}
			if(d.divs.get("TRP") != null && d.divs.get("TRP") != 0.0){
				recentDivTRP.add(0, d.divs.get("TRP"));
				recentDivTRP.remove(4);
				double s = 0.0;
				for(double dd: recentDivTRP){
					s += dd; // / 4.0;
				}
				rdTRP = s;
			}
			if(d.earnings.get("TRP") != null && d.earnings.get("TRP") != 0.0){
				recentEarnTRP.add(0, d.earnings.get("TRP"));
				recentEarnTRP.remove(4);
				double s = 0.0;
				for(double dd: recentEarnTRP){
					s += dd; // / 4.0;
				}
				reTRP = s;
			}
			if(d.divs.get("CNR") != null && d.divs.get("CNR") != 0.0){
				recentDivCNR.add(0, d.divs.get("CNR"));
				recentDivCNR.remove(4);
				double s = 0.0;
				for(double dd: recentDivCNR){
					s += dd; // / 4.0;
				}
				rdCNR = s;
			}
			if(d.earnings.get("CNR") != null && d.earnings.get("CNR") != 0.0){
				recentEarnCNR.add(0, d.earnings.get("CNR"));
				recentEarnCNR.remove(4);
				double s = 0.0;
				for(double dd: recentEarnCNR){
					s += dd; // / 4.0;
				}
				reCNR = s;
			}
			if(d.divs.get("CP") != null && d.divs.get("CP") != 0.0){
				recentDivCP.add(0, d.divs.get("CP"));
				recentDivCP.remove(4);
				double s = 0.0;
				for(double dd: recentDivCP){
					s += dd; // / 4.0;
				}
				rdCP = s;
			}
			if(d.earnings.get("CP") != null && d.earnings.get("CP") != 0.0){
				recentEarnCP.add(0, d.earnings.get("CP"));
				recentEarnCP.remove(4);
				double s = 0.0;
				for(double dd: recentEarnCP){
					s += dd; // / 4.0;
				}
				reCP = s;
			}
			if(d.divs.get("SLF") != null && d.divs.get("SLF") != 0.0){
				recentDivSLF.add(0, d.divs.get("SLF"));
				recentDivSLF.remove(4);
				double s = 0.0;
				for(double dd: recentDivSLF){
					s += dd; // / 4.0;
				}
				rdSLF = s;
			}
			if(d.earnings.get("SLF") != null && d.earnings.get("SLF") != 0.0){
				recentEarnSLF.add(0, d.earnings.get("SLF"));
				recentEarnSLF.remove(4);
				double s = 0.0;
				for(double dd: recentEarnSLF){
					s += dd;// / 4.0;
				}
				reSLF = s;
			}
			if(d.divs.get("MFC") != null && d.divs.get("MFC") != 0.0){
				recentDivMFC.add(0, d.divs.get("MFC"));
				recentDivMFC.remove(4);
				double s = 0.0;
				for(double dd: recentDivMFC){
					s += dd; // / 4.0;
				}
				rdMFC = s;
			}
			if(d.earnings.get("MFC") != null && d.earnings.get("MFC") != 0.0){
				recentEarnMFC.add(0, d.earnings.get("MFC"));
				recentEarnMFC.remove(4);
				double s = 0.0;
				for(double dd: recentEarnMFC){
					s += dd; // / 4.0;
				}
				reMFC = s;
			}
			
			
			if(recentDivBMO.get(3) != 0.0 && d.price.get("BMO") != null){
				d.div_yld.put("BMO", 100.00 * rdBMO / d.price.get("BMO"));
			}
			if(recentEarnBMO.get(3) != 0.0 && d.price.get("BMO") != null){
				d.p_e.put("BMO", d.price.get("BMO") / reBMO );
			}
			if(recentDivBNS.get(3) != 0.0 && d.price.get("BNS") != null){d.div_yld.put("BNS", 100.00 * rdBNS / d.price.get("BNS"));}
			if(recentEarnBNS.get(3) != 0.0 && d.price.get("BNS") != null){d.p_e.put("BNS", d.price.get("BNS") / reBNS );}
			if(recentDivENB.get(3) != 0.0 && d.price.get("ENB") != null){d.div_yld.put("ENB", 100.00 * rdENB / d.price.get("ENB"));}
			if(recentEarnENB.get(3) != 0.0 && d.price.get("ENB") != null){d.p_e.put("ENB", d.price.get("ENB") / reENB );}
			if(recentDivTRP.get(3) != 0.0 && d.price.get("TRP") != null){d.div_yld.put("TRP", 100.00 * rdTRP / d.price.get("TRP"));}
			if(recentEarnTRP.get(3) != 0.0 && d.price.get("TRP") != null){d.p_e.put("TRP", d.price.get("TRP") / reTRP );}
			if(recentDivCNR.get(3) != 0.0 && d.price.get("CNR") != null){d.div_yld.put("CNR", 100.00 * rdCNR / d.price.get("CNR"));}
			if(recentEarnCNR.get(3) != 0.0 && d.price.get("CNR") != null){d.p_e.put("CNR", d.price.get("CNR") / reCNR );}
			if(recentDivCP.get(3) != 0.0 && d.price.get("CP") != null){d.div_yld.put("CP", 100.00 * rdCP / d.price.get("CP"));}
			if(recentEarnCP.get(3) != 0.0 && d.price.get("CP") != null){d.p_e.put("CP", d.price.get("CP") / reCP );}
			if(recentDivSLF.get(3) != 0.0 && d.price.get("SLF") != null){d.div_yld.put("SLF", 100.00 * rdSLF / d.price.get("SLF"));}
			if(recentEarnSLF.get(3) != 0.0 && d.price.get("SLF") != null){d.p_e.put("SLF", d.price.get("SLF") / reSLF );}
			if(recentDivMFC.get(3) != 0.0 &&  d.price.get("MFC") != null){d.div_yld.put("MFC", 100.00 * rdMFC / d.price.get("MFC"));}
			if(recentEarnMFC.get(3) != 0.0 &&  d.price.get("MFC") != null){d.p_e.put("MFC", d.price.get("MFC") / reMFC );}
			
		}

		//////////////// Compute moving averages
		double BMOsum200 = 0.0;
		double BMOsum20 = 0.0;
		double BNSsum200 = 0.0;
		double BNSsum20 = 0.0;
		double ENBsum200 = 0.0;
		double ENBsum20 = 0.0;
		double TRPsum200 = 0.0;
		double TRPsum20 = 0.0;
		double CNRsum200 = 0.0;
		double CNRsum20 = 0.0;
		double CPsum200 = 0.0;
		double CPsum20 = 0.0;
		double SLFsum200 = 0.0;
		double SLFsum20 = 0.0;
		double MFCsum200 = 0.0;
		double MFCsum20 = 0.0;
		
		
		for(int i = 0; i < data.size(); ++i){
			Double BMO = data.get(i).price.get("BMO");
			Double BNS = data.get(i).price.get("BNS");
			Double ENB = data.get(i).price.get("ENB");
			Double TRP = data.get(i).price.get("TRP");
			Double CNR = data.get(i).price.get("CNR");
			Double CP = data.get(i).price.get("CP");
			Double SLF = data.get(i).price.get("SLF");
			Double MFC = data.get(i).price.get("MFC");
			
			if(BMO != null){
				BMOsum200 += BMO;
				BMOsum20 += BMO;
				//System.out.println("Date: " + DailyData.df.format((data.get(i).date)) + " BMO Price: " + BMO + " 20day Sum" + BMOsum20 + " avg: " + BMOsum20 / 20 );
			}
			
			if(BNS != null){
				BNSsum200 += BNS;
				BNSsum20 += BNS;
			}
			
			if(ENB != null){
				ENBsum200 += ENB;
				ENBsum20 += ENB;
			}
			
			if(TRP != null){
				TRPsum200 += TRP;
				TRPsum20 += TRP;
			}
			
			if(CNR != null){
				CNRsum200 += CNR;
				CNRsum20 += CNR;
			}
			
			if(CP != null){
				CPsum200 += CP;
				CPsum20 += CP;
			}
			
			if(SLF != null){
				SLFsum200 += SLF;
				SLFsum20 += SLF;
			}
			
			if(MFC != null){
				MFCsum200 += MFC;
				MFCsum20 += MFC;
			}
			
			if(i >= 20 && data.get(i-20).price.get("BMO") != null){ // && data.get(i).price.get("BMO") != null){
				BMOsum20 -= data.get(i-20).price.get("BMO");
				}
			if(data.get(i).price.get("BMO") != null){	
				data.get(i).ma20.put("BMO", data.get(i).price.get("BMO") - (BMOsum20 / 20));
				//System.out.println("BMO - date " + DailyData.df.format(data.get(i).date) + " Price: " + data.get(i).price.get("BMO") + " 20day sum: " + BMOsum20 + " 20day avg: " + (BMOsum20 / 20));
			} //else if (i - 20 >= 0) System.out.println("Cant remove!" + DailyData.df.format(data.get(i-20).date));
			
			if(i >= 200 && data.get(i-200).price.get("BMO") != null){ // && data.get(i).price.get("BMO") != null){
				BMOsum200 -= data.get(i-200).price.get("BMO");
				}
			if(	data.get(i).price.get("BMO") != null){
				data.get(i).ma200.put("BMO", data.get(i).price.get("BMO") - (BMOsum200 / 200));
			}
			
			if(i >= 20 && data.get(i-20).price.get("BNS") != null) // && 
				BNSsum20 -= data.get(i-20).price.get("BNS");
			if(	data.get(i).price.get("BNS") != null){
				data.get(i).ma20.put("BNS", data.get(i).price.get("BNS") - (BNSsum20 / 20));
			}
			
			if(i >= 200 && data.get(i-200).price.get("BNS") != null)// && 
				BNSsum200 -= data.get(i-200).price.get("BNS");
			if(data.get(i).price.get("BNS") != null){
				data.get(i).ma200.put("BNS", data.get(i).price.get("BNS") - (BNSsum200 / 200));
			}
			
			if(i >= 20 && data.get(i-20).price.get("ENB") != null) // && 
				ENBsum20 -= data.get(i-20).price.get("ENB");
			if(data.get(i).price.get("ENB") != null){
				data.get(i).ma20.put("ENB", data.get(i).price.get("ENB") - (ENBsum20 / 20));
			}
			
			if(i >= 200 && data.get(i-200).price.get("ENB") != null) // && 
				ENBsum200 -= data.get(i-200).price.get("ENB");
			if(data.get(i).price.get("ENB") != null){
				data.get(i).ma200.put("ENB", data.get(i).price.get("ENB") - (ENBsum200 / 200));
			}
			
			if(i >= 20 && data.get(i-20).price.get("TRP") != null) // &&
				TRPsum20 -= data.get(i-20).price.get("TRP");
			if( data.get(i).price.get("TRP") != null){
				data.get(i).ma20.put("TRP", data.get(i).price.get("TRP") - (TRPsum20 / 20));
			}
			
			if(i >= 200 && data.get(i-200).price.get("TRP") != null) // && 
				TRPsum200 -= data.get(i-200).price.get("TRP");
			if(data.get(i).price.get("TRP") != null){
				data.get(i).ma200.put("TRP", data.get(i).price.get("TRP") - (TRPsum200 / 200));
			}
			
			if(i >= 20 && data.get(i-20).price.get("CNR") != null) // && 
				CNRsum20 -= data.get(i-20).price.get("CNR");
			if(data.get(i).price.get("CNR") != null){
				data.get(i).ma20.put("CNR", data.get(i).price.get("CNR") - (CNRsum20 / 20));
			}
			
			if(i >= 200 && data.get(i-200).price.get("CNR") != null) // && 
				CNRsum200 -= data.get(i-200).price.get("CNR");
			if(data.get(i).price.get("CNR") != null){
				data.get(i).ma200.put("CNR", data.get(i).price.get("CNR") - (CNRsum200 / 200));
			}
			
			if(i >= 20 && data.get(i-20).price.get("CP") != null) // && 
				CPsum20 -= data.get(i-20).price.get("CP");
			if(data.get(i).price.get("CP") != null){
				data.get(i).ma20.put("CP", data.get(i).price.get("CP") - (CPsum20 / 20));
			}
			
			if(i >= 200 && data.get(i-200).price.get("CP") != null) // && 
				CPsum200 -= data.get(i-200).price.get("CP");
			if(data.get(i).price.get("CP") != null){
				data.get(i).ma200.put("CP", data.get(i).price.get("CP") - (CPsum200 / 200));
			}
			
			if(i >= 20 && data.get(i-20).price.get("SLF") != null) // && 
				SLFsum20 -= data.get(i-20).price.get("SLF");
			if(data.get(i).price.get("SLF") != null){
				data.get(i).ma20.put("SLF", data.get(i).price.get("SLF") - (SLFsum20 / 20));
			}
			
			if(i >= 200 && data.get(i-200).price.get("SLF") != null) // && 
				SLFsum200 -= data.get(i-200).price.get("SLF");
			if(data.get(i).price.get("SLF") != null){
				data.get(i).ma200.put("SLF", data.get(i).price.get("SLF") - (SLFsum200 / 200));
			}
			
			if(i >= 20 && data.get(i-20).price.get("MFC") != null)// && 
				MFCsum20 -= data.get(i-20).price.get("MFC");
			if(data.get(i).price.get("MFC") != null){
				data.get(i).ma20.put("MFC", data.get(i).price.get("MFC") - (MFCsum20 / 20));
			}
			
			if(i >= 200 && data.get(i-200).price.get("MFC") != null)// && 
				MFCsum200 -= data.get(i-200).price.get("MFC");
			if(data.get(i).price.get("MFC") != null){
				data.get(i).ma200.put("MFC", data.get(i).price.get("MFC") - (MFCsum200 / 200));
			}			
		}
		
/////COMPUTE BETA
		 /*  Beta: Measure of volatility of the stock relative to benchmark (TSX here)
		 *  	Beta(t) = cov(S(t) I(t)) / var(I(t))
		 *  			Where S(t) is the vector containing daily % changes of the security
		 *  			And I(t) is the vector containing the daily % changes of the index
		*/
		
		for(DailyData d: data){
			if(d.price.get("BMO") != null && d.price.get("BMO") != 0.0){
				double bet =  beta("BMO", data.indexOf(d));
				//System.out.println("BMO Beta on " + DailyData.df.format(d.date) + ": " + bet );
				d.beta.put("BMO", bet);
			}
			
			if(d.price.get("BNS") != null && d.price.get("BNS") != 0.0){
				double bet =  beta("BNS", data.indexOf(d));
				d.beta.put("BNS", bet);
			}
			
			if(d.price.get("ENB") != null && d.price.get("ENB") != 0.0){
				double bet =  beta("ENB", data.indexOf(d));
				d.beta.put("ENB", bet);
			}
			
			if(d.price.get("TRP") != null && d.price.get("TRP") != 0.0){
				double bet =  beta("TRP", data.indexOf(d));
				d.beta.put("TRP", bet);
			}
			
			if(d.price.get("CNR") != null && d.price.get("CNR") != 0.0){
				double bet =  beta("CNR", data.indexOf(d));
				d.beta.put("CNR", bet);
			}
			
			if(d.price.get("CP") != null && d.price.get("CP") != 0.0){
				double bet =  beta("CP", data.indexOf(d));
				d.beta.put("CP", bet);
			}
			
			if(d.price.get("SLF") != null && d.price.get("SLF") != 0.0){
				double bet =  beta("SLF", data.indexOf(d));
				d.beta.put("SLF", bet);
			}
			
			if(d.price.get("MFC") != null && d.price.get("MFC") != 0.0){
				double bet =  beta("MFC", data.indexOf(d));
				d.beta.put("MFC", bet);
			}
			
			
		}
		
		
		/////COMPUTE REL STR
		for(int i = 100; i < data.size(); ++i){
			double tsxPerf = data.get(i).tsx / data.get(i-100).tsx;
			if(data.get(i - 100).price.get("BMO") != null && data.get(i - 100).price.get("BMO") > 0 && data.get(i).price.get("BMO") != null){
				double BMOperf = data.get(i).price.get("BMO") / data.get(i - 100).price.get("BMO");
				data.get(i).relStr.put("BMO", BMOperf / tsxPerf);
			}
			
			if(data.get(i - 100).price.get("BNS") != null && data.get(i - 100).price.get("BNS") > 0 && data.get(i).price.get("BNS") != null ){
				double BNSperf = data.get(i).price.get("BNS") / data.get(i - 100).price.get("BNS");
				data.get(i).relStr.put("BNS", BNSperf / tsxPerf);
			}
			
			if(data.get(i - 100).price.get("ENB") != null && data.get(i - 100).price.get("ENB") > 0 && data.get(i).price.get("ENB") != null){
				double ENBperf = data.get(i).price.get("ENB") / data.get(i - 100).price.get("ENB");
				data.get(i).relStr.put("ENB", ENBperf / tsxPerf);
			}
			
			if(data.get(i - 100).price.get("TRP") != null && data.get(i - 100).price.get("TRP") > 0 && data.get(i).price.get("TRP")  != null){
				double TRPperf = data.get(i).price.get("TRP") / data.get(i - 100).price.get("TRP");
				data.get(i).relStr.put("TRP", TRPperf / tsxPerf);
			}
			
			if(data.get(i - 100).price.get("CNR") != null && data.get(i - 100).price.get("CNR") > 0 &&  data.get(i).price.get("CNR") != null ){
				double CNRperf = data.get(i).price.get("CNR") / data.get(i - 100).price.get("CNR");
				data.get(i).relStr.put("CNR", CNRperf / tsxPerf);
			}
			
			if(data.get(i - 100).price.get("CP") != null && data.get(i - 100).price.get("CP") > 0 && data.get(i).price.get("CP") != null){
				double CPperf = data.get(i).price.get("CP") / data.get(i - 100).price.get("CP");
				data.get(i).relStr.put("CP", CPperf / tsxPerf);
			}
			
			if(data.get(i - 100).price.get("SLF") != null && data.get(i - 100).price.get("SLF") > 0 && data.get(i).price.get("SLF") != null){
				double SLFperf = data.get(i).price.get("SLF") / data.get(i - 100).price.get("SLF");
				data.get(i).relStr.put("SLF", SLFperf / tsxPerf);
			}
			
			if(data.get(i - 100).price.get("MFC") != null && data.get(i - 100).price.get("MFC") > 0 && data.get(i).price.get("MFC") != null){
				double MFCperf = data.get(i).price.get("MFC") / data.get(i - 100).price.get("MFC");
				data.get(i).relStr.put("MFC", MFCperf / tsxPerf);
			}
			
			
			
			
		}
		
		
	}
	
   double getMean(ArrayList<Double> dat){
        double sum = 0.0;
        for(double a : dat)
            sum += a;
        return sum/dat.size();
    }
	
    double getVariance(ArrayList<Double> dat) {
        double mean = getMean(dat);
        double temp = 0;
        for(double a :dat)
            temp += (a-mean)*(a-mean);
        return temp/dat.size();
    }
    
    double beta(String tick, int startindex){
    	class DoubleDouble{
    		double d1;
    		double d2;
    		public DoubleDouble(double d1_, double d2_){d1 = d1_; d2 = d2_;}
    	}
    	
    	//keep the sample size sensible
    	if(startindex + 200 > data.size()) startindex = data.size() - 200;
    	
    	ArrayList<DoubleDouble> doubles = new ArrayList<>();
    	
    	for(int i = startindex; i < startindex + 200 && i < data.size(); ++i){
    		DailyData d = data.get(i);
    		if(d.tsx != 0.0 && d.price.get(tick) != null && d.price.get(tick) != 0){
    			doubles.add(new DoubleDouble(d.tsx, d.price.get(tick)));
    		}
    	}
    	
    	ArrayList<DoubleDouble> deltas = new ArrayList<>();
    	
    	for(int i = 1; i < doubles.size(); ++i){
    		double bmChange = (doubles.get(i).d1 - doubles.get(i-1).d1) / doubles.get(i-1).d1;
    		double secChange = (doubles.get(i).d2 - doubles.get(i-1).d2) / doubles.get(i-1).d2;
    		deltas.add(new DoubleDouble(bmChange, secChange));
    	}
    	
    	double bmTot = 0.0;
    	double secTot = 0.0;
    	for(DoubleDouble d: deltas){
    		bmTot += d.d1;
    		secTot += d.d2;
    	}
    	
    	//http://www.investopedia.com/terms/c/covariance.asp
    	
    	double bmMean = bmTot / deltas.size();
    	double secMean = secTot / deltas.size();
    	double cov = 0.0;
    	double var = 0.0;
    	
    	for(int i = 0; i < deltas.size(); ++i){
    		cov += (deltas.get(i).d1 - bmMean) * (deltas.get(i).d2 - secMean);
    		var += Math.pow((deltas.get(i).d1 - bmMean),2);
    	}
    	
    	if(deltas.size() == 0 || deltas.size() == 1) return 0;
    	cov /= deltas.size() -1;
    	var /= deltas.size();    	
    	
    	//System.out.println(" BM Mean:" + bmMean + " SecMean" + secMean + " Cov: " + cov + " Var: "+ var + " beta:" + cov/var);
    	
    	return cov/var;	
    }

	private void writeFeatures() {
		try {
			database.setAutoCommit(false);
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		for(DailyData d: data){
			for(String s: d.p_e.keySet()){
				try{
					if(d.p_e.get(s) != null){
						String sqlString = "UPDATE corporate SET p_e = " + d.p_e.get(s) + " WHERE ticker = '" + s + "' AND year = " + d.year + " AND month = " + d.month + " AND day = " + d.day + ";";
						System.out.println(sqlString);
						stat.executeUpdate(sqlString);
					}
					}				
					
					catch(SQLException e){
						e.printStackTrace();			
					}
			}
			
			for(String s: d.div_yld.keySet()){
				try{
					if(d.div_yld.get(s) != null){
						String sqlString = "UPDATE corporate SET div_yld = " + d.div_yld.get(s) + " WHERE ticker = '" + s + "' AND year = " + d.year + " AND month = " + d.month + " AND day = " + d.day + ";";
						System.out.println(sqlString);
						stat.executeUpdate(sqlString);
					}
					}				
					
					catch(SQLException e){
						e.printStackTrace();			
					}
			}
			
			for(String s: d.ma200.keySet()){
				try{
					if(d.ma200.get(s) != null){
						String sqlString = "UPDATE corporate SET ma200 = " + d.ma200.get(s) + " WHERE ticker = '" + s + "' AND year = " + d.year + " AND month = " + d.month + " AND day = " + d.day + ";";
						System.out.println(sqlString);
						stat.executeUpdate(sqlString);
					}
					}				
					
					catch(SQLException e){
						e.printStackTrace();			
					}
			}
			
			for(String s: d.ma20.keySet()){
				try{
					if(d.ma20.get(s) != null){
						String sqlString = "UPDATE corporate SET ma20 = " + d.ma20.get(s) + " WHERE ticker = '" + s + "' AND year = " + d.year + " AND month = " + d.month + " AND day = " + d.day + ";";
						System.out.println(sqlString);
						stat.executeUpdate(sqlString);
					}
					}				
					
					catch(SQLException e){
						e.printStackTrace();			
					}
			}
			
			for(String s: d.beta.keySet()){
				try{
					if(d.beta.get(s) != null){
						String sqlString = "UPDATE corporate SET beta = " + d.beta.get(s) + " WHERE ticker = '" + s + "' AND year = " + d.year + " AND month = " + d.month + " AND day = " + d.day + ";";
						System.out.println(sqlString);
						stat.executeUpdate(sqlString);
					}
				}				
			
				catch(SQLException e){
					e.printStackTrace();			
				}
			}
			
			for(String s: d.relStr.keySet()){
				try{
					if(d.relStr.get(s) != null){
						String sqlString = "UPDATE corporate SET relStr = " + d.relStr.get(s) + " WHERE ticker = '" + s + "' AND year = " + d.year + " AND month = " + d.month + " AND day = " + d.day + ";";
						System.out.println(sqlString);
						stat.executeUpdate(sqlString);
					}
				} 			
				catch(SQLException e){
					e.printStackTrace();			
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

}
